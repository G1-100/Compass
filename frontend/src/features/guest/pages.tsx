// Guest sharing UI. Owner: Ramanjot.
import { TodoPage } from '../../components/TodoPage';

/** Embedded in the dashboard for leads. */
export function GuestLinksPanel({ projectId }: { projectId: number }) {
  return (
    <TodoPage
      title={`Guest links (project ${projectId})`}
      owner="Ramanjot"
      api={['guestApi.list', 'guestApi.create', 'guestApi.revoke']}
      todos={['Create link with optional expiry; copy share URL (shown once)', 'List with status; revoke']}
    />
  );
}

/** Public page at /guest/:token — no sign-in, no app chrome. */
export function GuestViewPage() {
  return (
    <TodoPage
      title="Shared project"
      owner="Ramanjot"
      api={['guestApi.view']}
      todos={['Read-only summary, milestones with progress, task titles + statuses, member names', 'GUEST_LINK_UNAVAILABLE -> "This link has expired"']}
    />
  );
}
