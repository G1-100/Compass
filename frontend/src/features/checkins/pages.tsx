import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { checkinsApi } from '../../api/checkins';
import { getDevUserId } from '../../api/client';
import type { Blocker, Checkin, CheckinUpdate } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorBanner } from '../../components/shared';
import { LocalTime, ManageCheckin, ProgressForm, ScheduleCheckinForm, statusLabel, type DetailWrite } from './CheckinForms';
import styles from './Checkins.module.css';

function useViewer(teamId?: number) {
  const { me, roleIn, isSystemAdmin } = useAuth();
  const devUserId = getDevUserId();
  return {
    userId: devUserId ? Number(devUserId) : me?.user.userId,
    canManage: !!devUserId || isSystemAdmin || (teamId !== undefined && roleIn(teamId) === 'LEAD'),
  };
}

function CheckinStatus({ status }: { status: Checkin['status'] }) {
  return <span className={styles.status} data-status={status}>{statusLabel[status]}</span>;
}

function Location({ value }: { value: string }) {
  const location = value.trim();
  if (/^https?:\/\//i.test(location)) {
    return <a href={location} target="_blank" rel="noreferrer">{location}</a>;
  }
  return location;
}

type When = 'upcoming' | 'past';

export function CheckinsPage() {
  const teamId = Number(useParams().teamId);
  if (!Number.isSafeInteger(teamId) || teamId <= 0) {
    return <div className={styles.page}><h1>Team not found</h1><Link to="/teams">Back to teams</Link></div>;
  }
  return <TeamCheckins teamId={teamId} key={teamId} />;
}

function TeamCheckins({ teamId }: { teamId: number }) {
  const { canManage } = useViewer(teamId);
  const [when, setWhen] = useState<When>('upcoming');
  const [checkins, setCheckins] = useState<Checkin[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [reload, setReload] = useState(0);
  const [scheduled, setScheduled] = useState<Checkin | null>(null);

  useEffect(() => {
    let active = true;
    setError(null);
    setCheckins(null);
    checkinsApi.list(teamId, when).then(
      (result) => { if (active) setCheckins(result); },
      (error) => { if (active) setError(error); },
    );
    return () => { active = false; };
  }, [teamId, when, reload]);

  return (
    <div className={styles.page}>
      <Link className={styles.back} to={`/teams/${teamId}`}>← Back to team</Link>
      <h1>Check-ins</h1>
      <div className={canManage ? styles.columns : undefined}>
        <section aria-label="Team check-ins" className={styles.listSection}>
          <div aria-label="Check-in time" role="group" className={styles.filters}>
            <button type="button" aria-pressed={when === 'upcoming'} onClick={() => setWhen('upcoming')}>Upcoming</button>
            <button type="button" aria-pressed={when === 'past'} onClick={() => setWhen('past')}>Past</button>
          </div>
          {scheduled && (
            <p className={styles.success} role="status">
              Check-in scheduled. <Link to={`/checkins/${scheduled.checkinId}`}>View check-in</Link>
            </p>
          )}
          <ErrorBanner error={error} onRetry={() => setReload((count) => count + 1)} />
          {!checkins && !error && <p role="status">Loading check-ins…</p>}
          {checkins?.length === 0 && <p className={styles.empty}>No {when} check-ins.</p>}
          {!!checkins?.length && (
            <ul className={styles.meetings}>
              {checkins.map((checkin) => (
                <li className={styles.meeting} key={checkin.checkinId}>
                  <div>
                    <Link className={styles.meetingLink} to={`/checkins/${checkin.checkinId}`}>
                      <LocalTime value={checkin.scheduledAt} />
                    </Link>
                    {checkin.description && <p>{checkin.description}</p>}
                    {checkin.location && <p className={styles.secondaryText}><Location value={checkin.location} /></p>}
                  </div>
                  <CheckinStatus status={checkin.status} />
                </li>
              ))}
            </ul>
          )}
        </section>
        {canManage && (
          <ScheduleCheckinForm teamId={teamId} onScheduled={(checkin) => {
            setScheduled(checkin);
            setWhen('upcoming');
            setReload((count) => count + 1);
          }} />
        )}
      </div>
    </div>
  );
}

export function CheckinDetailPage() {
  const checkinId = Number(useParams().checkinId);
  if (!Number.isSafeInteger(checkinId) || checkinId <= 0) {
    return <div className={styles.page}><h1>Check-in not found</h1><Link to="/teams">Back to teams</Link></div>;
  }
  return <CheckinDetails checkinId={checkinId} key={checkinId} />;
}

type DetailAction = 'meeting' | 'progress' | 'blocker' | 'refresh';

function CheckinDetails({ checkinId }: { checkinId: number }) {
  const [checkin, setCheckin] = useState<Checkin | null>(null);
  const [updates, setUpdates] = useState<CheckinUpdate[]>([]);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [refreshError, setRefreshError] = useState<unknown>(null);
  const [reload, setReload] = useState(0);
  const [pending, setPending] = useState<DetailAction | null>(null);
  const writing = useRef(false);
  const { canManage, userId } = useViewer(checkin?.teamId);

  useEffect(() => {
    let active = true;
    setLoadError(null);
    Promise.all([checkinsApi.get(checkinId), checkinsApi.updates(checkinId)]).then(
      ([checkin, updates]) => {
        if (!active) return;
        setCheckin(checkin);
        setUpdates(updates);
      },
      (error) => { if (active) setLoadError(error); },
    );
    return () => { active = false; };
  }, [checkinId, reload]);

  function writeFor(action: DetailAction): DetailWrite {
    return {
      busy: pending !== null,
      pending: pending === action,
      async run(write) {
        if (writing.current) return;
        writing.current = true;
        setPending(action);
        try {
          await write();
        } finally {
          writing.current = false;
          setPending(null);
        }
      },
    };
  }

  async function onCheckinSaved(saved: Checkin) {
    setCheckin((previous) => !previous || saved.version >= previous.version ? saved : previous);
    if (saved.status !== 'SCHEDULED') {
      setRefreshError(null);
      try {
        setUpdates(await checkinsApi.updates(checkinId));
      } catch (error) {
        setRefreshError(error);
      }
    }
  }

  async function refreshCheckin() {
    setRefreshError(null);
    try {
      await onCheckinSaved(await checkinsApi.get(checkinId));
    } catch (error) {
      setRefreshError(error);
    }
  }

  function onProgressSaved(saved: CheckinUpdate) {
    setUpdates((previous) => [saved, ...previous.filter((update) => update.updateId !== saved.updateId)]
      .sort((left, right) => Date.parse(right.submittedAt) - Date.parse(left.submittedAt) || right.updateId - left.updateId));
  }

  function onBlockerResolved(saved: Blocker) {
    setUpdates((previous) => previous.map((update) => update.updateId === saved.updateId
      ? { ...update, blockers: update.blockers.map((blocker) => blocker.blockerId === saved.blockerId ? saved : blocker) }
      : update));
  }

  if (!checkin) {
    return (
      <div className={styles.page}>
        <h1>Check-in</h1>
        <ErrorBanner error={loadError} onRetry={() => setReload((count) => count + 1)} />
        {!loadError && <p role="status">Loading check-in…</p>}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Link className={styles.back} to={`/teams/${checkin.teamId}/checkins`}>← All check-ins</Link>
      <h1>Check-in</h1>
      <ErrorBanner error={refreshError} onRetry={() => void writeFor('refresh').run(refreshCheckin)} />
      <section className={styles.card} aria-labelledby="meeting-time">
        <div className={styles.meetingHeader}>
          <h2 id="meeting-time"><LocalTime value={checkin.scheduledAt} /></h2>
          <CheckinStatus status={checkin.status} />
        </div>
        {checkin.description && <p className={styles.preserve}>{checkin.description}</p>}
        {checkin.location && <p><strong>Location</strong> <Location value={checkin.location} /></p>}
        {checkin.completedAt && <p className={styles.secondaryText}>Completed <LocalTime value={checkin.completedAt} /></p>}
        {canManage && <ManageCheckin checkin={checkin} onSaved={onCheckinSaved}
          refreshCheckin={refreshCheckin} write={writeFor('meeting')} />}
      </section>
      {checkin.status !== 'SCHEDULED' && (
        <p className={styles.notice}>This check-in is closed. Progress updates are read-only.</p>
      )}
      <div className={styles.details}>
        {userId !== undefined && (
          <ProgressForm checkin={checkin} initial={updates.find((update) => update.userId === userId)}
            onSaved={onProgressSaved} refreshCheckin={refreshCheckin} write={writeFor('progress')} />
        )}
        <TeamUpdates checkinId={checkinId} updates={updates} userId={userId} canManage={canManage}
          onResolved={onBlockerResolved} write={writeFor('blocker')} />
      </div>
    </div>
  );
}

function TeamUpdates({
  checkinId, updates, userId, canManage, onResolved, write,
}: {
  checkinId: number;
  updates: CheckinUpdate[];
  userId?: number;
  canManage: boolean;
  onResolved: (blocker: Blocker) => void;
  write: DetailWrite;
}) {
  const [resolving, setResolving] = useState<number | null>(null);
  const [failure, setFailure] = useState<{ blockerId: number; error: unknown } | null>(null);

  const resolve = (blockerId: number) => write.run(async () => {
    setFailure(null);
    setResolving(blockerId);
    try {
      onResolved(await checkinsApi.resolveBlocker(checkinId, blockerId));
    } catch (error) {
      setFailure({ blockerId, error });
    } finally {
      setResolving(null);
    }
  });

  return (
    <section className={styles.card} aria-labelledby="updates-title">
      <h2 id="updates-title">Submitted updates</h2>
      {updates.length === 0 && <p className={styles.secondaryText}>No updates submitted yet.</p>}
      <ul className={styles.updates}>
        {updates.map((update) => (
          <li key={update.updateId} className={styles.update}>
            <div className={styles.updateHeader}>
              <h3>{update.displayName}{update.userId === userId ? ' (you)' : ''}</h3>
              <span className={styles.secondaryText}><LocalTime value={update.submittedAt} /></span>
            </div>
            <dl className={styles.updateText}>
              <dt>Completed work</dt><dd>{update.completedWork || '—'}</dd>
              <dt>Upcoming tasks</dt><dd>{update.upcomingTasks || '—'}</dd>
            </dl>
            {update.blockers.length > 0 && (
              <ul className={styles.blockers} aria-label="Blockers">
                {update.blockers.map((blocker) => (
                  <li className={styles.blocker} key={blocker.blockerId}>
                    <span className={styles.severity} data-severity={blocker.severity}>{blocker.severity.toLowerCase()}</span>
                    <p>{blocker.description}</p>
                    {blocker.taskId !== null && <Link to={`/tasks/${blocker.taskId}`}>Task #{blocker.taskId}</Link>}
                    {blocker.resolvedAt ? (
                      <span className={styles.secondaryText}>Resolved <LocalTime value={blocker.resolvedAt} /></span>
                    ) : (canManage || update.userId === userId) && (
                      <button type="button" disabled={write.busy} onClick={() => void resolve(blocker.blockerId)}>
                        {resolving === blocker.blockerId ? 'Resolving…' : 'Resolve blocker'}
                      </button>
                    )}
                    {failure?.blockerId === blocker.blockerId && (
                      <ErrorBanner error={failure.error} onRetry={() => void resolve(blocker.blockerId)} />
                    )}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
