import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { projectScope } from '../../api/queries';

export type Draft = { name: string; source: string; enabled: boolean };
export type Trigger = 'TASK_MOVED' | 'PULL_REQUEST_MERGED' | 'OVERDUE';
export type Rule = Draft & { id: number; trigger: Trigger; version: number };
export type Run = {
  id: number;
  ruleId: number;
  ruleName: string;
  taskId: number;
  taskKey: string | null;
  trigger: Trigger;
  outcome: 'APPLIED' | 'SKIPPED' | 'FAILED';
  detail: string;
  executedAt: string;
};
export type GithubSettings = { endpointId: string; repository: string };
export type IssuedGithub = { settings: GithubSettings; secret: string };

const base = (projectId: number) => `/projects/${projectId}/automation`;
const scope = (projectId: number) => [...projectScope(projectId), 'automation'];

export const rulesQuery = (projectId: number) =>
  queryOptions({
    queryKey: [...scope(projectId), 'rules'],
    queryFn: () => api<Rule[]>(`${base(projectId)}/rules`),
  });
export const runsQuery = (projectId: number) =>
  queryOptions({
    queryKey: [...scope(projectId), 'runs'],
    queryFn: () => api<Run[]>(`${base(projectId)}/runs`),
  });
export const githubQuery = (projectId: number) =>
  queryOptions({
    queryKey: [...scope(projectId), 'github'],
    queryFn: async () =>
      (await api<GithubSettings>(`${base(projectId)}/github`)) ?? null,
  });

export function useAutomationActions(projectId: number) {
  const client = useQueryClient();
  const changed = () =>
    client.invalidateQueries({ queryKey: scope(projectId) });
  return {
    save: useMutation({
      mutationFn: ({
        draft,
        rule,
      }: {
        draft: Draft;
        rule?: Rule | undefined;
      }) =>
        api<Rule>(`${base(projectId)}/rules${rule ? `/${rule.id}` : ''}`, {
          method: rule ? 'PUT' : 'POST',
          headers: rule ? { 'If-Match': `"${rule.version}"` } : {},
          body: JSON.stringify(draft),
        }),
      onSuccess: changed,
    }),
    remove: useMutation({
      mutationFn: (rule: Rule) =>
        api<unknown>(`${base(projectId)}/rules/${rule.id}`, {
          method: 'DELETE',
          headers: { 'If-Match': `"${rule.version}"` },
        }),
      onSuccess: changed,
    }),
    github: useMutation({
      mutationFn: (repository: string) =>
        api<IssuedGithub>(`${base(projectId)}/github`, {
          method: 'PUT',
          body: JSON.stringify({ repository }),
        }),
      onSuccess: changed,
      gcTime: 0,
    }),
    disconnect: useMutation({
      mutationFn: () =>
        api<unknown>(`${base(projectId)}/github`, { method: 'DELETE' }),
      onSuccess: changed,
    }),
  };
}
