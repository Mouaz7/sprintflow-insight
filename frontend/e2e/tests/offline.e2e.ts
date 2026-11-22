import type { Page } from '@playwright/test';
import { expectAccessible } from '../support/a11y.ts';
import { expect, isoDate, test } from '../support/fixtures.ts';

async function prepareOffline(page: Page) {
  await expect
    .poll(() =>
      page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
    )
    .toBe(true);
  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute(
    'href',
    '/manifest.webmanifest',
  );
}

function card(page: Page, title: string) {
  return page.locator('.task-board article').filter({ hasText: title });
}

test('reopens a cached workspace and replays queued writes after conflict review', async ({
  account,
  api,
  page,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: `Offline queue ${account.email}`,
  });
  const create = (title: string) =>
    api.json<{ id: number }>('/tasks', 'POST', {
      title,
      status: 'To Do',
      projectId: project.id,
    });
  const normal = await create('Queue three updates');
  const conflict = await create('Edit on both devices');
  const keep = await create('Keep a newer server change');
  const openComments = () =>
    card(page, 'Queue three updates')
      .getByRole('button', { name: 'Comments', exact: true })
      .click();
  const comments = page.getByRole('dialog', {
    name: 'Comments · Queue three updates',
  });
  await page.goto('/tasks');
  await expect(card(page, 'Queue three updates')).toBeVisible();
  await prepareOffline(page);
  await openComments();
  await expect(
    comments.getByRole('textbox', { name: 'New comment' }),
  ).toBeVisible();
  await comments.getByRole('button', { name: 'Close panel' }).click();
  await page.goto('/time');
  await expect(
    page.getByRole('button', { name: 'Log time', exact: true }),
  ).toBeVisible();
  await page.goto('/tasks');
  await expect(card(page, 'Queue three updates')).toBeVisible();

  const syncStatus = page.locator('.offline-summary').getByRole('status');
  await expect(syncStatus).toBeEmpty();
  const context = page.context();
  await context.setOffline(true);
  try {
    await expect(
      page.getByText(/Offline\. Showing saved workspace data/),
    ).toBeVisible();
    for (const [title, status] of [
      ['Queue three updates', 'Review'],
      ['Edit on both devices', 'Done'],
      ['Keep a newer server change', 'Done'],
    ] as const)
      await card(page, title)
        .getByRole('combobox', { name: 'Move to' })
        .selectOption(status);
    await openComments();
    await comments
      .getByRole('textbox', { name: 'New comment' })
      .fill('Written without a connection');
    await comments.getByRole('button', { name: 'Post comment' }).click();
    await expect(
      comments.getByRole('textbox', { name: 'New comment' }),
    ).toHaveValue('');
    await comments.getByRole('button', { name: 'Close panel' }).click();
    await page.goto('/time');
    await page.getByRole('button', { name: 'Log time', exact: true }).click();
    const form = page.getByRole('dialog', { name: 'Log time' });
    await form
      .getByRole('combobox', { name: 'Task', exact: true })
      .selectOption(String(normal.id));
    await form.getByLabel('Minutes', { exact: true }).fill('45');
    await form.getByLabel('Day worked', { exact: true }).fill(isoDate(0));
    await form
      .getByLabel('Note (optional)', { exact: true })
      .fill('Logged offline');
    await form.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(form).toHaveCount(0);
    await expect(page.getByText(/5 queued changes/)).toBeVisible();
    await page.goto('/tasks');
    await expect(page).toHaveTitle('Tasks · SprintFlow');
    await expect(page.getByText(/5 queued changes/)).toBeVisible();
    await expect(card(page, 'Queue three updates')).toBeVisible();
    await expectAccessible(page, 'offline-cached-workspace');

    // The API client can still reach the real server independently of the offline page.
    for (const [task, title] of [
      [conflict, 'Changed remotely before redo'],
      [keep, 'Changed remotely and kept'],
    ] as const)
      await api.json(`/tasks/${task.id}`, 'PUT', {
        title,
        projectId: project.id,
        status: 'In Progress',
      });
  } finally {
    await context.setOffline(false);
  }

  const first = page
    .locator('.offline-banner li')
    .filter({ hasText: 'Edit on both devices' });
  const second = page
    .locator('.offline-banner li')
    .filter({ hasText: 'Keep a newer server change' });
  await expect(
    first.getByText('This task changed on the server.'),
  ).toBeVisible();
  await expect(
    second.getByText('This task changed on the server.'),
  ).toBeVisible();
  await expect(syncStatus).toContainText('2 changes need your review.');
  await expectAccessible(page, 'offline-conflict-review');
  expect(
    (await api.json<{ status: string }>(`/tasks/${conflict.id}`)).status,
  ).toBe('In Progress');
  await first.getByRole('button', { name: 'Redo my change' }).focus();
  await page.keyboard.press('Enter');
  const keepVersion = second.getByRole('button', {
    name: 'Keep server version',
  });
  await expect(keepVersion).toBeFocused();
  await expect(keepVersion).toBeEnabled();
  await page.keyboard.press('Enter');
  await expect(page.locator('.offline-banner')).toHaveCount(0);
  await expect(page.locator('#workspace-page')).toBeFocused();
  expect(
    (await api.json<{ status: string }>(`/tasks/${normal.id}`)).status,
  ).toBe('Review');
  expect(
    (await api.json<{ status: string }>(`/tasks/${conflict.id}`)).status,
  ).toBe('Done');
  expect((await api.json<{ status: string }>(`/tasks/${keep.id}`)).status).toBe(
    'In Progress',
  );
  const entries = await api.json<{ items: unknown[] }>(
    `/projects/${project.id}/time-entries`,
  );
  expect(entries.items).toMatchObject([
    { note: 'Logged offline', minutes: 45 },
  ]);
  const saved = await api.json<unknown[]>(`/tasks/${normal.id}/comments`);
  expect(saved).toMatchObject([{ message: 'Written without a connection' }]);
});

test('restores an offline shared description and merges it with a concurrent edit', async ({
  account,
  api,
  baseURL,
  browser,
  page,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: `Offline shared text ${account.email}`,
  });
  const task = await api.json<{ id: number }>('/tasks', 'POST', {
    title: 'Shared offline description',
    projectId: project.id,
    status: 'To Do',
    description: 'sprint',
  });
  const edit = (target: Page) =>
    card(target, 'Shared offline description')
      .getByRole('button', { name: 'Co-edit description' })
      .click();
  const description = (target: Page) =>
    target.getByRole('textbox', { name: 'Shared description' });
  const context = page.context();
  await page.goto('/tasks');
  await prepareOffline(page);
  await edit(page);
  await page.getByRole('button', { name: 'Start co-editing' }).click();
  await expect(
    page.getByRole('dialog').getByText('Synced', { exact: true }),
  ).toBeVisible();
  const peer = await browser.newContext({
    baseURL,
    storageState: await context.storageState(),
  });
  const otherPage = await peer.newPage();
  try {
    await context.setOffline(true);
    await description(page).fill('sprint offline');
    await expect(
      page.getByText(/Edits are saved on this device/),
    ).toBeVisible();
    await page.reload();
    await edit(page);
    await expect(description(page)).toHaveValue('sprint offline');
    await otherPage.goto('/tasks');
    await edit(otherPage);
    await expect(
      otherPage.getByRole('dialog').getByText('Synced', { exact: true }),
    ).toBeVisible();
    await description(otherPage).fill('remote sprint');
    const savedDescription = async () =>
      (await api.json<{ description: string }>(`/tasks/${task.id}`))
        .description;
    await expect.poll(savedDescription).toBe('remote sprint');
    await context.setOffline(false);
    await expect(description(page)).toHaveValue('remote sprint offline');
    await expect(description(otherPage)).toHaveValue('remote sprint offline');
    await expect.poll(savedDescription).toBe('remote sprint offline');
  } finally {
    await context.setOffline(false);
    await peer.close();
  }
});
