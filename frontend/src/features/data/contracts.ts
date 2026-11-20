export type ImportRequest = {
  format: 'CSV' | 'JIRA_JSON';
  content: string;
  mapping: Record<string, string>;
  statuses: Record<string, string>;
  truncateText: boolean;
  preserveHistory: boolean;
  expectedFingerprint?: string;
};
export type ImportRow = {
  rowNumber: number;
  title: string;
  description: string;
  status: string;
  sourceKey: string | null;
  storyPoints: number;
  assigneeEmail: string | null;
  labels: string[];
  dueAt: string | null;
  createdAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  customValues: Record<string, string>;
  errors: string[];
  warnings: string[];
  duplicate: boolean;
};
export type ImportPreview = {
  columns: string[];
  mapping: Record<string, string>;
  rows: ImportRow[];
  fingerprint: string;
  createCount: number;
  skipCount: number;
  canCommit: boolean;
  previousBatchId: string | null;
};
export type ImportResult = {
  batchId: string;
  createdCount: number;
  skippedCount: number;
  replayed: boolean;
};
export type ImportBatch = {
  id: string;
  format: string;
  createdCount: number;
  skippedCount: number;
  createdAt: string;
};
export type AuditEntry = {
  id: number;
  actorUserId: number;
  actorRole: string;
  action: string;
  targetType: string;
  targetId: string | null;
  createdAt: string;
};
export type AuditPage = { items: AuditEntry[]; nextBefore: number | null };
