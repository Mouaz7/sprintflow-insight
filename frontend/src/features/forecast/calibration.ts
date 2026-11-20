import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';

type Basis = 'THROUGHPUT' | 'VELOCITY';
export type ForecastModelName =
  'gamma-poisson' | 'ewma-poisson' | 'bootstrap-monte-carlo';

export type CalibrationReport = {
  basis: Basis;
  unit: 'ITEMS' | 'STORY_POINTS';
  periodDays: number;
  availablePeriods: number;
  requiredPeriods: number;
  trainingPeriods: number;
  horizonPeriods: number;
  seed: string;
  status: 'SELECTED' | 'INSUFFICIENT_HISTORY' | 'NO_COMPLETIONS';
  selectedModel: ForecastModelName | null;
  prediction: {
    median: number;
    intervals: { confidence: number; lower: number; upper: number }[];
  } | null;
  candidates: {
    model: ForecastModelName;
    origins: number;
    meanCrps: number;
    coverage: Record<string, number>;
    pitHistogram: number[];
    medianAbsoluteError: number;
  }[];
};

export const calibrationQuery = (projectId: number, basis: Basis) =>
  queryOptions({
    queryKey: ['project', projectId, 'forecast', 'calibration', basis],
    queryFn: ({ signal }) =>
      api<CalibrationReport>(
        `/projects/${projectId}/forecast/backtest?basis=${basis}`,
        { signal },
      ),
  });
