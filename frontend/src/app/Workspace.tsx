import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Link,
  Navigate,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
  useOutletContext,
} from 'react-router';
import { api } from '../api/client';
import type {
  Language,
  Project,
  Session,
  User,
  WorkspaceView,
} from '../api/contracts';
import { preferencesQuery, roleQuery } from '../api/queries';
import { PresenceAvatars } from '../features/realtime/PresenceAvatars';
import { useProjectRealtime } from '../features/realtime/useProjectRealtime';
import {
  CommandCenter,
  type SearchTarget,
} from '../features/search/CommandCenter';
import { Onboarding } from '../features/onboarding/Onboarding';
import { useLanguage, useT } from '../shared/i18n/LanguageProvider';
import { DraftProjectsContext } from '../shared/lib/useDraftProject';
import { useTheme } from '../shared/theme/ThemeProvider';
import { InterfaceIcon } from '../shared/ui/InterfaceIcons';
import { LanguageSelector } from '../shared/ui/LanguageSelector';
import {
  ConfirmProvider,
  EditorProjectContext,
  Notice,
  useModalFocus,
} from '../shared/ui/ui';
import { appText } from './i18n';
import { NotificationBell } from './NotificationBell';
import { useSession } from './SessionGate';

export const workspaceViews: readonly WorkspaceView[] = [
  'overview',
  'projects',
  'backlog',
  'sprints',
  'tasks',
  'metrics',
  'updates',
  'profile',
  'calendar',
  'retrospective',
  'trash',
];
const projectStorage = 'sprintflow-active-project';
const viewStorage = (userId: number) => `sprintflow-view-v1:${userId}`;
const projectFreeViews: readonly WorkspaceView[] = [
  'projects',
  'profile',
  'trash',
];

export function rememberedView(userId: number): WorkspaceView {
  try {
    const saved = sessionStorage.getItem(viewStorage(userId));
    return workspaceViews.find((view) => view === saved) ?? 'overview';
  } catch {
    return 'overview';
  }
}

function rememberView(userId: number, view: WorkspaceView) {
  try {
    sessionStorage.setItem(viewStorage(userId), view);
  } catch {
    // Navigation remains usable when browser storage is unavailable.
  }
}

function storedProject(projects: Project[]): number | undefined {
  let saved: string | null = null;
  try {
    saved = localStorage.getItem(projectStorage);
  } catch {
    // Fall back to the first project.
  }
  return (
    projects.find((project) => project.id === Number(saved))?.id ??
    projects[0]?.id
  );
}

/** Shared state for the workspace pages, provided to every view route. */
export type WorkspaceContext = {
  session: Session;
  activeProject: Project | undefined;
  activeProjectId: number | undefined;
  setActiveProjectId: (id: number) => void;
  selectView: (view: WorkspaceView) => void;
  taskRequest: number;
  requestTask: () => void;
  clearTaskRequest: () => void;
  openGuide: () => void;
  refreshProjects: () => Promise<void>;
  changeUser: (user: User) => void;
  signedOut: () => void;
  logout: () => Promise<void>;
};

export function useWorkspace() {
  return useOutletContext<WorkspaceContext>();
}

export function WorkspaceLayout() {
  const { pathname } = useLocation();
  const { session } = useSession();
  // `/` opens the view this account last used in this browser tab.
  if (pathname === '/')
    return <Navigate to={`/${rememberedView(session.user.id)}`} replace />;
  return (
    <ConfirmProvider>
      <WorkspaceShell />
    </ConfirmProvider>
  );
}

function WorkspaceShell() {
  const { session, refreshProjects, changeUser, signedOut, logout } =
    useSession();
  const { language, setLanguage } = useLanguage();
  const { theme, setTheme } = useTheme();
  const t = useT(appText);
  const navigate = useNavigate();
  const location = useLocation();
  const view = workspaceViews.find(
    (candidate) => location.pathname === `/${candidate}`,
  );
  const [guideOpen, setGuideOpen] = useState(false);
  const [guideDismissed, setGuideDismissed] = useState(false);
  const [preferenceError, setPreferenceError] = useState('');
  const [selectedProjectId, setSelectedProjectId] = useState<
    number | undefined
  >(() => storedProject(session.projects));
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const focusAfterMenu = useRef<'navigation' | 'toggle' | null>(null);
  useEffect(() => {
    const target = focusAfterMenu.current;
    if (menuOpen || !target) return;
    focusAfterMenu.current = null;
    // Runs after the dialog's cleanup has removed `inert` from the page.
    if (target === 'toggle') menuButton.current?.focus();
    else
      document
        .querySelector<HTMLElement>('.header-navigation [aria-current="page"]')
        ?.focus();
  }, [menuOpen]);
  const [taskRequest, setTaskRequest] = useState(0);
  const darkMode = theme === 'dark';
  const accountName = session.user.displayName || session.user.email;
  const accountInitial = accountName.slice(0, 1).toUpperCase();
  // A removed or inaccessible project falls back to the first available one.
  const activeProject =
    session.projects.find((project) => project.id === selectedProjectId) ??
    session.projects[0];
  const activeProjectId = activeProject?.id;
  const realtime = useProjectRealtime(activeProjectId);
  const preferences = useQuery(preferencesQuery);
  const projectRole = useQuery({
    ...roleQuery(activeProjectId ?? 0),
    enabled: !!activeProjectId,
  });
  const canCreateTask =
    !activeProject?.archivedAt &&
    ['Owner', 'Admin', 'Member'].includes(projectRole.data?.role ?? '');
  const guideAvailable =
    !guideDismissed && preferences.data?.onboardingCompleted === false;

  useEffect(() => {
    try {
      if (activeProjectId === undefined)
        localStorage.removeItem(projectStorage);
      else localStorage.setItem(projectStorage, String(activeProjectId));
    } catch {
      // The selection still works for this page.
    }
  }, [activeProjectId]);
  useEffect(() => {
    if (view) rememberView(session.user.id, view);
  }, [session.user.id, view]);
  // Server preferences win over this browser's remembered appearance.
  const synced = useRef<unknown>(undefined);
  useEffect(() => {
    const value = preferences.data;
    if (!value || synced.current === value) return;
    synced.current = value;
    if (!['EN', 'SV'].includes(value.language) || value.version == null) return;
    setLanguage(value.language);
    setTheme(value.theme === 'dark' ? 'dark' : 'light');
  }, [preferences.data, setLanguage, setTheme]);

  function savePreference(body: Record<string, string>) {
    void api('/auth/preferences', {
      method: 'PUT',
      body: JSON.stringify(body),
    }).catch((cause: Error) => setPreferenceError(cause.message));
  }
  function changeLanguage(value: Language) {
    setLanguage(value);
    savePreference({ language: value });
  }
  function changeTheme() {
    const value = darkMode ? 'light' : 'dark';
    setTheme(value);
    savePreference({ theme: value });
  }
  function setActiveProjectId(id: number) {
    setSelectedProjectId(id);
    if (view === 'tasks' && location.search)
      void navigate('/tasks', { replace: true });
  }
  function selectView(next: WorkspaceView) {
    void navigate(`/${next}`);
    setMenuOpen(false);
  }
  function navigateSearch(target: SearchTarget) {
    if (target.projectId !== undefined) setSelectedProjectId(target.projectId);
    const params = new URLSearchParams();
    if (target.taskKey) params.set('task', target.taskKey);
    void navigate(`/${target.view}${params.size ? `?${params}` : ''}`);
    setMenuOpen(false);
  }
  const context: WorkspaceContext = {
    session,
    activeProject,
    activeProjectId,
    setActiveProjectId,
    selectView,
    taskRequest,
    requestTask: () => {
      setTaskRequest((n) => n + 1);
      selectView('tasks');
    },
    clearTaskRequest: () => setTaskRequest(0),
    openGuide: () => setGuideOpen(true),
    refreshProjects,
    changeUser,
    signedOut,
    logout,
  };
  const draftProjects = {
    projects: session.projects,
    onSelect: setActiveProjectId,
  };
  const nav = (mobile = false) => (
    <nav aria-label={t(mobile ? 'mobileNavigation' : 'mainNavigation')}>
      {workspaceViews.map((key) => (
        <NavLink
          key={key}
          to={`/${key}`}
          className={({ isActive }) => (isActive ? 'active' : '')}
          onClick={() => setMenuOpen(false)}
        >
          <InterfaceIcon name={key} />
          <span>{t(key)}</span>
        </NavLink>
      ))}
    </nav>
  );
  return (
    <div className="workspace-shell" data-theme={theme}>
      <a className="skip-link" href="#workspace-page">
        {t('skipToContent')}
      </a>
      <header className="workspace-header">
        <div className="header-main">
          <Link
            className="brand brand-home"
            to="/overview"
            aria-label="SprintFlow"
          >
            <span className="brand-mark">
              <InterfaceIcon name="tasks" />
            </span>
            <strong>SprintFlow</strong>
          </Link>
          <div className="header-controls">
            <label className="project-control">
              <span>{t('project')}</span>
              <select
                value={activeProjectId ?? ''}
                disabled={!session.projects.length}
                onChange={(e) => setActiveProjectId(Number(e.target.value))}
              >
                <option value="" disabled>
                  {t('noProjects')}
                </option>
                {session.projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <CommandCenter
              canCreateTask={canCreateTask}
              onNavigate={navigateSearch}
              onNewTask={context.requestTask}
            />
            <LanguageSelector language={language} onChange={changeLanguage} />
            <PresenceAvatars
              viewers={realtime.viewers}
              selfId={session.user.id}
              status={realtime.status}
              language={language}
            />
            <button
              className="theme-toggle"
              type="button"
              aria-label={t(darkMode ? 'switchToLight' : 'switchToDark')}
              onClick={changeTheme}
            >
              <InterfaceIcon name={darkMode ? 'sun' : 'moon'} />
            </button>
            <NotificationBell
              onProject={(id) => {
                setActiveProjectId(id);
                selectView('tasks');
              }}
            />
            <Link
              className="account-profile"
              to="/profile"
              aria-label={`${session.user.avatarImage ? '' : `${accountInitial} `}${accountName} · ${t('profile')}`}
            >
              <span className="avatar">
                {session.user.avatarImage ? (
                  <img src={session.user.avatarImage} alt="" />
                ) : (
                  accountInitial
                )}
              </span>{' '}
              <span className="account-name">{accountName}</span>
            </Link>
            <button
              ref={menuButton}
              className="menu-toggle icon-button"
              type="button"
              aria-label={t('openMenu')}
              aria-expanded={menuOpen}
              aria-controls="mobile-menu"
              onClick={() => setMenuOpen(true)}
            >
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                aria-hidden="true"
              >
                <path d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          </div>
        </div>
        <div className="header-navigation">
          {nav()}
          <span className="workspace-caption">{t('caption')}</span>
        </div>
      </header>
      {menuOpen && (
        <MobileMenu
          onClose={() => {
            // Focus moves after the menu has unmounted and restored the page.
            focusAfterMenu.current = window.matchMedia('(min-width: 1024px)')
              .matches
              ? 'navigation'
              : 'toggle';
            setMenuOpen(false);
          }}
        >
          {nav(true)}
        </MobileMenu>
      )}
      <DraftProjectsContext value={draftProjects}>
        <EditorProjectContext
          value={
            view && projectFreeViews.includes(view) ? undefined : activeProject
          }
        >
          <main className="workspace-content" id="workspace-page" tabIndex={-1}>
            <Notice error={preferenceError} />
            {guideAvailable &&
              view === 'overview' &&
              session.projects.length === 0 && (
                <section className="onboarding-banner">
                  <p>{t('onboardingPrompt')}</p>
                  <button onClick={() => setGuideOpen(true)}>
                    {t('showIntroduction')}
                  </button>
                </section>
              )}
            <Outlet context={context} />
          </main>
        </EditorProjectContext>
      </DraftProjectsContext>
      {guideOpen && (
        <Onboarding
          onClose={() => {
            setGuideOpen(false);
            setGuideDismissed(true);
          }}
          onNavigate={selectView}
        />
      )}
      <footer className="workspace-footer">
        <span>SprintFlow</span>
        <span>{t('slogan')}</span>
      </footer>
    </div>
  );
}

function MobileMenu({
  children,
  onClose,
}: {
  children: ReactNode;
  onClose: () => void;
}) {
  const t = useT(appText);
  const ref = useRef<HTMLDivElement>(null);
  useModalFocus(onClose, ref);
  useEffect(() => {
    const resize = () => {
      if (window.innerWidth >= 1024) onClose();
    };
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, [onClose]);
  return (
    <div
      role="presentation"
      className="mobile-menu-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        id="mobile-menu"
        className="mobile-menu"
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label={t('navigation')}
      >
        <header>
          <strong>SprintFlow</strong>
          <button
            type="button"
            className="icon-button"
            aria-label={t('closeMenu')}
            onClick={onClose}
          >
            ✕
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}
