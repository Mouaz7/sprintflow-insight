import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { Route, useLocation } from 'react-router';
import type { WorkspaceView } from '../api/contracts';
import { useLanguage } from '../shared/i18n/LanguageProvider';
import { useTheme } from '../shared/theme/ThemeProvider';
import { RouteLoading } from '../shared/ui/RouteLoading';
import { useWorkspace } from './Workspace';

// Each view is its own chunk, loaded when its route is first visited.
const DataView = lazy(() =>
  import('../features/data/DataView').then((m) => ({ default: m.DataView })),
);
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
const CustomFieldsView = lazy(() =>
  import('../features/custom-fields/CustomFieldsView').then((m) => ({
    default: m.CustomFieldsView,
  })),
);
const AutomationView = lazy(
  () => import('../features/automation/AutomationView'),
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
const RoadmapView = lazy(() =>
  import('../features/roadmap/RoadmapView').then((m) => ({
    default: m.RoadmapView,
  })),
);
const PokerView = lazy(() =>
  import('../features/poker/PokerView').then((m) => ({ default: m.PokerView })),
);
const DashboardView = lazy(() =>
  import('../features/dashboard/DashboardView').then((m) => ({
    default: m.DashboardView,
  })),
);
const TimeView = lazy(() =>
  import('../features/time/TimeView').then((m) => ({ default: m.TimeView })),
);
const FlowView = lazy(() =>
  import('../features/flow/FlowView').then((m) => ({ default: m.FlowView })),
);
const ForecastView = lazy(() =>
  import('../features/forecast/ForecastView').then((m) => ({
    default: m.ForecastView,
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
  const { activeProject, selectView, requestTask } = useWorkspace();
  return (
    <OverviewView
      project={activeProject}
      onNavigate={selectView}
      onNewTask={requestTask}
    />
  );
}

function DataRoute() {
  const { activeProject } = useWorkspace();
  return <DataView project={activeProject} />;
}
function RoadmapRoute() {
  const { activeProject } = useWorkspace();
  return <RoadmapView project={activeProject} />;
}

function PokerRoute() {
  const { activeProject } = useWorkspace();
  return <PokerView project={activeProject} />;
}

function DashboardRoute() {
  const { activeProject, session } = useWorkspace();
  return <DashboardView project={activeProject} user={session.user} />;
}
function TimeRoute() {
  const { activeProject, session } = useWorkspace();
  return <TimeView project={activeProject} user={session.user} />;
}

function FlowRoute() {
  const { activeProject } = useWorkspace();
  return <FlowView project={activeProject} />;
}

function ForecastRoute() {
  const { activeProject } = useWorkspace();
  return <ForecastView project={activeProject} />;
}

function ProjectsRoute() {
  const { session, activeProject, refreshProjects, setActiveProjectId } =
    useWorkspace();
  return (
    <ProjectsView
      projects={session.projects}
      project={activeProject}
      user={session.user}
      onChanged={refreshProjects}
      onSelect={setActiveProjectId}
    />
  );
}

function CustomFieldsRoute() {
  const { activeProject } = useWorkspace();
  return <CustomFieldsView project={activeProject} />;
}

function AutomationRoute() {
  const { activeProject } = useWorkspace();
  return <AutomationView project={activeProject} />;
}

function TasksRoute() {
  const location = useLocation();
  const taskKey = new URLSearchParams(location.search).get('task') ?? '';
  const {
    session,
    activeProject,
    activeProjectId,
    taskRequest,
    clearTaskRequest,
  } = useWorkspace();
  return (
    <TasksView
      key={activeProjectId}
      focusTaskKey={taskKey}
      focusRequestKey={location.key}
      hasProjects={session.projects.length > 0}
      projectId={activeProjectId}
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
  const { session, activeProjectId } = useWorkspace();
  return (
    <ProjectInsightView
      key={activeProjectId}
      view={view}
      projectId={activeProjectId}
      hasProjects={session.projects.length > 0}
    />
  );
}

function CalendarRoute() {
  const { activeProjectId } = useWorkspace();
  return <CalendarView key={activeProjectId} projectId={activeProjectId} />;
}

function RetrospectiveRoute() {
  const { activeProjectId, session } = useWorkspace();
  return (
    <RetrospectiveView
      key={activeProjectId}
      projectId={activeProjectId}
      userEmail={session.user.email}
    />
  );
}

function TrashRoute() {
  const { session, refreshProjects } = useWorkspace();
  return (
    <TrashView
      projects={session.projects}
      userEmail={session.user.email}
      onChanged={refreshProjects}
    />
  );
}

function ProfileRoute() {
  const { session, changeUser, logout, openGuide, signedOut } = useWorkspace();
  const { setLanguage } = useLanguage();
  const { setTheme } = useTheme();
  return (
    <ProfileView
      user={session.user}
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
  fields: <CustomFieldsRoute />,
  automation: <AutomationRoute />,
  data: <DataRoute />,
  backlog: <InsightRoute view="backlog" />,
  sprints: <InsightRoute view="sprints" />,
  roadmap: <RoadmapRoute />,
  tasks: <TasksRoute />,
  metrics: <InsightRoute view="metrics" />,
  poker: <PokerRoute />,
  time: <TimeRoute />,
  dashboard: <DashboardRoute />,
  flow: <FlowRoute />,
  forecast: <ForecastRoute />,
  updates: <InsightRoute view="updates" />,
  profile: <ProfileRoute />,
  calendar: <CalendarRoute />,
  retrospective: <RetrospectiveRoute />,
  trash: <TrashRoute />,
};

export const workspaceRoutes = Object.entries(routes).map(([path, element]) => (
  <Route key={path} path={path} element={<Page>{element}</Page>} />
));
