// Workload and alerts API (Detailed Design 3.5.4). Owner: Liv.
import { api } from './client';
import type { Alert, AlertStatus, Availability, WorkloadResult } from './types';

export const workloadApi = {
  reportAvailability: (projectId: number, hoursPerWeek: number, reportsOverload = false) =>
    api.put<Availability>(`/api/projects/${projectId}/availability/me`, { hoursPerWeek, reportsOverload }),
  get: (projectId: number) => api.get<WorkloadResult>(`/api/projects/${projectId}/workload`),
  alerts: (projectId: number, status?: AlertStatus) => api.get<Alert[]>(`/api/projects/${projectId}/alerts`, { status }),
  updateAlert: (projectId: number, alertId: number, status: 'ACKNOWLEDGED' | 'RESOLVED') =>
    api.patch<Alert>(`/api/projects/${projectId}/alerts/${alertId}`, { status }),
};
