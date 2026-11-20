// Slide-over task panel: details, assignee split, comments and activity. Owner: Adam.
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { tasksApi } from '../../api/tasks';
import type { ActivityEntry, Comment, EffortLevel, Task, TaskStatus } from '../../api/types';
import { EFFORT_POINTS } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ConflictDialog } from '../../components/shared';
import {
  EFFORT_LABEL,
  EFFORT_ORDER,
  errorText,
  formatDate,
  PREVIEW_ACTIVITY,
  PREVIEW_COMMENTS,
  riskMessage,
  shouldUsePreview,
  sprintName,
  STATUS_LABEL,
  timeAgo,
  useCurrentUser,
  useProjectContext,
} from './data';
import { Avatar, MentionText, StatusPill, WarningIcon } from './pieces';
import styles from './Tasks.module.css';

interface Draft {
  title: string;
  description: string;
  dueDate: string;
  effort: EffortLevel;
  sprintId: string;
}

interface AssigneeRow {
  userId: number;
  displayName: string;
  percent: string;
}

function toDraft(task: Task): Draft {
  return {
    title: task.title,
    description: task.description ?? '',
    dueDate: task.dueDate ?? '',
    effort: task.effort,
    sprintId: task.sprintId === null ? '' : String(task.sprintId),
  };
}

function toRows(task: Task): AssigneeRow[] {
  return task.assignees.map((a) => ({
    userId: a.userId,
    displayName: a.displayName,
    percent: a.allocationPercent === null ? '' : String(a.allocationPercent),
  }));
}

function describeActivity(entry: ActivityEntry): string {
  const pretty = (value: string | null) => (value && value in STATUS_LABEL ? STATUS_LABEL[value as TaskStatus] : value);
  switch (entry.activityType) {
    case 'TASK_CREATED':
      return 'created the task';
    case 'STATUS_CHANGED':
      return `changed status from ${pretty(entry.oldValue)} to ${pretty(entry.newValue)}`;
    case 'ASSIGNEES_CHANGED':
      return `changed assignees to ${entry.newValue ?? 'nobody'}`;
    default: {
      const verb = entry.activityType.toLowerCase().replace(/_/g, ' ');
      if (entry.oldValue && entry.newValue) return `${verb}: ${entry.oldValue} → ${entry.newValue}`;
      return entry.newValue ? `${verb}: ${entry.newValue}` : verb;
    }
  }
}

export function TaskDetailPanel({
  task,
  preview,
  onClose,
  onTaskChange,
  onTaskRemoved,
}: {
  task: Task;
  /** True when the task came from preview data: changes are applied locally only. */
  preview: boolean;
  onClose: () => void;
  onTaskChange: (task: Task) => void;
  onTaskRemoved: (taskId: number) => void;
}) {
  const { roleIn } = useAuth();
  const me = useCurrentUser();
  const context = useProjectContext(task.projectId);
  const teamId = context.project?.teamId;
  const role = (teamId !== undefined ? roleIn(teamId) : null) ?? context.members.find((m) => m.userId === me.userId)?.role ?? null;
  const isLead = role === 'LEAD';
  const isAssignee = task.assignees.some((a) => a.userId === me.userId);
  const milestone = context.milestones.find((m) => m.linkedTaskIds.includes(task.taskId))?.milestone;

  const [error, setError] = useState('');
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft>(() => toDraft(task));
  const [conflict, setConflict] = useState<Task | null>(null);
  const [saving, setSaving] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [rows, setRows] = useState<AssigneeRow[]>(() => toRows(task));
  const [addingAssignee, setAddingAssignee] = useState(false);
  const [tab, setTab] = useState<'comments' | 'activity'>('comments');
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentsPreview, setCommentsPreview] = useState(false);
  const [activity, setActivity] = useState<ActivityEntry[] | null>(null);
  const [commentText, setCommentText] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);

  // Reset local state whenever a different task (or a newer version of it) is shown.
  useEffect(() => {
    setDraft(toDraft(task));
    setRows(toRows(task));
  }, [task]);

  useEffect(() => {
    setEditing(false);
    setError('');
    setTab('comments');
    setActivity(null);
    setAddingAssignee(false);
    let active = true;
    tasksApi
      .comments(task.taskId)
      .then((page) => {
        if (!active) return;
        setComments(page.items);
        setCommentsPreview(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setComments(PREVIEW_COMMENTS[task.taskId] ?? []);
        setCommentsPreview(true);
        if (!shouldUsePreview(err)) setError(errorText(err, 'Could not load comments'));
      });
    return () => {
      active = false;
    };
  }, [task.taskId]);

  useEffect(() => {
    if (tab !== 'activity' || activity !== null) return;
    let active = true;
    tasksApi
      .activity(task.taskId)
      .then((page) => active && setActivity(page.items))
      .catch(() => active && setActivity(PREVIEW_ACTIVITY[task.taskId] ?? []));
    return () => {
      active = false;
    };
  }, [tab, activity, task.taskId]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (menuOpen) setMenuOpen(false);
      else if (!editing && !conflict) onClose();
    };
    const onPointer = (event: PointerEvent) => {
      if (menuOpen && !menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointer);
    };
  }, [menuOpen, editing, conflict, onClose]);

  /** Runs a task mutation against the API, or locally when showing preview data. */
  async function mutate(call: () => Promise<Task>, local: () => Task, failure: string): Promise<boolean> {
    setError('');
    if (preview) {
      onTaskChange({ ...local(), version: task.version + 1, updatedAt: new Date().toISOString() });
      return true;
    }
    setSaving(true);
    try {
      onTaskChange(await call());
      return true;
    } catch (err) {
      if (err instanceof ApiError && err.code === 'VERSION_CONFLICT' && err.current) setConflict(err.current as Task);
      else setError(errorText(err, failure));
      return false;
    } finally {
      setSaving(false);
    }
  }

  function changeStatus(status: TaskStatus) {
    void mutate(() => tasksApi.updateStatus(task.taskId, status, task.version), () => ({ ...task, status }), 'Could not change status');
  }

  async function saveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const title = draft.title.trim();
    if (!title) return;
    const body = {
      title,
      description: draft.description.trim() || undefined,
      dueDate: draft.dueDate || undefined,
      effort: draft.effort,
      ...(draft.sprintId ? { sprintId: Number(draft.sprintId) } : { moveToBacklog: true }),
      version: task.version,
    };
    const ok = await mutate(
      () => tasksApi.update(task.taskId, body),
      () => ({
        ...task,
        title,
        description: draft.description.trim() || null,
        dueDate: draft.dueDate || null,
        effort: draft.effort,
        sprintId: draft.sprintId ? Number(draft.sprintId) : null,
      }),
      'Could not save task',
    );
    if (ok) setEditing(false);
  }

  function moveToBacklog() {
    setMenuOpen(false);
    void mutate(
      () => tasksApi.update(task.taskId, { moveToBacklog: true, version: task.version }),
      () => ({ ...task, sprintId: null }),
      'Could not move task',
    );
  }

  async function deleteTask() {
    setMenuOpen(false);
    if (!window.confirm(`Delete “${task.title}”? This can't be undone.`)) return;
    if (!preview) {
      try {
        await tasksApi.delete(task.taskId);
      } catch (err) {
        setError(errorText(err, 'Could not delete task'));
        return;
      }
    }
    onTaskRemoved(task.taskId);
  }

  // ---- Assignees ----
  const total = rows.reduce((sum, row) => sum + (Number(row.percent) || 0), 0);
  const anyPercent = rows.some((row) => row.percent !== '');
  const splitValid = !anyPercent || (total === 100 && rows.every((row) => row.percent !== ''));
  const rowsDirty = JSON.stringify(rows) !== JSON.stringify(toRows(task));
  const addable = context.members.filter((m) => !rows.some((row) => row.userId === m.userId));

  function saveAssignees() {
    const assignees = rows.map((row) => ({
      userId: row.userId,
      ...(anyPercent ? { allocationPercent: Number(row.percent) } : {}),
    }));
    void mutate(
      () => tasksApi.replaceAssignees(task.taskId, assignees, task.version),
      () => ({
        ...task,
        assignees: rows.map((row) => ({
          userId: row.userId,
          displayName: row.displayName,
          allocationPercent: anyPercent ? Number(row.percent) : null,
        })),
      }),
      'Could not save assignees',
    );
  }

  // ---- Comments ----
  async function postComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = commentText.trim();
    if (!text) return;
    setError('');
    if (commentsPreview) {
      setComments((current) => [
        ...current,
        { commentId: Date.now(), taskId: task.taskId, authorId: me.userId, authorName: me.displayName, commentText: text, createdAt: new Date().toISOString() },
      ]);
      setCommentText('');
      return;
    }
    try {
      const created = await tasksApi.addComment(task.taskId, text);
      setComments((current) => [...current, created]);
      setCommentText('');
    } catch (err) {
      setError(errorText(err, 'Could not post comment'));
    }
  }

  const banner = riskMessage(task);
  const projectName = context.project?.name ?? 'Project';

  return (
    <>
      <div className={styles.scrim} onMouseDown={onClose} aria-hidden="true" />
      <aside className={styles.panel} role="dialog" aria-modal="true" aria-labelledby="task-panel-title">
        <header className={styles.panelHeader}>
          <p className={styles.breadcrumb}>
            {projectName} / {sprintName(context.sprints, task.sprintId)}
          </p>
          <div className={styles.panelActions}>
            {!editing && (
              <button type="button" className={styles.ghostButton} onClick={() => setEditing(true)}>
                Edit
              </button>
            )}
            <div className={styles.menuWrap} ref={menuRef}>
              <button
                type="button"
                className={styles.ghostButton}
                aria-label="More task actions"
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                onClick={() => setMenuOpen((open) => !open)}
              >
                •••
              </button>
              {menuOpen && (
                <div className={styles.menu} role="menu">
                  <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); setTab('activity'); }}>
                    View activity
                  </button>
                  <button type="button" role="menuitem" disabled={!isLead || task.sprintId === null} onClick={moveToBacklog}>
                    Move to backlog
                  </button>
                  <button type="button" role="menuitem" className={styles.menuDanger} disabled={!isLead} onClick={() => void deleteTask()}>
                    Delete task
                  </button>
                </div>
              )}
            </div>
            <button type="button" className={styles.closeButton} aria-label="Close task" onClick={onClose}>
              <svg viewBox="0 0 16 16" aria-hidden="true">
                <path d="m4 4 8 8M12 4l-8 8" />
              </svg>
            </button>
          </div>
        </header>

        <div className={styles.panelBody}>
          {editing ? (
            <form className={styles.editForm} onSubmit={saveEdit}>
              <label>
                Title
                <input value={draft.title} maxLength={200} autoFocus onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
              </label>
              <label>
                Description
                <textarea rows={4} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
              </label>
              <div className={styles.editRow}>
                <label>
                  Due date
                  <input type="date" value={draft.dueDate} onChange={(e) => setDraft({ ...draft, dueDate: e.target.value })} />
                </label>
                <label>
                  Effort
                  <select value={draft.effort} onChange={(e) => setDraft({ ...draft, effort: e.target.value as EffortLevel })}>
                    {EFFORT_ORDER.map((effort) => (
                      <option key={effort} value={effort}>
                        {EFFORT_LABEL[effort]} · {EFFORT_POINTS[effort]} pts
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Sprint
                  <select value={draft.sprintId} onChange={(e) => setDraft({ ...draft, sprintId: e.target.value })}>
                    <option value="">Backlog</option>
                    {context.sprints
                      .filter((s) => s.status !== 'COMPLETED' || s.sprintId === task.sprintId)
                      .map((s) => (
                        <option key={s.sprintId} value={s.sprintId}>
                          {s.name}
                        </option>
                      ))}
                  </select>
                </label>
              </div>
              {error && <p className={styles.formError} role="alert">{error}</p>}
              <div className={styles.formActions}>
                {/* Cancel sends nothing to the server. */}
                <button type="button" className={styles.secondaryButton} onClick={() => { setDraft(toDraft(task)); setEditing(false); setError(''); }}>
                  Cancel
                </button>
                <button type="submit" className={styles.primaryButton} disabled={saving || !draft.title.trim()}>
                  {saving ? 'Saving…' : 'Save'}
                </button>
              </div>
            </form>
          ) : (
            <>
              <h2 id="task-panel-title" className={styles.panelTitle}>
                {task.title}
              </h2>

              {banner && (
                <p className={`${styles.riskBanner} ${task.lateRisk === 'AT_RISK_UNRESOLVED_BLOCKER' || task.lateRisk === 'OVERDUE' ? styles.riskBannerDanger : ''}`}>
                  <WarningIcon />
                  <span>{banner}</span>
                </p>
              )}
              {error && <p className={styles.formError} role="alert">{error}</p>}

              <dl className={styles.details}>
                <dt>Status</dt>
                <dd>
                  <StatusPill status={task.status} onChange={isAssignee ? changeStatus : undefined} disabled={saving} />
                  {!isAssignee && <span className={styles.detailHint}>Only assignees can change this</span>}
                </dd>
                <dt>Due</dt>
                <dd className={task.overdue ? styles.overdueText : undefined}>
                  {task.dueDate ? formatDate(task.dueDate, true) : 'No due date'}
                  {task.overdue && ' · Overdue'}
                </dd>
                <dt>Sprint</dt>
                <dd>{sprintName(context.sprints, task.sprintId)}</dd>
                <dt>Effort</dt>
                <dd>
                  {EFFORT_LABEL[task.effort]} · {EFFORT_POINTS[task.effort]} pts
                </dd>
                <dt>Milestone</dt>
                <dd>{milestone?.name ?? <span className={styles.muted}>None</span>}</dd>
              </dl>

              <section className={styles.assigneeSection} aria-labelledby="assignees-title">
                <h3 id="assignees-title" className={styles.sectionLabel}>
                  Assignees
                </h3>
                  <div className={styles.assigneeBox}>
                    {rows.length === 0 && <p className={styles.muted}>Nobody assigned yet</p>}
                    {rows.map((row, index) => (
                      <div key={row.userId} className={styles.assigneeRow}>
                        <Avatar name={row.displayName} />
                        <span className={styles.assigneeName}>{row.displayName}</span>
                        {isLead ? (
                          <>
                            <label className={styles.percentInput}>
                              <input
                                inputMode="numeric"
                                aria-label={`${row.displayName} allocation percent`}
                                placeholder="—"
                                value={row.percent}
                                onChange={(e) => {
                                  const percent = e.target.value.replace(/\D/g, '').slice(0, 3);
                                  setRows(rows.map((r, i) => (i === index ? { ...r, percent } : r)));
                                }}
                              />
                              %
                            </label>
                            <button
                              type="button"
                              className={styles.removeAssignee}
                              aria-label={`Remove ${row.displayName}`}
                              onClick={() => setRows(rows.filter((_, i) => i !== index))}
                            >
                              ×
                            </button>
                          </>
                        ) : (
                          row.percent && <span className={styles.percentStatic}>{row.percent} %</span>
                        )}
                      </div>
                    ))}
                    <div className={styles.assigneeFooter}>
                      {isLead &&
                        (addingAssignee ? (
                          <select
                            autoFocus
                            className={styles.addSelect}
                            aria-label="Add assignee"
                            value=""
                            onBlur={() => setAddingAssignee(false)}
                            onChange={(e) => {
                              const member = addable.find((m) => m.userId === Number(e.target.value));
                              if (member) setRows([...rows, { userId: member.userId, displayName: member.displayName, percent: '' }]);
                              setAddingAssignee(false);
                            }}
                          >
                            <option value="" disabled>
                              Choose a teammate…
                            </option>
                            {addable.map((m) => (
                              <option key={m.userId} value={m.userId}>
                                {m.displayName}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <button type="button" className={styles.linkButton} disabled={addable.length === 0} onClick={() => setAddingAssignee(true)}>
                            + Add assignee
                          </button>
                        ))}
                      {anyPercent && (
                        <span className={splitValid ? styles.totalOk : styles.totalBad}>
                          Total {total} % {splitValid ? '✓' : '(must be 100)'}
                        </span>
                      )}
                    </div>
                    {rowsDirty && (
                      <div className={styles.assigneeActions}>
                        <button type="button" className={styles.secondaryButton} onClick={() => setRows(toRows(task))}>
                          Cancel
                        </button>
                        <button type="button" className={styles.primaryButton} disabled={!splitValid || saving} onClick={saveAssignees}>
                          Save assignees
                        </button>
                      </div>
                    )}
                  </div>
              </section>

              {task.description && (
                <section aria-labelledby="description-title">
                  <h3 id="description-title" className={styles.sectionLabel}>
                    Description
                  </h3>
                  <p className={styles.description}>{task.description}</p>
                </section>
              )}
            </>
          )}

          <div className={styles.panelTabs} role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'comments'} onClick={() => setTab('comments')}>
              Comments ({comments.length})
            </button>
            <button type="button" role="tab" aria-selected={tab === 'activity'} onClick={() => setTab('activity')}>
              Activity
            </button>
          </div>

          {tab === 'comments' ? (
            <ul className={styles.feed}>
              {comments.length === 0 && <li className={styles.muted}>No comments yet. Start the conversation below.</li>}
              {comments.map((comment) => (
                <li key={comment.commentId} className={styles.feedItem}>
                  <Avatar name={comment.authorName} />
                  <div>
                    <p className={styles.feedMeta}>
                      <strong>{comment.authorName}</strong> <span>{timeAgo(comment.createdAt)}</span>
                    </p>
                    <p className={styles.feedText}>
                      <MentionText text={comment.commentText} />
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <ul className={styles.feed}>
              {activity === null && <li className={styles.muted}>Loading activity…</li>}
              {activity?.length === 0 && <li className={styles.muted}>No activity yet.</li>}
              {activity?.map((entry) => (
                <li key={entry.activityId} className={styles.feedItem}>
                  <Avatar name={entry.actorName ?? 'System'} />
                  <div>
                    <p className={styles.feedText}>
                      <strong>{entry.actorName ?? 'System'}</strong> {describeActivity(entry)}
                    </p>
                    <p className={styles.feedMeta}>
                      <span>{timeAgo(entry.createdAt)}</span>
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <form className={styles.composer} onSubmit={postComment}>
          <input
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder="Write a comment… @ to mention"
            aria-label="Write a comment"
            maxLength={2000}
          />
          <button type="submit" className={styles.primaryButton} disabled={!commentText.trim()}>
            Post
          </button>
        </form>
      </aside>

      {conflict && (
        <ConflictDialog
          latest={conflict}
          myChanges={JSON.stringify(draft, null, 2)}
          onReloadLatest={(latest) => {
            onTaskChange(latest);
            setConflict(null);
            setEditing(false);
          }}
          onClose={() => setConflict(null)}
        />
      )}
    </>
  );
}
