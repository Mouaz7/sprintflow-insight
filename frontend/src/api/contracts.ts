export type User = {
  id: number;
  email: string;
  displayName?: string;
  jobTitle?: string;
  bio?: string;
  avatarImage?: string;
};
export type Project = {
  id: number;
  name: string;
  description?: string;
  /** Unique 2–10 letter key, e.g. SF; prefixes task identifiers. */
  key?: string;
  ownerEmail: string;
  version?: number;
  archivedAt?: string;
  trashedAt?: string;
};
export type TaskStatus = 'To Do' | 'In Progress' | 'Review' | 'Done';
export type Task = {
  id: number;
  title: string;
  description?: string;
  status: TaskStatus;
  projectId: number;
  /** Per-project sequence number allocated by the server. */
  number?: number;
  projectKey?: string;
  /** Human identifier such as SF-123. */
  key?: string;
  storyPoints: number;
  estimateMinutes?: number | null;
  dueAt?: string;
  sprintId?: number;
  storyId?: number;
  assigneeEmail?: string;
  blocked?: boolean;
  blockReason?: string;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
  version?: number;
  labels?: string[];
  checklist?: ChecklistStep[];
};
export type Language = 'EN' | 'SV';
export type WorkspaceView =
  | 'overview'
  | 'projects'
  | 'fields'
  | 'automation'
  | 'data'
  | 'backlog'
  | 'sprints'
  | 'roadmap'
  | 'tasks'
  | 'metrics'
  | 'flow'
  | 'poker'
  | 'time'
  | 'dashboard'
  | 'forecast'
  | 'updates'
  | 'profile'
  | 'calendar'
  | 'retrospective'
  | 'trash';
export type ChecklistStep = {
  id: string;
  label: string;
  completed: boolean;
  required: boolean;
};
export type Preferences = {
  language: Language;
  theme: 'light' | 'dark';
  notifications: boolean;
  onboardingCompleted: boolean;
  savedFilters: string;
  version?: number;
};
export type TaskComment = {
  id: number;
  message: string;
  authorEmail: string;
  createdAt: string;
  version?: number;
};
export type Notification = {
  id: number;
  projectId: number;
  message: string;
  read: boolean;
  createdAt: string;
};
export type TrashItem = {
  version: number;
  kind: 'project' | 'task' | 'story' | 'sprint';
  id: number;
  projectId: number;
  title: string;
  trashedAt: string;
};
export type RetrospectiveEntry = {
  id: number;
  sprintId: number;
  category: 'well' | 'improve' | 'actions';
  message: string;
  authorEmail: string;
  createdAt: string;
  version?: number;
};
export type Session = { user: User; projects: Project[] };
export type ProjectMember = {
  id: number;
  email: string;
  role: 'Owner' | 'Admin' | 'Member' | 'Viewer';
};

export type CustomFieldKind = 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'USER';
export type CustomFieldOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'LESS_THAN'
  | 'AT_MOST'
  | 'GREATER_THAN'
  | 'AT_LEAST'
  | 'IS_SET'
  | 'IS_NOT_SET';
export type CustomFieldDefinition = {
  id: number;
  projectId: number;
  name: string;
  kind: CustomFieldKind;
  options: string[];
  version: number;
};
export type CustomFieldValue = {
  fieldId: number;
  value: string;
  displayValue: string;
};
export type TaskCustomFields = {
  taskId: number;
  version: number;
  values: CustomFieldValue[];
};
export type CustomFieldCondition = {
  fieldId: number;
  operator: CustomFieldOperator;
  value?: string;
};
export type Story = {
  epicId?: number | null;
  backlogPosition?: number;
  id: number;
  title: string;
  description?: string;
  acceptanceCriteria: string;
  storyPoints: number;
  priorityId: number;
  businessValue?: number;
  sprintId?: number;
  projectId: number;
  version?: number;
};
export type Epic = {
  id: number;
  title: string;
  description: string | null;
  color: string;
  startDate: string | null;
  targetDate: string | null;
  version: number;
};
export type RoadmapTask = {
  id: number;
  key: string;
  title: string;
  status: string;
  sprintId: number | null;
};
export type TaskDependency = {
  id: number;
  blocker: RoadmapTask;
  blocked: RoadmapTask;
  open: boolean;
};
export type DependencyOverview = {
  dependencies: TaskDependency[];
  blockedTaskIds: number[];
  externalBlocks: { task: RoadmapTask; blocker: RoadmapTask }[];
};
export type TaskEstimate = {
  taskId: number;
  task: RoadmapTask;
  optimistic: number;
  mostLikely: number;
  pessimistic: number;
  expected: number;
  standardDeviation: number;
  version: number;
};
export type PertForecast = {
  start: string;
  expectedDays: number;
  standardDeviationDays: number;
  expectedFinish: string | null;
  probabilityOnTime: number | null;
  estimatedTasks: number;
  openTasks: number;
  unplannedStories: number;
  criticalPath: RoadmapTask[];
};
export type RoadmapEpic = {
  epic: Epic;
  plannedStart: string | null;
  plannedEnd: string | null;
  storyCount: number;
  totalPoints: number;
  donePoints: number;
  taskCount: number;
  doneTaskCount: number;
  progress: number;
  storyIds: number[];
  sprintIds: number[];
  forecast: PertForecast;
};
export type RoadmapSnapshot = {
  today: string;
  epics: RoadmapEpic[];
  sprints: {
    id: number;
    name: string;
    status: string;
    startDate: string | null;
    endDate: string | null;
  }[];
  epicLinks: {
    fromEpicId: number;
    toEpicId: number;
    dependencies: number;
    open: number;
  }[];
  dependencies: DependencyOverview;
  project: PertForecast;
};
export type Sprint = {
  id: number;
  name: string;
  goal?: string;
  status: string;
  totalStoryPoints: number;
  startDate?: string;
  endDate?: string;
  projectId: number;
  version?: number;
};
export type Activity = {
  id: number;
  eventType?: string;
  summary: string;
  actorEmail: string;
  createdAt: string;
};
export type Metrics = Record<string, unknown>;

export type WipSettings = {
  version: number;
  limits: {
    status: 'To Do' | 'In Progress' | 'Review';
    limit: number | null;
  }[];
};
export type FlowDistribution = {
  count: number;
  mean: number | null;
  p50: number | null;
  p85: number | null;
  p95: number | null;
};
export type FlowSignal =
  | 'OUTSIDE_LIMITS'
  | 'TWO_OF_THREE_BEYOND_TWO_SIGMA'
  | 'FOUR_OF_FIVE_BEYOND_ONE_SIGMA'
  | 'RUN_OF_EIGHT'
  | 'LARGE_MOVING_RANGE';
export type FlowReport = {
  days: number;
  start: string;
  end: string;
  generatedAt: string;
  cumulativeFlow: {
    date: string;
    toDo: number;
    inProgress: number;
    review: number;
    done: number;
  }[];
  cycleTimeHours: FlowDistribution;
  leadTimeHours: FlowDistribution;
  completedTasks: number;
  throughputPerDay: number;
  completions: {
    taskId: number;
    key: string;
    completedAt: string;
    cycleTimeHours: number | null;
    leadTimeHours: number | null;
  }[];
  agingWip: {
    taskId: number;
    key: string;
    title: string;
    status: string;
    ageHours: number;
    statusAgeHours: number;
    band:
      'BELOW_P50' | 'P50_TO_P85' | 'P85_TO_P95' | 'ABOVE_P95' | 'NO_REFERENCE';
  }[];
  columns: {
    status: string;
    currentWip: number;
    averageWip: number;
    limit: number | null;
    overLimit: boolean;
    bottleneck: 'OVER_LIMIT' | 'HIGHEST_AVERAGE_WIP' | null;
  }[];
  controlChart: {
    available: boolean;
    minimumPoints: number;
    mean: number | null;
    upperLimit: number | null;
    lowerLimit: number | null;
    movingRangeMean: number | null;
    upperRangeLimit: number | null;
    signalCount: number;
    points: {
      taskId: number;
      key: string;
      completedAt: string;
      cycleTimeHours: number;
      movingRange: number | null;
      signals: FlowSignal[];
    }[];
  };
  littlesLaw: {
    status: 'CONSISTENT' | 'INCONSISTENT' | 'INSUFFICIENT_DATA';
    averageWip: number;
    throughputPerDay: number;
    averageCycleTimeDays: number;
    expectedWip: number;
    ratio: number | null;
    tolerance: number;
  };
  tasksWithoutHistory: number;
};

export type ForecastBasis = 'THROUGHPUT' | 'VELOCITY';
export type ForecastWarning =
  | 'NO_HISTORY'
  | 'NO_COMPLETIONS'
  | 'INSUFFICIENT_HISTORY'
  | 'MISSING_TASK_HISTORY'
  | 'UNPLANNED_STORIES'
  | 'ZERO_POINT_TASKS'
  | 'EPIC_USES_PROJECT_HISTORY'
  | 'PAST_SPRINT_DEADLINE'
  | 'TARGET_BEYOND_HORIZON';
export type ForecastBin = { from: number; to: number; count: number };
export type ForecastTargetSource = 'REQUEST' | 'ACTIVE_SPRINT' | 'DEFAULT';
export type ForecastReport = {
  basis: ForecastBasis;
  unit: 'ITEMS' | 'STORY_POINTS';
  periodDays: number;
  start: string;
  seed: string;
  simulations: number;
  minimumPeriods: number;
  history: {
    start: string;
    end: string;
    count: number;
    label: string | null;
  }[];
  warnings: ForecastWarning[];
  scope: {
    epicId: number | null;
    epicTitle: string | null;
    openTasks: number;
    openStoryPoints: number;
    unplannedStories: number;
    zeroPointTasks: number;
    tasksWithoutHistory: number;
  };
  remaining: number;
  completionDates: { confidence: number; date: string | null }[];
  completionHistogram: ForecastBin[];
  beyondHorizon: number;
  horizonDays: number;
  itemsByDate: {
    date: string;
    source: ForecastTargetSource;
    items: { confidence: number; items: number }[];
    histogram: ForecastBin[];
  } | null;
  target: {
    items: number;
    date: string;
    source: ForecastTargetSource;
    probability: number | null;
  };
};
