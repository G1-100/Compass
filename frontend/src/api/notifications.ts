// Notifications API (Detailed Design 3.5.11). Owner: Raegen.
import { api } from './client';
import type { Notification, NotificationPreference, Page } from './types';

export const notificationsApi = {
  list: (filter: { read?: boolean; projectId?: number; taskId?: number; page?: number } = {}) =>
    api.get<Page<Notification>>('/api/notifications', { ...filter }),
  unreadCount: () => api.get<{ count: number }>('/api/notifications/unread-count'),
  markRead: (notifId: number, isRead = true) => api.patch<Notification>(`/api/notifications/${notifId}`, { isRead }),
  getPreferences: () => api.get<NotificationPreference>('/api/users/me/notification-preferences'),
  updatePreferences: (body: { emailEnabled: boolean; inAppEnabled: boolean; earlyNotifMinutes?: number | null }) =>
    api.put<NotificationPreference>('/api/users/me/notification-preferences', body),
  sendCustom: (projectId: number, body: { message: string; recipientUserIds: number[]; alsoEmail: boolean }) =>
    api.post(`/api/projects/${projectId}/notifications`, body),
};
