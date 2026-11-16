import { Link } from 'react-router';

/**
 * Bell icon in the top bar with the unread badge. Owner: Raegen.
 * TODO(Raegen): poll notificationsApi.unreadCount() (e.g. every 60s) and show the count.
 */
export function NotificationBell() {
  return (
    <Link to="/notifications" aria-label="Notifications">
      🔔
    </Link>
  );
}
