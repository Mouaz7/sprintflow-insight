import 'fake-indexeddb/auto';
import { HttpResponse, http } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { clearCsrfToken } from '../../api/client';
import type { Task, User } from '../../api/contracts';
import { createQueryClient, queryKeys } from '../../api/queries';
import { server } from '../../test/server';
import {
  enqueueTaskChange,
  replayOfflineChanges,
  retryOfflineChange,
} from './operations';
import {
  clearOfflineAccount,
  offlineState,
  verifyOfflineAccount,
} from './storage';

const account: User = { id: 12, email: 'offline@example.test' };
const task: Task = {
  id: 5,
  projectId: 7,
  status: 'To Do',
  version: 2,
  storyPoints: 0,
  title: 'Offline task',
};

beforeEach(async () => {
  await clearOfflineAccount();
  await verifyOfflineAccount(account);
  clearCsrfToken();
  server.use(
    http.get('*/api/auth/me', () => HttpResponse.json(account)),
    http.get('*/api/auth/csrf', () =>
      HttpResponse.json({ token: 'test', headerName: 'X-CSRF-TOKEN' }),
    ),
  );
});

function prepare() {
  const client = createQueryClient();
  client.setQueryData(queryKeys.me, account);
  return client;
}

describe('offline workspace operations', () => {
  it('replays confirmed edits in order with the server revision of its own earlier change', async () => {
    const client = prepare();
    expect(
      await enqueueTaskChange(client, task, { kind: 'MOVE', status: 'Review' }),
    ).toBe(true);
    expect(
      await enqueueTaskChange(client, task, {
        kind: 'COMMENT',
        message: 'Saved locally',
      }),
    ).toBe(true);
    const versions: (string | null)[] = [];
    const actions: string[] = [];
    server.use(
      http.post('*/api/projects/7/offline-operations', async ({ request }) => {
        const body = (await request.json()) as {
          accountId: number;
          kind: string;
          operationId: string;
        };
        expect(body.accountId).toBe(account.id);
        expect(body.operationId).toMatch(/^[0-9a-f-]{36}$/);
        versions.push(request.headers.get('If-Match'));
        actions.push(body.kind);
        return HttpResponse.json({ taskVersion: 3 });
      }),
    );
    await replayOfflineChanges(client, account.id);
    expect(versions).toEqual(['"2"', '"3"']);
    expect(actions).toEqual(['MOVE', 'COMMENT']);
    expect((await offlineState())?.operations).toEqual([]);
  });

  it('holds conflicts until the user explicitly reapplies to a fresh revision', async () => {
    const client = prepare();
    await enqueueTaskChange(client, task, { kind: 'MOVE', status: 'Done' });
    const sent: (string | null)[] = [];
    server.use(
      http.get('*/api/tasks/5', () =>
        HttpResponse.json({ ...task, version: 9 }),
      ),
      http.post('*/api/projects/7/offline-operations', ({ request }) => {
        const version = request.headers.get('If-Match');
        sent.push(version);
        return version === '"9"'
          ? HttpResponse.json({ taskVersion: 10 })
          : HttpResponse.json({}, { status: 412 });
      }),
    );
    await replayOfflineChanges(client, account.id);
    await replayOfflineChanges(client, account.id);
    const conflict = (await offlineState())?.operations[0];
    expect(conflict?.state).toBe('conflict');
    expect(sent).toEqual(['"2"']);
    if (!conflict) throw new Error('Expected a conflict.');
    await retryOfflineChange(client, account.id, conflict);
    expect(sent).toEqual(['"2"', '"9"']);
    expect((await offlineState())?.operations).toEqual([]);
  });

  it('keeps account data separate and never replays with another account’s session', async () => {
    const client = prepare();
    await enqueueTaskChange(client, task, {
      kind: 'COMMENT',
      message: 'Private',
    });
    const different = { id: 99, email: 'another@example.test' };
    server.use(http.get('*/api/auth/me', () => HttpResponse.json(different)));
    await replayOfflineChanges(client, account.id);
    expect((await offlineState())?.operations).toHaveLength(1);
    await verifyOfflineAccount(different);
    expect((await offlineState())?.operations).toEqual([]);
    expect(
      await enqueueTaskChange(client, task, { kind: 'MOVE', status: 'Done' }),
    ).toBe(false);
  });
});
