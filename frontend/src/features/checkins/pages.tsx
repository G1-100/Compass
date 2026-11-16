// Check-in pages. Owner: Graham.
import { TodoPage } from '../../components/TodoPage';

export function CheckinsPage() {
  return (
    <TodoPage
      title="Check-ins"
      owner="Graham"
      api={['checkinsApi.list', 'checkinsApi.schedule']}
      todos={['Upcoming and past tabs', 'Leads: schedule (future time, description, location or link)', 'Show times in local time']}
    />
  );
}

export function CheckinDetailPage() {
  return (
    <TodoPage
      title="Check-in"
      owner="Graham"
      api={['checkinsApi.get', 'checkinsApi.update', 'checkinsApi.cancel', 'checkinsApi.complete', 'checkinsApi.updates', 'checkinsApi.submitMyUpdate', 'checkinsApi.resolveBlocker']}
      todos={[
        'My update form: completed work, upcoming tasks, blockers (severity, optional linked task)',
        "Everyone's updates; resolve blocker (author or lead)",
        'Leads: edit/reschedule, cancel, mark complete (locks updates)',
      ]}
    />
  );
}
