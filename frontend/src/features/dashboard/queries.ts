import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import type { DashboardLayout } from './contracts';
export const dashboardKey = (projectId: number) =>
  ['project', projectId, 'dashboard'] as const;
export const dashboardQuery = (projectId: number) =>
  queryOptions({
    queryKey: dashboardKey(projectId),
    queryFn: ({ signal }) =>
      api<DashboardLayout>(`/projects/${projectId}/dashboard`, { signal }),
  });
export function useSaveDashboard(projectId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (draft: DashboardLayout) =>
      api<DashboardLayout>(`/projects/${projectId}/dashboard`, {
        method: 'PUT',
        headers: { 'If-Match': `"${draft.version}"` },
        body: JSON.stringify({ widgets: draft.widgets }),
      }),
    onSuccess: (saved) => {
      client.setQueryData(dashboardKey(projectId), saved);
    },
    onError: async () => {
      await client.invalidateQueries({ queryKey: dashboardKey(projectId) });
    },
  });
}
