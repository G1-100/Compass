// Check-ins API (Detailed Design 3.5.8). Owner: Graham.
import { api } from './client';
import type { Blocker, Checkin, CheckinUpdate, Instant, SubmitCheckinUpdate } from './types';

export const checkinsApi = {
  list: (teamId: number, when: 'upcoming' | 'past' = 'upcoming') => api.get<Checkin[]>('/api/checkins', { teamId, when }),
  schedule: (body: { teamId: number; scheduledAt: Instant; description?: string; location?: string }) =>
    api.post<Checkin>('/api/checkins', body),
  get: (checkinId: number) => api.get<Checkin>(`/api/checkins/${checkinId}`),
  update: (checkinId: number, body: { scheduledAt?: Instant; description?: string; location?: string; version: number }) =>
    api.patch<Checkin>(`/api/checkins/${checkinId}`, body),
  cancel: (checkinId: number, version: number) => api.post<Checkin>(`/api/checkins/${checkinId}/cancel`, { version }),
  complete: (checkinId: number, version: number) => api.post<Checkin>(`/api/checkins/${checkinId}/complete`, { version }),
  updates: (checkinId: number) => api.get<CheckinUpdate[]>(`/api/checkins/${checkinId}/updates`),
  submitMyUpdate: (checkinId: number, body: SubmitCheckinUpdate) =>
    api.put<CheckinUpdate>(`/api/checkins/${checkinId}/updates/me`, body),
  resolveBlocker: (checkinId: number, blockerId: number) =>
    api.patch<Blocker>(`/api/checkins/${checkinId}/blockers/${blockerId}`, {}),
};
