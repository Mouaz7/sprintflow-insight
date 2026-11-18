import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { afterProjectWrite, queryKeys } from '../../api/queries';
import type {
  PokerCommand,
  PokerSession,
  PokerSummary,
  StartPoker,
} from './contracts';

const base = (projectId: number) => `/projects/${projectId}/poker-sessions`;
export const pokerKey = (projectId: number) =>
  ['project', projectId, 'poker'] as const;
export const pokerListQuery = (projectId: number, closed: boolean) =>
  queryOptions({
    queryKey: [...pokerKey(projectId), 'list', closed],
    queryFn: ({ signal }) =>
      api<PokerSummary[]>(`${base(projectId)}?closed=${closed}`, { signal }),
    refetchInterval: 10_000,
  });
export const pokerSessionQuery = (projectId: number, sessionId: number) =>
  queryOptions({
    queryKey: [...pokerKey(projectId), 'session', sessionId],
    queryFn: ({ signal }) =>
      api<PokerSession>(`${base(projectId)}/${sessionId}`, { signal }),
    enabled: sessionId > 0,
    refetchInterval: (query) =>
      ['VOTING', 'REVEALED'].includes(query.state.data?.status ?? '')
        ? 5_000
        : false,
  });
export function useStartPoker(projectId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (draft: StartPoker) =>
      api<PokerSession>(base(projectId), {
        method: 'POST',
        body: JSON.stringify(draft),
      }),
    onSuccess: async (saved) => {
      client.setQueryData(
        pokerSessionQuery(projectId, saved.id).queryKey,
        saved,
      );
      await client.invalidateQueries({ queryKey: pokerKey(projectId) });
    },
  });
}
export function usePokerCommand(projectId: number, sessionId: number) {
  const client = useQueryClient();
  return useMutation({
    onMutate: () =>
      client.cancelQueries({
        queryKey: pokerSessionQuery(projectId, sessionId).queryKey,
      }),
    mutationFn: ({ action, ...body }: PokerCommand) => {
      const suffix =
        action === 'cancel'
          ? ''
          : action === 'retract'
            ? '/vote'
            : `/${action}`;
      const method =
        action === 'vote'
          ? 'PUT'
          : ['retract', 'cancel'].includes(action)
            ? 'DELETE'
            : 'POST';
      return api<PokerSession>(`${base(projectId)}/${sessionId}${suffix}`, {
        method,
        body: JSON.stringify(body),
      });
    },
    onSuccess: async (saved, command) => {
      client.setQueryData(
        pokerSessionQuery(projectId, sessionId).queryKey,
        saved,
      );
      await client.invalidateQueries({ queryKey: pokerKey(projectId) });
      if (command.action === 'accept')
        await afterProjectWrite(
          client,
          projectId,
          queryKeys.stories(projectId),
        );
    },
    onError: async () => {
      await client.invalidateQueries({ queryKey: pokerKey(projectId) });
    },
  });
}
