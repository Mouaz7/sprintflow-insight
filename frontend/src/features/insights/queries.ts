import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Activity, Metrics } from '../../api/contracts';

export const metricsQuery = (projectId: number, sprintId: string) =>
  queryOptions({
    queryKey: ['project', projectId, 'metrics', sprintId] as const,
    queryFn: () =>
      api<Metrics>(
        `/projects/${projectId}/metrics${sprintId ? `?sprintId=${sprintId}` : ''}`,
      ),
  });

export type SharedReport = { id: number; createdAt: string; expiresAt: string };

export const reportsQuery = (projectId: number) =>
  queryOptions({
    queryKey: ['project', projectId, 'reports'] as const,
    queryFn: () => api<SharedReport[]>(`/projects/${projectId}/reports`),
  });

/** A public report snapshot; no session is needed. */
export const publicReportQuery = (token: string) =>
  queryOptions({
    queryKey: ['shared-report', token] as const,
    queryFn: () =>
      api<Record<string, unknown>>(
        `/shared-reports/${encodeURIComponent(token)}`,
      ),
  });

export const activityQuery = (projectId: number) =>
  queryOptions({
    queryKey: ['project', projectId, 'activity'] as const,
    queryFn: () => api<Activity[]>(`/projects/${projectId}/activity`),
  });
