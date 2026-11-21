import { lazy } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { projectScope } from '../../api/queries';
import { collaborationApi, type CollaborationScope } from './contracts';

export const SharedTextDrawer = lazy(() => import('./SharedTextDrawer'));

export function useSharedTextActive(scope: CollaborationScope | undefined) {
  const query = useQuery({
    queryKey: [
      ...projectScope(scope?.projectId ?? 0),
      'shared-text',
      scope?.target,
      scope?.id,
    ],
    queryFn: scope
      ? () => api<{ active: boolean }>(`${collaborationApi(scope)}/metadata`)
      : skipToken,
    retry: false,
  });
  return query.data?.active ?? false;
}
