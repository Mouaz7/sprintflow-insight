import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { expect, test } from '../support/fixtures.ts';
import { expectAccessible } from '../support/a11y.ts';

test.use({ actionTimeout: 10_000 });

test('previews, maps and atomically imports CSV, reads audit and downloads a private account archive', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Import verification',
    key: 'IM',
    description: account.email,
  });
  await page.goto('/data');
  await expect(
    page.getByRole('heading', { name: 'Import & audit', exact: true }),
  ).toBeVisible();
  await page.getByLabel('Source file', { exact: true }).setInputFiles({
    name: 'tasks.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(
      'title,status,sourceKey\nAccessible imported task,Open,DEMO-1\nCompleted imported task,Done,DEMO-2\n',
    ),
  });
  await page.getByRole('button', { name: 'Preview file', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Preview rows' }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Import tasks', exact: true }),
  ).toBeDisabled();
  await page.getByLabel('Open', { exact: true }).selectOption('To Do');
  await page.getByRole('button', { name: 'Preview file', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Import tasks', exact: true }),
  ).toBeEnabled();
  expect(await api.json(`/tasks?projectId=${project.id}`)).toEqual([]);
  await expectAccessible(page, 'import-preview');
  await page.getByRole('button', { name: 'Import tasks', exact: true }).click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm', exact: true })
    .click();
  await expect(page.getByText(/Import complete/)).toBeVisible();
  const tasks = await api.json<{ id: number; title: string }[]>(
    `/tasks?projectId=${project.id}`,
  );
  expect(tasks.map((t) => t.title)).toEqual(
    expect.arrayContaining([
      'Accessible imported task',
      'Completed imported task',
    ]),
  );
  expect(tasks).toHaveLength(2);
  await expect(
    page
      .getByRole('region', { name: 'Administrative audit log' })
      .getByText('IMPORT_COMPLETED'),
  ).toBeVisible();
  // A real browser download must use the authenticated POST and produce a readable JSON ZIP.
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Settings', exact: true }).click();
  const responsePromise = page.waitForResponse(
    (r) =>
      r.url().endsWith('/api/account/export') &&
      r.request().method() === 'POST',
  );
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download account ZIP' }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  expect(response.headers()['cache-control']).toBe('no-store');
  const download = await downloadPromise;
  const path = await download.path();
  expect(path).toBeTruthy();
  if (!path) throw new Error('Downloaded archive is missing.');
  const archive = await readFile(path);
  expect(archive.subarray(0, 2).toString()).toBe('PK');
  const manifest = JSON.parse(
    execFileSync('unzip', ['-p', path, 'manifest.json'], { encoding: 'utf8' }),
  ) as { schemaVersion: number; sections: Record<string, number> };
  expect(manifest.schemaVersion).toBe(1);
  expect(manifest.sections['tasks.json']).toBe(2);
  await expectAccessible(page, 'account-export');
});

test('maps a Jira historical milestone import on a Swedish dark mobile screen', async ({
  page,
  api,
  account,
}) => {
  await api.json('/projects', 'POST', {
    name: 'Historisk import',
    key: 'HI',
    description: account.email,
  });
  await page.setViewportSize({ width: 360, height: 800 });
  await page.addInitScript(() => {
    localStorage.setItem('sprintflow-language', 'SV');
    localStorage.setItem('sprintflow-theme', 'dark');
  });
  await page.goto('/data');
  const issues = {
    issues: [
      {
        key: 'SPARK-1',
        fields: {
          summary: 'Historisk uppgift',
          description: 'Kort beskrivning',
          status: { name: 'Resolved' },
          created: '2020-01-01T00:00:00.000+0000',
          resolutiondate: '2020-01-03T00:00:00.000+0000',
        },
        changelog: {
          histories: [
            {
              created: '2020-01-02T00:00:00.000+0000',
              items: [{ field: 'status', toString: 'In Progress' }],
            },
          ],
        },
      },
    ],
  };
  await page.getByLabel('Källfil', { exact: true }).setInputFiles({
    name: 'jira.json',
    mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify(issues)),
  });
  await page.getByLabel(/Bevara historiska/).check();
  await page
    .getByRole('button', { name: 'Förhandsgranska fil', exact: true })
    .click();
  await page.getByLabel('Resolved', { exact: true }).selectOption('Done');
  await page
    .getByRole('button', { name: 'Förhandsgranska fil', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: 'Importera uppgifter', exact: true }),
  ).toBeEnabled();
  await expectAccessible(page, 'jira-import-sv-mobile');
  await page
    .getByRole('button', { name: 'Importera uppgifter', exact: true })
    .click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Bekräfta', exact: true })
    .click();
  await expect(page.getByText(/Importen är klar/)).toBeVisible();
  const projects = await api.json<{ id: number }[]>('/projects');
  const selected = projects[0];
  if (!selected) throw new Error('Fixture project is missing.');
  const tasks = await api.json<
    { createdAt: string; completedAt: string; startedAt: string }[]
  >(`/tasks?projectId=${selected.id}`);
  const task = tasks[0];
  if (!task) throw new Error('Imported task is missing.');
  expect(task.createdAt).toContain('2020-01-01');
  expect(task.startedAt).toContain('2020-01-02');
  expect(task.completedAt).toContain('2020-01-03');
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Import och granskning', exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
