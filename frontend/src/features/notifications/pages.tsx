// Notification pages. Owner: Raegen.
import { TodoPage } from '../../components/TodoPage';

export function NotificationsPage() {
  return (
    <TodoPage
      title="Notifications"
      owner="Raegen"
      api={['notificationsApi.list', 'notificationsApi.markRead']}
      todos={['List with filters (read/unread, project, task)', 'Mark read/unread; click through to the task or project']}
    />
  );
}

export function NotificationPreferencesPage() {
  return (
    <TodoPage
      title="Notification settings"
      owner="Raegen"
      api={['notificationsApi.getPreferences', 'notificationsApi.updatePreferences']}
      todos={['Email on/off, in-app on/off, check-in reminder lead time']}
    />
  );
}
