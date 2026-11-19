import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../../api/queries';
import { encodeFrame } from './stomp';
import { useProjectRealtime } from './useProjectRealtime';

vi.mock('../../api/client', () => ({
  api: async () => ({ projectId: 7, latestSeq: 0, resync: false, events: [] }),
  csrfHeader: async () => ({ token: 't', headerName: 'X-CSRF-TOKEN' }),
}));

const sockets: FakeSocket[] = [];
class FakeSocket {
  readonly OPEN = 1;
  readyState = 1;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  constructor(readonly url: string) {
    sockets.push(this);
  }
  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.readyState = 3;
  }
  receive(command: string, headers: Record<string, string>, body = '') {
    this.onmessage?.({ data: encodeFrame(command, headers, body) });
  }
}

const moved = (seq: number) =>
  JSON.stringify({
    projectId: 7,
    seq,
    type: 'TaskMoved',
    entityId: 1,
    version: 1,
  });

function setup(projectId: number | undefined) {
  const client = createQueryClient();
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
  return {
    client,
    ...renderHook(() => useProjectRealtime(projectId), { wrapper }),
  };
}

describe('useProjectRealtime', () => {
  afterEach(() => {
    sockets.length = 0;
    vi.unstubAllGlobals();
  });

  it('coalesces poker messages into a project-scoped refresh without reloading the board or role', async () => {
    vi.stubGlobal('WebSocket', FakeSocket);
    const { client, unmount } = setup(7);
    const socket = sockets[0]!;
    await act(async () => {
      socket.onopen?.();
      socket.receive('CONNECTED', {});
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    const keys = [
      ['project', 7, 'poker'],
      ['project', 8, 'poker'],
      ['project', 7, 'tasks'],
      ['project', 7, 'role'],
      ['workspace-search'],
    ];
    keys.forEach((key) => client.setQueryData(key, []));
    await act(async () => {
      for (let seq = 1; seq <= 2; seq++)
        socket.receive(
          'MESSAGE',
          { subscription: 'events' },
          JSON.stringify({
            projectId: 7,
            seq,
            type: 'PokerSessionChanged',
            entityId: 9,
            version: seq,
          }),
        );
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    expect(client.getQueryState(keys[0]!)?.isInvalidated).toBe(true);
    for (const key of keys.slice(1))
      expect(client.getQueryState(key)?.isInvalidated).toBe(false);
    unmount();
  });

  it('stays idle without a project or WebSocket support', () => {
    const { result } = setup(7);
    expect(result.current).toEqual({
      viewers: [],
      status: 'offline',
    });
    vi.stubGlobal('WebSocket', FakeSocket);
    setup(undefined);
    expect(sockets).toHaveLength(0);
  });

  it('refreshes the active project and changed thread, coalesces bursts and tracks presence', async () => {
    vi.stubGlobal('WebSocket', FakeSocket);
    const { result, client, unmount } = setup(7);
    const socket = sockets[0]!;
    await act(async () => {
      socket.onopen?.();
      await vi.waitFor(() => expect(socket.sent.length).toBe(1));
      socket.receive('CONNECTED', {});
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    expect(result.current.status).toBe('live');
    const keys = [
      ['project', 7, 'tasks'],
      ['project', 8, 'tasks'],
      ['task', 11, 'comments'],
      ['task', 22, 'comments'],
      ['notifications'],
    ];
    keys.forEach((key) => client.setQueryData(key, []));
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
      socket.receive('MESSAGE', { subscription: 'events' }, moved(1));
      socket.receive('MESSAGE', { subscription: 'events' }, moved(2));
      socket.receive(
        'MESSAGE',
        { subscription: 'events' },
        JSON.stringify({
          projectId: 7,
          seq: 3,
          type: 'CommentUpdated',
          entityId: 11,
          version: null,
        }),
      );
      socket.receive(
        'MESSAGE',
        { subscription: 'events' },
        JSON.stringify({
          projectId: 7,
          seq: 4,
          type: 'NotificationsChanged',
          entityId: null,
          version: null,
        }),
      );
      socket.receive(
        'MESSAGE',
        { subscription: 'presence' },
        JSON.stringify({ viewers: [{ userId: 2, name: 'Ada' }] }),
      );
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    expect(client.getQueryState(keys[0]!)?.isInvalidated).toBe(true);
    expect(client.getQueryState(keys[1]!)?.isInvalidated).toBe(false);
    expect(client.getQueryState(keys[2]!)?.isInvalidated).toBe(true);
    expect(client.getQueryState(keys[3]!)?.isInvalidated).toBe(false);
    expect(client.getQueryState(keys[4]!)?.isInvalidated).toBe(true);
    expect(
      invalidate.mock.calls.filter(
        ([options]) =>
          JSON.stringify(options?.queryKey) ===
          JSON.stringify(['project', 7, 'tasks']),
      ),
    ).toHaveLength(1);
    expect(result.current.viewers).toEqual([{ userId: 2, name: 'Ada' }]);
    unmount();
    expect(socket.readyState).toBe(3);
  });
});
