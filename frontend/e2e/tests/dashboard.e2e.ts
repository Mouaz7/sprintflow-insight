import type { APIRequestContext } from '@playwright/test';
import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test, isoDate } from '../support/fixtures.ts';
import {
  seedForecastHistory,
  type HistoricalTask,
} from '../support/forecast-history.ts';

type Layout = { version: number; widgets: { type: string; wide: boolean }[] };
async function saveLayout(
  request: APIRequestContext,
  projectId: number,
  widgets: Layout['widgets'],
) {
  const layout = (await (
    await request.get(`/api/projects/${projectId}/dashboard`)
  ).json()) as Layout;
  const csrf = (await (await request.get('/api/auth/csrf')).json()) as {
    headerName: string;
    token: string;
  };
  return request.put(`/api/projects/${projectId}/dashboard`, {
    headers: {
      [csrf.headerName]: csrf.token,
      'If-Match': `"${layout.version}"`,
    },
    data: { widgets },
  });
}
test('loads real project widgets, saves a private ordered layout and preserves a stale draft', async ({
  page,
  api,
  account,
  baseURL,
}, testInfo) => {
  if (!baseURL) throw new Error('Local application URL required.');
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Personal delivery dashboard',
    description: account.email,
    key: 'DB',
  });
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Delivery sprint',
    goal: 'Ship a usable dashboard',
    status: 'Active',
    startDate: isoDate(-2),
    endDate: isoDate(11),
  });
  await api.json('/tasks', 'POST', {
    projectId: project.id,
    sprintId: sprint.id,
    title: 'My dashboard task',
    status: 'In Progress',
    storyPoints: 3,
    assigneeEmail: account.email,
  });
  await api.json('/tasks', 'POST', {
    projectId: project.id,
    sprintId: sprint.id,
    title: 'Unassigned dashboard task',
    status: 'To Do',
    storyPoints: 2,
  });
  const history = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Historical delivery',
    status: 'Completed',
    startDate: isoDate(-63),
    endDate: isoDate(-7),
  });
  const tasks: HistoricalTask[] = [];
  for (let week = 1; week <= 7; week++) {
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      title: `Past delivery ${week}`,
      status: 'To Do',
      storyPoints: 1,
    });
    await api.json(`/tasks/${task.id}`, 'PUT', {
      projectId: project.id,
      title: `Past delivery ${week}`,
      status: 'Done',
      storyPoints: 1,
    });
    tasks.push({
      id: task.id,
      sprintId: history.id,
      createdAt: `${isoDate(-week * 7 - 2)}T08:00:00Z`,
      completedAt: `${isoDate(-week * 7)}T12:00:00Z`,
    });
  }
  seedForecastHistory(baseURL, project.id, account.email, tasks, [
    {
      id: history.id,
      createdAt: `${isoDate(-63)}T00:00:00Z`,
      completedAt: `${isoDate(-7)}T18:00:00Z`,
    },
  ]);
  await page.goto('/dashboard');
  await expect(
    page.getByRole('heading', { level: 1, name: 'Your dashboard' }),
  ).toBeVisible();
  await expect(page.locator('.dashboard-card')).toHaveCount(6);
  await expect(
    page.getByText('Ship a usable dashboard', { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('link', { name: /DB-1 · My dashboard task/ }),
  ).toBeVisible();
  const own = page.getByRole('region', { name: 'My open tasks', exact: true });
  await expect(own.getByText('Unassigned dashboard task')).toHaveCount(0);
  const data = await api.json<{
    completionDates: { confidence: number; date: string | null }[];
  }>(`/projects/${project.id}/forecast`);
  const forecast = page.getByRole('region', {
    name: 'Delivery forecast',
    exact: true,
  });
  for (const row of data.completionDates)
    await expect(
      forecast.getByText(`Confidence ${Math.round(row.confidence * 100)}%`, {
        exact: true,
      }),
    ).toBeVisible();
  await expect(
    page.getByRole('img', { name: 'Cumulative flow', exact: true }),
  ).toBeVisible();
  await expectAccessible(page, 'dashboard-all-widgets');
  await testInfo.attach('dashboard-desktop', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
  await page
    .getByRole('button', { name: 'Customize dashboard', exact: true })
    .click();
  let editor = page.getByRole('dialog', {
    name: 'Customize dashboard',
    exact: true,
  });
  for (let i = 0; i < 4; i++)
    await editor
      .getByRole('button', { name: 'Move up: My open tasks', exact: true })
      .click();
  for (const name of [
    'Sprint burndown',
    'Cumulative flow',
    'Delivery forecast',
  ])
    await editor.getByRole('checkbox', { name, exact: true }).uncheck();
  await editor
    .getByRole('checkbox', { name: 'Wide card', exact: true })
    .first()
    .check();
  await expectAccessible(page, 'dashboard-layout-editor');
  await editor
    .getByRole('button', { name: 'Save dashboard', exact: true })
    .click();
  await expect(editor).toHaveCount(0);
  await expect(page.locator('.dashboard-card').first()).toHaveAttribute(
    'aria-label',
    'My open tasks',
  );
  await expect(page.locator('.dashboard-card').first()).toHaveClass(
    /dashboard-wide/,
  );
  const stored = await api.json<Layout>(`/projects/${project.id}/dashboard`);
  expect(stored.widgets.map((w) => w.type)).toEqual([
    'MY_TASKS',
    'WIP',
    'SPRINT_GOAL',
  ]);
  expect(stored.widgets[0]?.wide).toBe(true);
  await page.reload();
  await expect(page.locator('.dashboard-card')).toHaveCount(3);
  await expect(page.locator('.dashboard-card').first()).toHaveAttribute(
    'aria-label',
    'My open tasks',
  );
  // A second window for the same account saves while the first holds a draft.
  const second = await page.context().newPage();
  await page
    .getByRole('button', { name: 'Customize dashboard', exact: true })
    .click();
  editor = page.getByRole('dialog', { name: 'Customize dashboard' });
  await editor
    .getByRole('checkbox', { name: 'Wide card', exact: true })
    .nth(1)
    .check();
  const changed = await saveLayout(second.request, project.id, [
    { type: 'SPRINT_GOAL', wide: false },
  ]);
  expect(changed.status()).toBe(200);
  await editor
    .getByRole('button', { name: 'Save dashboard', exact: true })
    .click();
  await expect(editor.getByRole('alert')).toContainText(
    'Your draft is still here',
  );
  await expect(
    editor.getByRole('checkbox', { name: 'Wide card', exact: true }).nth(1),
  ).toBeChecked();
  await expect(
    editor.getByRole('button', { name: 'Save dashboard', exact: true }),
  ).toBeDisabled();
  await editor.getByRole('button', { name: 'Cancel', exact: true }).click();
  await second.close();
  const other = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Independent dashboard',
    description: account.email,
    key: 'DI',
  });
  expect(
    (await api.json<Layout>(`/projects/${other.id}/dashboard`)).version,
  ).toBe(0);
});

test('allows a Viewer to personalize only their dashboard and keeps archived layouts read-only on Swedish mobile', async ({
  api,
  account,
  browser,
  baseURL,
}, testInfo) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Dashboard på mobil',
    description: account.email,
    key: 'DM',
  });
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Mobil sprint',
    goal: 'Ett tydligt mål för leverans',
    status: 'Active',
    startDate: isoDate(0),
    endDate: isoDate(13),
  });
  const viewerContext = await browser.newContext({
    baseURL,
    viewport: { width: 360, height: 800 },
    reducedMotion: 'reduce',
  });
  const viewerApi = new Api(viewerContext.request);
  const viewer = newAccount('dashboard-viewer');
  let registered = false;
  try {
    await register(viewerApi, viewer);
    registered = true;
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: viewer.email,
      role: 'Viewer',
    });
    await viewerContext.addInitScript(() => {
      localStorage.setItem('sprintflow-language', 'SV');
      localStorage.setItem('sprintflow-theme', 'dark');
    });
    const watched = await viewerContext.newPage();
    await watched.goto(`/dashboard?sprint=${sprint.id}`);
    await expect(
      watched.getByText('Ett tydligt mål för leverans', { exact: true }),
    ).toBeVisible();
    await watched
      .getByRole('button', { name: 'Anpassa dashboard', exact: true })
      .click();
    const editor = watched.getByRole('dialog', { name: 'Anpassa dashboard' });
    for (const name of [
      'Sprintburndown',
      'Kumulativt flöde',
      'Leveransprognos',
      'Pågående arbete',
      'Mina öppna uppgifter',
    ])
      await editor.getByRole('checkbox', { name, exact: true }).uncheck();
    await expectAccessible(watched, 'dashboard-viewer-editor-mobile');
    await editor
      .getByRole('button', { name: 'Spara dashboard', exact: true })
      .click();
    await expect(editor).toHaveCount(0);
    await expect(watched.locator('.dashboard-card')).toHaveCount(1);
    expect(
      (await api.json<Layout>(`/projects/${project.id}/dashboard`)).widgets,
    ).toHaveLength(6);
    await watched.reload();
    await expect(watched.locator('.dashboard-card')).toHaveCount(1);
    await expectAccessible(watched, 'dashboard-viewer-swedish-mobile');
    expect(
      await watched.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await api.json(`/projects/${project.id}/archive`, 'PUT', {
      archived: true,
    });
    await watched.reload();
    await expect(
      watched.getByText('Ett tydligt mål för leverans', { exact: true }),
    ).toBeVisible();
    await expect(
      watched.getByRole('button', { name: 'Anpassa dashboard', exact: true }),
    ).toHaveCount(0);
    expect(
      (await saveLayout(viewerContext.request, project.id, [])).status(),
    ).toBe(409);
    await expectAccessible(watched, 'dashboard-archived-mobile');
    await testInfo.attach('dashboard-mobile-dark', {
      body: await watched.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
  } finally {
    if (registered) await removeAccount(viewerApi, viewer);
    await viewerContext.close();
  }
});
