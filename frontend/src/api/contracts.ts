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
  | 'backlog'
  | 'sprints'
  | 'roadmap'
  | 'tasks'
  | 'metrics'
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
