// Sprints and milestones pages. Owner: Siya.
import { TodoPage } from '../../components/TodoPage';

export function SprintsPage() {
  return (
    <TodoPage
      title="Sprints"
      owner="Siya"
      api={['sprintsApi.list', 'sprintsApi.create', 'sprintsApi.update', 'sprintsApi.start', 'sprintsApi.complete', 'sprintsApi.delete']}
      todos={[
        'Sprints with status and dates; backlog section',
        'Leads: create, edit, start, delete (PLANNED only)',
        'Complete Sprint dialog: choose next sprint or backlog; ConflictDialog on 409',
      ]}
    />
  );
}

export function MilestonesPage() {
  return (
    <TodoPage
      title="Milestones"
      owner="Siya"
      api={['milestonesApi.list', 'milestonesApi.create', 'milestonesApi.update', 'milestonesApi.replaceTasks', 'milestonesApi.delete']}
      todos={[
        'Progress bar per milestone; "Progress unavailable" when no tasks',
        'Highlight APPROACHING and OVERDUE',
        'Leads: create/edit, choose linked tasks',
      ]}
    />
  );
}
