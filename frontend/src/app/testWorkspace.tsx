import { Outlet, Route, Routes } from 'react-router';
import { vi } from 'vitest';
import type { Language, Session } from '../api/contracts';
import { renderWithProviders } from '../test/render';
import { workspaceTree } from './App';
import type { SessionContext } from './SessionGate';

const defaultSession: Session = {
  user: { id: 1, email: 'qa@example.com' },
  projects: [],
};

/** Renders the workspace routes for a signed-in session without loading it. */
export function renderWorkspace({
  session = defaultSession,
  path = '/',
  language,
  overrides = {},
}: {
  session?: Session;
  path?: string;
  language?: Language;
  overrides?: Partial<SessionContext>;
} = {}) {
  const context: SessionContext = {
    session,
    refreshProjects: vi.fn().mockResolvedValue(undefined),
    changeUser: vi.fn(),
    signedOut: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  return renderWithProviders(
    <Routes>
      <Route element={<Outlet context={context} />}>{workspaceTree}</Route>
    </Routes>,
    { path, language },
  );
}
