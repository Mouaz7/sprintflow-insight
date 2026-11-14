import { pdfFixture } from './fixtures.ts';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile, unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

interface SmokeState {
  email: string;
  password: string;
  project?: number;
  task?: number;
  attachment?: number;
  storyOrder?: number[];
}
interface Entity {
  id: number;
  version: number;
}
interface Csrf {
  headerName: string;
  token: string;
}
interface Metrics {
  completedSprints: { actualStoryPoints: number }[];
}

const args = process.argv.slice(2);
const phase = args[0];
assert.ok(phase === 'prepare' || phase === 'verify', 'Use prepare or verify.');
function option(name: string, fallback: string): string {
  const index = args.indexOf(name);
  if (index === -1) return fallback;
  assert.ok(args[index + 1], `Missing value for ${name}`);
  return args[index + 1];
}
const base = option('--url', 'http://127.0.0.1:8081');
const statePath = option('--state', join(tmpdir(), 'sprintflow-smoke.json'));
const cookies = new Map<string, string>();
const pdf = pdfFixture();

async function call<T>(
  path: string,
  method = 'GET',
  data?: Record<string, unknown> | FormData,
  version?: number,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    const csrf = await call<Csrf>('/api/auth/csrf');
    headers[csrf.headerName] = csrf.token;
  }
  if (version !== undefined) headers['If-Match'] = `"${version}"`;
  if (cookies.size)
    headers.Cookie = [...cookies]
      .map(([name, value]) => `${name}=${value}`)
      .join('; ');
  let body: string | FormData | undefined;
  if (data instanceof FormData) body = data;
  else if (data !== undefined) {
    body = JSON.stringify(data);
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(base + path, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(30000),
  });
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0];
    const separator = pair.indexOf('=');
    const name = pair.slice(0, separator);
    const value = pair.slice(separator + 1);
    if (/max-age=0(?:;|$)/i.test(cookie)) cookies.delete(name);
    else cookies.set(name, value);
  }
  assert.ok(response.ok, `${method} ${path}: HTTP ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  return (
    bytes.length && response.headers.get('content-type')?.includes('json')
      ? JSON.parse(bytes.toString('utf8'))
      : bytes
  ) as T;
}
async function save(state: SmokeState): Promise<void> {
  // Contains only this check's isolated account; keep it private even on shared hosts.
  await writeFile(statePath, JSON.stringify(state), {
    encoding: 'utf8',
    mode: 0o600,
  });
}

assert.deepEqual(await call('/actuator/health'), { status: 'UP' });
assert.match((await call<Buffer>('/')).toString().toLowerCase(), /<html/);
if (phase === 'prepare') {
  const state: SmokeState = {
    email: `docker-smoke-${randomUUID()}@example.com`,
    password: 'Docker-smoke-12345',
  };
  await save(state);
  await call('/api/auth/register', 'POST', {
    ...state,
    displayName: 'Container QA',
  });
  const project = await call<Entity>('/api/projects', 'POST', {
    name: `Isolated Docker QA ${randomUUID()}`,
  });
  state.project = project.id;
  await save(state);
  const stories: Entity[] = [];
  for (const title of ['First smoke story', 'Second smoke story'])
    stories.push(
      await call<Entity>('/api/stories', 'POST', {
        projectId: project.id,
        title,
        acceptanceCriteria: 'Persists after restart',
        storyPoints: 3,
        priorityId: 2,
      }),
    );
  const ordered = await call<Entity[]>(
    `/api/stories/project/${project.id}/order`,
    'PUT',
    {
      items: [...stories].reverse().map(({ id, version }) => ({ id, version })),
    },
  );
  state.storyOrder = ordered.map(({ id }) => id);
  assert.deepEqual(state.storyOrder, stories.map(({ id }) => id).reverse());
  const today = new Date();
  const end = new Date(today.getTime() + 14 * 86400000);
  const sprint = await call<Entity>('/api/sprints', 'POST', {
    name: 'Container sprint',
    status: 'Active',
    projectId: project.id,
    totalStoryPoints: 5,
    startDate: today.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  });
  const task = await call<Entity>('/api/tasks', 'POST', {
    title: 'Persistent attachment task',
    labels: ['Smoke'],
    assigneeEmail: state.email,
    dueAt: end.toISOString(),
    status: 'Done',
    projectId: project.id,
    sprintId: sprint.id,
    storyPoints: 5,
  });
  state.task = task.id;
  const exportQuery = new URLSearchParams({
    status: 'Done',
    sprintId: String(sprint.id),
    label: 'Smoke',
    due: 'has-date',
    q: 'Persistent',
    assignee: state.email,
  });
  const csv = (
    await call<Buffer>(`/api/projects/${project.id}/export.csv?${exportQuery}`)
  ).toString();
  assert.match(csv, /Persistent attachment task/);
  assert.match(csv, /Filters,/);
  assert.match(csv, /label=Smoke/);
  const upload = new FormData();
  upload.append(
    'file',
    new Blob([pdf], { type: 'application/pdf' }),
    'smoke.pdf',
  );
  const attachment = await call<Entity>(
    `/api/tasks/${task.id}/attachments/upload`,
    'POST',
    upload,
  );
  state.attachment = attachment.id;
  await call(
    `/api/sprints/${sprint.id}/status?status=Completed`,
    'PUT',
    undefined,
    sprint.version,
  );
  await save(state);
  console.log(
    'PASS: health, static frontend, registration, project/sprint/task creation, versioned backlog order, filtered CSV, file upload.',
  );
} else {
  const state: SmokeState = JSON.parse(await readFile(statePath, 'utf8'));
  assert.ok(
    state.project && state.task && state.attachment,
    'Run prepare successfully before verify.',
  );
  await call('/api/auth/login', 'POST', {
    email: state.email,
    password: state.password,
  });
  assert.ok(
    (await call<Entity[]>('/api/projects')).some(
      (project) => project.id === state.project,
    ),
  );
  assert.deepEqual(
    await call<Buffer>(
      `/api/tasks/${state.task}/attachments/${state.attachment}/file`,
    ),
    pdf,
  );
  assert.deepEqual(
    (await call<Entity[]>(`/api/stories/project/${state.project}`)).map(
      ({ id }) => id,
    ),
    state.storyOrder,
  );
  assert.match(
    (
      await call<Buffer>(
        `/api/projects/${state.project}/export.csv?status=Done&label=Smoke&q=Persistent`,
      )
    ).toString(),
    /Persistent attachment task/,
  );
  const metrics = await call<Metrics>(`/api/projects/${state.project}/metrics`);
  assert.ok(
    metrics.completedSprints.some((sprint) => sprint.actualStoryPoints === 5),
  );
  await call(`/api/projects/${state.project}`, 'DELETE');
  await call('/api/auth/account', 'DELETE', {
    email: state.email,
    currentPassword: state.password,
  });
  await unlink(statePath);
  console.log(
    'PASS: login, persisted project/upload/sprint history/backlog order and filtered export after restart, cascade delete, account cleanup.',
  );
}
