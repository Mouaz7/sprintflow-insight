import { api } from '../../api/client';

export type SearchType = 'task' | 'story' | 'sprint' | 'comment' | 'project';

/** Plain text; `match` marks words that matched. Never markup. */
export type SearchSegment = { text: string; match: boolean };

export type SearchHit = {
  type: SearchType;
  id: number;
  projectId: number;
  projectKey: string;
  projectName: string;
  taskId: number | null;
  key: string | null;
  title: string;
  titleSegments: SearchSegment[];
  snippet: SearchSegment[];
  exactMatch: boolean;
};

export type SearchPage = { items: SearchHit[]; nextCursor: string | null };

export const MAX_QUERY_LENGTH = 200;

/** Same shape as the server's TaskKey: SF-123, any case. */
const TASK_KEY = /^\s*([A-Za-z]{2,10})-([1-9][0-9]{0,8})\s*$/;

export function taskKeyOf(query: string): string | undefined {
  const [, project, number] = TASK_KEY.exec(query) ?? [];
  return project && number ? `${project.toUpperCase()}-${number}` : undefined;
}

export function searchWorkspace(
  query: string,
  options: { cursor?: string | undefined; signal?: AbortSignal } = {},
): Promise<SearchPage> {
  const params = new URLSearchParams({ q: query, limit: '20' });
  if (options.cursor) params.set('cursor', options.cursor);
  return api<SearchPage>(
    `/search?${params}`,
    options.signal ? { signal: options.signal } : {},
  );
}

export type TaskByKey = { id: number; projectId: number; key: string };

export function taskByKey(key: string): Promise<TaskByKey> {
  return api<TaskByKey>(`/tasks/by-key/${encodeURIComponent(key)}`);
}
