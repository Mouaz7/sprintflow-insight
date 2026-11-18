import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';
import { queryKeys } from '../../api/queries';
import type { ForecastReport } from '../../api/contracts';

export const forecastQuery = (projectId: number, query: string) =>
  queryOptions({
    queryKey: [...queryKeys.forecast(projectId), query],
    queryFn: () =>
      api<ForecastReport>(
        `/projects/${projectId}/forecast${query ? `?${query}` : ''}`,
      ),
  });
