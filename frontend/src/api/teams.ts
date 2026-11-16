// Teams, membership, invitations and audit log API (Detailed Design 3.5.2). Owner: Solomon.
import { api } from './client';
import type { AuditEvent, Invitation, Membership, Page, Team, TeamRole, TeamWithRole } from './types';

export const teamsApi = {
  create: (name: string) => api.post<TeamWithRole>('/api/teams', { name }),
  listMine: () => api.get<TeamWithRole[]>('/api/teams'),
  get: (teamId: number) => api.get<TeamWithRole>(`/api/teams/${teamId}`),
  rename: (teamId: number, name: string, version: number) => api.patch<Team>(`/api/teams/${teamId}`, { name, version }),
  delete: (teamId: number) => api.delete(`/api/teams/${teamId}`),

  listMembers: (teamId: number) => api.get<Membership[]>(`/api/teams/${teamId}/members`),
  changeRole: (teamId: number, userId: number, role: TeamRole, version: number) =>
    api.patch(`/api/teams/${teamId}/members/${userId}`, { role, version }),
  removeMember: (teamId: number, userId: number) => api.delete(`/api/teams/${teamId}/members/${userId}`),
  leave: (teamId: number) => api.delete(`/api/teams/${teamId}/members/me`),

  invite: (teamId: number, email: string, role: TeamRole) =>
    api.post<Invitation>(`/api/teams/${teamId}/invitations`, { email, role }),
  listInvitations: (teamId: number) => api.get<Invitation[]>(`/api/teams/${teamId}/invitations`),
  revokeInvitation: (teamId: number, invitationId: number) =>
    api.delete(`/api/teams/${teamId}/invitations/${invitationId}`),
  myInvitations: () => api.get<Invitation[]>('/api/users/me/invitations'),
  acceptInvitation: (teamId: number, invitationId: number) =>
    api.post<Membership>(`/api/teams/${teamId}/invitations/${invitationId}/accept`),
  declineInvitation: (teamId: number, invitationId: number) =>
    api.post(`/api/teams/${teamId}/invitations/${invitationId}/decline`),

  auditLog: (teamId: number, page = 1) => api.get<Page<AuditEvent>>(`/api/teams/${teamId}/audit-log`, { page }),
};
