import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { TrashItem } from '../../api/contracts';

/** The signed-in account's recycle bin across all projects. */
export const trashQuery = queryOptions({
  queryKey: ['trash'],
  queryFn: () => api<TrashItem[]>('/workspace/trash'),
  // Other views move items here, so the bin always reloads when opened.
  staleTime: 0,
});
