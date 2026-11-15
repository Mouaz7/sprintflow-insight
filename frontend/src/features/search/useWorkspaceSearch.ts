import { useEffect, useRef, useState } from 'react';
import { MAX_QUERY_LENGTH, searchWorkspace, type SearchHit } from './searchApi';

export const SEARCH_DELAY_MS = 200;

type State = {
  query: string;
  items: SearchHit[];
  nextCursor: string | null;
  loading: boolean;
  error: string;
};

const idle: State = {
  query: '',
  items: [],
  nextCursor: null,
  loading: false,
  error: '',
};

/**
 * Debounced search for the palette. A newer query aborts the previous request,
 * and an answer is only applied while its query is still current.
 */
export function useWorkspaceSearch(rawQuery: string) {
  const query = rawQuery.trim().slice(0, MAX_QUERY_LENGTH);
  const [state, setState] = useState<State>(idle);
  const controller = useRef<AbortController | null>(null);

  useEffect(() => {
    controller.current?.abort();
    if (!query) {
      setState(idle);
      return;
    }
    setState((previous) => ({ ...previous, query, loading: true, error: '' }));
    const abort = new AbortController();
    controller.current = abort;
    const timer = setTimeout(() => {
      searchWorkspace(query, { signal: abort.signal })
        .then((page) => {
          if (abort.signal.aborted) return;
          setState({
            query,
            items: page.items,
            nextCursor: page.nextCursor,
            loading: false,
            error: '',
          });
        })
        .catch((cause: Error) => {
          if (abort.signal.aborted) return;
          setState({ ...idle, query, error: cause.message });
        });
    }, SEARCH_DELAY_MS);
    return () => {
      clearTimeout(timer);
      abort.abort();
    };
  }, [query]);

  function loadMore() {
    if (!state.nextCursor || state.loading) return;
    const abort = new AbortController();
    controller.current = abort;
    const current = state.query;
    setState((previous) => ({ ...previous, loading: true }));
    searchWorkspace(current, { cursor: state.nextCursor, signal: abort.signal })
      .then((page) => {
        if (abort.signal.aborted) return;
        setState((previous) =>
          previous.query !== current
            ? previous
            : {
                ...previous,
                items: [...previous.items, ...page.items],
                nextCursor: page.nextCursor,
                loading: false,
              },
        );
      })
      .catch((cause: Error) => {
        if (abort.signal.aborted) return;
        setState((previous) => ({
          ...previous,
          loading: false,
          error: cause.message,
        }));
      });
  }

  const current = state.query === query;
  return {
    query,
    items: current ? state.items : [],
    nextCursor: current ? state.nextCursor : null,
    loading: !!query && (state.loading || !current),
    error: current ? state.error : '',
    loadMore,
  };
}
