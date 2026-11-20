import {
  infiniteQueryOptions,
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { afterProjectWrite, queryKeys } from '../../api/queries';
import type {
  Capacity,
  TimeEntry,
  TimeEntryPage,
  TimeReport,
  TimeWrite,
} from './contracts';
export const timeKey = (projectId: number) =>
  ['project', projectId, 'time'] as const;
export const timeBase = (projectId: number) => `/projects/${projectId}`;
export const reportQuery = (projectId: number, sprintId: number) =>
  queryOptions({
    queryKey: [...timeKey(projectId), 'report', sprintId],
    queryFn: ({ signal }) =>
      api<TimeReport>(
        `${timeBase(projectId)}/time-report${sprintId ? `?sprintId=${sprintId}` : ''}`,
        { signal },
      ),
  });
export const entriesQuery = (projectId: number, sprintId: number) =>
  infiniteQueryOptions({
    queryKey: [...timeKey(projectId), 'entries', sprintId],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({ size: '50' });
      if (sprintId) params.set('sprintId', String(sprintId));
      if (pageParam !== null) params.set('before', String(pageParam));
      return api<TimeEntryPage>(
        `${timeBase(projectId)}/time-entries?${params}`,
        { signal },
      );
    },
    getNextPageParam: (page) => page.nextBefore ?? undefined,
  });
export const timerQuery = (projectId: number) =>
  queryOptions({
    queryKey: [...timeKey(projectId), 'timer'],
    queryFn: async ({ signal }) =>
      (await api<TimeEntry | undefined>(`${timeBase(projectId)}/timer`, {
        signal,
      })) ?? null,
    refetchInterval: 15_000,
  });
export const capacityQuery = (projectId: number, sprintId: number) =>
  queryOptions({
    queryKey: [...timeKey(projectId), 'capacity', sprintId],
    queryFn: ({ signal }) =>
      api<Capacity>(`${timeBase(projectId)}/sprints/${sprintId}/capacity`, {
        signal,
      }),
    enabled: sprintId > 0,
  });
export function useTimeWrite(projectId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ path, method, body, version }: TimeWrite) =>
      api<unknown>(`${timeBase(projectId)}${path}`, {
        method,
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        ...(version == null ? {} : { headers: { 'If-Match': `"${version}"` } }),
      }),
    onSuccess: async () =>
      afterProjectWrite(
        client,
        projectId,
        timeKey(projectId),
        queryKeys.tasks(projectId),
        ['project', projectId, 'activity'],
      ),
    onError: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: timeKey(projectId) }),
        client.invalidateQueries({ queryKey: queryKeys.tasks(projectId) }),
      ]);
    },
  });
}
