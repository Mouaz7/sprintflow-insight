import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import {
  seedForecastHistory,
  type HistoricalTask,
  type HistoricalSprint,
} from '../support/forecast-history.ts';
import { expect, test, isoDate } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

test('forecasts real project and epic work from historical throughput and sprint velocity', async ({
  page,
  api,
  account,
  baseURL,
}, testInfo) => {
  test.setTimeout(120_000);
  if (!baseURL) throw new Error('A local application URL is required.');
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Forecast delivery',
    description: account.email,
    key: 'FC',
  });
  const current = new Date();
  current.setUTCHours(0, 0, 0, 0);
  current.setUTCDate(current.getUTCDate() - ((current.getUTCDay() + 6) % 7));
  const first = current.getTime() - 8 * 7 * 86400000;
  const taskRows: HistoricalTask[] = [],
    sprintRows: HistoricalSprint[] = [];
  const counts = [1, 2, 1, 3, 1, 4, 2, 3];
  for (let period = 0; period < 4; period++) {
    const start = first + period * 14 * 86400000,
      end = start + 13 * 86400000;
    const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
      projectId: project.id,
      name: `Historical sprint ${period + 1}`,
      goal: 'Recorded delivery',
      status: 'Completed',
      totalStoryPoints: 10,
      startDate: new Date(start).toISOString().slice(0, 10),
      endDate: new Date(end).toISOString().slice(0, 10),
    });
    sprintRows.push({
      id: sprint.id,
      createdAt: new Date(start).toISOString(),
      completedAt: new Date(end + 18 * 3600000).toISOString(),
    });
    for (let week = 0; week < 2; week++)
      for (let n = 0; n < (counts[period * 2 + week] ?? 0); n++) {
        const title = `Historical task ${period}-${week}-${n}`;
        const task = await api.json<{ id: number }>('/tasks', 'POST', {
          projectId: project.id,
          title,
          status: 'To Do',
          storyPoints: 1,
        });
        await api.json(`/tasks/${task.id}`, 'PUT', {
          projectId: project.id,
          title,
          status: 'Done',
          storyPoints: 1,
        });
        const created = start + week * 7 * 86400000;
        taskRows.push({
          id: task.id,
          sprintId: sprint.id,
          createdAt: new Date(created).toISOString(),
          completedAt: new Date(
            created + 2 * 86400000 + 12 * 3600000,
          ).toISOString(),
        });
      }
  }
  const epic = await api.json<{ id: number }>(
    `/projects/${project.id}/epics`,
    'POST',
    { title: 'Customer release' },
  );
  const active = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Current sprint',
    goal: 'Ship the release',
    status: 'Active',
    totalStoryPoints: 6,
    startDate: isoDate(0),
    endDate: isoDate(14),
  });
  const story = await api.json<{ id: number }>('/stories', 'POST', {
    projectId: project.id,
    title: 'Release work',
    sprintId: active.id,
    acceptanceCriteria: 'Ready',
    storyPoints: 8,
    priorityId: 2,
  });
  await api.json(`/projects/${project.id}/stories/${story.id}/epic`, 'PUT', {
    epicId: epic.id,
  });
  const unplanned = await api.json<{ id: number }>('/stories', 'POST', {
    projectId: project.id,
    title: 'Work not yet split',
    acceptanceCriteria: 'Ready',
    storyPoints: 3,
    priorityId: 2,
  });
  await api.json(
    `/projects/${project.id}/stories/${unplanned.id}/epic`,
    'PUT',
    { epicId: epic.id },
  );
  for (let n = 0; n < 10; n++)
    await api.json('/tasks', 'POST', {
      projectId: project.id,
      title: `Remaining task ${n + 1}`,
      status: 'To Do',
      storyPoints: 2,
      ...(n < 3 ? { storyId: story.id, sprintId: active.id } : {}),
    });
  seedForecastHistory(baseURL, project.id, account.email, taskRows, sprintRows);
  await page.goto('/forecast');
  await expect(
    page.getByRole('heading', {
      name: 'When will the remaining work be done?',
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', {
      name: 'Selected work in the active sprint',
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole('combobox', { name: 'Work scope', exact: true })
    .selectOption(String(epic.id));
  const selected = page.waitForResponse(
    (response) =>
      response.url().includes(`/projects/${project.id}/forecast?`) &&
      response.request().method() === 'GET',
  );
  await page
    .getByRole('button', { name: 'Update forecast', exact: true })
    .click();
  const scoped = await (await selected).json();
  expect(scoped.remaining).toBe(3);
  expect(scoped.history).toHaveLength(8);
  expect(scoped.scope.epicId).toBe(epic.id);
  expect(scoped.simulations).toBe(10000);
  expect(
    scoped.completionHistogram.reduce(
      (sum: number, row: { count: number }) => sum + row.count,
      0,
    ) + scoped.beyondHorizon,
  ).toBe(10000);
  await expect(
    page.getByText(/This epic uses the whole project/),
  ).toBeVisible();
  await expect(page.getByText(/1 story has no tasks/)).toBeVisible();
  await page
    .getByRole('spinbutton', { name: 'Remaining work (optional)', exact: true })
    .fill('10');
  const deadline = page.getByRole('textbox', {
    name: 'Target date (optional)',
    exact: true,
  });
  await deadline.fill(isoDate(-1));
  await page
    .getByRole('button', { name: 'Update forecast', exact: true })
    .click();
  await expect(deadline).toBeFocused();
  expect(
    await deadline.evaluate(
      (input: HTMLInputElement) => input.validationMessage,
    ),
  ).not.toBe('');
  await expect(page).not.toHaveURL(/targetDate=/);
  await page
    .getByRole('textbox', { name: 'Target date (optional)', exact: true })
    .fill(isoDate(28));
  expect(
    await deadline.evaluate((input: HTMLInputElement) => input.validity.valid),
  ).toBe(true);
  await page
    .getByRole('spinbutton', { name: 'Target amount (optional)', exact: true })
    .fill('8');
  await page
    .getByRole('button', { name: 'Update forecast', exact: true })
    .click();
  await expect(page).toHaveURL(/remaining=10/);
  await page.reload();
  await expect(
    page.getByRole('spinbutton', {
      name: 'Remaining work (optional)',
      exact: true,
    }),
  ).toHaveValue('10');
  const chart = page.getByRole('figure').filter({
    has: page.locator('figcaption', {
      hasText: 'Simulated days to completion',
    }),
  });
  await chart.getByText('View data as a table', { exact: true }).click();
  await expect(chart.getByRole('table')).toBeVisible();
  const slider = chart.getByRole('slider');
  await slider.focus();
  await slider.press('End');
  await expect(slider).toHaveValue((await slider.getAttribute('max')) ?? '0');
  await slider.press('Home');
  await expect(slider).toHaveValue('0');
  await expectAccessible(page, 'forecast-throughput-desktop');
  await page.screenshot({
    path: testInfo.outputPath('forecast-desktop.png'),
    fullPage: true,
  });
  await page
    .getByRole('combobox', { name: 'History basis', exact: true })
    .selectOption('VELOCITY');
  await expect(
    page.getByRole('spinbutton', {
      name: 'Remaining work (optional)',
      exact: true,
    }),
  ).toHaveValue('');
  const velocity = page.waitForResponse(
    (response) =>
      response.url().includes('/forecast?') &&
      response.url().includes('basis=VELOCITY'),
  );
  await page
    .getByRole('button', { name: 'Update forecast', exact: true })
    .click();
  const points = await (await velocity).json();
  expect(points.unit).toBe('STORY_POINTS');
  expect(points.remaining).toBe(6);
  expect(points.history).toHaveLength(4);
  await expect(page.getByText(/Fewer than 6 complete periods/)).toBeVisible();
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await page
    .getByRole('combobox', { name: 'Language', exact: true })
    .selectOption('SV');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole('heading', { name: 'Prognoser', exact: true }),
  ).toBeVisible();
  await expectAccessible(page, 'forecast-swedish-mobile-dark');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath('forecast-mobile-dark.png'),
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page
    .getByRole('combobox', { name: 'Språk', exact: true })
    .selectOption('EN');
  const empty = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'A second forecast project',
  });
  await page.reload();
  await page
    .getByRole('combobox', { name: 'Project', exact: true })
    .selectOption(String(empty.id));
  await expect(page).toHaveURL(/\/forecast$/);
  await expect(
    page.getByRole('heading', {
      name: 'More delivery history is needed',
      exact: true,
    }),
  ).toBeVisible();
});

test('lets a viewer explore forecasts and reads archived work without writing records', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}) => {
  const context = await browser.newContext({ baseURL });
  const viewer = newAccount('forecast-viewer');
  const peerApi = new Api(context.request);
  let registered = false;
  try {
    await register(peerApi, viewer);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Read-only forecasts',
      description: account.email,
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: viewer.email,
      role: 'Viewer',
    });
    const peer = await context.newPage();
    const writes: string[] = [];
    peer.on('request', (request) => {
      if (
        request.url().includes(`/projects/${project.id}/forecast`) &&
        request.method() !== 'GET'
      )
        writes.push(request.method());
    });
    await peer.goto('/forecast');
    await expect(
      peer.getByRole('heading', { name: 'More delivery history is needed' }),
    ).toBeVisible();
    await peer
      .getByRole('spinbutton', {
        name: 'Remaining work (optional)',
        exact: true,
      })
      .fill('10');
    await peer
      .getByRole('button', { name: 'Update forecast', exact: true })
      .click();
    await expect(peer).toHaveURL(/remaining=10/);
    await expectAccessible(peer, 'forecast-viewer-empty');
    await api.json(`/projects/${project.id}/archive`, 'PUT', {
      archived: true,
    });
    await page.goto('/forecast');
    await expect(
      page.getByRole('heading', { name: 'More delivery history is needed' }),
    ).toBeVisible();
    await expectAccessible(page, 'forecast-archived');
    await peer.reload();
    await expect(
      peer.getByRole('button', { name: 'Update forecast', exact: true }),
    ).toBeEnabled();
    expect(writes).toEqual([]);
  } finally {
    if (registered) await removeAccount(peerApi, viewer);
    await context.close();
  }
});
