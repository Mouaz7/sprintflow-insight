import {
  QueryClient,
  queryOptions,
  type QueryKey,
} from '@tanstack/react-query';
import { api, ApiError } from './client';
import type {
  Preferences,
  DependencyOverview,
  Project,
  ProjectMember,
  Sprint,
  Story,
  Task,
  User,
  WipSettings,
  Epic,
} from './contracts';

/**
 * Behaviour matches the former effect-based loading: no automatic retries or
 * focus refetches, and a view refetches when it mounts. The short stale time
 * only merges the duplicate requests of components mounting together; every
 * write calls `afterProjectWrite` (or invalidates its own keys).
 */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        staleTime: 5_000,
      },
      mutations: { retry: false },
    },
  });
}

export const queryKeys = {
  me: ['auth', 'me'] as const,
  preferences: ['preferences'] as const,
  projects: ['projects'] as const,
  project: (id: number) => ['project', id] as const,
  role: (id: number) => ['project', id, 'role'] as const,
  members: (id: number) => ['project', id, 'members'] as const,
  sprints: (id: number) => ['project', id, 'sprints'] as const,
  stories: (id: number) => ['project', id, 'stories'] as const,
  tasks: (id: number) => ['project', id, 'tasks'] as const,
  dependencies: (id: number) => ['project', id, 'dependencies'] as const,
  roadmap: (id: number) => ['project', id, 'roadmap'] as const,
  estimates: (id: number) => ['project', id, 'estimates'] as const,
  wipLimits: (id: number) => ['project', id, 'wip-limits'] as const,
  flow: (id: number) => ['project', id, 'flow'] as const,
  forecast: (id: number) => ['project', id, 'forecast'] as const,
  epics: (id: number) => ['project', id, 'epics'] as const,
  customFields: (id: number) => ['project', id, 'custom-fields'] as const,
  customMatches: (id: number) =>
    ['project', id, 'custom-field-matches'] as const,
  taskCustomFields: (projectId: number, taskId: number) =>
    ['project', projectId, 'task-custom-fields', taskId] as const,
};

/** The signed-in user, or `null` when the server reports no session. */
export async function fetchSessionUser() {
  try {
    return await api<User>('/auth/me');
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401) return null;
    throw cause;
  }
}

export const meQuery = queryOptions({
  queryKey: queryKeys.me,
  queryFn: fetchSessionUser,
});

export const projectsQuery = queryOptions({
  queryKey: queryKeys.projects,
  queryFn: () => api<Project[]>('/projects'),
});

export const preferencesQuery = queryOptions({
  queryKey: queryKeys.preferences,
  queryFn: () => api<Preferences>('/auth/preferences'),
});

export const roleQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.role(projectId),
    queryFn: () => api<{ role: string }>(`/projects/${projectId}/role`),
  });

export const membersQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.members(projectId),
    queryFn: () => api<ProjectMember[]>(`/projects/${projectId}/members`),
  });

export const sprintsQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.sprints(projectId),
    queryFn: () => api<Sprint[]>(`/sprints?projectId=${projectId}`),
  });

export const storiesQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.stories(projectId),
    queryFn: () => api<Story[]>(`/stories/project/${projectId}`),
  });

export const dependenciesQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.dependencies(projectId),
    queryFn: () =>
      api<DependencyOverview>(`/projects/${projectId}/dependencies`),
  });

export const wipLimitsQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.wipLimits(projectId),
    queryFn: () => api<WipSettings>(`/projects/${projectId}/wip-limits`),
  });

export const epicsQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.epics(projectId),
    queryFn: () => api<Epic[]>(`/projects/${projectId}/epics`),
  });

export const tasksQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.tasks(projectId),
    queryFn: () => api<Task[]>(`/tasks?projectId=${projectId}`),
  });

/** Every query for one project, so a write can refresh all views of it. */
export const projectScope = (projectId: number) => ['project', projectId];

/**
 * After a write in a project: refetch the listed queries now and mark every
 * other query of that project stale, so other views reload when they open.
 */
export async function afterProjectWrite(
  client: QueryClient,
  projectId: number,
  ...refresh: QueryKey[]
) {
  await Promise.all(
    refresh.map((queryKey) => client.refetchQueries({ queryKey })),
  );
  await client.invalidateQueries({
    queryKey: projectScope(projectId),
    refetchType: 'none',
  });
}
