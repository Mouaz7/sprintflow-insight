import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { RetrospectiveEntry } from '../../api/contracts';

export const retrospectiveQuery = (projectId: number, sprintId: string) =>
  queryOptions({
    queryKey: ['project', projectId, 'retrospective', sprintId] as const,
    queryFn: () =>
      api<RetrospectiveEntry[]>(
        `/projects/${projectId}/retrospective?sprintId=${sprintId}`,
      ),
  });
