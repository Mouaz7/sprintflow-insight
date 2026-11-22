import type { QueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../api/client';
import type { Task, User } from '../../api/contracts';
import { fetchSessionUser, queryKeys } from '../../api/queries';
import {
  offlineAccount,
  offlineState,
  updateOfflineAccount,
  type OfflineChange,
  type OperationTarget,
  type QueuedOperation,
} from './storage';

const changesEvent = 'sprintflow:offline-operations';
const maximumQueued = 100;

function notify() {
  window.dispatchEvent(new Event(changesEvent));
  if (typeof BroadcastChannel !== 'undefined') {
    const channel = new BroadcastChannel(changesEvent);
    channel.postMessage(null);
    channel.close();
  }
}

export function listenToOfflineChanges(listener: () => void) {
  const channel =
    typeof BroadcastChannel === 'undefined'
      ? undefined
      : new BroadcastChannel(changesEvent);
  window.addEventListener(changesEvent, listener);
  channel?.addEventListener('message', listener);
  return () => {
    window.removeEventListener(changesEvent, listener);
    channel?.close();
  };
}

export async function enqueueTaskChange(
  client: QueryClient,
  task: Task,
  change: OfflineChange,
) {
  if (task.version === undefined) return false;
  return enqueueOfflineChange(
    client,
    {
      projectId: task.projectId,
      taskId: task.id,
      title: task.title,
      version: task.version,
    },
    change,
  ).catch(() => false);
}

export async function enqueueTaskMove(
  client: QueryClient,
  task: Task,
  status: Task['status'],
) {
  if (!(await enqueueTaskChange(client, task, { kind: 'MOVE', status })))
    return false;
  client.setQueryData<Task[]>(queryKeys.tasks(task.projectId), (current) =>
    current?.map((item) => (item.id === task.id ? { ...item, status } : item)),
  );
  return true;
}

async function enqueueOfflineChange(
  client: QueryClient,
  target: OperationTarget,
  change: OfflineChange,
) {
  const account = client.getQueryData<User>(queryKeys.me);
  const active = await offlineAccount();
  if (
    !account ||
    account.id !== active?.user.id ||
    !Number.isSafeInteger(target.version)
  )
    return false;
  let written = false;
  await updateOfflineAccount(account.id, (state) => {
    if (state.operations.length >= maximumQueued) return state;
    written = true;
    return {
      ...state,
      operations: [
        ...state.operations,
        { ...target, ...change, operationId: crypto.randomUUID() },
      ],
    };
  });
  if (written) notify();
  return written;
}

function operationBody(operation: QueuedOperation, accountId: number) {
  const { taskId, operationId, kind } = operation;
  const identity = { taskId, operationId, kind, accountId };
  switch (operation.kind) {
    case 'MOVE':
      return { ...identity, status: operation.status };
    case 'COMMENT':
      return { ...identity, message: operation.message };
    case 'LOG_TIME':
      return { ...identity, time: operation.time };
  }
}

async function send(operation: QueuedOperation, accountId: number) {
  return api<{ taskVersion: number }>(
    `/projects/${operation.projectId}/offline-operations`,
    {
      method: 'POST',
      headers: { 'If-Match': `"${operation.version}"` },
      body: JSON.stringify(operationBody(operation, accountId)),
    },
  );
}

async function complete(
  accountId: number,
  operation: QueuedOperation,
  version: number,
) {
  await updateOfflineAccount(accountId, (state) => ({
    ...state,
    operations: state.operations
      .filter((item) => item.operationId !== operation.operationId)
      .map((item) =>
        item.taskId === operation.taskId &&
        item.version === operation.version &&
        !item.state
          ? { ...item, version }
          : item,
      ),
  }));
  notify();
}

async function quarantine(
  accountId: number,
  operation: QueuedOperation,
  status: number,
) {
  await updateOfflineAccount(accountId, (state) => ({
    ...state,
    operations: state.operations.map((item) =>
      item.operationId === operation.operationId
        ? { ...item, state: status === 412 ? 'conflict' : 'blocked' }
        : item,
    ),
  }));
  notify();
}

let currentReplay: Promise<void> | undefined;

export function replayOfflineChanges(client: QueryClient, accountId: number) {
  if (!navigator.onLine) return Promise.resolve();
  currentReplay ??= (
    navigator.locks
      ? navigator.locks.request(changesEvent, () => replay(client, accountId))
      : replay(client, accountId)
  ).finally(() => {
    currentReplay = undefined;
  });
  return currentReplay;
}

async function replay(client: QueryClient, accountId: number) {
  const initial = await offlineState();
  if (
    initial?.user.id !== accountId ||
    !initial.operations.some((item) => !item.state)
  )
    return;
  if ((await fetchSessionUser())?.id !== accountId) return;
  while (navigator.onLine) {
    const current = await offlineState();
    if (current?.user.id !== accountId) return;
    const firstForTask = new Set<number>();
    const next = current.operations.find((item) => {
      if (firstForTask.has(item.taskId)) return false;
      firstForTask.add(item.taskId);
      return !item.state;
    });
    if (!next) return;
    try {
      const result = await send(next, accountId);
      await complete(accountId, next, result.taskVersion);
      void client.invalidateQueries({ queryKey: ['project', next.projectId] });
      void client.invalidateQueries({
        queryKey: ['task', next.taskId, 'comments'],
      });
    } catch (cause) {
      if (
        !(cause instanceof ApiError) ||
        ![400, 403, 404, 409, 412, 428].includes(cause.status)
      )
        return;
      await quarantine(accountId, next, cause.status);
    }
  }
}

export async function discardOfflineChange(
  accountId: number,
  operation: QueuedOperation,
) {
  await updateOfflineAccount(accountId, (state) => ({
    ...state,
    operations: state.operations.filter(
      (item) => item.operationId !== operation.operationId,
    ),
  }));
  notify();
}

export async function retryOfflineChange(
  client: QueryClient,
  accountId: number,
  operation: QueuedOperation,
) {
  if ((await fetchSessionUser())?.id !== accountId) return;
  const task = await api<Task>(`/tasks/${operation.taskId}`);
  const version = task.version;
  if (version === undefined || task.projectId !== operation.projectId) return;
  await updateOfflineAccount(accountId, (state) => ({
    ...state,
    operations: state.operations.map((item) =>
      item.operationId === operation.operationId
        ? { ...item, state: undefined, version }
        : item,
    ),
  }));
  notify();
  await replayOfflineChanges(client, accountId);
}
