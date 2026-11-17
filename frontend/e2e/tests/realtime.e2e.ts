import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

test('updates two browsers, preserves comment drafts and recovers a dropped connection', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}, testInfo) => {
  test.setTimeout(90_000);
  if (!baseURL) throw new Error('The real application base URL is required.');
  const peerContext = await browser.newContext({
    baseURL,
    viewport: { width: 1440, height: 1000 },
    locale: 'en-GB',
    timezoneId: 'Europe/Stockholm',
    reducedMotion: 'reduce',
  });
  // Fault injection closes an actual socket; no API or WebSocket response is mocked.
  await peerContext.addInitScript(() => {
    const NativeSocket = window.WebSocket;
    const sockets = new Set<WebSocket>();
    window.WebSocket = class extends NativeSocket {
      constructor(url: string | URL, protocols?: string | string[]) {
        super(url, protocols);
        sockets.add(this);
        this.addEventListener('close', () => sockets.delete(this));
      }
    };
    window.addEventListener('e2e:drop-realtime', () => {
      sockets.forEach((socket) => socket.close());
    });
    document.addEventListener('securitypolicyviolation', (event) => {
      if (event.sourceFile)
        console.error(`Realtime CSP violation: ${event.effectiveDirective}`);
    });
  });
  const peer = await peerContext.newPage();
  const peerErrors: string[] = [];
  peer.on('pageerror', (error) => peerErrors.push(error.message));
  peer.on('console', (message) => {
    if (message.text().startsWith('Realtime CSP violation:'))
      peerErrors.push(message.text());
  });
  const peerApi = new Api(peerContext.request);
  const teammate = newAccount('realtime-member');
  let registered = false;
  try {
    await register(peerApi, teammate);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: `Live ${account.email.slice(0, 30)}`,
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: teammate.email,
      role: 'Member',
    });
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      title: 'Shared live task',
      status: 'To Do',
      projectId: project.id,
    });
    const gapPositions: number[] = [];
    peer.on('request', (request) => {
      const url = new URL(request.url());
      if (url.pathname === `/api/projects/${project.id}/events`)
        gapPositions.push(Number(url.searchParams.get('after')));
    });
    await page.goto('/tasks');
    await peer.goto('/tasks');
    await expect(page.locator('.presence')).toHaveAttribute(
      'title',
      /E2E realtime-member/,
    );
    await expect(peer.locator('.presence')).toHaveClass(/presence-live/);
    await expectAccessible(page, 'realtime-board-owner');
    await expectAccessible(peer, 'realtime-board-member');

    const peerCard = peer
      .locator('.task-board article')
      .filter({ hasText: 'Shared live task' });
    await peerCard
      .getByRole('combobox', { name: 'Move to' })
      .selectOption('In Progress');
    const ownerCard = page
      .locator('.task-board article')
      .filter({ hasText: 'Shared live task' });
    await expect(
      ownerCard.getByRole('combobox', { name: 'Move to' }),
    ).toHaveValue('In Progress');
    // Ten-second assertion excludes the bell's sixty-second polling fallback.
    await expect(
      page.getByRole('button', { name: /Notifications/ }),
    ).toHaveAccessibleName(/1/);

    await ownerCard
      .getByRole('button', { name: 'Comments', exact: true })
      .click();
    await peerCard
      .getByRole('button', { name: 'Comments', exact: true })
      .click();
    const ownerThread = page.getByRole('dialog', {
      name: 'Comments · Shared live task',
      exact: true,
    });
    const peerThread = peer.getByRole('dialog', {
      name: 'Comments · Shared live task',
      exact: true,
    });
    await expect(page.locator('.presence')).toHaveAttribute(
      'title',
      new RegExp(`task #${task.id}`),
    );
    const draft = ownerThread.getByRole('textbox', { name: 'New comment' });
    await draft.fill('Keep my local draft');
    await peerThread
      .getByRole('textbox', { name: 'New comment' })
      .fill('Comment from the other browser');
    await peerThread
      .getByRole('button', { name: 'Post comment', exact: true })
      .click();
    await expect(
      ownerThread.getByText('Comment from the other browser', { exact: true }),
    ).toBeVisible();
    await expect(draft).toHaveValue('Keep my local draft');
    await peerThread.getByRole('button', { name: 'Edit', exact: true }).click();
    await peerThread
      .getByRole('textbox', { name: 'Edit comment' })
      .fill('Revised live comment');
    await peerThread
      .getByRole('button', { name: 'Save comment', exact: true })
      .click();
    await expect(
      ownerThread.getByText('Revised live comment', { exact: true }),
    ).toBeVisible();
    await expect(
      ownerThread.getByText('Comment from the other browser', { exact: true }),
    ).toHaveCount(0);
    await expect(draft).toHaveValue('Keep my local draft');
    await expectAccessible(page, 'realtime-open-thread');
    await testInfo.attach('realtime-live-comments', {
      body: await page.screenshot(),
      contentType: 'image/png',
    });

    const comments = await peerApi.json<{ id: number }[]>(
      `/tasks/${task.id}/comments`,
    );
    expect(comments).toHaveLength(1);
    await peerApi.json(
      `/tasks/${task.id}/comments/${comments[0]!.id}`,
      'DELETE',
    );
    await expect(
      ownerThread.getByText('Revised live comment', { exact: true }),
    ).toHaveCount(0);
    await expect(draft).toHaveValue('Keep my local draft');
    await draft.fill('');
    await ownerThread
      .getByRole('button', { name: 'Close panel', exact: true })
      .click();
    await peerThread
      .getByRole('button', { name: 'Close panel', exact: true })
      .click();
    await expect(page.locator('.presence')).not.toHaveAttribute(
      'title',
      /task #/,
    );

    await peerContext.setOffline(true);
    await peer.evaluate(() =>
      window.dispatchEvent(new Event('e2e:drop-realtime')),
    );
    await expect(peer.locator('.presence')).toHaveClass(/presence-offline/);
    await api.json(`/tasks/${task.id}`, 'PUT', {
      title: 'Recovered after a dropped connection',
      status: 'In Progress',
      projectId: project.id,
    });
    await expect(
      peer.getByText('Shared live task', { exact: true }),
    ).toBeVisible();
    await peerContext.setOffline(false);
    await expect(peer.locator('.presence')).toHaveClass(/presence-live/);
    await expect(
      peer.getByText('Recovered after a dropped connection', { exact: true }),
    ).toBeVisible();
    expect(gapPositions.some((position) => position > 0)).toBe(true);
    await testInfo.attach('realtime-reconnected', {
      body: await peer.screenshot(),
      contentType: 'image/png',
    });
    expect(peerErrors).toEqual([]);
  } finally {
    await peerContext.setOffline(false);
    if (registered) await removeAccount(peerApi, teammate);
    await peerContext.close();
  }
});
