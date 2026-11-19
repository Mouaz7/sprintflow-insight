import type { CustomFieldCondition } from '../../api/contracts';
import { customConditionReady } from '../../api/customFields';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { MAX_QUERY_LENGTH, searchWorkspace } from './searchApi';

export const SEARCH_DELAY_MS = 200;

/** Debounced, cancellable search pages owned by the shared query cache. */
export function useWorkspaceSearch(
  rawQuery: string,
  projectId?: number,
  condition: CustomFieldCondition | null = null,
) {
  const query = rawQuery.trim().slice(0, MAX_QUERY_LENGTH);
  const signature = JSON.stringify([
    query,
    condition ? projectId : null,
    condition,
  ]);
  const [settledQuery, setSettledQuery] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setSettledQuery(signature), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [signature]);

  const result = useInfiniteQuery({
    // Change the observed key immediately: consuming signal cancels the old
    // request on typing, rather than waiting for the next debounce to expire.
    queryKey: ['workspace-search', signature],
    queryFn: ({ pageParam, signal }) =>
      searchWorkspace(query, {
        signal,
        projectId,
        condition,
        ...(pageParam ? { cursor: pageParam } : {}),
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled:
      Boolean(query) &&
      signature === settledQuery &&
      (!condition || (!!projectId && customConditionReady(condition))),
    staleTime: 0,
    gcTime: 0,
  });
  return {
    query,
    items: result.data?.pages.flatMap((page) => page.items) ?? [],
    nextCursor: result.data?.pages.at(-1)?.nextCursor ?? null,
    loading:
      Boolean(query) && (signature !== settledQuery || result.isFetching),
    error: result.error?.message ?? '',
    loadMore: () => {
      if (result.hasNextPage && !result.isFetching)
        void result.fetchNextPage({ cancelRefetch: false });
    },
  };
}
