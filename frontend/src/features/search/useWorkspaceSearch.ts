import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { MAX_QUERY_LENGTH, searchWorkspace } from './searchApi';

export const SEARCH_DELAY_MS = 200;

/** Debounced, cancellable search pages owned by the shared query cache. */
export function useWorkspaceSearch(rawQuery: string) {
  const query = rawQuery.trim().slice(0, MAX_QUERY_LENGTH);
  const [settledQuery, setSettledQuery] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setSettledQuery(query), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [query]);

  const result = useInfiniteQuery({
    // Change the observed key immediately: consuming signal cancels the old
    // request on typing, rather than waiting for the next debounce to expire.
    queryKey: ['workspace-search', query],
    queryFn: ({ pageParam, signal }) =>
      searchWorkspace(query, {
        signal,
        ...(pageParam ? { cursor: pageParam } : {}),
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled: Boolean(query) && query === settledQuery,
    staleTime: 0,
    gcTime: 0,
  });
  return {
    query,
    items: result.data?.pages.flatMap((page) => page.items) ?? [],
    nextCursor: result.data?.pages.at(-1)?.nextCursor ?? null,
    loading: Boolean(query) && (query !== settledQuery || result.isFetching),
    error: result.error?.message ?? '',
    loadMore: () => {
      if (result.hasNextPage && !result.isFetching)
        void result.fetchNextPage({ cancelRefetch: false });
    },
  };
}
