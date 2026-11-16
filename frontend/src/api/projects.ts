// Projects and dashboard API (Detailed Design 3.5.3). Owner: Solomon.
import { api } from './client';
import type { DashboardView, LocalDate, Project, ProjectStatus } from './types';

export interface ProjectInput {
  name: string;
  description?: string;
  targetDate?: LocalDate;
  status?: ProjectStatus;
}

export const projectsApi = {
  search: (q: string) => api.get<Project[]>('/api/projects', { q }),
  listForTeam: (teamId: number, includeArchived = false) =>
    api.get<Project[]>(`/api/teams/${teamId}/projects`, { includeArchived }),
  create: (teamId: number, body: ProjectInput) => api.post<Project>(`/api/teams/${teamId}/projects`, body),
  get: (projectId: number) => api.get<Project>(`/api/projects/${projectId}`),
  update: (projectId: number, body: Partial<ProjectInput> & { version: number }) =>
    api.patch<Project>(`/api/projects/${projectId}`, body),
  archive: (projectId: number, version: number) => api.post<Project>(`/api/projects/${projectId}/archive`, { version }),
  dashboard: (projectId: number) => api.get<DashboardView>(`/api/projects/${projectId}/dashboard`),
};
