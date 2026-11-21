import {
  queryOptions,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';

export type OrganizationRole = 'Owner' | 'Admin' | 'Member' | 'Viewer';
export type Organization = {
  id: number;
  slug: string;
  name: string;
  role: OrganizationRole;
  personal: boolean;
};
export type Member = {
  userId: number;
  email: string;
  name?: string;
  role: OrganizationRole;
};
export type Team = { id: number; name: string; memberCount: number };
export type Invitation = {
  id: number;
  email: string;
  role: OrganizationRole;
  expiresAt: string;
};
export type IssuedInvitation = { invitation: Invitation; token: string };
export type StarterKind = 'Blank' | 'Scrum' | 'Kanban';
export type Starter = {
  id: number;
  name: string;
  kind: StarterKind;
  description?: string;
};

export const organizationsQuery = queryOptions({
  queryKey: ['organizations'],
  queryFn: () => api<Organization[]>('/organizations'),
});

export const orgPath = (slug: string) =>
  `/organizations/${encodeURIComponent(slug)}`;
export const orgRoute = (slug: string) => `/o/${encodeURIComponent(slug)}`;

export function organizationQuery<T>(slug: string, part: string) {
  return queryOptions({
    queryKey: ['organizations', slug, part],
    queryFn: () => api<T>(`${orgPath(slug)}/${part}`),
  });
}

export function useOrganizationWrite<T = void>(
  base: string,
  onSaved?: (result: T) => void | Promise<void>,
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({
      path = '',
      method = 'POST',
      body,
    }: {
      path?: string;
      method?: 'POST' | 'PUT' | 'DELETE';
      body?: object;
    }) =>
      api<T>(`${base}${path}`, {
        method,
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      }),
    onSuccess: async (value) => {
      await client.invalidateQueries({ queryKey: organizationsQuery.queryKey });
      await onSaved?.(value);
    },
  });
}
