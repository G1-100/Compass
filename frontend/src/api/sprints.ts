// Sprints and milestones API (Detailed Design 3.5.6-3.5.7). Owner: Siya.
import { api } from './client';
import type { LocalDate, MilestoneView, Sprint, SprintCompletionResult } from './types';

export const sprintsApi = {
  list: (projectId: number) => api.get<Sprint[]>(`/api/projects/${projectId}/sprints`),
  create: (projectId: number, body: { name: string; startDate: LocalDate; endDate: LocalDate }) =>
    api.post<Sprint>(`/api/projects/${projectId}/sprints`, body),
  update: (sprintId: number, body: { name?: string; startDate?: LocalDate; endDate?: LocalDate; version: number }) =>
    api.patch<Sprint>(`/api/sprints/${sprintId}`, body),
  start: (sprintId: number, version: number) => api.post<Sprint>(`/api/sprints/${sprintId}/start`, { version }),
  /** targetSprintId null = backlog. */
  complete: (sprintId: number, targetSprintId: number | null, version: number) =>
    api.post<SprintCompletionResult>(`/api/sprints/${sprintId}/complete`, { targetSprintId, version }),
  delete: (sprintId: number) => api.delete(`/api/sprints/${sprintId}`),
};

export const milestonesApi = {
  list: (projectId: number) => api.get<MilestoneView[]>(`/api/projects/${projectId}/milestones`),
  create: (projectId: number, body: { name: string; description?: string; targetDate: LocalDate; taskIds?: number[] }) =>
    api.post<MilestoneView>(`/api/projects/${projectId}/milestones`, body),
  get: (milestoneId: number) => api.get<MilestoneView>(`/api/milestones/${milestoneId}`),
  update: (milestoneId: number, body: { name?: string; description?: string; targetDate?: LocalDate; version: number }) =>
    api.patch<MilestoneView>(`/api/milestones/${milestoneId}`, body),
  replaceTasks: (milestoneId: number, taskIds: number[], version: number) =>
    api.put<MilestoneView>(`/api/milestones/${milestoneId}/tasks`, { taskIds, version }),
  delete: (milestoneId: number) => api.delete(`/api/milestones/${milestoneId}`),
};
