import { expectAccessible } from '../support/a11y.ts';
import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expect, isoDate, test } from '../support/fixtures.ts';

test('converges in two browsers after delayed edits and a broken connection', async ({
  account,
  api,
  baseURL,
  browser,
  page,
}) => {
  if (!baseURL) throw new Error('The application URL is required.');
  const control = process.env.E2E_TOXIPROXY_API;
  const proxyPort = process.env.E2E_TOXIPROXY_PORT ?? '8089';
  const name = 'sprintflow-shared-text';
  const proxy = async (path: string, method: string, body?: object) => {
    if (!control) return;
    const response = await fetch(`${control}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    expect(
      response.ok,
      `Toxiproxy ${method} ${path}: ${await response.text()}`,
    ).toBe(true);
  };
  const peerUrl = new URL(baseURL);
  if (control) {
    await proxy('/proxies', 'POST', {
      name,
      listen: `127.0.0.1:${proxyPort}`,
      upstream: peerUrl.host,
    });
    peerUrl.port = proxyPort;
  }
  const peerContext = await browser.newContext({ baseURL: peerUrl.href });
  const peer = await peerContext.newPage();
  const failures: string[] = [];
  peer.on('pageerror', (error) => failures.push(error.message));
  const peerApi = new Api(peerContext.request);
  const teammate = newAccount('shared-text-member');
  try {
    await register(peerApi, teammate);
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: `Collaboration ${account.email}`,
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: teammate.email,
      role: 'Member',
    });
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      title: 'Shared task text',
      description: 'sprint',
      status: 'To Do',
    });
    await Promise.all([page.goto('/tasks'), peer.goto('/tasks')]);
    const openDescription = async (target: typeof page) =>
      target
        .locator('.task-board article')
        .filter({ hasText: 'Shared task text' })
        .getByRole('button', { name: 'Co-edit description' })
        .click();
    await openDescription(page);
    await page.getByRole('button', { name: 'Start co-editing' }).click();
    await openDescription(peer);
    const first = page.getByRole('textbox', { name: 'Shared description' });
    const second = peer.getByRole('textbox', { name: 'Shared description' });
    await expect(first).toHaveValue('sprint');
    await expect(second).toHaveValue('sprint');
    await expect(
      page.getByRole('dialog').getByText('Synced', { exact: true }),
    ).toBeVisible();
    await expect(
      peer.getByRole('dialog').getByText('Synced', { exact: true }),
    ).toBeVisible();
    await expectAccessible(page, 'shared-task-editor');

    for (const stream of ['upstream', 'downstream']) {
      await proxy(`/proxies/${name}/toxics`, 'POST', {
        name: `delay-${stream}`,
        type: 'latency',
        stream,
        attributes: { latency: 450, jitter: 0 },
      });
    }
    await Promise.all([
      first.fill('owner sprint'),
      second.fill('sprint member'),
    ]);
    await expect(first).toHaveValue('owner sprint member');
    await expect(second).toHaveValue('owner sprint member');
    await second.press('End');
    await expect(
      page.getByRole('group', { name: 'Writing here' }),
    ).toContainText(teammate.email);

    if (control) {
      await proxy(`/proxies/${name}`, 'POST', { enabled: false });
      await expect(
        peer.getByRole('dialog').getByText(/Connecting/),
      ).toBeVisible();
      await Promise.all([
        first.fill('remote owner sprint member'),
        second.fill('owner sprint member offline'),
      ]);
      await proxy(`/proxies/${name}`, 'POST', { enabled: true });
      await expect(first).toHaveValue('remote owner sprint member offline');
      await expect(second).toHaveValue('remote owner sprint member offline');
    }

    const expected = control
      ? 'remote owner sprint member offline'
      : 'owner sprint member';
    await expect
      .poll(
        async () =>
          (await api.json<{ description: string }>(`/tasks/${task.id}`))
            .description,
      )
      .toBe(expected);
    await peer.reload();
    await openDescription(peer);
    await expect(
      peer.getByRole('textbox', { name: 'Shared description' }),
    ).toHaveValue(expected);
    const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
      projectId: project.id,
      name: 'Collaboration sprint',
      startDate: isoDate(0),
      endDate: isoDate(7),
      status: 'Planned',
    });
    const note = await api.json<{ id: number }>(
      `/projects/${project.id}/retrospective`,
      'POST',
      {
        sprintId: sprint.id,
        category: 'well',
        message: 'First retrospective note',
      },
    );
    const room = `/projects/${project.id}/collaboration/RETROSPECTIVE/${note.id}`;
    const readOnly = await peerApi.json<{ editable: boolean }>(room);
    expect(readOnly.editable).toBe(false);
    expect(await peerApi.status(room, 'POST')).toBe(403);
    await page.goto('/retrospective');
    await page.getByRole('button', { name: 'Co-edit note' }).click();
    await page.getByRole('button', { name: 'Start co-editing' }).click();
    const sharedNote = page.getByRole('textbox', { name: 'Shared note' });
    await expect(sharedNote).toBeEditable();
    await sharedNote.fill('First retrospective note, updated');
    await expect
      .poll(async () => {
        const notes = await api.json<{ id: number; message: string }[]>(
          `/projects/${project.id}/retrospective?sprintId=${sprint.id}`,
        );
        return notes.find((entry) => entry.id === note.id)?.message;
      })
      .toBe('First retrospective note, updated');
    expect(failures).toEqual([]);
  } finally {
    await removeAccount(peerApi, teammate).catch(() => {});
    await peerContext.close();
    await proxy(`/proxies/${name}`, 'DELETE');
  }
});
