import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { Route, useLocation } from 'react-router';
import type { WorkspaceView } from '../api/contracts';
import { useLanguage } from '../shared/i18n/LanguageProvider';
import { useTheme } from '../shared/theme/ThemeProvider';
import { RouteLoading } from '../shared/ui/RouteLoading';
import { useWorkspace } from './Workspace';

// Each view is its own chunk, loaded when its route is first visited.
const OverviewView = lazy(() =>
  import('../features/insights/OverviewView').then((m) => ({
    default: m.OverviewView,
  })),
);
const ProjectsView = lazy(() =>
  import('../features/projects/ProjectsView').then((m) => ({
    default: m.ProjectsView,
  })),
);
const TrashView = lazy(() =>
  import('../features/projects/TrashView').then((m) => ({
    default: m.TrashView,
  })),
);
const TasksView = lazy(() =>
  import('../features/tasks/TasksView').then((m) => ({
    default: m.TasksView,
  })),
);
const CalendarView = lazy(() =>
  import('../features/planning/CalendarView').then((m) => ({
    default: m.CalendarView,
  })),
);
const RetrospectiveView = lazy(() =>
  import('../features/planning/RetrospectiveView').then((m) => ({
    default: m.RetrospectiveView,
  })),
);
const ProfileView = lazy(() =>
  import('../features/profile/ProfileView').then((m) => ({
    default: m.ProfileView,
  })),
);
const ProjectInsightView = lazy(() =>
  import('./ProjectInsights').then((m) => ({
    default: m.ProjectInsightView,
  })),
);

/** Moves focus to the page heading after client-side navigation (not on load). */
function FocusHeading() {
  const { pathname, key } = useLocation();
  useEffect(() => {
    if (key === 'default') return;
    const heading =
      document.querySelector<HTMLElement>('#workspace-page h1') ??
      document.querySelector<HTMLElement>('#workspace-page');
    if (!heading) return;
    if (!heading.hasAttribute('tabindex')) heading.tabIndex = -1;
    heading.focus();
  }, [pathname, key]);
  return null;
}

function Page({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<RouteLoading />}>
      {children}
      <FocusHeading />
    </Suspense>
  );
}

function OverviewRoute() {
  const { activeProject, language, selectView, requestTask } = useWorkspace();
  return (
    <OverviewView
      project={activeProject}
      language={language}
      onNavigate={selectView}
      onNewTask={requestTask}
    />
  );
}

function ProjectsRoute() {
  const {
    session,
    activeProject,
    language,
    refreshProjects,
    setActiveProjectId,
  } = useWorkspace();
  return (
    <ProjectsView
      projects={session.projects}
      project={activeProject}
      user={session.user}
      language={language}
      onChanged={refreshProjects}
      onSelect={setActiveProjectId}
    />
  );
}

function TasksRoute() {
  const {
    session,
    activeProject,
    activeProjectId,
    language,
    taskRequest,
    clearTaskRequest,
  } = useWorkspace();
  return (
    <TasksView
      key={activeProjectId}
      hasProjects={session.projects.length > 0}
      projectId={activeProjectId}
      language={language}
      openRequest={taskRequest}
      userEmail={session.user.email}
      readOnly={!!activeProject?.archivedAt}
      onRequestHandled={clearTaskRequest}
    />
  );
}

function InsightRoute({
  view,
}: {
  view: 'backlog' | 'sprints' | 'metrics' | 'updates';
}) {
  const { session, activeProjectId, language } = useWorkspace();
  return (
    <ProjectInsightView
      key={activeProjectId}
      view={view}
      projectId={activeProjectId}
      hasProjects={session.projects.length > 0}
      language={language}
    />
  );
}

function CalendarRoute() {
  const { activeProjectId, language } = useWorkspace();
  return (
    <CalendarView
      key={activeProjectId}
      projectId={activeProjectId}
      language={language}
    />
  );
}

function RetrospectiveRoute() {
  const { activeProjectId, language, session } = useWorkspace();
  return (
    <RetrospectiveView
      key={activeProjectId}
      projectId={activeProjectId}
      language={language}
      userEmail={session.user.email}
    />
  );
}

function TrashRoute() {
  const { session, language, refreshProjects } = useWorkspace();
  return (
    <TrashView
      language={language}
      projects={session.projects}
      userEmail={session.user.email}
      onChanged={refreshProjects}
    />
  );
}

function ProfileRoute() {
  const { session, language, changeUser, logout, openGuide, signedOut } =
    useWorkspace();
  const { setLanguage } = useLanguage();
  const { setTheme } = useTheme();
  return (
    <ProfileView
      user={session.user}
      language={language}
      onSaved={changeUser}
      onLogout={logout}
      onAppearance={(nextLanguage, theme) => {
        setLanguage(nextLanguage);
        setTheme(theme === 'dark' ? 'dark' : 'light');
      }}
      onReplay={openGuide}
      onAccountDeleted={signedOut}
    />
  );
}

const routes: Record<WorkspaceView, ReactNode> = {
  overview: <OverviewRoute />,
  projects: <ProjectsRoute />,
  backlog: <InsightRoute view="backlog" />,
  sprints: <InsightRoute view="sprints" />,
  tasks: <TasksRoute />,
  metrics: <InsightRoute view="metrics" />,
  updates: <InsightRoute view="updates" />,
  profile: <ProfileRoute />,
  calendar: <CalendarRoute />,
  retrospective: <RetrospectiveRoute />,
  trash: <TrashRoute />,
};

export const workspaceRoutes = Object.entries(routes).map(([path, element]) => (
  <Route key={path} path={path} element={<Page>{element}</Page>} />
));
