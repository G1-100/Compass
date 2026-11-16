// Tasks, suggestions, comments and activity API (Detailed Design 3.5.5). Owner: Adam.
import { api } from './client';
import type { ActivityEntry, Comment, EffortLevel, LocalDate, Page, Task, TaskFilter, TaskStatus } from './types';

export interface TaskInput {
  title: string;
  description?: string;
  dueDate?: LocalDate;
  effort: EffortLevel;
  sprintId?: number | null;
}

export const tasksApi = {
  list: (projectId: number, filter: TaskFilter = {}) =>
    api.get<Page<Task>>(`/api/projects/${projectId}/tasks`, { ...filter }),
  listMine: (filter: TaskFilter = {}) => api.get<Page<Task>>('/api/users/me/tasks', { ...filter }),
  create: (projectId: number, body: TaskInput) => api.post<Task>(`/api/projects/${projectId}/tasks`, body),
  suggest: (projectId: number, body: Omit<TaskInput, 'sprintId'>) =>
    api.post<Task>(`/api/projects/${projectId}/tasks/suggestions`, body),
  approve: (taskId: number, body: Partial<TaskInput> & { version: number }) =>
    api.post<Task>(`/api/tasks/${taskId}/approve`, body),
  get: (taskId: number) => api.get<Task>(`/api/tasks/${taskId}`),
  /** To move to the backlog send { moveToBacklog: true }. */
  update: (taskId: number, body: Partial<TaskInput> & { moveToBacklog?: boolean; version: number }) =>
    api.patch<Task>(`/api/tasks/${taskId}`, body),
  updateStatus: (taskId: number, status: TaskStatus, version: number) =>
    api.patch<Task>(`/api/tasks/${taskId}/status`, { status, version }),
  replaceAssignees: (taskId: number, assignees: { userId: number; allocationPercent?: number }[], version: number) =>
    api.put<Task>(`/api/tasks/${taskId}/assignees`, { assignees, version }),
  delete: (taskId: number) => api.delete(`/api/tasks/${taskId}`),
  comments: (taskId: number, page = 1) => api.get<Page<Comment>>(`/api/tasks/${taskId}/comments`, { page }),
  addComment: (taskId: number, commentText: string) => api.post<Comment>(`/api/tasks/${taskId}/comments`, { commentText }),
  activity: (taskId: number, page = 1) => api.get<Page<ActivityEntry>>(`/api/tasks/${taskId}/activity`, { page }),
};
