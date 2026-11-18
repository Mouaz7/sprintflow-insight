import type { Locator } from '@playwright/test';
import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test, openView } from '../support/fixtures.ts';

async function choose(dialog: Locator, name: string, id: number) {
  await dialog
    .getByRole('combobox', { name, exact: true })
    .selectOption(String(id));
}

test('plans epics, rejects dependency cycles and updates blocked work and PERT in the real app', async ({
  page,
  api,
  account,
}, testInfo) => {
  test.setTimeout(120_000);
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Roadmap release',
    description: account.email,
    key: 'RM',
  });
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    name: 'Delivery sprint',
    projectId: project.id,
    status: 'Active',
    totalStoryPoints: 8,
    startDate: '2026-11-17',
    endDate: '2026-12-15',
  });
  const base = `/projects/${project.id}`;
  const foundation = await api.json<{ id: number }>('/stories', 'POST', {
    title: 'Foundation story',
    projectId: project.id,
    acceptanceCriteria: 'Ready',
    storyPoints: 3,
    priorityId: 2,
  });
  const delivery = await api.json<{ id: number }>('/stories', 'POST', {
    title: 'Delivery story',
    projectId: project.id,
    acceptanceCriteria: 'Released',
    storyPoints: 5,
    priorityId: 2,
    sprintId: sprint.id,
  });
  const first = await api.json<{ id: number; key: string }>('/tasks', 'POST', {
    projectId: project.id,
    storyId: foundation.id,
    title: 'Build foundation',
    status: 'To Do',
    storyPoints: 3,
  });
  const last = await api.json<{ id: number; key: string }>('/tasks', 'POST', {
    projectId: project.id,
    storyId: delivery.id,
    sprintId: sprint.id,
    title: 'Ship customer portal',
    status: 'To Do',
    storyPoints: 5,
  });
  await page.goto('/roadmap');
  await page.getByRole('button', { name: 'New epic', exact: true }).click();
  let dialog = page.getByRole('dialog');
  await dialog
    .getByLabel('Epic title', { exact: true })
    .fill('Platform foundation');
  await dialog.getByLabel('Start date', { exact: true }).fill('2026-11-17');
  await dialog.getByLabel('Target date', { exact: true }).fill('2026-12-01');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'New epic', exact: true }).click();
  dialog = page.getByRole('dialog');
  await dialog
    .getByLabel('Epic title', { exact: true })
    .fill('Customer portal');
  await dialog
    .getByLabel('Description', { exact: true })
    .fill('A release with accessible planning.');
  await dialog.getByLabel('Start date', { exact: true }).fill('2026-12-01');
  await dialog.getByLabel('Target date', { exact: true }).fill('2026-12-15');
  await expectAccessible(page, 'roadmap-epic-editor');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const epics = await api.json<{ id: number; title: string }[]>(
    `${base}/epics`,
  );
  const platform = epics.find((epic) => epic.title === 'Platform foundation');
  const portal = epics.find((epic) => epic.title === 'Customer portal');
  if (!platform || !portal)
    throw new Error('Both epics must have been created.');
  for (const [story, epic] of [
    [foundation, platform],
    [delivery, portal],
  ] as const) {
    await page.getByRole('button', { name: 'Story epic', exact: true }).click();
    dialog = page.getByRole('dialog');
    await choose(dialog, 'Choose a story', story.id);
    await choose(dialog, 'Epic', epic.id);
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
  }
  async function dependency(blocker: number, blocked: number) {
    await page
      .getByRole('button', { name: 'Add dependency', exact: true })
      .click();
    const panel = page.getByRole('dialog');
    await choose(panel, 'Must finish first', blocker);
    await choose(panel, 'Waiting task', blocked);
    await panel.getByRole('button', { name: 'Save', exact: true }).click();
    return panel;
  }
  await expect(await dependency(first.id, last.id)).toHaveCount(0);
  const cycle = await dependency(last.id, first.id);
  await expect(cycle.getByRole('alert')).toContainText('dependency cycle');
  await expect(cycle.getByRole('alert')).toContainText(first.key);
  await expect(
    cycle.getByRole('combobox', { name: 'Waiting task', exact: true }),
  ).toHaveValue(String(first.id));
  await cycle.getByRole('button', { name: 'Close panel' }).click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm', exact: true })
    .click();
  for (const task of [first, last]) {
    await page
      .getByRole('button', { name: 'Estimate a task', exact: true })
      .click();
    dialog = page.getByRole('dialog');
    await choose(dialog, 'Choose a task', task.id);
    await dialog.getByLabel('Optimistic days', { exact: true }).fill('1');
    await dialog.getByLabel('Most likely days', { exact: true }).fill('2');
    await dialog.getByLabel('Pessimistic days', { exact: true }).fill('3');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
  }
  await page.getByLabel('Focus on an epic').selectOption(String(portal.id));
  await page.reload();
  await expect(page.getByLabel('Focus on an epic')).toHaveValue(
    String(portal.id),
  );
  await expect(
    page.getByText('2 of 2 remaining tasks estimated', { exact: true }),
  ).toBeVisible();
  await page.getByText('Estimated critical path (2)', { exact: true }).click();
  await expect(page.locator('.roadmap-critical-path li')).toHaveCount(2);
  await expect(
    page.getByRole('heading', { name: 'Outside the active sprint' }),
  ).toBeVisible();
  expect(await page.locator('.roadmap-timeline path[marker-end]').count()).toBe(
    1,
  );
  await page
    .getByText('Dates and progress as a table', { exact: true })
    .click();
  await expect(page.getByRole('table')).toContainText('Delivery sprint');
  await expectAccessible(page, 'roadmap-complete-light');
  await page.screenshot({
    path: testInfo.outputPath('roadmap-complete.png'),
    fullPage: true,
  });
  await page
    .getByRole('button', { name: `Remove estimate: ${last.key}`, exact: true })
    .click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm', exact: true })
    .click();
  await expect(
    page.getByText('1 of 2 remaining tasks estimated', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText(/Break down the stories and estimate every remaining task/),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Estimate a task', exact: true })
    .click();
  dialog = page.getByRole('dialog');
  await choose(dialog, 'Choose a task', last.id);
  await dialog.getByLabel('Optimistic days', { exact: true }).fill('1');
  await dialog.getByLabel('Most likely days', { exact: true }).fill('2');
  await dialog.getByLabel('Pessimistic days', { exact: true }).fill('3');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const restored = await api.json<{ taskId: number; version: number }[]>(
    `${base}/estimates`,
  );
  expect(
    restored.find((estimate) => estimate.taskId === last.id)?.version,
  ).toBe(2);
  await expect(
    page.getByText('2 of 2 remaining tasks estimated', { exact: true }),
  ).toBeVisible();
  await openView(page, 'Tasks');
  const waiting = page
    .locator('.task-board article')
    .filter({ hasText: last.key });
  await expect(waiting.getByText('WAITING ON DEPENDENCIES')).toBeVisible();
  const blocker = page
    .locator('.task-board article')
    .filter({ hasText: first.key });
  await blocker.getByRole('combobox', { name: 'Move to' }).selectOption('Done');
  await expect(waiting.getByText('WAITING ON DEPENDENCIES')).toHaveCount(0);
  await openView(page, 'Roadmap');
  await page.getByLabel('Focus on an epic').selectOption(String(platform.id));
  await expect(page.locator('.roadmap-epics progress')).toHaveAttribute(
    'value',
    '1',
  );
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await page
    .getByRole('combobox', { name: 'Language', exact: true })
    .selectOption('SV');
  await expect(
    page.getByRole('heading', { name: 'Färdplan', exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page
    .getByText('Datum och framsteg som tabell', { exact: true })
    .click();
  await expect(page.getByRole('table')).toBeVisible();
  await expectAccessible(page, 'roadmap-mobile-dark');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath('roadmap-mobile-dark.png'),
    fullPage: true,
  });
});

test('keeps the roadmap readable for a viewer and an archived project', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}) => {
  const context = await browser.newContext({ baseURL });
  const viewer = newAccount('roadmap-viewer');
  const viewerApi = new Api(context.request);
  let registered = false;
  try {
    await register(viewerApi, viewer);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Read-only roadmap',
      description: account.email,
    });
    await api.json(`/projects/${project.id}/epics`, 'POST', {
      title: 'Visible release',
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: viewer.email,
      role: 'Viewer',
    });
    const viewerPage = await context.newPage();
    await viewerPage.goto('/roadmap');
    await expect(
      viewerPage.getByRole('heading', { name: 'Visible release', exact: true }),
    ).toBeVisible();
    await expect(
      viewerPage.getByRole('button', { name: 'New epic', exact: true }),
    ).toHaveCount(0);
    await expect(
      viewerPage.getByRole('button', { name: 'Delete epic', exact: true }),
    ).toHaveCount(0);
    expect(
      await viewerApi.status(`/projects/${project.id}/epics`, 'POST', {
        title: 'Forbidden',
      }),
    ).toBe(403);
    await expectAccessible(viewerPage, 'roadmap-viewer');
    await api.json(`/projects/${project.id}/archive`, 'PUT', {
      archived: true,
    });
    await page.goto('/roadmap');
    await expect(
      page.getByRole('heading', { name: 'Visible release', exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'New epic', exact: true }),
    ).toHaveCount(0);
    await expect(page.getByText(/You can explore this roadmap/)).toBeVisible();
    await expectAccessible(page, 'roadmap-archived');
  } finally {
    if (registered) await removeAccount(viewerApi, viewer);
    await context.close();
  }
});
