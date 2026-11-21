import { execFileSync } from 'node:child_process';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

type EventPage = {
  latestSeq: number;
  resync: boolean;
  events: { seq: number; type: string; entityId: number | null }[];
};

test.skip(!process.env.E2E_KIND_CONTEXT, 'Requires the isolated kind cluster');

test('replays every task event after the ingress WebSocket pod is removed', async ({
  page,
  api,
  account,
}, testInfo) => {
  const context = process.env.E2E_KIND_CONTEXT;
  if (context !== 'kind-sprintflow-ci')
    throw new Error('Expected the disposable kind context.');
  const kubectl = (...args: string[]) =>
    execFileSync(
      'kubectl',
      ['--context', context, '-n', 'sprintflow-ci', ...args],
      {
        encoding: 'utf8',
        timeout: 30_000,
      },
    ).trim();

  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: `Kubernetes failover ${account.email.slice(0, 32)}`,
  });
  const createTask = (title: string) =>
    api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      status: 'To Do',
      title,
    });
  const waitForTaskEvent = async (taskId: number, after: number) => {
    await expect
      .poll(async () => {
        const result = await api.json<EventPage>(
          `/projects/${project.id}/events?after=${after}`,
        );
        return result.events.some(
          ({ entityId, type }) => entityId === taskId && type === 'TaskCreated',
        );
      })
      .toBe(true);
  };
  await waitForTaskEvent((await createTask('Before the pod stopped')).id, 0);

  await page.addInitScript(() => {
    const NativeWebSocket = window.WebSocket;
    let pause = false;
    window.addEventListener('e2e:hold-websocket', () => {
      pause = true;
    });
    window.addEventListener('e2e:release-websocket', () => {
      pause = false;
    });
    window.WebSocket = class extends NativeWebSocket {
      constructor(url: string | URL, protocols?: string | string[]) {
        // Keep post-failure reconnects pending until all writes have committed.
        if (pause) throw new Error('The test is holding reconnects.');
        super(url, protocols);
      }
    };
  });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Network.enable');
  const podsBySocket = new Map<string, string>();
  const closedSockets = new Set<string>();
  const wireMessages: string[] = [];
  page.on('websocket', (socket) => {
    socket.on('framereceived', ({ payload }) => {
      const frame = payload.toString().trimStart();
      if (frame.startsWith('MESSAGE')) wireMessages.push(frame);
    });
  });
  cdp.on(
    'Network.webSocketHandshakeResponseReceived',
    ({ requestId, response }) => {
      const header = Object.entries(response.headers).find(
        ([name]) => name.toLowerCase() === 'x-sprintflow-instance',
      );
      if (response.status === 101 && typeof header?.[1] === 'string')
        podsBySocket.set(requestId, header[1]);
    },
  );
  cdp.on('Network.webSocketClosed', ({ requestId }) =>
    closedSockets.add(requestId),
  );
  const path = `/api/projects/${project.id}/events`;
  const findReplay = (after: number) =>
    page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.ok() &&
        url.pathname === path &&
        url.searchParams.get('after') === String(after)
      );
    });
  const initialReplay = findReplay(0);
  await page.goto('/tasks');
  await expect(page.locator('.presence')).toHaveClass(/presence-live/);
  await expect(
    page.getByText('Before the pod stopped', { exact: true }),
  ).toBeVisible();
  const initial = (await (await initialReplay).json()) as EventPage;
  expect(initial.latestSeq).toBeGreaterThan(0);
  await expect.poll(() => podsBySocket.size).toBe(1);
  const handshake = [...podsBySocket.entries()][0];
  if (!handshake)
    throw new Error('No ingress WebSocket handshake was recorded.');
  const [requestId, pod] = handshake;
  expect(pod).toMatch(/^sprintflow-[a-z0-9]+-[a-z0-9]+$/);
  const selected = JSON.parse(kubectl('get', 'pod', pod, '-o', 'json')) as {
    metadata: { labels: Record<string, string> };
  };
  expect(selected.metadata.labels['app.kubernetes.io/instance']).toBe(
    'sprintflow',
  );

  await page.evaluate(() =>
    window.dispatchEvent(new Event('e2e:hold-websocket')),
  );
  kubectl('delete', 'pod', pod, '--grace-period=0', '--force', '--wait=false');
  await expect.poll(() => closedSockets.has(requestId)).toBe(true);
  await expect(page.locator('.presence')).toHaveClass(/presence-offline/);

  const titles = [
    'First task during outage',
    'Second task during outage',
    'Third task during outage',
  ];
  const created: number[] = [];
  for (const title of titles) {
    const task = await createTask(title);
    await waitForTaskEvent(task.id, initial.latestSeq);
    created.push(task.id);
  }
  for (const title of titles)
    await expect(page.getByText(title, { exact: true })).toHaveCount(0);

  const catchup = findReplay(initial.latestSeq);
  await page.evaluate(() =>
    window.dispatchEvent(new Event('e2e:release-websocket')),
  );
  await expect(page.locator('.presence')).toHaveClass(/presence-live/);
  await expect
    .poll(() => [...podsBySocket.values()].some((name) => name !== pod))
    .toBe(true);
  const replay = (await (await catchup).json()) as EventPage;
  expect(replay.resync).toBe(false);
  expect(replay.events.map(({ entityId }) => entityId)).toEqual(created);
  expect(replay.events.map(({ seq }) => seq)).toEqual(
    created.map((_, i) => initial.latestSeq + i + 1),
  );
  for (const title of titles)
    await expect(page.getByText(title, { exact: true })).toHaveCount(1);

  const liveTask = await createTask('Task after reconnect');
  await expect
    .poll(() =>
      wireMessages.some(
        (frame) =>
          frame.includes('"type":"TaskCreated"') &&
          frame.includes(`"entityId":${liveTask.id},`),
      ),
    )
    .toBe(true);
  await expect(
    page.getByText('Task after reconnect', { exact: true }),
  ).toBeVisible();
  await expectAccessible(page, 'kubernetes-recovered-board');
  kubectl('rollout', 'status', 'deployment/sprintflow', '--timeout=25s');
  await testInfo.attach('failover-evidence', {
    body: JSON.stringify({
      removedPod: pod,
      replacementSockets: [...podsBySocket.values()],
      replay,
    }),
    contentType: 'application/json',
  });
});
