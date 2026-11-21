export type CollaborationScope = {
  projectId: number;
  target: 'TASK' | 'RETROSPECTIVE';
  id: number;
};

export type Snapshot = {
  active: boolean;
  editable: boolean;
  initialText: string;
  maxLength: number;
  revision: number;
  projectionRevision: number;
  snapshotRevision: number;
  snapshot: string | null;
  updates: { revision: number; bytes: string }[];
};

export type Caret = { anchor: string | null; head: string | null };
export type Presence = Caret & {
  sessionId: string;
  userId: number;
  name: string;
};

export const collaborationApi = ({
  projectId,
  target,
  id,
}: CollaborationScope) =>
  `/projects/${projectId}/collaboration/${target}/${id}`;

export function fromBase64(value: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}

export function toBase64(value: Uint8Array): string {
  return btoa(Array.from(value, (byte) => String.fromCharCode(byte)).join(''));
}
