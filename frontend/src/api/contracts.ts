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
