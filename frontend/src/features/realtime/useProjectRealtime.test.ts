import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
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

describe('useProjectRealtime', () => {
  afterEach(() => {
    sockets.length = 0;
    vi.unstubAllGlobals();
  });

  it('stays idle without a project or WebSocket support', () => {
    const { result } = renderHook(() => useProjectRealtime(7));
    expect(result.current).toEqual({
      revision: 0,
      viewers: [],
      status: 'offline',
    });
    vi.stubGlobal('WebSocket', FakeSocket);
    renderHook(() => useProjectRealtime(undefined));
    expect(sockets).toHaveLength(0);
  });

  it('coalesces a burst of changes into one revision and tracks presence', async () => {
    vi.stubGlobal('WebSocket', FakeSocket);
    const { result, unmount } = renderHook(() => useProjectRealtime(7));
    const socket = sockets[0]!;
    await act(async () => {
      socket.onopen?.();
      await vi.waitFor(() => expect(socket.sent.length).toBe(1));
      socket.receive('CONNECTED', {});
    });
    expect(result.current.status).toBe('live');
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
      socket.receive('MESSAGE', { subscription: 'events' }, moved(1));
      socket.receive('MESSAGE', { subscription: 'events' }, moved(2));
      socket.receive(
        'MESSAGE',
        { subscription: 'presence' },
        JSON.stringify({ viewers: [{ userId: 2, name: 'Ada' }] }),
      );
      await new Promise((resolve) => setTimeout(resolve, 300));
    });
    expect(result.current.revision).toBe(1);
    expect(result.current.viewers).toEqual([{ userId: 2, name: 'Ada' }]);
    unmount();
    expect(socket.readyState).toBe(3);
  });
});
