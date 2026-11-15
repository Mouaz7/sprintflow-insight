import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type {
  Language,
  Preferences,
  Session,
  User,
  WorkspaceView,
} from '../api/contracts';
import { OverviewView } from '../features/insights/OverviewView';
import { Onboarding } from '../features/onboarding/Onboarding';
import { CalendarView } from '../features/planning/CalendarView';
import { RetrospectiveView } from '../features/planning/RetrospectiveView';
import { ProfileView } from '../features/profile/ProfileView';
import { ProjectsView } from '../features/projects/ProjectsView';
import { TrashView } from '../features/projects/TrashView';
import {
  CommandCenter,
  type SearchTarget,
} from '../features/search/CommandCenter';
import { TasksView } from '../features/tasks/TasksView';
import { DraftProjectsContext } from '../shared/lib/useDraftProject';
import { InterfaceIcon } from '../shared/ui/InterfaceIcons';
import { LanguageSelector } from '../shared/ui/LanguageSelector';
import {
  ConfirmProvider,
  EditorProjectContext,
  Notice,
  useModalFocus,
} from '../shared/ui/ui';
import { NotificationBell } from './NotificationBell';
import { ProjectInsightView } from './ProjectInsights';

type Props = {
  session: Session;
  onProjectsChanged: () => Promise<void>;
  onLogout: () => Promise<void>;
  onUserChanged: (user: User) => void;
  onAccountDeleted: () => void;
};
const projectStorage = 'sprintflow-active-project';
const workspaceViews: readonly WorkspaceView[] = [
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
const viewStorage = (userId: number) => `sprintflow-view-v1:${userId}`;

function restoreView(userId: number): WorkspaceView {
  try {
    const saved = sessionStorage.getItem(viewStorage(userId));
    return workspaceViews.find((view) => view === saved) ?? 'overview';
  } catch {
    return 'overview';
  }
}

export function Workspace(props: Props) {
  const [language, setLanguage] = useState<Language>(() =>
    localStorage.getItem('sprintflow-language') === 'SV' ? 'SV' : 'EN',
  );
  return (
    <ConfirmProvider language={language}>
      <WorkspaceContent
        {...props}
        language={language}
        setLanguage={setLanguage}
      />
    </ConfirmProvider>
  );
}

function WorkspaceContent({
  session,
  onProjectsChanged,
  onLogout,
  onUserChanged,
  onAccountDeleted,
  language,
  setLanguage,
}: Props & { language: Language; setLanguage: (value: Language) => void }) {
  const [view, setView] = useState<WorkspaceView>(() =>
    restoreView(session.user.id),
  );
  const [guideOpen, setGuideOpen] = useState(false);
  const [guideAvailable, setGuideAvailable] = useState(false);
  const [preferenceError, setPreferenceError] = useState('');
  const [activeProjectId, setActiveProjectId] = useState<number | undefined>(
    () =>
      session.projects.find(
        (p) => p.id === Number(localStorage.getItem(projectStorage)),
      )?.id ?? session.projects[0]?.id,
  );
  const [darkMode, setDarkMode] = useState(
    () => localStorage.getItem('sprintflow-theme') === 'dark',
  );
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const [taskRequest, setTaskRequest] = useState(0);
  const [focusTaskKey, setFocusTaskKey] = useState<string>();
  const sv = language === 'SV';
  const accountName = session.user.displayName || session.user.email;
  const accountInitial = accountName.slice(0, 1).toUpperCase();
  const activeProject = session.projects.find((p) => p.id === activeProjectId);
  const draftProjects = useMemo(
    () => ({ projects: session.projects, onSelect: setActiveProjectId }),
    [session.projects],
  );
  const labels = sv
    ? {
        overview: 'Översikt',
        projects: 'Projekt',
        backlog: 'Backlog',
        sprints: 'Sprintar',
        tasks: 'Uppgifter',
        metrics: 'Mätvärden',
        updates: 'Uppdateringar',
        profile: 'Profil',
        calendar: 'Kalender',
        retrospective: 'Retrospektiv',
        trash: 'Papperskorg',
      }
    : {
        overview: 'Overview',
        projects: 'Projects',
        backlog: 'Backlog',
        sprints: 'Sprints',
        tasks: 'Tasks',
        metrics: 'Metrics',
        updates: 'Updates',
        profile: 'Profile',
        calendar: 'Calendar',
        retrospective: 'Retrospective',
        trash: 'Recycle bin',
      };
  useEffect(() => {
    localStorage.setItem('sprintflow-language', language);
    document.documentElement.lang = sv ? 'sv' : 'en';
  }, [language, sv]);
  useEffect(() => {
    localStorage.setItem('sprintflow-theme', darkMode ? 'dark' : 'light');
    document.documentElement.dataset.theme = darkMode ? 'dark' : 'light';
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', darkMode ? '#151D2A' : '#FAF8F5');
  }, [darkMode]);
  useEffect(() => {
    const id =
      session.projects.find((p) => p.id === activeProjectId)?.id ??
      session.projects[0]?.id;
    if (id === undefined) localStorage.removeItem(projectStorage);
    else localStorage.setItem(projectStorage, String(id));
    if (id !== activeProjectId) setActiveProjectId(id);
  }, [activeProjectId, session.projects]);
  useEffect(() => {
    let live = true;
    api<Preferences>('/auth/preferences')
      .then((value) => {
        if (!live || !value || !['EN', 'SV'].includes(value.language)) return;
        setGuideAvailable(!value.onboardingCompleted);
        if (value.version != null) {
          setLanguage(value.language);
          setDarkMode(value.theme === 'dark');
        }
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [session.user.id, setLanguage]);
  function changeLanguage(value: Language) {
    setLanguage(value);
    void api('/auth/preferences', {
      method: 'PUT',
      body: JSON.stringify({ language: value }),
    }).catch((cause) => setPreferenceError(cause.message));
  }
  function changeTheme() {
    const value = !darkMode;
    setDarkMode(value);
    void api('/auth/preferences', {
      method: 'PUT',
      body: JSON.stringify({ theme: value ? 'dark' : 'light' }),
    }).catch((cause) => setPreferenceError(cause.message));
  }
  function selectView(next: WorkspaceView) {
    setView(next);
    setFocusTaskKey(undefined);
    try {
      sessionStorage.setItem(viewStorage(session.user.id), next);
    } catch {
      // Navigation remains usable when browser storage is unavailable.
    }
    setMenuOpen(false);
  }
  function openSearchTarget(target: SearchTarget) {
    if (target.projectId !== undefined) setActiveProjectId(target.projectId);
    selectView(target.view);
    setFocusTaskKey(target.taskKey);
  }
  function requestNewTask() {
    setTaskRequest((n) => n + 1);
    selectView('tasks');
  }
  const nav = (mobile = false) => (
    <nav
      aria-label={
        sv
          ? mobile
            ? 'Mobilmeny'
            : 'Huvudmeny'
          : mobile
            ? 'Mobile navigation'
            : 'Main navigation'
      }
    >
      {(Object.keys(labels) as WorkspaceView[]).map((key) => (
        <button
          type="button"
          key={key}
          className={view === key ? 'active' : ''}
          aria-current={view === key ? 'page' : undefined}
          onClick={() => selectView(key)}
        >
          <InterfaceIcon name={key} />
          <span>{labels[key]}</span>
        </button>
      ))}
    </nav>
  );
  return (
    <main className="workspace-shell" data-theme={darkMode ? 'dark' : 'light'}>
      <a className="skip-link" href="#workspace-page">
        {sv ? 'Hoppa till innehåll' : 'Skip to content'}
      </a>
      <header className="workspace-header">
        <div className="header-main">
          <button
            className="brand brand-home"
            type="button"
            onClick={() => selectView('overview')}
            aria-label="SprintFlow"
          >
            <span className="brand-mark">
              <InterfaceIcon name="tasks" />
            </span>
            <strong>SprintFlow</strong>
          </button>
          <div className="header-controls">
            <CommandCenter
              language={language}
              onNavigate={openSearchTarget}
              onNewTask={requestNewTask}
            />
            <label className="project-control">
              <span>{sv ? 'Projekt' : 'Project'}</span>
              <select
                aria-label={sv ? 'Projekt' : 'Project'}
                value={activeProjectId ?? ''}
                disabled={!session.projects.length}
                onChange={(e) => setActiveProjectId(Number(e.target.value))}
              >
                <option value="" disabled>
                  {sv ? 'Inga projekt' : 'No projects'}
                </option>
                {session.projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <LanguageSelector language={language} onChange={changeLanguage} />
            <button
              className="theme-toggle"
              type="button"
              aria-label={
                sv
                  ? darkMode
                    ? 'Byt till ljust läge'
                    : 'Byt till mörkt läge'
                  : darkMode
                    ? 'Switch to light mode'
                    : 'Switch to dark mode'
              }
              onClick={changeTheme}
            >
              <InterfaceIcon name={darkMode ? 'sun' : 'moon'} />
            </button>
            <NotificationBell
              language={language}
              onProject={(id) => {
                setActiveProjectId(id);
                selectView('tasks');
              }}
            />
            <button
              className="account-profile"
              type="button"
              aria-label={`${session.user.avatarImage ? '' : `${accountInitial} `}${accountName} · ${labels.profile}`}
              onClick={() => selectView('profile')}
            >
              <span className="avatar">
                {session.user.avatarImage ? (
                  <img src={session.user.avatarImage} alt="" />
                ) : (
                  accountInitial
                )}
              </span>{' '}
              <span className="account-name">{accountName}</span>
            </button>
            <button
              ref={menuButton}
              className="menu-toggle icon-button"
              type="button"
              aria-label={sv ? 'Öppna meny' : 'Open menu'}
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
          <span className="workspace-caption">
            {sv ? 'DIN GEMENSAMMA ARBETSYTA' : 'YOUR SHARED WORKSPACE'}
          </span>
        </div>
      </header>
      {menuOpen && (
        <MobileMenu
          language={language}
          onClose={() => {
            setMenuOpen(false);
            if (window.matchMedia('(min-width: 1024px)').matches) {
              // The modal restores its previous focus during cleanup; focus the
              // visible desktop navigation after that cleanup has completed.
              requestAnimationFrame(() => {
                document
                  .querySelector<HTMLButtonElement>(
                    '.header-navigation [aria-current="page"]',
                  )
                  ?.focus();
              });
            } else {
              menuButton.current?.focus();
            }
          }}
        >
          {nav(true)}
        </MobileMenu>
      )}
      <DraftProjectsContext.Provider value={draftProjects}>
        <EditorProjectContext.Provider
          value={
            ['projects', 'profile', 'trash'].includes(view)
              ? undefined
              : activeProject
          }
        >
          <div className="workspace-content" id="workspace-page" tabIndex={-1}>
            <Notice language={language} error={preferenceError} />
            {guideAvailable &&
              view === 'overview' &&
              session.projects.length === 0 && (
                <section className="onboarding-banner">
                  <p>
                    {sv
                      ? 'Ny här? Vi hjälper dig att komma igång.'
                      : 'New here? Let us help you get started.'}
                  </p>
                  <button onClick={() => setGuideOpen(true)}>
                    {sv ? 'Visa introduktion' : 'Show introduction'}
                  </button>
                </section>
              )}
            {activeProject &&
              !['projects', 'profile', 'trash'].includes(view) && (
                <label className="project-context">
                  <span>{sv ? 'Aktuellt projekt' : 'Current project'}</span>
                  <select
                    aria-label={sv ? 'Aktuellt projekt' : 'Current project'}
                    value={activeProjectId}
                    onChange={(event) =>
                      setActiveProjectId(Number(event.target.value))
                    }
                  >
                    {session.projects.map((project) => (
                      <option key={project.id} value={project.id}>
                        {project.name}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            {view === 'calendar' && (
              <CalendarView
                key={activeProjectId}
                projectId={activeProjectId}
                language={language}
              />
            )}
            {view === 'retrospective' && (
              <RetrospectiveView
                key={activeProjectId}
                projectId={activeProjectId}
                language={language}
                userEmail={session.user.email}
              />
            )}
            {view === 'trash' && (
              <TrashView
                language={language}
                projects={session.projects}
                userEmail={session.user.email}
                onChanged={onProjectsChanged}
              />
            )}
            {view === 'overview' && (
              <OverviewView
                project={activeProject}
                language={language}
                onNavigate={selectView}
                onNewTask={requestNewTask}
              />
            )}
            {view === 'projects' && (
              <ProjectsView
                projects={session.projects}
                project={activeProject}
                user={session.user}
                language={language}
                onChanged={onProjectsChanged}
                onSelect={setActiveProjectId}
              />
            )}
            {view === 'tasks' && (
              <TasksView
                key={`${activeProjectId}:${focusTaskKey ?? ''}`}
                focusTaskKey={focusTaskKey}
                hasProjects={session.projects.length > 0}
                projectId={activeProjectId}
                language={language}
                openRequest={taskRequest}
                userEmail={session.user.email}
                readOnly={!!activeProject?.archivedAt}
                onRequestHandled={() => setTaskRequest(0)}
              />
            )}
            {(view === 'backlog' ||
              view === 'sprints' ||
              view === 'metrics' ||
              view === 'updates') && (
              <ProjectInsightView
                key={activeProjectId}
                view={view}
                projectId={activeProjectId}
                hasProjects={session.projects.length > 0}
                language={language}
              />
            )}
            {view === 'profile' && (
              <ProfileView
                user={session.user}
                language={language}
                onSaved={onUserChanged}
                onLogout={onLogout}
                onAppearance={(nextLanguage, theme) => {
                  setLanguage(nextLanguage);
                  setDarkMode(theme === 'dark');
                }}
                onReplay={() => setGuideOpen(true)}
                onAccountDeleted={onAccountDeleted}
              />
            )}
          </div>
        </EditorProjectContext.Provider>
      </DraftProjectsContext.Provider>
      {guideOpen && (
        <Onboarding
          language={language}
          onClose={() => {
            setGuideOpen(false);
            setGuideAvailable(false);
          }}
          onNavigate={selectView}
        />
      )}
      <footer className="workspace-footer">
        <span>SprintFlow</span>
        <span>
          {sv ? 'En plan. Ett team. Framåt.' : 'One plan. One team. Forward.'}
        </span>
      </footer>
    </main>
  );
}

function MobileMenu({
  children,
  language,
  onClose,
}: {
  children: React.ReactNode;
  language: Language;
  onClose: () => void;
}) {
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
        aria-label="Navigation"
      >
        <header>
          <strong>SprintFlow</strong>
          <button
            type="button"
            className="icon-button"
            aria-label={language === 'SV' ? 'Stäng meny' : 'Close menu'}
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
