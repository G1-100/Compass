import { useRef, useState, type FormEvent } from 'react';
import { checkinsApi } from '../../api/checkins';
import { ApiError } from '../../api/client';
import type {
  Checkin, CheckinBlockerInput, CheckinUpdate, Severity, SubmitCheckinUpdate,
} from '../../api/types';
import { ConflictDialog, ErrorBanner, FieldError, SubmitButton } from '../../components/shared';
import styles from './Checkins.module.css';

export const statusLabel: Record<Checkin['status'], string> = {
  SCHEDULED: 'Scheduled',
  COMPLETED: 'Completed',
  CANCELED: 'Canceled',
};

const localDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

export function LocalTime({ value }: { value: string }) {
  return <time dateTime={value}>{localDate.format(new Date(value))}</time>;
}

type MeetingDraft = { scheduledAt: string; description: string; location: string };
const emptyMeeting: MeetingDraft = { scheduledAt: '', description: '', location: '' };

function localInputValue(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function scheduledInstant(value: string) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime()) || localInputValue(date) !== value) {
    throw new ApiError(400, 'VALIDATION_FAILED', 'Some fields need attention.', {
      scheduledAt: 'Enter a valid date and time in your local time zone.',
    });
  }
  return date.toISOString();
}

function meetingDraft(checkin: Checkin): MeetingDraft {
  return {
    scheduledAt: localInputValue(new Date(checkin.scheduledAt)),
    description: checkin.description ?? '',
    location: checkin.location ?? '',
  };
}

function MeetingFields({
  draft, setDraft, error,
}: { draft: MeetingDraft; setDraft: (draft: MeetingDraft) => void; error: unknown }) {
  return (
    <>
      <label>
        Scheduled for (local time)
        <input type="datetime-local" required value={draft.scheduledAt}
          onChange={(event) => setDraft({ ...draft, scheduledAt: event.target.value })} />
        <FieldError field="scheduledAt" error={error} />
      </label>
      <label>
        Description
        <textarea rows={3} value={draft.description}
          onChange={(event) => setDraft({ ...draft, description: event.target.value })} />
        <FieldError field="description" error={error} />
      </label>
      <label>
        Location or meeting link
        <input value={draft.location} onChange={(event) => setDraft({ ...draft, location: event.target.value })} />
        <FieldError field="location" error={error} />
      </label>
    </>
  );
}

export function ScheduleCheckinForm({ teamId, onScheduled }: { teamId: number; onScheduled: (checkin: Checkin) => void }) {
  const [draft, setDraft] = useState(emptyMeeting);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function save() {
    if (pending) return;
    setError(null);
    setPending(true);
    try {
      const checkin = await checkinsApi.schedule({ ...draft, teamId, scheduledAt: scheduledInstant(draft.scheduledAt) });
      setDraft(emptyMeeting);
      onScheduled(checkin);
    } catch (error) {
      setError(error);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className={styles.card} aria-labelledby="schedule-title">
      <h2 id="schedule-title">Schedule a check-in</h2>
      <ErrorBanner error={error} onRetry={() => void save()} />
      <form onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <fieldset disabled={pending} className={styles.fields}>
          <MeetingFields draft={draft} setDraft={setDraft} error={error} />
          <SubmitButton pending={pending} className={styles.primary}>Schedule check-in</SubmitButton>
        </fieldset>
      </form>
    </section>
  );
}

type MeetingAction = 'cancel' | 'complete';

export type DetailWrite = {
  busy: boolean;
  pending: boolean;
  run: (write: () => Promise<void>) => Promise<void>;
};

export function ManageCheckin({
  checkin, onSaved, refreshCheckin, write,
}: {
  checkin: Checkin;
  onSaved: (checkin: Checkin) => Promise<void>;
  refreshCheckin: () => Promise<void>;
  write: DetailWrite;
}) {
  const [base, setBase] = useState<Checkin | null>(null);
  const [draft, setDraft] = useState(() => meetingDraft(checkin));
  const [action, setAction] = useState<MeetingAction | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [conflict, setConflict] = useState<Checkin | null>(null);
  const scheduled = checkin.status === 'SCHEDULED';

  function startEditing(latest: Checkin) {
    setError(null);
    setBase(latest);
    setDraft(meetingDraft(latest));
  }

  const save = () => write.run(async () => {
    if (!scheduled || (!base && !action)) return;
    setError(null);
    setConflict(null);
    try {
      let saved: Checkin;
      if (base) {
        const original = meetingDraft(base);
        saved = await checkinsApi.update(checkin.checkinId, {
          version: base.version,
          scheduledAt: draft.scheduledAt === original.scheduledAt ? undefined : scheduledInstant(draft.scheduledAt),
          description: draft.description === original.description ? undefined : draft.description,
          location: draft.location === original.location ? undefined : draft.location,
        });
      } else {
        const request = action === 'complete' ? checkinsApi.complete : checkinsApi.cancel;
        saved = await request(checkin.checkinId, checkin.version);
      }
      setBase(null);
      setAction(null);
      await onSaved(saved);
    } catch (error) {
      setError(error);
      if (error instanceof ApiError && error.isVersionConflict && error.current) {
        setConflict(error.current as Checkin);
      }
      if (error instanceof ApiError && error.code === 'INVALID_STATE') await refreshCheckin();
    }
  });

  if (!scheduled) {
    return base ? <UnsavedDraft text={meetingText(draft)} /> : null;
  }

  return (
    <>
      <ErrorBanner error={conflict ? null : error} onRetry={() => void save()} />
      {base ? (
        <form onSubmit={(event) => { event.preventDefault(); void save(); }}>
          <fieldset disabled={write.busy} className={styles.fields}>
            <MeetingFields draft={draft} setDraft={setDraft} error={error} />
            <div className={styles.actions}>
              <SubmitButton pending={write.pending} className={styles.primary}>Save check-in details</SubmitButton>
              <button type="button" onClick={() => { setBase(null); setError(null); }}>Discard edits</button>
            </div>
          </fieldset>
        </form>
      ) : action ? (
        <div className={styles.confirmation}>
          <p>
            {action === 'complete' ? 'Mark this check-in complete?' : 'Cancel this check-in?'}
            {' '}Team progress updates will become read-only.
          </p>
          <div className={styles.actions}>
            <button type="button" disabled={write.busy} onClick={() => void save()}
              className={action === 'complete' ? styles.primary : styles.danger}>
              {write.pending ? 'Saving…' : action === 'complete' ? 'Confirm completion' : 'Confirm cancellation'}
            </button>
            <button type="button" disabled={write.busy} onClick={() => { setAction(null); setError(null); }}>Go back</button>
          </div>
        </div>
      ) : (
        <div className={styles.actions}>
          <button type="button" onClick={() => startEditing(checkin)}>Edit or reschedule</button>
          <button type="button" onClick={() => setAction('complete')}>Mark complete</button>
          <button type="button" className={styles.danger} onClick={() => setAction('cancel')}>Cancel check-in</button>
        </div>
      )}
      {conflict && (
        <ConflictDialog
          latest={conflict}
          latestContent={<SavedVersion text={meetingText(meetingDraft(conflict))} />}
          myChanges={base ? meetingText(draft) : action === 'complete' ? 'Mark complete' : 'Cancel check-in'}
          onReloadLatest={(latest) => void write.run(async () => {
            if (base) startEditing(latest);
            setAction(null);
            setConflict(null);
            setError(null);
            await onSaved(latest);
          })}
          onClose={() => setConflict(null)}
        />
      )}
    </>
  );
}

function meetingText(draft: MeetingDraft) {
  return [draft.scheduledAt.replace('T', ' '), draft.description, draft.location].filter(Boolean).join('\n');
}

type BlockerDraft = Omit<CheckinBlockerInput, 'taskId'> & { key: number; taskId: string };
type ProgressDraft = Omit<SubmitCheckinUpdate, 'blockers'> & { blockers: BlockerDraft[] };

function progressDraft(update?: CheckinUpdate): ProgressDraft {
  return {
    version: update?.version ?? 0,
    completedWork: update ? update.completedWork : '',
    upcomingTasks: update ? update.upcomingTasks : '',
    blockers: update?.blockers.map((blocker) => ({
      key: blocker.blockerId,
      blockerId: blocker.blockerId,
      description: blocker.description,
      severity: blocker.severity,
      taskId: blocker.taskId?.toString() ?? '',
    })) ?? [],
  };
}

function progressRequest(draft: ProgressDraft): SubmitCheckinUpdate {
  return {
    ...draft,
    blockers: draft.blockers.map(({ key: _key, taskId, ...blocker }, index) => {
      const input = taskId.trim();
      const id = Number(input);
      if (input && (!/^\d+$/.test(input) || !Number.isSafeInteger(id) || id <= 0)) {
        throw new ApiError(400, 'VALIDATION_FAILED', 'Some fields need attention.', {
          [`blockers[${index}].taskId`]: 'Enter a positive task ID.',
        });
      }
      return { ...blocker, taskId: input ? id : null };
    }),
  };
}

function progressText(draft: ProgressDraft) {
  const text = [
    'Completed work', draft.completedWork || '—',
    '', 'Upcoming tasks', draft.upcomingTasks || '—',
  ];
  if (draft.blockers.length) {
    text.push('', 'Blockers', ...draft.blockers.map(
      (blocker) => `${blocker.severity} · ${blocker.description}${blocker.taskId ? ` · Task #${blocker.taskId}` : ''}`,
    ));
  }
  return text.join('\n');
}

function SavedVersion({ text }: { text: string }) {
  return <div><strong>Latest saved version</strong><pre className={styles.copy}>{text}</pre></div>;
}

function UnsavedDraft({ text }: { text: string }) {
  return <details><summary>View unsaved draft</summary><pre className={styles.copy}>{text}</pre></details>;
}

export function ProgressForm({
  checkin, initial, onSaved, refreshCheckin, write,
}: {
  checkin: Checkin;
  initial?: CheckinUpdate;
  onSaved: (update: CheckinUpdate) => void;
  refreshCheckin: () => Promise<void>;
  write: DetailWrite;
}) {
  const [draft, setDraft] = useState(() => progressDraft(initial));
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [savedMessage, setSavedMessage] = useState(false);
  const [conflict, setConflict] = useState<CheckinUpdate | null>(null);
  const nextKey = useRef(-1);

  function edit(changes: Partial<ProgressDraft>) {
    setDraft((current) => ({ ...current, ...changes }));
    setDirty(true);
    setSavedMessage(false);
  }

  function editBlocker(key: number, changes: Partial<BlockerDraft>) {
    edit({ blockers: draft.blockers.map((blocker) => blocker.key === key ? { ...blocker, ...changes } : blocker) });
  }

  async function save(event?: FormEvent) {
    event?.preventDefault();
    if (checkin.status !== 'SCHEDULED') return;
    await write.run(async () => {
      setError(null);
      setConflict(null);
      setSavedMessage(false);
      try {
        const saved = await checkinsApi.submitMyUpdate(checkin.checkinId, progressRequest(draft));
        setDraft(progressDraft(saved));
        setDirty(false);
        setSavedMessage(true);
        onSaved(saved);
      } catch (error) {
        setError(error);
        if (error instanceof ApiError && error.isVersionConflict && error.current) {
          setConflict(error.current as CheckinUpdate);
        }
        if (error instanceof ApiError && error.code === 'INVALID_STATE') await refreshCheckin();
      }
    });
  }

  if (checkin.status !== 'SCHEDULED') {
    return dirty ? <section className={styles.card}><UnsavedDraft text={progressText(draft)} /></section> : null;
  }

  return (
    <section className={styles.card} aria-labelledby="progress-title">
      <h2 id="progress-title">My progress update</h2>
      <ErrorBanner error={conflict ? null : error} onRetry={() => void save()} />
      {savedMessage && <p className={styles.success} role="status">Progress saved.</p>}
      <form onSubmit={(event) => void save(event)}>
        <fieldset disabled={write.busy} className={styles.fields}>
          <label>
            Completed work
            <textarea rows={3} value={draft.completedWork ?? ''} onChange={(event) => edit({ completedWork: event.target.value })} />
            <FieldError field="completedWork" error={error} />
          </label>
          <label>
            Upcoming tasks
            <textarea rows={3} value={draft.upcomingTasks ?? ''} onChange={(event) => edit({ upcomingTasks: event.target.value })} />
            <FieldError field="upcomingTasks" error={error} />
          </label>

          {draft.blockers.length > 0 && <h3>Blockers</h3>}
          {draft.blockers.map((blocker, index) => (
            <fieldset className={styles.blockerFields} key={blocker.key}>
              <legend>Blocker {index + 1}</legend>
              <FieldError field={`blockers[${index}]`} error={error} />
              <FieldError field={`blockers[${index}].blockerId`} error={error} />
              <label>
                Blocker description
                <textarea rows={2} required value={blocker.description}
                  onChange={(event) => editBlocker(blocker.key, { description: event.target.value })} />
                <FieldError field={`blockers[${index}].description`} error={error} />
              </label>
              <div className={styles.inlineFields}>
                <label>
                  Severity
                  <select value={blocker.severity} onChange={(event) => editBlocker(blocker.key, {
                    severity: event.target.value as Severity,
                  })}>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </select>
                  <FieldError field={`blockers[${index}].severity`} error={error} />
                </label>
                <label>
                  Linked task ID (optional)
                  <input inputMode="numeric" value={blocker.taskId}
                    onChange={(event) => editBlocker(blocker.key, { taskId: event.target.value })} />
                  <FieldError field={`blockers[${index}].taskId`} error={error} />
                </label>
              </div>
              <button type="button" onClick={() => edit({ blockers: draft.blockers.filter((item) => item.key !== blocker.key) })}>
                Remove blocker
              </button>
            </fieldset>
          ))}
          <div className={styles.actions}>
            <SubmitButton pending={write.pending} className={styles.primary}>Save progress</SubmitButton>
            <button type="button" onClick={() => edit({
              blockers: [...draft.blockers, { key: nextKey.current--, description: '', severity: 'LOW', taskId: '' }],
            })}>Add blocker</button>
          </div>
        </fieldset>
      </form>
      {conflict && (
        <ConflictDialog
          latest={conflict}
          latestContent={<SavedVersion text={progressText(progressDraft(conflict))} />}
          myChanges={progressText(draft)}
          onReloadLatest={(latest) => void write.run(async () => {
            onSaved(latest);
            setDraft(progressDraft(latest));
            setDirty(false);
            setConflict(null);
            setError(null);
          })}
          onClose={() => setConflict(null)}
        />
      )}
    </section>
  );
}
