import { infiniteQueryOptions, queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { AuditPage, ImportBatch } from './contracts';
export const transferKey = (id: number) => ['project', id, 'transfer'] as const;
export const batchesQuery = (id: number) =>
  queryOptions({
    queryKey: [...transferKey(id), 'batches'],
    queryFn: ({ signal }) =>
      api<ImportBatch[]>(`/projects/${id}/imports`, { signal }),
  });
export const auditQuery = (id: number) =>
  infiniteQueryOptions({
    queryKey: [...transferKey(id), 'audit'],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam, signal }) =>
      api<AuditPage>(
        `/projects/${id}/audit?size=50${pageParam === null ? '' : `&before=${pageParam}`}`,
        { signal },
      ),
    getNextPageParam: (page) => page.nextBefore ?? undefined,
  });
