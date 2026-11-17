import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectConnection, realtimeUrl } from './connection';
import { encodeFrame, parseFrames } from './stomp';

const api = vi.fn();
vi.mock('../../api/client', () => ({
  api: (path: string) => api(path),
  csrfHeader: async () => ({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }),
}));

class FakeSocket {
  readonly OPEN = 1;
  readyState = 0;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  constructor(readonly url: string) {}
  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.readyState = 3;
    this.onclose?.();
  }
  open() {
    this.readyState = 1;
    this.onopen?.();
  }
  receive(command: string, headers: Record<string, string>, body = '') {
    this.onmessage?.({ data: encodeFrame(command, headers, body) });
  }
  commands() {
    return this.sent.flatMap((data) =>
      parseFrames(data).frames.map((frame) => frame.command),
    );
  }
}

const event = (seq: number) => ({
  projectId: 7,
  seq,
  type: 'TaskMoved',
  entityId: 3,
  version: seq,
});

function setup() {
  const sockets: FakeSocket[] = [];
  const handlers = {
    onChange: vi.fn(),
    onPresence: vi.fn(),
    onStatus: vi.fn(),
  };
  const connection = new ProjectConnection(7, handlers, (url) => {
    const socket = new FakeSocket(url);
    sockets.push(socket);
    return socket as unknown as WebSocket;
  });
  return { sockets, handlers, connection };
}

async function connected(socket: FakeSocket) {
  socket.open();
  await vi.waitFor(() => expect(socket.commands()).toContain('CONNECT'));
  socket.receive('CONNECTED', { version: '1.2' });
}

describe('ProjectConnection', () => {
  beforeEach(() => {
    api.mockReset();
  });

  it('uses the same-origin WebSocket URL', () => {
    expect(
      realtimeUrl({ protocol: 'https:', host: 'a.example' } as Location),
    ).toBe('wss://a.example/ws');
    expect(
      realtimeUrl({ protocol: 'http:', host: 'localhost:5173' } as Location),
    ).toBe('ws://localhost:5173/ws');
  });

  it('connects with the CSRF header, subscribes and learns the position', async () => {
    api.mockResolvedValue({
      projectId: 7,
      latestSeq: 4,
      resync: false,
      events: [],
    });
    const { sockets, handlers, connection } = setup();
    connection.open();
    const socket = sockets[0]!;
    await connected(socket);

    const connect = parseFrames(socket.sent[0]!).frames[0]!;
    expect(connect.headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    expect(connect.headers.get('accept-version')).toBe('1.2');
    expect(socket.commands()).toEqual([
      'CONNECT',
      'SUBSCRIBE',
      'SUBSCRIBE',
      'SEND',
    ]);
    expect(handlers.onStatus).toHaveBeenLastCalledWith('live');
    await vi.waitFor(() =>
      expect(api).toHaveBeenCalledWith('/projects/7/events?after=0'),
    );
    expect(handlers.onChange).toHaveBeenCalledWith([]);
    handlers.onChange.mockClear();

    socket.receive(
      'MESSAGE',
      { subscription: 'events' },
      JSON.stringify(event(5)),
    );
    expect(handlers.onChange).toHaveBeenCalledWith([event(5)]);
    socket.receive(
      'MESSAGE',
      { subscription: 'events' },
      JSON.stringify(event(5)),
    );
    expect(handlers.onChange).toHaveBeenCalledTimes(1);

    socket.receive(
      'MESSAGE',
      { subscription: 'presence' },
      JSON.stringify({ projectId: 7, viewers: [{ userId: 2, name: 'Ada' }] }),
    );
    expect(handlers.onPresence).toHaveBeenCalledWith([
      { userId: 2, name: 'Ada' },
    ]);
    socket.receive('MESSAGE', { subscription: 'events' }, 'not json');
    expect(handlers.onChange).toHaveBeenCalledTimes(1);
    connection.close();
    expect(socket.commands()).toContain('DISCONNECT');
  });

  it('fills a sequence gap from the REST endpoint', async () => {
    api.mockResolvedValueOnce({
      projectId: 7,
      latestSeq: 1,
      resync: false,
      events: [],
    });
    const { sockets, handlers, connection } = setup();
    connection.open();
    await connected(sockets[0]!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledTimes(1));

    api.mockResolvedValueOnce({
      projectId: 7,
      latestSeq: 4,
      resync: false,
      events: [event(2), event(3), event(4)],
    });
    sockets[0]!.receive(
      'MESSAGE',
      { subscription: 'events' },
      JSON.stringify(event(4)),
    );
    await vi.waitFor(() =>
      expect(handlers.onChange).toHaveBeenCalledWith([
        event(2),
        event(3),
        event(4),
      ]),
    );
    expect(api).toHaveBeenLastCalledWith('/projects/7/events?after=1');
    connection.close();
  });

  it('reconnects with backoff and asks for a reload after a resync', async () => {
    vi.useFakeTimers();
    try {
      api.mockResolvedValueOnce({
        projectId: 7,
        latestSeq: 2,
        resync: false,
        events: [],
      });
      const { sockets, handlers, connection } = setup();
      connection.open();
      await connected(sockets[0]!);
      await vi.waitFor(() => expect(api).toHaveBeenCalledTimes(1));

      sockets[0]!.close();
      expect(handlers.onStatus).toHaveBeenLastCalledWith('offline');
      await vi.advanceTimersByTimeAsync(1300);
      expect(sockets).toHaveLength(2);

      api.mockResolvedValueOnce({
        projectId: 7,
        latestSeq: 900,
        resync: true,
        events: [],
      });
      await connected(sockets[1]!);
      await vi.waitFor(() =>
        expect(handlers.onChange).toHaveBeenCalledWith([]),
      );
      expect(api).toHaveBeenLastCalledWith('/projects/7/events?after=2');

      sockets[1]!.receive('ERROR', { message: 'denied' });
      expect(sockets[1]!.readyState).toBe(3);
      connection.close();
      await vi.advanceTimersByTimeAsync(60_000);
      expect(sockets).toHaveLength(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('closes a silent connection and keeps buffered messages in order', async () => {
    vi.useFakeTimers();
    try {
      let release: (value: unknown) => void = () => {};
      api.mockReturnValueOnce(new Promise((resolve) => (release = resolve)));
      const { sockets, handlers, connection } = setup();
      connection.open();
      await connected(sockets[0]!);
      sockets[0]!.receive(
        'MESSAGE',
        { subscription: 'events' },
        JSON.stringify(event(3)),
      );
      sockets[0]!.receive(
        'MESSAGE',
        { subscription: 'events' },
        JSON.stringify(event(2)),
      );
      release({ projectId: 7, latestSeq: 1, resync: false, events: [] });
      await vi.waitFor(() =>
        expect(handlers.onChange).toHaveBeenCalledTimes(3),
      );
      expect(handlers.onChange.mock.calls).toEqual([
        [[]],
        [[event(2)]],
        [[event(3)]],
      ]);

      await vi.advanceTimersByTimeAsync(10_000);
      expect(sockets[0]!.sent.at(-1)).toBe('\n');
      await vi.advanceTimersByTimeAsync(20_000);
      expect(sockets[0]!.readyState).toBe(3);
      connection.close();
    } finally {
      vi.useRealTimers();
    }
  });
});
