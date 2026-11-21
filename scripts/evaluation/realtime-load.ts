import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { EventEmitter } from 'node:events';
import { writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { request } from 'playwright';
import WebSocket from 'ws';
import * as Y from 'yjs';
import {
  encodeBinaryFrame,
  encodeFrame,
  parseBinaryFrames,
  type BinaryStompFrame,
} from '../../frontend/src/shared/lib/stomp.ts';

const [address, replicas, output] = process.argv.slice(2);
if (!address || !output || !['1', '2'].includes(replicas ?? ''))
  throw new Error(
    'Provide a local app URL, replica count (1 or 2) and output JSON path.',
  );
const base = new URL(address);
assert.ok(
  base.protocol === 'http:' &&
    base.hostname === '127.0.0.1' &&
    !base.username &&
    !base.password &&
    base.pathname === '/' &&
    !base.search &&
    !base.hash,
  'The load fixture requires an HTTP application bound to 127.0.0.1.',
);
const sizes = (process.env.EVAL_REALTIME_CLIENTS ?? '10,50,100,250,500')
  .split(',')
  .map(Number);
assert.ok(
  sizes.length > 0 &&
    sizes.length <= 5 &&
    sizes.every(
      (value) => Number.isInteger(value) && value >= 10 && value <= 500,
    ),
  'Use at most five client counts between 10 and 500.',
);

type Client = { socket: WebSocket; events: EventEmitter };
type Csrf = { headerName: string; token: string };
type Entity = { id: number };
const api = await request.newContext({ baseURL: base.origin, timeout: 20_000 });

async function command<T>(
  path: string,
  method: string,
  body?: object,
): Promise<T> {
  const token = await api.get('/api/auth/csrf');
  assert.ok(token.ok(), 'Could not establish the fixture CSRF session.');
  const csrf = (await token.json()) as Csrf;
  const response = await api.fetch('/api' + path, {
    method,
    headers: { [csrf.headerName]: csrf.token },
    data: body,
  });
  assert.ok(
    response.ok(),
    `${method} ${path.split('?')[0]} returned HTTP ${response.status()}`,
  );
  const data = await response.text();
  return (data ? JSON.parse(data) : undefined) as T;
}

function connect(
  cookie: string,
  csrf: Csrf,
  topics: string[],
): Promise<Client> {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(`ws://${base.host}/ws`, {
      origin: base.origin,
      headers: { Cookie: cookie },
      handshakeTimeout: 12_000,
      maxPayload: 512 * 1024,
    });
    socket.binaryType = 'arraybuffer';
    const events = new EventEmitter();
    let rest: Uint8Array = new Uint8Array();
    const timeout = setTimeout(() => {
      socket.terminate();
      reject(new Error('Timed out waiting for STOMP CONNECTED.'));
    }, 15_000);
    socket.on('error', () => {
      clearTimeout(timeout);
      reject(new Error('The fixture WebSocket could not connect.'));
    });
    socket.on('close', () => {
      clearTimeout(timeout);
      events.emit('closed');
      reject(new Error('The fixture WebSocket closed before CONNECTED.'));
    });
    socket.on('open', () =>
      socket.send(
        encodeFrame('CONNECT', {
          host: base.hostname,
          'accept-version': '1.2',
          'heart-beat': '10000,10000',
          [csrf.headerName]: csrf.token,
        }),
      ),
    );
    socket.on('message', (data) => {
      try {
        const bytes = Array.isArray(data)
          ? Buffer.concat(data)
          : data instanceof ArrayBuffer
            ? Buffer.from(data)
            : data;
        const parsed = parseBinaryFrames(Buffer.concat([rest, bytes]));
        rest = parsed.rest;
        for (const frame of parsed.frames) {
          if (frame.command === 'ERROR') {
            socket.close();
            continue;
          }
          if (frame.command === 'CONNECTED') {
            clearTimeout(timeout);
            topics.forEach((destination, id) =>
              socket.send(
                encodeFrame('SUBSCRIBE', { id: String(id), destination }),
              ),
            );
            resolve({ socket, events });
          } else if (frame.command === 'MESSAGE') events.emit('frame', frame);
        }
      } catch {
        socket.close();
      }
    });
  });
}

function observe(
  client: Client,
  matches: (frame: BinaryStompFrame) => boolean,
) {
  return new Promise<number>((resolve, reject) => {
    const clear = () => {
      clearTimeout(timer);
      client.events.off('frame', receive);
      client.events.off('closed', closed);
    };
    const closed = () => {
      clear();
      reject(new Error('A subscriber disconnected before receiving a sample.'));
    };
    const receive = (frame: BinaryStompFrame) => {
      if (!matches(frame)) return;
      clear();
      resolve(performance.now());
    };
    const timer = setTimeout(closed, 15_000);
    client.events.on('frame', receive);
    client.events.on('closed', closed);
  });
}

async function measure(
  clients: Client[],
  matches: (frame: BinaryStompFrame) => boolean,
  emit: () => Promise<unknown>,
) {
  const started = performance.now();
  const [received] = await Promise.all([
    Promise.all(clients.map((client) => observe(client, matches))),
    emit(),
  ]);
  return received.map((arrival) => arrival - started);
}

function stats(values: number[]) {
  const sorted = [...values].sort((a, b) => a - b);
  const percentile = (p: number) =>
    Number(sorted[Math.ceil(sorted.length * p) - 1].toFixed(2));
  return {
    delivered: sorted.length,
    medianMs: percentile(0.5),
    p95Ms: percentile(0.95),
    maxMs: percentile(1),
  };
}

const email = `evaluation-${randomUUID()}@example.test`;
const password = `Fixture-${randomUUID()}`;
type Sample = {
  clients: number;
  repeats: number;
  projectEvents: ReturnType<typeof stats>;
  collaboration: ReturnType<typeof stats>;
};
const results: Sample[] = [];
let registered = false;
let projectId: number | undefined;
try {
  await command('/auth/register', 'POST', {
    email,
    password,
    displayName: 'Evaluation fixture',
  });
  registered = true;
  const project = await command<Entity>('/projects', 'POST', {
    name: 'Realtime evaluation fixture',
  });
  projectId = project.id;
  const task = await command<Entity>('/tasks', 'POST', {
    projectId: project.id,
    title: 'Shared description fixture',
    status: 'To Do',
  });
  const collab = `/projects/${project.id}/collaboration/TASK/${task.id}`;
  await command(collab, 'POST');
  const { cookies } = await api.storageState();
  const cookie = cookies
    .map(({ name, value }) => `${name}=${value}`)
    .join('; ');
  const csrf = (await (await api.get('/api/auth/csrf')).json()) as Csrf;
  const topics = [`/topic/projects/${project.id}`, `/topic${collab}/updates`];
  const taskCreated = (frame: BinaryStompFrame) =>
    frame.headers.get('subscription') === '0' &&
    Buffer.from(frame.body).includes('"type":"TaskCreated"');
  const create = () =>
    command('/tasks', 'POST', {
      projectId: project.id,
      title: 'Measured project event',
      status: 'To Do',
    });

  for (const size of sizes) {
    const clients: Client[] = [];
    const heartbeat = setInterval(() => {
      for (const { socket } of clients)
        if (socket.readyState === WebSocket.OPEN) socket.send('\n');
    }, 10_000);
    try {
      for (let start = 0; start < size; start += 25) {
        const batch = await Promise.allSettled(
          Array.from({ length: Math.min(size - start, 25) }, () =>
            connect(cookie, csrf, topics),
          ),
        );
        for (const result of batch)
          if (result.status === 'fulfilled') clients.push(result.value);
        assert.ok(
          batch.every((result) => result.status === 'fulfilled'),
          'A subscriber failed to connect.',
        );
      }
      await delay(300);
      await measure(clients, taskCreated, create);
      const projectEvents: number[] = [];
      const collaboration: number[] = [];
      for (let trial = 0; trial < 3; trial++) {
        projectEvents.push(...(await measure(clients, taskCreated, create)));
        const updateId = Buffer.from(randomUUID().replaceAll('-', ''), 'hex');
        const doc = new Y.Doc();
        doc.getText('content').insert(0, 'Evaluation update ');
        const bytes = Y.encodeStateAsUpdate(doc);
        doc.destroy();
        const frame = encodeBinaryFrame(
          'SEND',
          {
            destination: `/app${collab}/updates`,
            'content-type': 'application/octet-stream',
          },
          Buffer.concat([updateId, bytes]),
        );
        const writer = clients[0];
        collaboration.push(
          ...(await measure(
            clients,
            (received) =>
              received.headers.get('subscription') === '1' &&
              Buffer.from(received.body.subarray(8, 24)).equals(updateId),
            async () => {
              writer.socket.send(frame);
            },
          )),
        );
      }
      const result = {
        clients: size,
        repeats: 3,
        projectEvents: stats(projectEvents),
        collaboration: stats(collaboration),
      };
      results.push(result);
      console.log(JSON.stringify(result));
    } finally {
      clearInterval(heartbeat);
      for (const { socket } of clients) socket.terminate();
    }
  }
  await writeFile(
    output,
    JSON.stringify({ replicas: Number(replicas), results }, null, 2) + '\n',
  );
} finally {
  try {
    if (projectId !== undefined)
      await command(`/projects/${projectId}`, 'DELETE');
    if (registered)
      await command('/auth/account', 'DELETE', {
        email,
        currentPassword: password,
      });
  } finally {
    await api.dispose();
  }
}
