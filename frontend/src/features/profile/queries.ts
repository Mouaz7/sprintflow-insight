import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';

/** Whether the server has Gmail and Google sign-in configured. */
export const integrationsQuery = queryOptions({
  queryKey: ['integrations'],
  queryFn: () =>
    api<{ gmailReady: boolean; googleReady: boolean }>('/auth/integrations'),
});
