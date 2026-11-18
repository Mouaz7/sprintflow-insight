import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';

/** The public Google client id; empty when Google sign-in is not configured. */
export const googleConfigQuery = queryOptions({
  queryKey: ['google-config'],
  queryFn: () => api<{ clientId: string }>('/auth/google/config'),
});
