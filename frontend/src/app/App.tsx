import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense, useState } from 'react';
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useParams,
} from 'react-router';
import { createQueryClient } from '../api/queries';
import { LanguageProvider } from '../shared/i18n/LanguageProvider';
import { ThemeProvider } from '../shared/theme/ThemeProvider';
import { RouteLoading } from '../shared/ui/RouteLoading';
import { SessionGate } from './SessionGate';
import { WorkspaceLayout } from './Workspace';
import { workspaceRoutes } from './WorkspaceRoutes';

const PublicReportView = lazy(() =>
  import('../features/insights/SharedReports').then((module) => ({
    default: module.PublicReportView,
  })),
);

export function App() {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <LanguageProvider>
        <ThemeProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ThemeProvider>
      </LanguageProvider>
    </QueryClientProvider>
  );
}

function AppRoutes() {
  const legacy = legacyRedirect(useLocation().search);
  if (legacy) return <Navigate to={legacy} replace />;
  return (
    <Routes>
      <Route path="/report/:token" element={<PublicReportRoute />} />
      <Route element={<SessionGate />}>
        {/* The gate renders the password form for this path. */}
        <Route path="/reset-password" element={null} />
        {workspaceTree}
      </Route>
    </Routes>
  );
}

/** The signed-in workspace: one route per view inside the shared shell. */
export const workspaceTree = (
  <Route element={<WorkspaceLayout />}>
    {workspaceRoutes}
    <Route path="*" element={<Navigate to="/overview" replace />} />
  </Route>
);

/** Links sent before A6 used query parameters on the root path. */
export function legacyRedirect(search: string): string | undefined {
  const params = new URLSearchParams(search);
  const report = params.get('report');
  if (report) return `/report/${encodeURIComponent(report)}`;
  const reset = params.get('resetToken');
  if (reset) return `/reset-password?token=${encodeURIComponent(reset)}`;
  return undefined;
}

function PublicReportRoute() {
  const { token = '' } = useParams();
  return (
    <Suspense fallback={<RouteLoading />}>
      <PublicReportView token={token} />
    </Suspense>
  );
}
