// Guest links API (Detailed Design 3.5.3, 3.5.9). Owner: Ramanjot.
import { api } from './client';
import type { CreatedGuestLink, GuestLink, GuestProjectView, Instant } from './types';

export const guestApi = {
  list: (projectId: number) => api.get<GuestLink[]>(`/api/projects/${projectId}/guest-links`),
  create: (projectId: number, expiresAt: Instant | null = null) =>
    api.post<CreatedGuestLink>(`/api/projects/${projectId}/guest-links`, { expiresAt }),
  revoke: (projectId: number, linkId: number) => api.delete(`/api/projects/${projectId}/guest-links/${linkId}`),
  /** Public — no session. Throws ApiError code GUEST_LINK_UNAVAILABLE for bad/expired/revoked tokens. */
  view: (token: string) => api.get<GuestProjectView>(`/api/guest/${encodeURIComponent(token)}`),
};
