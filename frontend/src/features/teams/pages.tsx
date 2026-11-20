// Teams, members, invitations, projects and dashboard pages. Owner: Solomon.
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/client';
import { notificationsApi } from '../../api/notifications';
import { teamsApi } from '../../api/teams';
import type { TeamRole, TeamWithRole } from '../../api/types';
import { TodoPage } from '../../components/TodoPage';
import styles from './TeamsPage.module.css';

interface Channel {
  id: string;
  name: string;
  role: TeamRole;
  version?: number;
  teamId?: number;
}

const previewChannels: Channel[] = [
  { id: 'csci-201', name: 'CSCI 201', role: 'LEAD' },
  { id: 'ee-250', name: 'EE 250', role: 'MEMBER' },
  { id: 'phys-151', name: 'PHYS 151', role: 'MEMBER' },
  { id: 'danc-312', name: 'DANC 312', role: 'MEMBER' },
];

function toChannel(item: TeamWithRole): Channel {
  return {
    id: String(item.team.teamId),
    teamId: item.team.teamId,
    name: item.team.name,
    role: item.myRole ?? 'MEMBER',
    version: item.team.version,
  };
}

function GroupIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="6.5" r="3" />
      <circle cx="5.5" cy="8" r="2.2" />
      <circle cx="18.5" cy="8" r="2.2" />
      <path d="M7.5 19.5v-3.75a4.5 4.5 0 0 1 9 0v3.75z" />
      <path d="M1.5 18.5v-2.25a3.25 3.25 0 0 1 4.6-2.95 5.9 5.9 0 0 0-.6 2.45v2.75z" />
      <path d="M22.5 18.5v-2.25a3.25 3.25 0 0 0-4.6-2.95 5.9 5.9 0 0 1 .6 2.45v2.75z" />
    </svg>
  );
}

function MailIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="2.5" y="5" width="19" height="14" rx="1.5" />
      <path className="flap" d="M3.5 6.5 12 12.5l8.5-6" />
    </svg>
  );
}

function KebabIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="5" r="1.8" />
      <circle cx="12" cy="12" r="1.8" />
      <circle cx="12" cy="19" r="1.8" />
    </svg>
  );
}

function ChannelCard({
  channel,
  onEdit,
  onDelete,
  onLeave,
}: {
  channel: Channel;
  onEdit: (channel: Channel) => void;
  onDelete: (channel: Channel) => void;
  onLeave: (channel: Channel) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('pointerdown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [menuOpen]);

  const isLiveChannel = channel.teamId !== undefined;
  const channelUrl = isLiveChannel ? `/teams/${channel.teamId}` : undefined;

  return (
    <article className={styles.channelCard} ref={menuRef}>
      {channelUrl ? (
        <Link to={channelUrl} className={styles.channelLink} aria-label={`Open ${channel.name}`}>
          <div className={styles.channelPreview} />
        </Link>
      ) : (
        <div className={styles.channelPreview} aria-hidden="true" />
      )}
      <div className={styles.channelFooter}>
        <div className={styles.channelMeta}>
          <h2>{channelUrl ? <Link to={channelUrl}>{channel.name}</Link> : channel.name}</h2>
          <p>{channel.role === 'LEAD' ? 'Team Lead' : 'Member'}</p>
        </div>
        <button
          type="button"
          className={styles.moreButton}
          aria-label={`Options for ${channel.name}`}
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <KebabIcon />
        </button>
      </div>
      {menuOpen && (
        <div className={styles.channelMenu} role="menu" aria-label={`${channel.name} options`}>
          {channelUrl ? (
            <Link to={channelUrl} role="menuitem" onClick={() => setMenuOpen(false)}>Open channel</Link>
          ) : (
            <button type="button" role="menuitem" disabled title="Available when live channel data is connected">Open channel</button>
          )}
          {channel.role === 'LEAD' ? (
            <>
              {channelUrl ? (
                <Link to={`${channelUrl}/members`} role="menuitem" onClick={() => setMenuOpen(false)}>Manage members</Link>
              ) : (
                <button type="button" role="menuitem" disabled title="Available when live channel data is connected">Manage members</button>
              )}
              <button type="button" role="menuitem" disabled={!channelUrl} onClick={() => { setMenuOpen(false); onEdit(channel); }}>
                Edit channel
              </button>
              <button type="button" role="menuitem" className={styles.dangerMenuItem} disabled={!channelUrl} onClick={() => { setMenuOpen(false); onDelete(channel); }}>
                Delete channel
              </button>
            </>
          ) : (
            <button type="button" role="menuitem" className={styles.dangerMenuItem} disabled={!channelUrl} onClick={() => { setMenuOpen(false); onLeave(channel); }}>
              Leave channel
            </button>
          )}
        </div>
      )}
    </article>
  );
}

export function TeamsPage() {
  const [channels, setChannels] = useState<Channel[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogError, setDialogError] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [teamName, setTeamName] = useState('');
  const [editingChannel, setEditingChannel] = useState<Channel | null>(null);
  const [pendingAction, setPendingAction] = useState<{ type: 'delete' | 'leave'; channel: Channel } | null>(null);
  const [saving, setSaving] = useState(false);
  const [hasUnread, setHasUnread] = useState(false);

  useEffect(() => {
    let active = true;
    notificationsApi
      .unreadCount()
      .then(({ count }) => {
        if (active) setHasUnread(count > 0);
      })
      .catch(() => {
        /* Unread badge is optional; ignore until the endpoint is live. */
      });
    return () => {
      active = false;
    };
  }, []);

  function openCreateDialog() {
    setDialogError('');
    setCreateOpen(true);
  }

  useEffect(() => {
    let active = true;
    teamsApi
      .listMine()
      .then((items) => {
        if (active) setChannels(items.map(toChannel));
      }).catch((err: unknown) => {
        if (!active) return;
        setChannels(previewChannels);
        console.warn(
          err instanceof ApiError && err.code === 'NOT_IMPLEMENTED'
            ? 'Team listing is not implemented yet; showing design preview channels.'
            : 'Could not load live channels; showing design preview channels.',
          err,
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function createTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = teamName.trim();
    if (!name) return;
    setSaving(true);
    setDialogError('');
    try {
      const created = await teamsApi.create(name);
      setChannels((current) => [...current, toChannel(created)]);
      setTeamName('');
      setCreateOpen(false);
    } catch (err) {
      setDialogError(err instanceof Error ? `Could not create channel: ${err.message}` : 'Could not create channel');
    } finally {
      setSaving(false);
    }
  }

  async function editTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editingChannel?.teamId || editingChannel.version === undefined) return;
    const name = teamName.trim();
    if (!name) return;
    setSaving(true);
    setDialogError('');
    try {
      const updated = await teamsApi.rename(editingChannel.teamId, name, editingChannel.version);
      setChannels((current) =>
        current.map((channel) =>
          channel.teamId === updated.teamId ? { ...channel, name: updated.name, version: updated.version } : channel,
        ),
      );
      setEditingChannel(null);
    } catch (err) {
      setDialogError(err instanceof Error ? `Could not edit channel: ${err.message}` : 'Could not edit channel');
    } finally {
      setSaving(false);
    }
  }

  async function performPendingAction() {
    if (!pendingAction?.channel.teamId) return;
    setSaving(true);
    setDialogError('');
    try {
      if (pendingAction.type === 'delete') {
        await teamsApi.delete(pendingAction.channel.teamId);
      } else {
        await teamsApi.leave(pendingAction.channel.teamId);
      }
      setChannels((current) => current.filter((channel) => channel.teamId !== pendingAction.channel.teamId));
      setPendingAction(null);
    } catch (err) {
      const action = pendingAction.type === 'delete' ? 'delete' : 'leave';
      setDialogError(err instanceof Error ? `Could not ${action} channel: ${err.message}` : `Could not ${action} channel`);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className={styles.page}>
      <div className={styles.headingRow}>
        <div className={styles.heading}>
          <h1 className={styles.title}>My Channels</h1>
          <span className={styles.count}>{loading ? '...' : channels.length + 2}</span>
        </div>
        <div className={styles.quickActions}>
          <Link to="/invitations" className={styles.quickButton} aria-label="Invitations">
            <GroupIcon />
          </Link>
          <Link to="/notifications" className={styles.quickButton} aria-label={hasUnread ? 'Notifications (unread)' : 'Notifications'}>
            <MailIcon />
            {hasUnread && <span className={styles.unreadDot} aria-hidden="true" />}
          </Link>
          <button type="button" className={`${styles.quickButton} ${styles.quickCreate}`} aria-label="Create a channel" onClick={openCreateDialog}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
          </button>
        </div>
      </div>

      <div className={styles.channelGrid}>
        {channels.map((channel) => (
          <ChannelCard
            key={channel.id}
            channel={channel}
            onEdit={(item) => {
              setDialogError('');
              setTeamName(item.name);
              setEditingChannel(item);
            }}
            onDelete={(item) => {
              setDialogError('');
              setPendingAction({ type: 'delete', channel: item });
            }}
            onLeave={(item) => {
              setDialogError('');
              setPendingAction({ type: 'leave', channel: item });
            }}
          />
        ))}

        <button type="button" className={styles.actionCard} onClick={openCreateDialog}>
          <span className={styles.actionPreview} aria-hidden="true">
            <span className={styles.actionIcon}><svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" /></svg></span>
          </span>
          <span className={styles.actionFooter}><strong>Create</strong><small>New channel</small></span>
        </button>

        <Link to="/invitations" className={styles.actionCard}>
          <span className={styles.actionPreview} aria-hidden="true">
            <span className={`${styles.actionIcon} ${styles.actionIconFilled}`}><GroupIcon /></span>
          </span>
          <span className={styles.actionFooter}><strong>Join</strong><small>Existing channel</small></span>
        </Link>
      </div>

      {createOpen && (
        <div className={styles.modalBackdrop} role="presentation" onMouseDown={() => setCreateOpen(false)}>
          <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="create-team-title" onMouseDown={(event) => event.stopPropagation()}>
            <h2 id="create-team-title">Create a channel</h2>
            {dialogError && <p className={styles.error} role="alert">{dialogError}</p>}
            <form onSubmit={createTeam}>
              <label htmlFor="team-name">Channel name</label>
              <input
                id="team-name"
                value={teamName}
                onChange={(event) => setTeamName(event.target.value)}
                maxLength={60}
                minLength={1}
                autoFocus
                placeholder="Enter a name"
              />
              <div className={styles.modalActions}>
                <button type="button" className={styles.cancelButton} onClick={() => setCreateOpen(false)}>Cancel</button>
                <button type="submit" className={styles.submitButton} disabled={saving || !teamName.trim()}>
                  {saving ? 'Creating...' : 'Create channel'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {editingChannel && (
        <div className={styles.modalBackdrop} role="presentation" onMouseDown={() => setEditingChannel(null)}>
          <div className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="edit-team-title" onMouseDown={(event) => event.stopPropagation()}>
            <h2 id="edit-team-title">Edit channel</h2>
            {dialogError && <p className={styles.error} role="alert">{dialogError}</p>}
            <form onSubmit={editTeam}>
              <label htmlFor="edit-team-name">Channel name</label>
              <input id="edit-team-name" value={teamName} onChange={(event) => setTeamName(event.target.value)} maxLength={60} minLength={1} autoFocus />
              <div className={styles.modalActions}>
                <button type="button" className={styles.cancelButton} onClick={() => setEditingChannel(null)}>Cancel</button>
                <button type="submit" className={styles.submitButton} disabled={saving || !teamName.trim()}>
                  {saving ? 'Saving...' : 'Save changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {pendingAction && (
        <div className={styles.modalBackdrop} role="presentation" onMouseDown={() => setPendingAction(null)}>
          <div className={styles.modal} role="alertdialog" aria-modal="true" aria-labelledby="confirm-channel-action" onMouseDown={(event) => event.stopPropagation()}>
            <h2 id="confirm-channel-action">{pendingAction.type === 'delete' ? 'Delete channel?' : 'Leave channel?'}</h2>
            <p>
              {pendingAction.type === 'delete'
                ? `This will permanently delete ${pendingAction.channel.name} and its associated data.`
                : `You will lose access to ${pendingAction.channel.name}.`}
            </p>
            {dialogError && <p className={styles.error} role="alert">{dialogError}</p>}
            <div className={styles.modalActions}>
              <button type="button" className={styles.cancelButton} onClick={() => setPendingAction(null)}>Cancel</button>
              <button type="button" className={styles.dangerButton} disabled={saving} onClick={() => void performPendingAction()}>
                {saving ? 'Working...' : pendingAction.type === 'delete' ? 'Delete channel' : 'Leave channel'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

export function TeamPage() {
  return (
    <TodoPage
      title="Team"
      owner="Solomon"
      api={['teamsApi.get', 'projectsApi.listForTeam', 'projectsApi.create', 'teamsApi.rename', 'teamsApi.delete']}
      todos={['Team header with rename/delete (leads)', 'Project list with "show archived" toggle', 'Create project (leads)', 'Tabs/links: Members, Check-ins']}
    />
  );
}

export function TeamMembersPage() {
  return (
    <TodoPage
      title="Team members"
      owner="Solomon"
      api={['teamsApi.listMembers', 'teamsApi.changeRole', 'teamsApi.removeMember', 'teamsApi.leave', 'teamsApi.invite', 'teamsApi.listInvitations', 'teamsApi.revokeInvitation', 'teamsApi.auditLog']}
      todos={[
        'Members with role and join date',
        'Leads: change role, remove, invite by email, revoke pending invites',
        'Explain the 409 when removing/demoting the last lead',
        'Audit-trail modal (leads)',
      ]}
    />
  );
}

export function MyInvitationsPage() {
  return (
    <TodoPage
      title="Invitations"
      owner="Solomon"
      api={['teamsApi.myInvitations', 'teamsApi.acceptInvitation', 'teamsApi.declineInvitation']}
      todos={['Pending invitations sent to my email', 'Accept / decline']}
    />
  );
}

export function DashboardPage() {
  return (
    <TodoPage
      title="Project dashboard"
      owner="Solomon"
      api={['projectsApi.dashboard', 'projectsApi.update', 'projectsApi.archive']}
      todos={[
        'Project progress, team assignments, upcoming deadlines, milestones with status, my tasks',
        'Highlight approaching/overdue milestones',
        'Slots for <WorkloadPanel> (Liv) and <GuestLinksPanel> (Ramanjot) for leads',
        'Links to Tasks, Sprints, Milestones',
      ]}
    />
  );
}
