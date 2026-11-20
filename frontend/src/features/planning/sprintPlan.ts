import { queryOptions } from '@tanstack/react-query';
import { api } from '../../api/client';

export type CapacityUnit = 'POINTS' | 'HOURS';
export type StoryLock = 'in' | 'out';
export type PlanOptions = {
  sprintId: number;
  unit: CapacityUnit;
  configuredCapacity: number;
  reservedEffort: number;
  unestimatedCommitments: number;
  members: {
    userId: number;
    email: string;
    configuredCapacity: number;
    reservedEffort: number;
  }[];
  stories: {
    id: number;
    title: string;
    value: number;
    effort: number;
    assignedEffort: Record<string, number>;
    eligibility: 'READY' | 'MISSING_ESTIMATE' | 'HAS_SCHEDULED_TASK';
  }[];
};
type Excluded = {
  storyId: string;
  reason:
    | 'LOCKED_OUT'
    | 'MISSING_DEPENDENCY'
    | 'CAPACITY'
    | 'LOWER_VALUE_DENSITY'
    | 'NO_ADDED_VALUE'
    | 'NOT_REACHED'
    | 'PLAN_INFEASIBLE';
  relatedStoryIds: string[];
  resources: string[];
};
export type Proposal = {
  options: PlanOptions;
  plan: {
    status: 'OPTIMAL' | 'FEASIBLE' | 'INFEASIBLE' | 'UNKNOWN';
    selectedStoryIds: string[];
    objective: number;
    bestBound: number | null;
    relativeGap: number | null;
    explanation: {
      resources: {
        resource: string;
        used: number;
        available: number;
        binding: boolean;
      }[];
      excluded: Excluded[];
      conflicts: {
        kind: 'LOCKED_IN_BLOCKED' | 'LOCKED_IN_OVER_CAPACITY';
        storyIds: string[];
        relatedStoryIds: string[];
        resource: string | null;
      }[];
    };
  };
};
export const planOptionsQuery = (
  projectId: number,
  sprintId: number,
  unit: CapacityUnit,
) =>
  queryOptions({
    queryKey: ['project', projectId, 'sprint-plan', sprintId, unit],
    queryFn: ({ signal }) =>
      api<PlanOptions>(
        `/projects/${projectId}/sprint-plan/options?sprintId=${sprintId}&unit=${unit}`,
        { signal },
      ),
  });
