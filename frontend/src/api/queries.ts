import { QueryClient, queryOptions } from '@tanstack/react-query';
import { api, ApiError } from './client';
import type {
  Preferences,
  Project,
  ProjectMember,
  Sprint,
  Story,
  Task,
  User,
} from './contracts';

/**
 * Behaviour matches the former effect-based loading: no automatic retries or
 * focus refetches; views refresh explicitly after their own writes.
 */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        staleTime: 30_000,
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
};

/** The signed-in user, or `null` when the server reports no session. */
export const meQuery = queryOptions({
  queryKey: queryKeys.me,
  queryFn: async () => {
    try {
      return await api<User>('/auth/me');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) return null;
      throw cause;
    }
  },
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

export const tasksQuery = (projectId: number) =>
  queryOptions({
    queryKey: queryKeys.tasks(projectId),
    queryFn: () => api<Task[]>(`/tasks?projectId=${projectId}`),
  });

/** Every query for one project, so a write can refresh all views of it. */
export const projectScope = (projectId: number) => ['project', projectId];
