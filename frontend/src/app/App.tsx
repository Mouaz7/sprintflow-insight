import { useEffect, useState } from 'react';
import { api, ApiError, clearCsrfToken } from '../api/client';
import type { Project, Session, User } from '../api/contracts';
import { AuthScreen } from './AuthScreen';
import { Workspace } from './Workspace';
import { PublicReportView } from './SharedReports';

export function App() {
  const token = new URLSearchParams(window.location.search).get('report');
  return token ? <PublicReportView token={token} /> : <SessionApp />;
}

function SessionApp() {
  const [session, setSession] = useState<Session>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let cancelled = false;
    Promise.all([api<User>('/auth/me'), api<Project[]>('/projects')])
      .then(([user, projects]) => {
        if (!cancelled) setSession({ user, projects });
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401))
          setError(
            'Could not load the workspace. Please check the connection and try again.',
          );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  async function signedIn(user: User) {
    const projects = await api<Project[]>('/projects');
    setSession({ user, projects });
  }
  async function refreshProjects() {
    const projects = await api<Project[]>('/projects');
    setSession((current) => current && { ...current, projects });
  }
  function signedOut() {
    clearCsrfToken();
    setSession(undefined);
    setError('');
  }
  if (loading)
    return (
      <main className="app-loading" role="status">
        Loading SprintFlow…
      </main>
    );
  if (!session || new URLSearchParams(window.location.search).has('resetToken'))
    return <AuthScreen onSignedIn={signedIn} initialError={error} />;
  return (
    <Workspace
      session={session}
      onProjectsChanged={refreshProjects}
      onUserChanged={(user) =>
        setSession((current) => current && { ...current, user })
      }
      onAccountDeleted={signedOut}
      onLogout={async () => {
        await api('/auth/logout', { method: 'POST' });
        signedOut();
      }}
    />
  );
}
