import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

test('analyses real history, keeps WIP advisory and reads the same report on a Swedish mobile screen', async ({
  page,
  api,
  account,
}, testInfo) => {
  test.setTimeout(90_000);
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Flow delivery',
    description: account.email,
    key: 'FL',
  });
  for (let i = 0; i < 8; i++) {
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      title: `Flow task ${i + 1}`,
      status: 'To Do',
      storyPoints: 1,
    });
    await api.json(`/tasks/${task.id}`, 'PUT', {
      title: `Flow task ${i + 1}`,
      projectId: project.id,
      storyPoints: 1,
      status: 'In Progress',
    });
    if (i < 6) {
      for (const status of ['Review', 'Done']) {
        await api.json(`/tasks/${task.id}`, 'PUT', {
          title: `Flow task ${i + 1}`,
          projectId: project.id,
          storyPoints: 1,
          status,
        });
      }
    }
  }
  await page.goto('/flow');
  await expect(
    page.getByRole('heading', { name: 'Flow analysis', exact: true }),
  ).toBeVisible();
  await expect(page.locator('.flow-statistics').first()).toContainText('6');
  await page
    .getByRole('combobox', { name: 'History window', exact: true })
    .selectOption('7');
  await expect(page).toHaveURL(/days=7/);
  await page.reload();
  await expect(
    page.getByRole('combobox', { name: 'History window', exact: true }),
  ).toHaveValue('7');
  await expect(
    page.getByRole('heading', { name: 'Cycle-time XmR chart', exact: true }),
  ).toBeVisible();
  const chart = page.getByRole('figure').filter({
    has: page.locator('figcaption', { hasText: 'Cumulative flow' }),
  });
  await chart.getByText('View data as a table', { exact: true }).click();
  await expect(chart.getByRole('table')).toBeVisible();
  await expect(chart.getByRole('row')).toHaveCount(8);
  const explorer = chart.getByRole('slider');
  await explorer.focus();
  await explorer.press('End');
  await expect(explorer).toHaveValue('6');
  await explorer.press('Home');
  await expect(explorer).toHaveValue('0');
  await expectAccessible(page, 'flow-history-light');
  await page
    .getByRole('button', { name: 'Set WIP limits', exact: true })
    .click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('In Progress', { exact: true }).fill('1');
  await expectAccessible(page, 'flow-wip-editor');
  await dialog
    .getByRole('button', { name: 'Save limits', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.flow-column-over')).toContainText('In Progress');
  await expect(page.locator('.flow-column-over')).toContainText(
    'Over the WIP limit',
  );
  const tasks = await api.json<{ id: number; key: string; status: string }[]>(
    `/tasks?projectId=${project.id}`,
  );
  const active = tasks.find((task) => task.status === 'In Progress');
  if (!active) throw new Error('Expected active work.');
  await page.goto(`/tasks?task=${active.key}`);
  await expect(
    page.getByText('WIP: 2 / 1 · Over the WIP limit', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('.task-board article')).toHaveCount(1);
  // The advisory limit must never prevent an authorised task transition.
  await page
    .locator('.task-board article')
    .getByRole('combobox', { name: 'Move to' })
    .selectOption('Review');
  await expect(page.getByText('WIP: 1 / 1', { exact: true })).toBeVisible();
  await page.goto('/flow?days=7');
  await expect(
    page.getByRole('heading', { name: 'Cycle-time XmR chart', exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath('flow-desktop.png'),
    fullPage: true,
  });
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await page
    .getByRole('combobox', { name: 'Language', exact: true })
    .selectOption('SV');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole('heading', { name: 'Flödesanalys', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('combobox', { name: 'Historikperiod', exact: true }),
  ).toHaveValue('7');
  await expectAccessible(page, 'flow-mobile-dark');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath('flow-mobile-dark.png'),
    fullPage: true,
  });
});

test('preserves a stale administrator draft and makes archived and viewer settings read-only', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}) => {
  test.setTimeout(90_000);
  const context = await browser.newContext({ baseURL });
  const teammate = newAccount('flow-admin');
  const peerApi = new Api(context.request);
  let registered = false;
  try {
    await register(peerApi, teammate);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Concurrent flow settings',
      description: account.email,
    });
    const members = `/projects/${project.id}/members`;
    await api.json(members, 'POST', { email: teammate.email, role: 'Admin' });
    const peer = await context.newPage();
    await page.goto('/flow');
    await peer.goto('/flow');
    await page
      .getByRole('button', { name: 'Set WIP limits', exact: true })
      .click();
    await peer
      .getByRole('button', { name: 'Set WIP limits', exact: true })
      .click();
    const ownerPanel = page.getByRole('dialog'),
      peerPanel = peer.getByRole('dialog');
    await ownerPanel.getByLabel('In Progress', { exact: true }).fill('3');
    await peerPanel.getByLabel('In Progress', { exact: true }).fill('5');
    await ownerPanel
      .getByRole('button', { name: 'Save limits', exact: true })
      .click();
    await expect(ownerPanel).toHaveCount(0);
    const stale = peer.waitForResponse(
      (response) =>
        response.url().endsWith('/wip-limits') &&
        response.request().method() === 'PUT',
    );
    await peerPanel
      .getByRole('button', { name: 'Save limits', exact: true })
      .click();
    expect((await stale).status()).toBe(412);
    await expect(peerPanel.getByRole('alert')).toContainText(
      'Your draft is kept',
    );
    await expect(
      peerPanel.getByLabel('In Progress', { exact: true }),
    ).toHaveValue('5');
    const saved = await api.json<{
      limits: { status: string; limit: number | null }[];
    }>(`/projects/${project.id}/wip-limits`);
    expect(
      saved.limits.find((item) => item.status === 'In Progress')?.limit,
    ).toBe(3);
    await expectAccessible(peer, 'flow-stale-draft');
    await api.json(members, 'POST', { email: teammate.email, role: 'Viewer' });
    await peer.reload();
    await expect(
      peer.getByRole('heading', { name: 'Flow analysis', exact: true }),
    ).toBeVisible();
    await expect(
      peer.getByText(/WIP changes need an owner or admin/),
    ).toBeVisible();
    await expect(
      peer.getByRole('button', { name: 'Set WIP limits', exact: true }),
    ).toHaveCount(0);
    await api.json(`/projects/${project.id}/archive`, 'PUT', {
      archived: true,
    });
    await page.reload();
    await expect(
      page.getByText(/WIP changes need an owner or admin/),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Set WIP limits', exact: true }),
    ).toHaveCount(0);
    await expectAccessible(page, 'flow-archived');
  } finally {
    if (registered) await removeAccount(peerApi, teammate);
    await context.close();
  }
});
