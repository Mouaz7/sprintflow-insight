export type TimeEntry = {
  id: number;
  taskId: number;
  taskKey: string | null;
  taskTitle: string;
  userId: number | null;
  userEmail: string | null;
  spentOn: string;
  minutes: number | null;
  running: boolean;
  startedAt: string | null;
  note: string | null;
  version: number;
};
export type TimeEntryPage = { items: TimeEntry[]; nextBefore: number | null };
export type TimeReportRow = {
  taskId: number;
  key: string;
  title: string;
  status: string;
  assigneeEmail: string | null;
  estimateMinutes: number | null;
  loggedMinutes: number;
  varianceMinutes: number | null;
  variancePercent: number | null;
};
export type TimeReport = {
  sprintId: number | null;
  rows: TimeReportRow[];
  estimatedMinutes: number;
  loggedMinutes: number;
  loggedOnEstimatedMinutes: number;
  unestimatedTasks: number;
  varianceMinutes: number;
  variancePercent: number | null;
};
export type CapacityWarning =
  | 'SPRINT_DATES_MISSING'
  | 'CAPACITY_NOT_SET'
  | 'MEMBER_OVERBOOKED'
  | 'MEMBER_POINTS_OVERBOOKED'
  | 'TEAM_OVERBOOKED'
  | 'TEAM_POINTS_OVERBOOKED'
  | 'UNESTIMATED_TASKS'
  | 'UNASSIGNED_WORK';
export type MemberCapacity = {
  userId: number;
  email: string;
  role: string;
  configured: boolean;
  minutesPerDay: number | null;
  availabilityPercent: number | null;
  pointCapacity: number | null;
  daysOff: string[];
  version: number | null;
  availableDays: number;
  availableMinutes: number;
  effectivePoints: number | null;
  committedMinutes: number;
  committedPoints: number;
  unestimatedTasks: number;
  loggedMinutes: number;
  loadPercent: number | null;
  overbooked: boolean;
  pointsLoadPercent: number | null;
  pointsOverbooked: boolean;
};
export type TeamCapacity = {
  availableMinutes: number;
  effectivePoints: number | null;
  committedMinutes: number;
  committedPoints: number;
  unassignedMinutes: number;
  unassignedPoints: number;
  loggedMinutes: number;
  loadPercent: number | null;
  overbooked: boolean;
  pointsLoadPercent: number | null;
  pointsOverbooked: boolean;
};
export type Capacity = {
  sprintId: number;
  sprintName: string;
  startDate: string | null;
  endDate: string | null;
  workingDays: number;
  members: MemberCapacity[];
  team: TeamCapacity;
  warnings: { code: CapacityWarning; userId: number | null }[];
};
export type TimeWrite = {
  path: string;
  method: 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  version?: number | null;
};
