// Teams, members, invitations, projects and dashboard pages. Owner: Solomon.
import { TodoPage } from '../../components/TodoPage';

export function TeamsPage() {
  return (
    <TodoPage
      title="My teams"
      owner="Solomon"
      api={['teamsApi.listMine', 'teamsApi.create']}
      todos={['List teams with my role', 'Create team (name 1-60 chars)', 'Link each team to /teams/:teamId']}
    />
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
