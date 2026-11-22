import { useQuery, useQueryClient } from '@tanstack/react-query';
import { lazy, Suspense, useEffect } from 'react';
import { Outlet, useMatch, useOutletContext } from 'react-router';
import { api, ApiError, clearCsrfToken } from '../api/client';
import type { Session, User } from '../api/contracts';
import { meQuery, projectsQuery } from '../api/queries';
import { useT } from '../shared/i18n/LanguageProvider';
import {
  loadSessionWithOfflineFallback,
  watchOfflineQueries,
} from '../shared/offline/sessionCache';
import {
  clearOfflineAccount,
  verifyOfflineAccount,
} from '../shared/offline/storage';
import { appText } from './i18n';

// Signed-in visits never download the sign-in page.
const AuthScreen = lazy(() =>
  import('../features/auth/AuthScreen').then((module) => ({
    default: module.AuthScreen,
  })),
);

export type SessionContext = {
  session: Session;
  refreshProjects: () => Promise<void>;
  changeUser: (user: User) => void;
  signedOut: () => void;
  logout: () => Promise<void>;
};

export function useSession() {
  return useOutletContext<SessionContext>();
}

/** Loads the account and its projects; signed-out visitors get the sign-in page. */
export function SessionGate() {
  const t = useT(appText);
  const queryClient = useQueryClient();
  const resetPassword = useMatch('/reset-password');
  const me = useQuery({
    ...meQuery,
    queryFn: () => loadSessionWithOfflineFallback(queryClient),
    networkMode: 'always',
    refetchOnReconnect: 'always',
  });
  const projects = useQuery({ ...projectsQuery, enabled: Boolean(me.data) });
  const user = me.data;
  const accountId = user?.id;
  useEffect(
    () =>
      accountId === undefined
        ? undefined
        : watchOfflineQueries(queryClient, accountId),
    [accountId, queryClient],
  );

  async function refreshProjects() {
    await queryClient.fetchQuery({ ...projectsQuery, staleTime: 0 });
  }
  // Cached data belongs to one account; the session query itself stays observed.
  function forgetAccountData() {
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== meQuery.queryKey[0],
    });
  }
  async function signedIn(next: User) {
    forgetAccountData();
    await verifyOfflineAccount(next).catch(() => undefined);
    await refreshProjects();
    queryClient.setQueryData(meQuery.queryKey, next);
  }
  function signedOut() {
    clearCsrfToken();
    void clearOfflineAccount(accountId).catch(() => undefined);
    queryClient.setQueryData(meQuery.queryKey, null);
    forgetAccountData();
  }

  const loading = (
    <main className="app-loading" role="status">
      {t('loadingApp')}
    </main>
  );
  if (me.isPending || (user && projects.isPending)) return loading;
  const failed =
    me.isError ||
    (user &&
      projects.isError &&
      !(projects.error instanceof ApiError && projects.error.status === 401));
  if (!user || !projects.data || resetPassword)
    return (
      <Suspense fallback={loading}>
        <AuthScreen
          onSignedIn={signedIn}
          initialError={failed ? t('workspaceFailed') : ''}
        />
      </Suspense>
    );
  const context: SessionContext = {
    session: { user, projects: projects.data },
    refreshProjects,
    changeUser: (next) => queryClient.setQueryData(meQuery.queryKey, next),
    signedOut,
    logout: async () => {
      await api('/auth/logout', { method: 'POST' });
      signedOut();
    },
  };
  return <Outlet context={context} />;
}
