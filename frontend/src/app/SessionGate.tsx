import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Outlet, useMatch, useOutletContext } from 'react-router';
import { api, ApiError, clearCsrfToken } from '../api/client';
import type { Session, User } from '../api/contracts';
import { meQuery, projectsQuery } from '../api/queries';
import { AuthScreen } from '../features/auth/AuthScreen';
import { useT } from '../shared/i18n/LanguageProvider';
import { appText } from './i18n';

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
  const me = useQuery(meQuery);
  const projects = useQuery({ ...projectsQuery, enabled: me.data !== null });
  const user = me.data;

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
    await refreshProjects();
    queryClient.setQueryData(meQuery.queryKey, next);
  }
  function signedOut() {
    clearCsrfToken();
    queryClient.setQueryData(meQuery.queryKey, null);
    forgetAccountData();
  }

  if (me.isPending || (user && projects.isPending))
    return (
      <main className="app-loading" role="status">
        {t('loadingApp')}
      </main>
    );
  const failed =
    me.isError ||
    (user &&
      projects.isError &&
      !(projects.error instanceof ApiError && projects.error.status === 401));
  if (!user || !projects.data || resetPassword)
    return (
      <AuthScreen
        onSignedIn={signedIn}
        initialError={failed ? t('workspaceFailed') : ''}
      />
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
