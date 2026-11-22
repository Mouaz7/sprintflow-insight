import {
  dehydrate,
  hydrate,
  type QueryClient,
  type QueryKey,
} from '@tanstack/react-query';
import { ApiError, clearCsrfToken } from '../../api/client';
import type { Project, Task, User } from '../../api/contracts';
import { fetchSessionUser, queryKeys } from '../../api/queries';
import {
  clearOfflineAccount,
  offlineAccount,
  updateOfflineAccount,
  verifyOfflineAccount,
} from './storage';

const projectQueries = new Set([
  'role',
  'tasks',
  'sprints',
  'stories',
  'members',
  'dependencies',
  'wip-limits',
  'time',
]);
const maximumQueries = 200;

function eligible(key: QueryKey): boolean {
  const [prefix, id, kind] = key;
  return (
    prefix === 'projects' ||
    prefix === 'preferences' ||
    (prefix === 'project' &&
      typeof id === 'number' &&
      (kind === undefined ||
        (typeof kind === 'string' && projectQueries.has(kind)))) ||
    (prefix === 'task' && typeof id === 'number' && kind === 'comments')
  );
}

export async function loadSessionWithOfflineFallback(client: QueryClient) {
  try {
    const user = await fetchSessionUser();
    const previous = client.getQueryData<User>(queryKeys.me);
    if (previous && previous.id !== user?.id) {
      clearCsrfToken();
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== 'auth',
      });
    }
    if (user) await verifyOfflineAccount(user).catch(() => undefined);
    else await clearOfflineAccount().catch(() => undefined);
    return user;
  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 0) throw cause;
    const local = await offlineAccount().catch(() => undefined);
    if (!local) throw cause;
    hydrate(client, local.cache);
    return local.user;
  }
}

export function watchOfflineQueries(client: QueryClient, accountId: number) {
  let queued = false;
  let closed = false;
  const save = () => {
    const projects = client.getQueryData<Project[]>(queryKeys.projects);
    if (!projects) return;
    const projectIds = new Set(projects.map(({ id }) => id));
    const snapshot = dehydrate(client, {
      shouldDehydrateMutation: () => false,
      shouldDehydrateQuery: (query) =>
        query.state.status === 'success' && eligible(query.queryKey),
    });
    void updateOfflineAccount(accountId, (current) => {
      const queries = new Map(
        current.cache.queries.map((query) => [query.queryHash, query]),
      );
      for (const query of snapshot.queries) queries.set(query.queryHash, query);
      const allowedTasks = new Set<number>();
      for (const { queryKey, state } of queries.values())
        if (
          queryKey[0] === 'project' &&
          queryKey[2] === 'tasks' &&
          projectIds.has(Number(queryKey[1]))
        )
          for (const task of state.data as Task[]) allowedTasks.add(task.id);
      const keep = [...queries.values()].filter(({ queryKey }) => {
        if (queryKey[0] === 'project')
          return projectIds.has(Number(queryKey[1]));
        if (queryKey[0] === 'task')
          return allowedTasks.has(Number(queryKey[1]));
        return true;
      });
      keep.sort((a, b) => b.state.dataUpdatedAt - a.state.dataUpdatedAt);
      return {
        ...current,
        cache: { mutations: [], queries: keep.slice(0, maximumQueries) },
      };
    }).catch(() => undefined);
  };
  const unsubscribe = client.getQueryCache().subscribe(({ type, query }) => {
    if (queued || type !== 'updated' || !eligible(query.queryKey)) return;
    queued = true;
    queueMicrotask(() => {
      queued = false;
      if (!closed) save();
    });
  });
  const onHidden = () => {
    if (document.visibilityState === 'hidden') save();
  };
  document.addEventListener('visibilitychange', onHidden);
  save();
  return () => {
    document.removeEventListener('visibilitychange', onHidden);
    unsubscribe();
    closed = true;
  };
}
