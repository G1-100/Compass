// Task board, task detail, and my tasks. Owner: Adam.
import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { tasksApi, type TaskInput } from '../../api/tasks';
import type { EffortLevel, Sprint, Task, TaskStatus } from '../../api/types';
import { EFFORT_POINTS } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import {
  daysUntil,
  DUE_BUCKET_LABEL,
  EFFORT_LABEL,
  EFFORT_ORDER,
  errorText,
  formatDate,
  groupByDue,
  PREVIEW_ME,
  PREVIEW_TASKS,
  shouldUsePreview,
  sprintName,
  STATUS_LABEL,
  STATUS_ORDER,
  useCurrentUser,
  useProjectContext,
} from './data';
import { AvatarStack, Caret, RiskPill, SearchIcon, StatusPill, TaskRow } from './pieces';
import { TaskDetailPanel } from './TaskDetailPanel';
import styles from './Tasks.module.css';

const PAGE_SIZE = 20;

type Tab = 'all' | 'backlog' | 'suggested';
type SortKey = 'dueDate' | 'effort' | 'sprint';

const isApproved = (task: Task) => task.suggestionStatus === null || task.suggestionStatus === 'APPROVED';

// ---------------------------------------------------------------------------
// Shared task list state: loading, preview fallback and status changes.
// ---------------------------------------------------------------------------

function useTaskCollection(load: () => Promise<Task[]>, previewTasks: () => Task[], deps: unknown[]) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [preview, setPreview] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    load()
      .then((items) => {
        if (!active) return;
        setTasks(items);
        setPreview(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        if (shouldUsePreview(err)) {
          setTasks(previewTasks());
          setPreview(true);
          console.warn('Tasks API is not available yet; showing design preview tasks.', err);
        } else {
          setError(errorText(err, 'Could not load tasks'));
        }
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const replace = useCallback((updated: Task) => {
    setTasks((current) => {
      const exists = current.some((t) => t.taskId === updated.taskId);
      return exists ? current.map((t) => (t.taskId === updated.taskId ? updated : t)) : [...current, updated];
    });
  }, []);

  const remove = useCallback((taskId: number) => setTasks((current) => current.filter((t) => t.taskId !== taskId)), []);

  async function changeStatus(task: Task, status: TaskStatus) {
    setError('');
    if (preview) {
      replace({ ...task, status, version: task.version + 1 });
      return;
    }
    try {
      replace(await tasksApi.updateStatus(task.taskId, status, task.version));
    } catch (err) {
      setError(errorText(err, 'Could not change status'));
    }
  }

  return { tasks, preview, loading, error, setError, replace, remove, changeStatus };
}

function useIsLead(teamId: number | undefined, members: { userId: number; role: string }[]) {
  const { roleIn } = useAuth();
  const me = useCurrentUser();
  const role = (teamId !== undefined ? roleIn(teamId) : null) ?? members.find((m) => m.userId === me.userId)?.role;
  return role === 'LEAD';
}

function sortTasks(tasks: Task[], key: SortKey, order: 'asc' | 'desc', sprints: Sprint[]): Task[] {
  const dir = order === 'asc' ? 1 : -1;
  const sprintIndex = (id: number | null) => {
    if (id === null) return Number.POSITIVE_INFINITY;
    const index = sprints.findIndex((s) => s.sprintId === id);
    return index === -1 ? id : index;
  };
  return [...tasks].sort((a, b) => {
    if (key === 'dueDate') {
      if (a.dueDate === b.dueDate) return 0;
      if (!a.dueDate) return 1; // no due date always last
      if (!b.dueDate) return -1;
      return a.dueDate < b.dueDate ? -dir : dir;
    }
    if (key === 'effort') return (EFFORT_POINTS[a.effort] - EFFORT_POINTS[b.effort]) * dir;
    return (sprintIndex(a.sprintId) - sprintIndex(b.sprintId)) * dir;
  });
}

// ---------------------------------------------------------------------------
// Project task board — /projects/:projectId/tasks
// ---------------------------------------------------------------------------

export function TaskBoardPage() {
  const projectId = Number(useParams().projectId);
  const me = useCurrentUser();
  const context = useProjectContext(projectId);
  const isLead = useIsLead(context.project?.teamId, context.members);
  const [params, setParams] = useSearchParams();

  const collection = useTaskCollection(
    () => tasksApi.list(projectId, { pageSize: 500 }).then((page) => page.items),
    () => PREVIEW_TASKS.map((t) => ({ ...t, projectId })),
    [projectId],
  );
  const { tasks, preview, loading, error, setError, replace, remove, changeStatus } = collection;

  const tab = (params.get('tab') as Tab | null) ?? 'all';
  const openTaskId = params.get('task') ? Number(params.get('task')) : null;
  const openTask = tasks.find((t) => t.taskId === openTaskId) ?? null;

  const [q, setQ] = useState('');
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [assignee, setAssignee] = useState('');
  const [sprintFilter, setSprintFilter] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [riskOnly, setRiskOnly] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('dueDate');
  const [order, setOrder] = useState<'asc' | 'desc'>('asc');
  const [view, setView] = useState<'list' | 'board'>('list');
  const [page, setPage] = useState(1);
  const [formMode, setFormMode] = useState<'create' | 'suggest' | null>(null);

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value === null) next.delete(key);
    else next.set(key, value);
    setParams(next, { replace: key === 'task' && value === null });
  };

  useEffect(() => setPage(1), [tab, q, status, assignee, sprintFilter, overdueOnly, riskOnly, sortKey, order]);

  const approved = tasks.filter(isApproved);
  const suggestions = tasks.filter((t) => t.suggestionStatus === 'PENDING');
  const activeSprint = context.sprints.find((s) => s.status === 'ACTIVE');

  const visible = useMemo(() => {
    let list = tab === 'suggested' ? suggestions : approved;
    if (tab === 'backlog') list = list.filter((t) => t.sprintId === null);
    const needle = q.trim().toLowerCase();
    if (needle) list = list.filter((t) => t.title.toLowerCase().includes(needle) || t.description?.toLowerCase().includes(needle));
    if (status && tab !== 'suggested') list = list.filter((t) => t.status === status);
    if (assignee && tab !== 'suggested') {
      const userId = assignee === 'me' ? me.userId : Number(assignee);
      list = list.filter((t) => t.assignees.some((a) => a.userId === userId));
    }
    if (sprintFilter && tab === 'all') list = list.filter((t) => String(t.sprintId) === sprintFilter);
    if (overdueOnly && tab !== 'suggested') list = list.filter((t) => t.overdue);
    if (riskOnly && tab !== 'suggested') list = list.filter((t) => t.lateRisk.startsWith('AT_RISK'));
    return sortTasks(list, sortKey, order, context.sprints);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tasks, tab, q, status, assignee, sprintFilter, overdueOnly, riskOnly, sortKey, order, context.sprints, me.userId]);

  const pageCount = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const pageItems = visible.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const showingFrom = visible.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1;
  const showingTo = Math.min(page * PAGE_SIZE, visible.length);
  const anyFilter = Boolean(q || status || assignee || sprintFilter || overdueOnly || riskOnly);

  function clearFilters() {
    setQ('');
    setStatus('');
    setAssignee('');
    setSprintFilter('');
    setOverdueOnly(false);
    setRiskOnly(false);
  }

  async function approve(task: Task) {
    setError('');
    if (preview) {
      replace({ ...task, suggestionStatus: 'APPROVED', version: task.version + 1 });
      return;
    }
    try {
      replace(await tasksApi.approve(task.taskId, { version: task.version }));
    } catch (err) {
      setError(errorText(err, 'Could not approve suggestion'));
    }
  }

  async function reject(task: Task) {
    if (!window.confirm(`Reject the suggestion “${task.title}”?`)) return;
    setError('');
    if (!preview) {
      try {
        await tasksApi.delete(task.taskId);
      } catch (err) {
        setError(errorText(err, 'Could not reject suggestion'));
        return;
      }
    }
    remove(task.taskId);
  }

  async function submitForm(input: TaskInput) {
    const mode = formMode;
    if (preview) {
      const now = new Date().toISOString();
      replace({
        taskId: Date.now(),
        projectId,
        sprintId: mode === 'create' ? input.sprintId ?? null : null,
        title: input.title,
        description: input.description ?? null,
        dueDate: input.dueDate ?? null,
        effort: input.effort,
        status: 'NOT_STARTED',
        suggestionStatus: mode === 'suggest' ? 'PENDING' : null,
        suggestedBy: mode === 'suggest' ? me.userId : null,
        createdBy: me.userId,
        createdAt: now,
        updatedAt: now,
        version: 1,
        assignees: [],
        overdue: false,
        lateRisk: 'NO_RISK',
      });
    } else if (mode === 'create') {
      replace(await tasksApi.create(projectId, input));
    } else {
      const { sprintId: _ignored, ...rest } = input;
      replace(await tasksApi.suggest(projectId, rest));
    }
    setFormMode(null);
    if (mode === 'suggest') setParam('tab', 'suggested');
  }

  // Sprint "balance card" numbers.
  const sprintTasks = activeSprint ? approved.filter((t) => t.sprintId === activeSprint.sprintId) : [];
  const sprintPoints = sprintTasks.reduce((sum, t) => sum + EFFORT_POINTS[t.effort], 0);
  const donePoints = sprintTasks.filter((t) => t.status === 'DONE').reduce((sum, t) => sum + EFFORT_POINTS[t.effort], 0);
  const sprintPercent = sprintPoints === 0 ? 0 : Math.round((donePoints / sprintPoints) * 100);
  const daysLeft = activeSprint ? daysUntil(activeSprint.endDate) : 0;
  const overdueCount = approved.filter((t) => t.overdue).length;
  const riskCount = approved.filter((t) => t.lateRisk.startsWith('AT_RISK')).length;
  const mineOpen = approved.filter((t) => t.status !== 'DONE' && t.assignees.some((a) => a.userId === me.userId)).length;
  const focus = (kind: 'overdue' | 'risk' | 'mine') => {
    setParam('tab', null);
    if (kind === 'overdue') setOverdueOnly(!overdueOnly);
    if (kind === 'risk') setRiskOnly(!riskOnly);
    if (kind === 'mine') setAssignee(assignee === 'me' ? '' : 'me');
  };

  return (
    <section className={styles.page}>
      {/* 1-2: title, count and primary actions */}
      <header className={styles.pageHeader}>
        <div className={styles.titleGroup}>
          <p className={styles.eyebrow}>{context.project?.name ?? 'Project'}</p>
          <h1 className={styles.pageTitle}>Tasks</h1>
          <span className={styles.pageMeta}>
            {loading ? 'Loading…' : `${approved.length} task${approved.length === 1 ? '' : 's'}`}
            {activeSprint && ` · ${activeSprint.name} in progress`}
          </span>
        </div>
        <div className={styles.headerActions}>
          <button type="button" className={styles.secondaryButton} onClick={() => setFormMode('suggest')}>
            + Suggest task
          </button>
          {isLead && (
            <button type="button" className={styles.primaryButton} onClick={() => setFormMode('create')}>
              + Create task
            </button>
          )}
        </div>
      </header>

      {activeSprint && (
        <section className={styles.hero} aria-label={`${activeSprint.name} progress`}>
          <div className={styles.heroMain}>
            <p className={styles.heroLabel}>
              {activeSprint.name} · {formatDate(activeSprint.startDate)} – {formatDate(activeSprint.endDate)}
            </p>
            <p className={styles.heroNumber}>
              {sprintPercent}
              <span>%</span>
            </p>
            <p className={styles.heroCaption}>
              of effort done · {donePoints} of {sprintPoints} pts ·{' '}
              {daysLeft > 0 ? `${daysLeft} day${daysLeft === 1 ? '' : 's'} left` : daysLeft === 0 ? 'ends today' : 'ended'}
            </p>
            <div className={styles.heroBar} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={sprintPercent} aria-label="Sprint effort completed">
              <span style={{ width: `${sprintPercent}%` }} />
            </div>
          </div>
          <div className={styles.heroStats}>
            <button type="button" className={styles.heroStat} aria-pressed={overdueOnly} onClick={() => focus('overdue')}>
              <strong>{overdueCount}</strong>
              <span>Overdue</span>
            </button>
            <button type="button" className={styles.heroStat} aria-pressed={riskOnly} onClick={() => focus('risk')}>
              <strong>{riskCount}</strong>
              <span>At risk</span>
            </button>
            <button type="button" className={styles.heroStat} aria-pressed={assignee === 'me'} onClick={() => focus('mine')}>
              <strong>{mineOpen}</strong>
              <span>On your plate</span>
            </button>
          </div>
        </section>
      )}

      {/* 3: tabs */}
      <nav className={styles.tabs} role="tablist" aria-label="Task lists">
        {([
          ['all', 'All tasks'],
          ['backlog', 'Backlog'],
          ['suggested', 'Suggested'],
        ] as const).map(([value, label]) => (
          <button key={value} type="button" role="tab" aria-selected={tab === value} onClick={() => setParam('tab', value === 'all' ? null : value)}>
            {label}
            {value === 'suggested' && suggestions.length > 0 && <span className={styles.tabBadge}>{suggestions.length}</span>}
          </button>
        ))}
      </nav>

      {/* 4: filters, sort and view toggle */}
      <div className={styles.toolbar}>
        <div className={styles.filters}>
          <label className={styles.search}>
            <SearchIcon />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search title or description" aria-label="Search tasks" />
          </label>

          {tab !== 'suggested' && (
          <FilterSelect label="Status" value={status} onChange={(v) => setStatus(v as TaskStatus | '')} display={status ? STATUS_LABEL[status] : ''}>
            {STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </FilterSelect>
          )}

          {tab !== 'suggested' && (
          <FilterSelect
            label="Assignee"
            value={assignee}
            onChange={setAssignee}
            display={assignee === 'me' ? 'Me' : context.members.find((m) => String(m.userId) === assignee)?.displayName ?? ''}
          >
            <option value="me">Me</option>
            {context.members
              .filter((m) => m.userId !== me.userId)
              .map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName}
                </option>
              ))}
          </FilterSelect>
          )}

          {tab === 'all' && (
            <FilterSelect
              label="Sprint"
              value={sprintFilter}
              onChange={setSprintFilter}
              display={sprintFilter ? sprintName(context.sprints, Number(sprintFilter)) : ''}
              hideLabelWhenSet
            >
              {context.sprints.map((s) => (
                <option key={s.sprintId} value={s.sprintId}>
                  {s.name}
                </option>
              ))}
            </FilterSelect>
          )}

          {tab !== 'suggested' && (
          <button type="button" className={`${styles.chip} ${overdueOnly ? styles.chipActive : ''}`} aria-pressed={overdueOnly} onClick={() => setOverdueOnly(!overdueOnly)}>
            Overdue only
          </button>
          )}

          {tab !== 'suggested' && (
          <button type="button" className={`${styles.chip} ${riskOnly ? styles.chipActive : ''}`} aria-pressed={riskOnly} onClick={() => setRiskOnly(!riskOnly)}>
            At risk
          </button>
          )}
        </div>

        <div className={styles.viewControls}>
          <span className={styles.sortLabel}>Sort</span>
          <span className={`${styles.chip} ${styles.sortChip}`}>
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)} aria-label="Sort by">
              <option value="dueDate">Due date</option>
              <option value="effort">Effort</option>
              <option value="sprint">Sprint</option>
            </select>
            <button type="button" aria-label={order === 'asc' ? 'Ascending, switch to descending' : 'Descending, switch to ascending'} onClick={() => setOrder(order === 'asc' ? 'desc' : 'asc')}>
              {order === 'asc' ? '↑' : '↓'}
            </button>
          </span>
          {tab !== 'suggested' && (
            <div className={styles.segmented} role="group" aria-label="View">
              <button type="button" aria-pressed={view === 'list'} onClick={() => setView('list')}>
                List
              </button>
              <button type="button" aria-pressed={view === 'board'} onClick={() => setView('board')}>
                Board
              </button>
            </div>
          )}
        </div>
      </div>

      {error && (
        <p className={styles.errorBar} role="alert">
          {error}
          <button type="button" onClick={() => setError('')} aria-label="Dismiss">
            ×
          </button>
        </p>
      )}

      {/* 5-7: list or board */}
      {view === 'board' && tab !== 'suggested' ? (
        <div className={styles.board}>
          {STATUS_ORDER.map((columnStatus) => {
            const items = visible.filter((t) => t.status === columnStatus);
            return (
              <section key={columnStatus} className={styles.boardColumn} aria-label={STATUS_LABEL[columnStatus]}>
                <h2>
                  <StatusPill status={columnStatus} /> <span>{items.length}</span>
                </h2>
                {items.map((task) => (
                  <button key={task.taskId} type="button" className={`${styles.boardCard} ${task.taskId === openTaskId ? styles.boardCardActive : ''}`} onClick={() => setParam('task', String(task.taskId))}>
                    <strong>{task.title}</strong>
                    <span className={styles.boardCardMeta}>
                      <AvatarStack assignees={task.assignees} />
                      {task.dueDate && <span className={task.overdue ? styles.overdueText : undefined}>{formatDate(task.dueDate)}</span>}
                    </span>
                    {task.lateRisk !== 'NO_RISK' && <RiskPill risk={task.lateRisk} />}
                  </button>
                ))}
                {items.length === 0 && <p className={styles.boardEmpty}>Nothing here</p>}
              </section>
            );
          })}
        </div>
      ) : (
        <div className={styles.listCard}>
          {loading && <p className={styles.emptyState}>Loading tasks…</p>}
          {!loading && pageItems.length === 0 && (
            <div className={styles.emptyState}>
              <strong>
                {anyFilter ? 'No tasks match these filters' : tab === 'suggested' ? 'No pending suggestions' : tab === 'backlog' ? 'The backlog is empty' : 'No tasks yet'}
              </strong>
              {anyFilter ? (
                <button type="button" className={styles.secondaryButton} onClick={clearFilters}>
                  Clear filters
                </button>
              ) : (
                <span>{tab === 'suggested' ? 'Ideas from teammates show up here for a lead to review.' : 'Create a task to get things moving.'}</span>
              )}
            </div>
          )}
          {!loading &&
            (sortKey === 'dueDate' && tab !== 'suggested' ? groupByDue(pageItems, order) : [{ bucket: null, tasks: pageItems }]).map((group) => (
              <section key={group.bucket ?? 'all'} className={styles.group}>
                {group.bucket && (
                  <h2 className={`${styles.groupTitle} ${group.bucket === 'overdue' ? styles.groupTitleDanger : ''}`}>
                    {DUE_BUCKET_LABEL[group.bucket]} <span>{group.tasks.length}</span>
                  </h2>
                )}
                <ul className={styles.rows}>
                  {group.tasks.map((task) => {
                    const mine = task.assignees.some((a) => a.userId === me.userId);
                    const open = () => setParam('task', String(task.taskId));
                    if (tab === 'suggested') {
                      const suggester = context.members.find((m) => m.userId === task.suggestedBy)?.displayName ?? 'A teammate';
                      return (
                        <TaskRow
                          key={task.taskId}
                          task={task}
                          active={task.taskId === openTaskId}
                          meta={`Suggested by ${suggester} · ${EFFORT_LABEL[task.effort]} effort${task.dueDate ? ` · due ${formatDate(task.dueDate)}` : ''}`}
                          status={<span className={`${styles.pill} ${styles.pillSuggested}`}>Suggested</span>}
                          trailing={
                            <span className={styles.rowActions} onClick={(e) => e.stopPropagation()}>
                              {isLead ? (
                                <>
                                  <button type="button" className={styles.secondaryButton} onClick={() => void reject(task)}>
                                    Reject
                                  </button>
                                  <button type="button" className={styles.primaryButton} onClick={() => void approve(task)}>
                                    Approve
                                  </button>
                                </>
                              ) : (
                                <span className={styles.rowCaption}>Waiting for a lead</span>
                              )}
                            </span>
                          }
                          onOpen={open}
                        />
                      );
                    }
                    return (
                      <TaskRow
                        key={task.taskId}
                        task={task}
                        active={task.taskId === openTaskId}
                        meta={`${EFFORT_LABEL[task.effort]} effort · ${sprintName(context.sprints, task.sprintId)}`}
                        status={
                          <StatusPill
                            status={task.status}
                            label={`Status of ${task.title}`}
                            onChange={mine ? (next) => void changeStatus(task, next) : undefined}
                          />
                        }
                        onOpen={open}
                      />
                    );
                  })}
                </ul>
              </section>
            ))}
        </div>
      )}

      {/* 8: pagination */}
      {view === 'list' || tab === 'suggested' ? (
        <footer className={styles.pager}>
          <span>
            Showing {showingFrom}–{showingTo} of {visible.length}
          </span>
          <span className={styles.pagerControls}>
            <button type="button" aria-label="Previous page" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              ‹
            </button>
            <span>
              {page}
              {pageCount > 1 && ` / ${pageCount}`}
            </span>
            <button type="button" aria-label="Next page" disabled={page >= pageCount} onClick={() => setPage(page + 1)}>
              ›
            </button>
          </span>
        </footer>
      ) : null}

      {openTask && (
        <TaskDetailPanel
          task={openTask}
          preview={preview}
          onClose={() => setParam('task', null)}
          onTaskChange={replace}
          onTaskRemoved={(id) => {
            remove(id);
            setParam('task', null);
          }}
        />
      )}

      {formMode && (
        <TaskFormModal
          mode={formMode}
          sprints={context.sprints}
          defaultSprintId={tab === 'backlog' ? null : activeSprint?.sprintId ?? null}
          onClose={() => setFormMode(null)}
          onSubmit={submitForm}
        />
      )}
    </section>
  );
}

/** A filter chip that is a native select underneath. Shows "Label ▾" when empty and "Label: Value ×" when set. */
function FilterSelect({
  label,
  value,
  onChange,
  display,
  hideLabelWhenSet,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  display: string;
  hideLabelWhenSet?: boolean;
  children: ReactNode;
}) {
  const active = value !== '';
  return (
    <span className={`${styles.chip} ${styles.chipSelect} ${active ? styles.chipActive : ''}`}>
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-label={`Filter by ${label.toLowerCase()}`}>
        <option value="">Any {label.toLowerCase()}</option>
        {children}
      </select>
      <span aria-hidden="true">{active ? (hideLabelWhenSet ? display : `${label}: ${display}`) : label}</span>
      {active ? (
        <button type="button" className={styles.chipClear} aria-label={`Clear ${label.toLowerCase()} filter`} onClick={() => onChange('')}>
          ×
        </button>
      ) : (
        <Caret />
      )}
    </span>
  );
}

function TaskFormModal({
  mode,
  sprints,
  defaultSprintId,
  onClose,
  onSubmit,
}: {
  mode: 'create' | 'suggest';
  sprints: Sprint[];
  defaultSprintId: number | null;
  onClose: () => void;
  onSubmit: (input: TaskInput) => Promise<void>;
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [effort, setEffort] = useState<EffortLevel>('MODERATE');
  const [sprintId, setSprintId] = useState(defaultSprintId === null ? '' : String(defaultSprintId));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim()) return;
    setSaving(true);
    setError('');
    try {
      await onSubmit({
        title: title.trim(),
        description: description.trim() || undefined,
        dueDate: dueDate || undefined,
        effort,
        sprintId: sprintId ? Number(sprintId) : null,
      });
    } catch (err) {
      setError(errorText(err, mode === 'create' ? 'Could not create task' : 'Could not suggest task'));
      setSaving(false);
    }
  }

  return (
    <div className={styles.modalBackdrop} onMouseDown={onClose}>
      <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="task-form-title" onMouseDown={(e) => e.stopPropagation()}>
        <h2 id="task-form-title">{mode === 'create' ? 'Create task' : 'Suggest a task'}</h2>
        {mode === 'suggest' && <p className={styles.modalHint}>A team lead will review your suggestion before it appears on the board.</p>}
        <form className={styles.editForm} onSubmit={submit}>
          <label>
            Title
            <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} autoFocus placeholder="What needs to be done?" />
          </label>
          <label>
            Description
            <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional details" />
          </label>
          <div className={styles.editRow}>
            <label>
              Due date
              <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </label>
            <label>
              Effort
              <select value={effort} onChange={(e) => setEffort(e.target.value as EffortLevel)}>
                {EFFORT_ORDER.map((value) => (
                  <option key={value} value={value}>
                    {EFFORT_LABEL[value]} · {EFFORT_POINTS[value]} pts
                  </option>
                ))}
              </select>
            </label>
            {mode === 'create' && (
              <label>
                Sprint
                <select value={sprintId} onChange={(e) => setSprintId(e.target.value)}>
                  <option value="">Backlog</option>
                  {sprints
                    .filter((s) => s.status !== 'COMPLETED')
                    .map((s) => (
                      <option key={s.sprintId} value={s.sprintId}>
                        {s.name}
                      </option>
                    ))}
                </select>
              </label>
            )}
          </div>
          {error && <p className={styles.formError} role="alert">{error}</p>}
          <div className={styles.formActions}>
            <button type="button" className={styles.secondaryButton} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className={styles.primaryButton} disabled={saving || !title.trim()}>
              {saving ? 'Saving…' : mode === 'create' ? 'Create task' : 'Send suggestion'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// My tasks — /my-tasks, and /tasks/:taskId (My tasks with the panel open)
// ---------------------------------------------------------------------------

export function MyTasksPage({ openTaskId = null }: { openTaskId?: number | null }) {
  const me = useCurrentUser();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [extraTask, setExtraTask] = useState<Task | null>(null);
  const [extraPreview, setExtraPreview] = useState(false);

  const collection = useTaskCollection(
    () => tasksApi.listMine({ pageSize: 500 }).then((page) => page.items),
    () => PREVIEW_TASKS.filter((t) => isApproved(t) && t.assignees.some((a) => a.userId === (me.userId || PREVIEW_ME))),
    [me.userId],
  );
  const { tasks, preview, loading, error, setError, replace, remove, changeStatus } = collection;

  // A task opened from a notification may not be assigned to me — fetch it on its own.
  const listed = tasks.find((t) => t.taskId === openTaskId) ?? null;
  useEffect(() => {
    if (openTaskId === null || loading || listed) {
      setExtraTask(null);
      return;
    }
    let active = true;
    tasksApi
      .get(openTaskId)
      .then((task) => {
        if (!active) return;
        setExtraTask(task);
        setExtraPreview(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        const fallback = PREVIEW_TASKS.find((t) => t.taskId === openTaskId) ?? null;
        setExtraTask(fallback);
        setExtraPreview(true);
        if (!fallback && !shouldUsePreview(err)) setError(errorText(err, 'Could not open task'));
      });
    return () => {
      active = false;
    };
  }, [openTaskId, loading, listed, setError]);

  const openTask = listed ?? extraTask;
  const needle = q.trim().toLowerCase();
  const visible = sortTasks(
    tasks.filter((t) => !needle || t.title.toLowerCase().includes(needle) || t.description?.toLowerCase().includes(needle)),
    'dueDate',
    'asc',
    [],
  );

  return (
    <section className={`${styles.page} ${styles.myTasksPage}`}>
      <label className={`${styles.search} ${styles.searchWide}`}>
        <SearchIcon />
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search your tasks" aria-label="Search your tasks" />
      </label>

      <header className={styles.pageHeader}>
        <div className={styles.titleGroup}>
          <p className={styles.eyebrow}>Across all your teams</p>
          <h1 className={styles.pageTitle}>My tasks</h1>
          <span className={styles.pageMeta}>
            {loading ? 'Loading…' : `${tasks.filter((t) => t.status !== 'DONE').length} open · ${tasks.filter((t) => t.overdue).length} overdue`}
          </span>
        </div>
      </header>

      {error && (
        <p className={styles.errorBar} role="alert">
          {error}
          <button type="button" onClick={() => setError('')} aria-label="Dismiss">
            ×
          </button>
        </p>
      )}

      <div className={styles.listCard}>
        {loading && <p className={styles.emptyState}>Loading tasks…</p>}
        {!loading && visible.length === 0 && (
          <div className={styles.emptyState}>
            <strong>{needle ? 'No tasks match your search' : 'Nothing on your plate'}</strong>
            <span>{needle ? 'Try a different word.' : 'Tasks assigned to you will show up here.'}</span>
          </div>
        )}
        {groupByDue(visible, 'asc').map((group) => (
          <section key={group.bucket} className={styles.group}>
            <h2 className={`${styles.groupTitle} ${group.bucket === 'overdue' ? styles.groupTitleDanger : ''}`}>
              {DUE_BUCKET_LABEL[group.bucket]} <span>{group.tasks.length}</span>
            </h2>
            <ul className={styles.rows}>
              {group.tasks.map((task) => (
                <TaskRow
                  key={task.taskId}
                  task={task}
                  active={task.taskId === openTaskId}
                  meta={`${EFFORT_LABEL[task.effort]} effort${task.assignees.length > 1 ? ` · with ${task.assignees.filter((a) => a.userId !== me.userId).map((a) => a.displayName).join(', ')}` : ''}`}
                  status={<StatusPill status={task.status} label={`Status of ${task.title}`} onChange={(next) => void changeStatus(task, next)} />}
                  onOpen={() => navigate(`/tasks/${task.taskId}`)}
                />
              ))}
            </ul>
          </section>
        ))}
      </div>

      {openTask && (
        <TaskDetailPanel
          task={openTask}
          preview={listed ? preview : extraPreview}
          onClose={() => navigate('/my-tasks')}
          onTaskChange={(task) => (listed ? replace(task) : setExtraTask(task))}
          onTaskRemoved={(id) => {
            remove(id);
            navigate('/my-tasks');
          }}
        />
      )}
    </section>
  );
}

export function TaskDetailPage() {
  const taskId = Number(useParams().taskId);
  return <MyTasksPage openTaskId={Number.isNaN(taskId) ? null : taskId} />;
}
