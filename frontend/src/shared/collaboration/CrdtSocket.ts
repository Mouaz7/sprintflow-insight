import { csrfHeader } from '../../api/client';
import {
  encodeBinaryFrame,
  encodeFrame,
  parseBinaryFrames,
  realtimeUrl,
} from '../lib/stomp';
import {
  collaborationApi,
  type Caret,
  type CollaborationScope,
  type Presence,
} from './contracts';

type Handlers = {
  connected: () => void;
  disconnected: () => void;
  update: (revision: number, updateId: string, body: Uint8Array) => void;
  awareness: (presence: Presence) => void;
};

const decoder = new TextDecoder();
const encoder = new TextEncoder();
const updateHeaderBytes = 24;

export class CrdtSocket {
  private socket: WebSocket | undefined;
  private rest: Uint8Array = new Uint8Array();
  private heartbeat: ReturnType<typeof setInterval> | undefined;
  private retry: ReturnType<typeof setTimeout> | undefined;
  private lastFrame = 0;
  private stopped = false;
  private live = false;
  private readonly path: string;

  constructor(
    scope: CollaborationScope,
    private readonly handlers: Handlers,
  ) {
    this.path = collaborationApi(scope);
  }

  open() {
    if (this.stopped) return;
    let socket: WebSocket;
    try {
      socket = new WebSocket(realtimeUrl());
    } catch {
      this.reconnect();
      return;
    }
    this.socket = socket;
    this.rest = new Uint8Array();
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => {
      void csrfHeader()
        .then((csrf) => {
          if (socket !== this.socket) return;
          socket.send(
            encodeFrame('CONNECT', {
              'accept-version': '1.2',
              host: window.location.hostname,
              'heart-beat': '10000,10000',
              [csrf.headerName]: csrf.token,
            }),
          );
        })
        .catch(() => socket.close());
    };
    socket.onmessage = (event: MessageEvent) => {
      if (socket !== this.socket) return;
      this.lastFrame = Date.now();
      try {
        const data =
          event.data instanceof ArrayBuffer
            ? new Uint8Array(event.data)
            : encoder.encode(String(event.data));
        const next = new Uint8Array(this.rest.length + data.length);
        next.set(this.rest);
        next.set(data, this.rest.length);
        const parsed = parseBinaryFrames(next);
        this.rest = parsed.rest;
        for (const frame of parsed.frames) {
          if (frame.command === 'CONNECTED') {
            this.subscribe(socket);
          } else if (frame.command === 'ERROR') {
            socket.close();
          } else if (frame.command === 'MESSAGE') {
            const sub = frame.headers.get('subscription');
            if (sub === 'shared-updates') this.receiveUpdate(frame.body);
            if (sub === 'shared-awareness')
              this.handlers.awareness(
                JSON.parse(decoder.decode(frame.body)) as Presence,
              );
          }
        }
      } catch {
        socket.close();
      }
    };
    socket.onclose = () => {
      if (socket !== this.socket) return;
      this.socket = undefined;
      this.live = false;
      clearInterval(this.heartbeat);
      this.handlers.disconnected();
      this.reconnect();
    };
  }

  shutDown() {
    this.stopped = true;
    this.live = false;
    clearTimeout(this.retry);
    clearInterval(this.heartbeat);
    const socket = this.socket;
    this.socket = undefined;
    if (socket?.readyState === WebSocket.OPEN)
      socket.send(encodeFrame('DISCONNECT'));
    socket?.close();
  }

  send(updateId: string, payload: Uint8Array) {
    if (!this.live || this.socket?.readyState !== WebSocket.OPEN) return;
    const header = Uint8Array.from(updateId.match(/.{2}/g) ?? [], (pair) =>
      Number.parseInt(pair, 16),
    );
    const body = new Uint8Array(header.length + payload.length);
    body.set(header);
    body.set(payload, header.length);
    this.socket.send(
      encodeBinaryFrame(
        'SEND',
        {
          destination: `/app${this.path}/updates`,
          'content-type': 'application/octet-stream',
        },
        body,
      ),
    );
  }

  sendCaret(caret: Caret) {
    if (!this.live || this.socket?.readyState !== WebSocket.OPEN) return;
    this.socket.send(
      encodeFrame(
        'SEND',
        {
          destination: `/app${this.path}/awareness`,
          'content-type': 'application/json',
        },
        JSON.stringify(caret),
      ),
    );
  }

  private subscribe(socket: WebSocket) {
    for (const topic of ['updates', 'awareness']) {
      socket.send(
        encodeFrame('SUBSCRIBE', {
          id: `shared-${topic}`,
          destination: `/topic${this.path}/${topic}`,
        }),
      );
    }
    this.live = true;
    clearInterval(this.heartbeat);
    this.heartbeat = setInterval(() => {
      if (Date.now() - this.lastFrame > 25_000) socket.close();
      else if (socket.readyState === WebSocket.OPEN) socket.send('\n');
    }, 10_000);
    this.handlers.connected();
  }

  private receiveUpdate(body: Uint8Array) {
    if (body.length <= updateHeaderBytes)
      throw new Error('Incomplete shared update.');
    const revision = Number(
      new DataView(body.buffer, body.byteOffset, 8).getBigUint64(0),
    );
    if (!Number.isSafeInteger(revision) || revision < 1)
      throw new Error('Invalid shared revision.');
    const updateId = Array.from(body.subarray(8, 24), (byte) =>
      byte.toString(16).padStart(2, '0'),
    ).join('');
    this.handlers.update(revision, updateId, body.slice(24));
  }

  private reconnect() {
    if (!this.stopped) this.retry = setTimeout(() => this.open(), 1500);
  }
}
