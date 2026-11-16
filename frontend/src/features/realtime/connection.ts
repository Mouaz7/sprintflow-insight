import { api, csrfHeader } from '../../api/client';
import {
  backoff,
  fill,
  step,
  type EventPage,
  type RealtimeMessage,
} from './sequence';
import { encodeFrame, parseFrames, type StompFrame } from './stomp';

export type Viewer = { userId: number; name: string };
export type RealtimeStatus = 'connecting' | 'live' | 'offline';

export type RealtimeHandlers = {
  /** Something in the project changed (one message, a filled gap or a resync). */
  onChange: (messages: RealtimeMessage[]) => void;
  onPresence: (viewers: Viewer[]) => void;
  onStatus: (status: RealtimeStatus) => void;
};

const HEARTBEAT_MS = 10_000;
/** No frame or heart-beat for this long means the connection is dead. */
const SILENCE_MS = 25_000;

export function realtimeUrl(location: Location = window.location): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

/**
 * One project's live connection: STOMP over the same-origin WebSocket,
 * sequence tracking with gap filling, and reconnects with backoff.
 */
export class ProjectConnection {
  private socket: WebSocket | undefined;
  private buffer = '';
  private last: number | undefined;
  private syncing = false;
  private pending: RealtimeMessage[] = [];
  private attempt = 0;
  private closed = false;
  private timers: ReturnType<typeof setTimeout>[] = [];
  private heartbeat: ReturnType<typeof setInterval> | undefined;
  private lastFrameAt = 0;

  constructor(
    private readonly projectId: number,
    private readonly handlers: RealtimeHandlers,
    private readonly createSocket: (url: string) => WebSocket = (url) =>
      new WebSocket(url),
  ) {}

  open() {
    if (this.closed) return;
    this.handlers.onStatus('connecting');
    let socket: WebSocket;
    try {
      socket = this.createSocket(realtimeUrl());
    } catch {
      this.retry();
      return;
    }
    this.socket = socket;
    this.buffer = '';
    socket.onopen = () => void this.connect(socket);
    socket.onmessage = (event: MessageEvent) => {
      if (socket !== this.socket) return;
      this.lastFrameAt = Date.now();
      const parsed = parseFrames(this.buffer + String(event.data));
      this.buffer = parsed.rest;
      parsed.frames.forEach((frame) => this.receive(frame));
    };
    socket.onclose = () => {
      if (socket !== this.socket) return;
      this.stopHeartbeat();
      this.socket = undefined;
      this.retry();
    };
  }

  close() {
    this.closed = true;
    this.timers.forEach(clearTimeout);
    this.timers = [];
    this.stopHeartbeat();
    const socket = this.socket;
    this.socket = undefined;
    if (socket && socket.readyState === socket.OPEN)
      socket.send(encodeFrame('DISCONNECT'));
    socket?.close();
  }

  private async connect(socket: WebSocket) {
    try {
      const csrf = await csrfHeader();
      if (socket !== this.socket) return;
      socket.send(
        encodeFrame('CONNECT', {
          'accept-version': '1.2',
          host: window.location.hostname,
          'heart-beat': `${HEARTBEAT_MS},${HEARTBEAT_MS}`,
          [csrf.headerName]: csrf.token,
        }),
      );
    } catch {
      socket.close();
    }
  }

  private receive(frame: StompFrame) {
    if (frame.command === 'CONNECTED') {
      this.attempt = 0;
      this.startHeartbeat();
      this.socket?.send(
        encodeFrame('SUBSCRIBE', {
          id: 'presence',
          destination: `/topic/projects/${this.projectId}/presence`,
        }),
      );
      this.socket?.send(
        encodeFrame('SUBSCRIBE', {
          id: 'events',
          destination: `/topic/projects/${this.projectId}`,
        }),
      );
      this.handlers.onStatus('live');
      void this.sync();
    } else if (frame.command === 'MESSAGE') {
      let payload: unknown;
      try {
        payload = JSON.parse(frame.body);
      } catch {
        return;
      }
      if (frame.headers.get('subscription') === 'presence')
        this.handlers.onPresence(
          (payload as { viewers?: Viewer[] }).viewers ?? [],
        );
      else if (frame.headers.get('subscription') === 'events')
        this.message(payload as RealtimeMessage);
    } else if (frame.command === 'ERROR') {
      // The server refused (for example lost access); reconnect re-checks.
      this.socket?.close();
    }
  }

  private message(message: RealtimeMessage) {
    if (this.syncing) {
      this.pending.push(message);
      return;
    }
    const next = step(this.last, message.seq);
    if (next === 'apply') {
      this.last = message.seq;
      this.handlers.onChange([message]);
    } else if (next === 'gap') {
      this.pending.push(message);
      void this.sync();
    }
  }

  /** Fetches what was missed since `last`, then replays buffered messages. */
  private async sync() {
    if (this.syncing) return;
    this.syncing = true;
    const initial = this.last === undefined;
    try {
      const page = await api<EventPage>(
        `/projects/${this.projectId}/events?after=${this.last ?? 0}`,
      );
      if (this.closed) return;
      const result = fill(this.last, page);
      this.last = result.last;
      if (!initial && result.resync) this.handlers.onChange([]);
      else if (result.missed.length) this.handlers.onChange(result.missed);
    } catch {
      // Keep the position; the next message or reconnect tries again.
    } finally {
      this.syncing = false;
    }
    const buffered = this.pending;
    this.pending = [];
    buffered
      .sort((a, b) => a.seq - b.seq)
      .forEach((message) => {
        if (step(this.last, message.seq) === 'apply') {
          this.last = message.seq;
          this.handlers.onChange([message]);
        }
      });
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.lastFrameAt = Date.now();
    this.heartbeat = setInterval(() => {
      const socket = this.socket;
      if (!socket) return;
      if (Date.now() - this.lastFrameAt > SILENCE_MS) socket.close();
      else if (socket.readyState === socket.OPEN) socket.send('\n');
    }, HEARTBEAT_MS);
  }

  private stopHeartbeat() {
    if (this.heartbeat !== undefined) clearInterval(this.heartbeat);
    this.heartbeat = undefined;
  }

  private retry() {
    if (this.closed) return;
    this.handlers.onStatus('offline');
    const delay = backoff(this.attempt++);
    this.timers.push(setTimeout(() => this.open(), delay));
  }
}
