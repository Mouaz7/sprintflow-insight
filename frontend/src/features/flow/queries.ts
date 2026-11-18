import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import type { FlowReport, WipSettings } from '../../api/contracts';
import { afterProjectWrite, queryKeys } from '../../api/queries';

export const flowQuery = (projectId: number, days: number) =>
  queryOptions({
    queryKey: [...queryKeys.flow(projectId), days],
    queryFn: () => api<FlowReport>(`/projects/${projectId}/flow?days=${days}`),
  });

export function useSaveWipLimits(projectId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (draft: WipSettings) =>
      api<WipSettings>(`/projects/${projectId}/wip-limits`, {
        method: 'PUT',
        headers: { 'If-Match': `"${draft.version}"` },
        body: JSON.stringify({
          limits: draft.limits.filter((row) => row.limit !== null),
        }),
      }),
    onSuccess: async (saved) => {
      client.setQueryData(queryKeys.wipLimits(projectId), saved);
      await afterProjectWrite(client, projectId, queryKeys.flow(projectId));
    },
  });
}
