// Guest sharing UI. Owner: Ramanjot.
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router';
import { ApiError } from '../../api/client';
import { guestApi } from '../../api/guest';
import type { CreatedGuestLink, DeadlineStatus, GuestLink, GuestLinkStatus, GuestProjectView, TaskStatus } from '../../api/types';
import { ErrorBanner, FieldError, SubmitButton } from '../../components/shared';
import styles from './Guest.module.css';

const LINK_STATUS_LABEL: Record<GuestLinkStatus, string> = { ACTIVE: 'Active', EXPIRED: 'Expired', REVOKED: 'Revoked' };
const TASK_STATUS_LABEL: Record<TaskStatus, string> = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  DONE: 'Done',
};
const DEADLINE_LABEL: Record<DeadlineStatus, string> = {
  COMPLETE: 'Complete',
  OVERDUE: 'Overdue',
  APPROACHING: 'Due soon',
  ON_SCHEDULE: 'On schedule',
};

export function formatDateTime(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/** "2026-12-01T09:30" from a datetime-local input (local time) -> ISO instant, or null when empty. */
export function localInputToInstant(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function ProgressBar({ percent, label }: { percent: number | null; label: string }) {
  if (percent === null) return <span className={styles.muted}>Progress unavailable</span>;
  const clamped = Math.max(0, Math.min(100, percent));
  return (
    <div className={styles.progress}>
      <div
        className={styles.progressTrack}
        role="progressbar"
        aria-label={label}
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div className={styles.progressFill} style={{ width: `${clamped}%` }} />
      </div>
      <span className={styles.progressValue}>{Math.round(clamped)}%</span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Guest links panel (dashboard, team leads)
// ---------------------------------------------------------------------------

/** Embedded in the dashboard for leads. */
export function GuestLinksPanel({ projectId }: { projectId: number }) {
  const [links, setLinks] = useState<GuestLink[] | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [expiresAt, setExpiresAt] = useState('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);
  const [created, setCreated] = useState<CreatedGuestLink | null>(null);
  const [copied, setCopied] = useState(false);

  const [confirmRevokeId, setConfirmRevokeId] = useState<number | null>(null);
  const [revokingId, setRevokingId] = useState<number | null>(null);
  const [revokeError, setRevokeError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      setLinks(await guestApi.list(projectId));
    } catch (e) {
      setLoadError(e);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setCreating(true);
    setCreateError(null);
    setCopied(false);
    try {
      const result = await guestApi.create(projectId, localInputToInstant(expiresAt));
      setCreated(result);
      setExpiresAt('');
      setLinks((current) => [result.link, ...(current ?? []).filter((l) => l.linkId !== result.link.linkId)]);
    } catch (e) {
      setCreateError(e);
    } finally {
      setCreating(false);
    }
  }

  async function handleCopy() {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.shareUrl);
      setCopied(true);
    } catch {
      setCopied(false); // clipboard blocked; the URL stays selectable in the input
    }
  }

  async function handleRevoke(linkId: number) {
    setRevokingId(linkId);
    setRevokeError(null);
    try {
      await guestApi.revoke(projectId, linkId);
      setConfirmRevokeId(null);
      if (created?.link.linkId === linkId) setCreated(null);
      await load();
    } catch (e) {
      setRevokeError(e);
    } finally {
      setRevokingId(null);
    }
  }

  return (
    <section className={styles.panel} aria-labelledby="guest-links-heading">
      <div className={styles.panelHeader}>
        <h2 id="guest-links-heading" className={styles.panelTitle}>
          Guest links
        </h2>
        <p className={styles.muted}>
          Share a read-only view of this project: name, summary, progress, milestones, task titles and member names.
        </p>
      </div>

      <form className={styles.createForm} onSubmit={handleCreate} noValidate>
        <label className={styles.field}>
          <span>Expires (optional)</span>
          <input
            type="datetime-local"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
            aria-describedby="guest-expiry-hint"
          />
          <span id="guest-expiry-hint" className={styles.hint}>
            Leave empty for a link that works until you revoke it.
          </span>
          <FieldError error={createError} field="expiresAt" />
        </label>
        <SubmitButton pending={creating} className={styles.primaryButton}>
          Create link
        </SubmitButton>
      </form>
      {!(createError instanceof ApiError && createError.fields.expiresAt) && <ErrorBanner error={createError} />}

      {created && (
        <div className={styles.created} role="status">
          <p className={styles.createdTitle}>Link created. Copy it now; it won't be shown again.</p>
          <div className={styles.copyRow}>
            <input
              className={styles.copyInput}
              readOnly
              value={created.shareUrl}
              aria-label="Share URL"
              onFocus={(e) => e.target.select()}
            />
            <button type="button" className={styles.secondaryButton} onClick={handleCopy}>
              {copied ? 'Copied' : 'Copy'}
            </button>
            <button type="button" className={styles.textButton} onClick={() => setCreated(null)}>
              Done
            </button>
          </div>
        </div>
      )}

      <ErrorBanner error={loadError} onRetry={load} />
      <ErrorBanner error={revokeError} />

      {links === null && !loadError ? (
        <p className={styles.muted}>Loading links…</p>
      ) : links && links.length === 0 ? (
        <p className={styles.muted}>No guest links yet.</p>
      ) : links ? (
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">Created</th>
              <th scope="col">Expires</th>
              <th scope="col">Status</th>
              <th scope="col">
                <span className={styles.srOnly}>Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {links.map((link) => (
              <tr key={link.linkId}>
                <td>{formatDateTime(link.createdAt)}</td>
                <td>{link.expiresAt ? formatDateTime(link.expiresAt) : 'Never'}</td>
                <td>
                  <span className={`${styles.badge} ${styles[`badge${link.status}`]}`}>{LINK_STATUS_LABEL[link.status]}</span>
                </td>
                <td className={styles.actions}>
                  {link.status === 'ACTIVE' &&
                    (confirmRevokeId === link.linkId ? (
                      <>
                        <button
                          type="button"
                          className={styles.dangerButton}
                          disabled={revokingId === link.linkId}
                          onClick={() => handleRevoke(link.linkId)}
                        >
                          {revokingId === link.linkId ? 'Revoking…' : 'Confirm revoke'}
                        </button>
                        <button type="button" className={styles.textButton} onClick={() => setConfirmRevokeId(null)}>
                          Cancel
                        </button>
                      </>
                    ) : (
                      <button type="button" className={styles.secondaryButton} onClick={() => setConfirmRevokeId(link.linkId)}>
                        Revoke
                      </button>
                    ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Public guest view (/guest/:token)
// ---------------------------------------------------------------------------

/** Public page at /guest/:token — no sign-in, no app chrome. */
export function GuestViewPage() {
  const { token = '' } = useParams();
  const [view, setView] = useState<GuestProjectView | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setView(await guestApi.view(token));
    } catch (e) {
      setError(e);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    document.title = view ? `${view.projectName} · Compass` : 'Shared project · Compass';
  }, [view]);

  const unavailable = error instanceof ApiError && (error.code === 'GUEST_LINK_UNAVAILABLE' || error.status === 404);

  return (
    <div className={styles.guestPage}>
      <header className={styles.guestHeader}>
        <span className={styles.brand}>Compass</span>
        <span className={styles.readOnly}>Read-only shared view</span>
      </header>

      <main className={styles.guestMain}>
        {unavailable ? (
          <div className={styles.unavailable}>
            <h1>This link has expired</h1>
            <p className={styles.muted}>
              The link may have been revoked or reached its expiry date. Ask the project lead for a new one.
            </p>
          </div>
        ) : error ? (
          <ErrorBanner error={error} onRetry={load} />
        ) : !view ? (
          <p className={styles.muted}>Loading…</p>
        ) : (
          <GuestProject view={view} />
        )}
      </main>
    </div>
  );
}

function GuestProject({ view }: { view: GuestProjectView }) {
  const done = view.tasks.filter((t) => t.status === 'DONE').length;
  return (
    <>
      <section className={styles.hero}>
        <h1 className={styles.projectName}>{view.projectName}</h1>
        {view.summary && <p className={styles.summary}>{view.summary}</p>}
        <div className={styles.overall}>
          <span className={styles.label}>Overall progress</span>
          <ProgressBar percent={view.overallProgressPercent} label="Overall progress" />
          {view.tasks.length > 0 && (
            <span className={styles.muted}>
              {done} of {view.tasks.length} tasks done
            </span>
          )}
        </div>
      </section>

      <div className={styles.grid}>
        <section className={styles.card} aria-labelledby="guest-milestones">
          <h2 id="guest-milestones" className={styles.cardTitle}>
            Milestones
          </h2>
          {view.milestones.length === 0 ? (
            <p className={styles.muted}>No milestones yet.</p>
          ) : (
            <ul className={styles.list}>
              {view.milestones.map((m, i) => (
                <li key={`${m.name}-${i}`} className={styles.milestone}>
                  <div className={styles.rowBetween}>
                    <span className={styles.itemName}>{m.name}</span>
                    {m.deadlineStatus && (
                      <span className={`${styles.badge} ${styles[`deadline${m.deadlineStatus}`]}`}>
                        {DEADLINE_LABEL[m.deadlineStatus]}
                      </span>
                    )}
                  </div>
                  <ProgressBar percent={m.progressPercent} label={`${m.name} progress`} />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={styles.card} aria-labelledby="guest-tasks">
          <h2 id="guest-tasks" className={styles.cardTitle}>
            Tasks
          </h2>
          {view.tasks.length === 0 ? (
            <p className={styles.muted}>No tasks yet.</p>
          ) : (
            <ul className={styles.list}>
              {view.tasks.map((t, i) => (
                <li key={`${t.title}-${i}`} className={styles.rowBetween}>
                  <span className={styles.itemName}>{t.title}</span>
                  <span className={`${styles.badge} ${styles[`task${t.status}`]}`}>{TASK_STATUS_LABEL[t.status]}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={styles.card} aria-labelledby="guest-members">
          <h2 id="guest-members" className={styles.cardTitle}>
            Team
          </h2>
          {view.memberDisplayNames.length === 0 ? (
            <p className={styles.muted}>No members listed.</p>
          ) : (
            <ul className={styles.members}>
              {view.memberDisplayNames.map((name, i) => (
                <li key={`${name}-${i}`} className={styles.member}>
                  <span className={styles.avatar} aria-hidden="true">
                    {name.trim().charAt(0).toUpperCase()}
                  </span>
                  {name}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
