import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { TaskComment } from '../../api/contracts';

export type Attachment = {
  id: number;
  name: string;
  url: string;
  createdAt: string;
};

type GithubTaskLink = {
  kind: 'PULL_REQUEST' | 'COMMIT';
  repository: string;
  key: string;
  url: string;
  title: string;
  state: 'OPEN' | 'CLOSED' | 'MERGED' | null;
  linkedAt: string;
};

// Task-level data; the components that read it know the task, not its project.
export const taskKeys = {
  attachments: (taskId: number) => ['task', taskId, 'attachments'] as const,
  comments: (taskId: number) => ['task', taskId, 'comments'] as const,
};

export const attachmentsQuery = (taskId: number) =>
  queryOptions({
    queryKey: taskKeys.attachments(taskId),
    queryFn: () => api<Attachment[]>(`/tasks/${taskId}/attachments`),
  });

export const commentsQuery = (taskId: number) =>
  queryOptions({
    queryKey: taskKeys.comments(taskId),
    queryFn: () => api<TaskComment[]>(`/tasks/${taskId}/comments`),
  });

export const githubTaskLinksQuery = (projectId: number, taskId: number) =>
  queryOptions({
    queryKey: ['task', taskId, 'github-links'],
    queryFn: () =>
      api<GithubTaskLink[]>(
        `/projects/${projectId}/github/tasks/${taskId}/links`,
      ),
    refetchInterval: 15_000,
  });
