import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api, ApiError } from '../../api/client';
import type {
  Epic,
  RoadmapSnapshot,
  Story,
  TaskEstimate,
} from '../../api/contracts';
import { afterProjectWrite, queryKeys } from '../../api/queries';
import type { RoadmapText } from './i18n';

export type EpicDraft = Pick<
  Epic,
  'title' | 'description' | 'color' | 'startDate' | 'targetDate'
>;
export type EstimateDraft = Pick<
  TaskEstimate,
  'optimistic' | 'mostLikely' | 'pessimistic'
>;
export type RoadmapAction =
  | { kind: 'epic'; draft: EpicDraft; epic?: Epic | undefined }
  | { kind: 'deleteEpic'; epic: Epic }
  | { kind: 'story'; story: Story; epicId: number | null }
  | { kind: 'dependency'; blockerTaskId: number; blockedTaskId: number }
  | { kind: 'removeDependency'; id: number }
  | {
      kind: 'estimate';
      taskId: number;
      draft: EstimateDraft;
      version?: number | undefined;
    }
  | { kind: 'clearEstimate'; estimate: TaskEstimate };

export const roadmapQuery = (id: number) =>
  queryOptions({
    queryKey: queryKeys.roadmap(id),
    queryFn: () => api<RoadmapSnapshot>(`/projects/${id}/roadmap`),
  });
export const estimatesQuery = (id: number) =>
  queryOptions({
    queryKey: queryKeys.estimates(id),
    queryFn: () => api<TaskEstimate[]>(`/projects/${id}/estimates`),
  });
const revision = (version: number | undefined): Record<string, string> =>
  version === undefined ? {} : { 'If-Match': `"${version}"` };

export function useRoadmapActions(id: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (action: RoadmapAction) => {
      const base = `/projects/${id}`;
      switch (action.kind) {
        case 'epic':
          await api(`${base}/epics${action.epic ? `/${action.epic.id}` : ''}`, {
            method: action.epic ? 'PUT' : 'POST',
            headers: revision(action.epic?.version),
            body: JSON.stringify(action.draft),
          });
          break;
        case 'deleteEpic':
          await api(`${base}/epics/${action.epic.id}`, {
            method: 'DELETE',
            headers: revision(action.epic.version),
          });
          break;
        case 'story':
          await api(`${base}/stories/${action.story.id}/epic`, {
            method: 'PUT',
            headers: revision(action.story.version),
            body: JSON.stringify({ epicId: action.epicId }),
          });
          break;
        case 'dependency':
          await api(`${base}/dependencies`, {
            method: 'POST',
            body: JSON.stringify({
              blockerTaskId: action.blockerTaskId,
              blockedTaskId: action.blockedTaskId,
            }),
          });
          break;
        case 'removeDependency':
          await api(`${base}/dependencies/${action.id}`, { method: 'DELETE' });
          break;
        case 'estimate':
          await api(`${base}/tasks/${action.taskId}/estimate`, {
            method: 'PUT',
            headers: revision(action.version),
            body: JSON.stringify(action.draft),
          });
          break;
        case 'clearEstimate':
          await api(`${base}/tasks/${action.estimate.taskId}/estimate`, {
            method: 'DELETE',
            headers: revision(action.estimate.version),
          });
          break;
      }
      await afterProjectWrite(
        client,
        id,
        queryKeys.roadmap(id),
        queryKeys.dependencies(id),
        queryKeys.estimates(id),
        queryKeys.stories(id),
      );
    },
  });
}

export function problemText(cause: unknown, t: RoadmapText): string {
  if (!cause) return '';
  if (cause instanceof ApiError) {
    const cycle = cause.problem.cycleKeys;
    if (Array.isArray(cycle) && cycle.every((key) => typeof key === 'string'))
      return t('cycle', { path: cycle.join(' → ') });
    if (cause.status === 412 || cause.status === 428) return t('stale');
    if (cause.status === 401 || cause.status === 403) return t('denied');
    if (cause.status === 404) return t('notFound');
    if (cause.status === 400) return t('invalid');
    if (cause.status === 409) return t('conflict');
  }
  return t('failed');
}
