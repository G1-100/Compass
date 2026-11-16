// Accounts and authentication API (Detailed Design 3.5.10). Owner: Gavin.
import { api } from './client';
import type { MeResponse, Page, SystemRole, User } from './types';

export const authApi = {
  register: (body: { email: string; password: string; displayName: string }) => api.post<User>('/api/auth/register', body),
  activate: (body: { email: string; code: string }) => api.post('/api/auth/activate', body),
  resendCode: (body: { email: string }) => api.post('/api/auth/resend-code', body),
  login: (body: { email: string; password: string }) => api.post<MeResponse>('/api/auth/login', body),
  /** credential = ID token from the Google sign-in pop-up. */
  loginWithGoogle: (credential: string) => api.post<MeResponse>('/api/auth/google', { credential }),
  logout: () => api.post('/api/auth/logout'),
  me: () => api.get<MeResponse>('/api/auth/me'),
  requestPasswordReset: (body: { email: string }) => api.post('/api/auth/password-reset/request', body),
  confirmPasswordReset: (body: { email: string; code: string; newPassword: string }) =>
    api.post('/api/auth/password-reset/confirm', body),
};

export const usersApi = {
  getProfile: () => api.get<User>('/api/users/me'),
  updateProfile: (body: { displayName: string; version: number }) => api.patch<User>('/api/users/me', body),
  list: (page = 1) => api.get<Page<User>>('/api/users', { page }),
  changeSystemRole: (userId: number, body: { systemRole: SystemRole; version: number }) =>
    api.patch<User>(`/api/users/${userId}/role`, body),
};
