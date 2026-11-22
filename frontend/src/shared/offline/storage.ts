import type { DehydratedState } from '@tanstack/react-query';
import type { TaskStatus, User } from '../../api/contracts';

export type OfflineChange =
  | { kind: 'MOVE'; status: TaskStatus }
  | { kind: 'COMMENT'; message: string }
  | {
      kind: 'LOG_TIME';
      time: { minutes: number; spentOn: string; note: string };
    };

export type OperationTarget = {
  projectId: number;
  taskId: number;
  version: number;
  title: string;
};

export type QueuedOperation = OfflineChange &
  OperationTarget & {
    operationId: string;
    state?: 'conflict' | 'blocked' | undefined;
  };

export type LocalDocument = {
  maxLength: number;
  state: Uint8Array;
  revision: number;
  projected: number;
  pending: Record<string, Uint8Array>;
};

export type OfflineState = {
  user: User;
  verifiedAt: number;
  cache: DehydratedState;
  operations: QueuedOperation[];
  documents: Record<string, LocalDocument>;
};

const cacheLifetime = 24 * 60 * 60 * 1000;
const databaseName = 'sprintflow-offline-v1';
const vault = 'vault';
const active = 'active';
let opening: Promise<IDBDatabase> | undefined;

function database(): Promise<IDBDatabase> {
  opening ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(databaseName, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(vault);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => {
        db.close();
        opening = undefined;
      };
      resolve(db);
    };
  }).catch((cause: unknown) => {
    opening = undefined;
    throw cause;
  });
  return opening;
}

async function transact(
  mode: IDBTransactionMode,
  update?: (current: OfflineState | undefined) => OfflineState | undefined,
): Promise<OfflineState | undefined> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(vault, mode);
    const store = transaction.objectStore(vault);
    const request = store.get(active);
    let result: OfflineState | undefined;
    transaction.oncomplete = () => resolve(result);
    transaction.onabort = () => reject(transaction.error);
    transaction.onerror = () => reject(transaction.error);
    request.onsuccess = () => {
      const current = request.result as OfflineState | undefined;
      try {
        result = update ? update(current) : current;
        if (update && result !== current) {
          if (result) store.put(result, active);
          else store.delete(active);
        }
      } catch (cause) {
        transaction.abort();
        reject(cause);
      }
    };
  });
}

export const offlineState = () => transact('readonly');

export async function offlineAccount() {
  const state = await offlineState();
  return state && Date.now() - state.verifiedAt < cacheLifetime
    ? state
    : undefined;
}

export async function verifyOfflineAccount(user: User) {
  const { id, email, displayName, mfaEnrollmentRequired } = user;
  const identity: User = {
    id,
    email,
    ...(displayName === undefined ? {} : { displayName }),
    ...(mfaEnrollmentRequired === undefined ? {} : { mfaEnrollmentRequired }),
  };
  await transact('readwrite', (current) => ({
    ...(current?.user.id === id
      ? current
      : {
          cache: { queries: [], mutations: [] },
          operations: [],
          documents: {},
        }),
    user: identity,
    verifiedAt: Date.now(),
  }));
}

export function clearOfflineAccount(accountId?: number) {
  return transact('readwrite', (current) =>
    accountId === undefined || current?.user.id === accountId
      ? undefined
      : current,
  );
}

export function updateOfflineAccount(
  accountId: number,
  update: (current: OfflineState) => OfflineState,
) {
  return transact('readwrite', (current) =>
    current?.user.id === accountId ? update(current) : current,
  );
}
