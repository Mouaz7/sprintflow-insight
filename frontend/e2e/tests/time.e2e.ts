import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test, isoDate } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

test('logs and corrects time, preserves a live draft, stops a timer and exports the real report', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}, testInfo) => {
  const peerContext = await browser.newContext({
    baseURL,
    reducedMotion: 'reduce',
  });
  const peerApi = new Api(peerContext.request);
  const teammate = newAccount('time-member');
  let registered = false;
  try {
    await register(peerApi, teammate);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Time and capacity delivery',
      description: account.email,
      key: 'TC',
    });
    const base = `/projects/${project.id}`;
    await api.json(`${base}/members`, 'POST', {
      email: teammate.email,
      role: 'Member',
    });
    const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
      projectId: project.id,
      name: 'Capacity sprint',
      status: 'Planned',
      startDate: isoDate(0),
      endDate: isoDate(13),
    });
    const task = await api.json<{ id: number; key: string }>('/tasks', 'POST', {
      projectId: project.id,
      sprintId: sprint.id,
      title: 'Accessible delivery',
      status: 'To Do',
      storyPoints: 3,
      assigneeEmail: account.email,
    });
    await api.json('/tasks', 'POST', {
      projectId: project.id,
      sprintId: sprint.id,
      title: '=2+2',
      status: 'To Do',
    });
    await page.goto(`/time?sprint=${sprint.id}`);
    await expect(
      page.getByRole('heading', { level: 1, name: 'Time & capacity' }),
    ).toBeVisible();
    await expect(page.locator('.presence')).toHaveClass(/presence-live/);
    const report = page.getByRole('table', { name: 'Estimated vs actual' });
    const taskRow = report
      .getByRole('row')
      .filter({ hasText: 'Accessible delivery' });
    await taskRow.getByRole('button', { name: 'Set time estimate' }).click();
    let dialog = page.getByRole('dialog', { name: 'Set time estimate' });
    await dialog.getByLabel('Estimate (minutes)', { exact: true }).fill('180');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(taskRow.getByRole('cell').first()).toHaveText('180');
    await page.getByRole('button', { name: 'Log time', exact: true }).click();
    dialog = page.getByRole('dialog', { name: 'Log time' });
    await dialog
      .getByRole('combobox', { name: 'Task', exact: true })
      .selectOption(String(task.id));
    await dialog.getByLabel('Minutes', { exact: true }).fill('60');
    await dialog.getByLabel('Day worked', { exact: true }).fill(isoDate(0));
    await dialog
      .getByLabel('Note (optional)', { exact: true })
      .fill('Owner work');
    await expectAccessible(page, 'time-log-editor');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText('Owner work', { exact: true })).toBeVisible();
    await page
      .getByRole('button', { name: 'Correct entry', exact: true })
      .click();
    dialog = page.getByRole('dialog', { name: 'Correct entry' });
    await dialog.getByLabel('Minutes', { exact: true }).fill('75');
    const refresh = page.waitForResponse(
      (response) =>
        response.url().includes(`${base}/time-entries?`) &&
        response.request().method() === 'GET',
    );
    await peerApi.json(`${base}/time-entries`, 'POST', {
      taskId: task.id,
      minutes: 30,
      spentOn: isoDate(0),
      note: 'Pairing from teammate',
    });
    await refresh;
    await expect(dialog.getByLabel('Minutes', { exact: true })).toHaveValue(
      '75',
    );
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(
      page.getByText('Pairing from teammate', { exact: true }),
    ).toBeVisible();
    await page
      .getByRole('button', { name: 'Start timer', exact: true })
      .click();
    dialog = page.getByRole('dialog', { name: 'Start timer' });
    await dialog
      .getByRole('combobox', { name: 'Task', exact: true })
      .selectOption(String(task.id));
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText(/Elapsed:/)).toBeVisible();
    expect(await api.status(`${base}/timer`, 'POST', { taskId: task.id })).toBe(
      409,
    );
    await page.getByRole('button', { name: 'Stop timer', exact: true }).click();
    await expect(
      page.getByText('No timer running in this project.', { exact: true }),
    ).toBeVisible();
    const data = await api.json<{
      loggedMinutes: number;
      varianceMinutes: number;
    }>(`${base}/time-report?sprintId=${sprint.id}`);
    expect(data.loggedMinutes).toBe(106);
    expect(data.varianceMinutes).toBe(-74);
    const ownerCapacity = page
      .locator('.time-members article')
      .filter({ hasText: account.email });
    await ownerCapacity
      .getByRole('button', { name: 'Set capacity', exact: true })
      .click();
    dialog = page.getByRole('dialog', { name: 'Set capacity' });
    await dialog
      .getByLabel('Working minutes per day', { exact: true })
      .fill('15');
    await dialog.getByLabel('Availability (%)', { exact: true }).fill('50');
    await dialog
      .getByLabel('Full-sprint points (optional)', { exact: true })
      .fill('4');
    // One known weekday within the two-week range; weekends have no capacity cost.
    const firstWeekday = new Date(`${isoDate(0)}T12:00:00Z`);
    while ([0, 6].includes(firstWeekday.getUTCDay()))
      firstWeekday.setUTCDate(firstWeekday.getUTCDate() + 1);
    await dialog
      .getByLabel('Days off (one date per line)', { exact: true })
      .fill(firstWeekday.toISOString().slice(0, 10));
    await expectAccessible(page, 'time-capacity-editor');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(
      ownerCapacity.getByText('Overbooked', { exact: true }),
    ).toBeVisible();
    const capacity = await api.json<{
      team: { availableMinutes: number; overbooked: boolean };
    }>(`${base}/sprints/${sprint.id}/capacity`);
    expect(capacity.team.availableMinutes).toBe(67);
    expect(capacity.team.overbooked).toBe(true);
    const downloadEvent = page.waitForEvent('download');
    await page
      .getByRole('link', { name: 'Export time report (CSV)', exact: true })
      .click();
    expect((await downloadEvent).suggestedFilename()).toContain(
      'sprintflow-time-',
    );
    const csv = await (
      await api.call(`${base}/time-report.csv?sprintId=${sprint.id}`)
    ).text();
    expect(csv).toContain('180,106,-74');
    expect(csv).toContain('"\'=2+2"');
    await page.reload();
    await expect(page.getByText('Owner work', { exact: true })).toBeVisible();
    await expect(
      ownerCapacity.getByText('Overbooked', { exact: true }),
    ).toBeVisible();
    await expectAccessible(page, 'time-report-and-capacity');
    await testInfo.attach('time-desktop', {
      body: await page.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
  } finally {
    if (registered) await removeAccount(peerApi, teammate);
    await peerContext.close();
  }
});

test('reads time and capacity as a Viewer and in an archived project on Swedish dark mobile', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}, testInfo) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Tid och kapacitet i mobilen',
    description: account.email,
  });
  const base = `/projects/${project.id}`;
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Mobil sprint',
    status: 'Planned',
    startDate: isoDate(0),
    endDate: isoDate(13),
  });
  const task = await api.json<{ id: number }>('/tasks', 'POST', {
    projectId: project.id,
    sprintId: sprint.id,
    title:
      'En lång uppgiftstitel som fortfarande ska kunna läsas på en liten mobilskärm',
    status: 'To Do',
  });
  await api.json(`${base}/time-entries`, 'POST', {
    taskId: task.id,
    minutes: 45,
    spentOn: isoDate(0),
    note: 'Verifierad tidpost',
  });
  const viewerContext = await browser.newContext({
    baseURL,
    viewport: { width: 360, height: 800 },
    reducedMotion: 'reduce',
  });
  const viewerApi = new Api(viewerContext.request);
  const viewer = newAccount('time-viewer');
  let registered = false;
  try {
    await register(viewerApi, viewer);
    registered = true;
    await api.json(`${base}/members`, 'POST', {
      email: viewer.email,
      role: 'Viewer',
    });
    await viewerContext.addInitScript(() => {
      localStorage.setItem('sprintflow-language', 'SV');
      localStorage.setItem('sprintflow-theme', 'dark');
    });
    const watched = await viewerContext.newPage();
    await watched.goto(`/time?sprint=${sprint.id}`);
    await expect(
      watched.getByText('Verifierad tidpost', { exact: true }),
    ).toBeVisible();
    await expect(
      watched.getByRole('button', { name: 'Logga tid', exact: true }),
    ).toHaveCount(0);
    await expect(
      watched.getByRole('button', { name: 'Ange kapacitet', exact: true }),
    ).toHaveCount(0);
    expect(
      await viewerApi.status(`${base}/time-entries`, 'POST', {
        taskId: task.id,
        minutes: 5,
        spentOn: isoDate(0),
      }),
    ).toBe(403);
    await expectAccessible(watched, 'time-viewer-swedish-mobile');
    expect(
      await watched.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await api.json(`${base}/archive`, 'PUT', { archived: true });
    await page.setViewportSize({ width: 360, height: 800 });
    await page.addInitScript(() => {
      localStorage.setItem('sprintflow-language', 'SV');
      localStorage.setItem('sprintflow-theme', 'dark');
    });
    await page.goto(`/time?sprint=${sprint.id}`);
    await expect(
      page.getByText('Verifierad tidpost', { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Logga tid', exact: true }),
    ).toHaveCount(0);
    expect(await api.status(`${base}/timer`, 'POST', { taskId: task.id })).toBe(
      409,
    );
    await expectAccessible(page, 'time-archived-swedish-mobile');
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await testInfo.attach('time-mobile-dark', {
      body: await page.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
  } finally {
    if (registered) await removeAccount(viewerApi, viewer);
    await viewerContext.close();
  }
});
