import { expect, test } from '../support/fixtures.ts';
import { expectAccessible } from '../support/a11y.ts';

test('suggests duplicates as the task draft changes', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Duplicate detection',
    description: account.email,
    key: 'DD',
  });
  const title = 'Synchronize customer search indexes';
  await api.json('/tasks', 'POST', {
    projectId: project.id,
    title,
    status: 'To Do',
    storyPoints: 3,
  });

  await page.goto('/tasks');
  await page.getByRole('button', { name: /New task/ }).click();
  const draft = page.getByRole('dialog', { name: 'New task' });
  const heading = draft.getByLabel('Title', { exact: true });
  await heading.fill(title);
  await expect(draft.getByText('Possible duplicates')).toBeVisible();
  await expect(
    draft.getByRole('link', { name: `DD-1 · ${title}` }),
  ).toHaveAttribute('href', '/tasks?task=DD-1');
  await expectAccessible(page, 'task-duplicate-suggestions');
  await heading.fill('Add invoice export');
  await expect(draft.getByText('Possible duplicates')).toBeHidden();
});

test('shows an inflow and throughput spike from imported history', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Flow signals',
    description: account.email,
    key: 'FL',
  });
  const now = new Date();
  now.setUTCHours(0, 0, 0, 0);
  const day = 86_400_000;
  const prior = new Date(now.getTime() - 36 * day).toISOString();
  const created = new Date(now.getTime() - day + 3_600_000).toISOString();
  const finished = new Date(now.getTime() - day + 7_200_000).toISOString();
  const rows = [
    'title,status,sourceKey,createdAt,completedAt',
    `Older task,To Do,C4-OLD,${prior},`,
    ...Array.from(
      { length: 12 },
      (_, index) => `Resolved ${index},Done,C4-${index},${created},${finished}`,
    ),
  ];
  const imports = `/projects/${project.id}/imports`;
  const body = {
    format: 'CSV',
    content: rows.join('\n'),
    mapping: {},
    statuses: {},
    preserveHistory: true,
  };
  const preview = await api.json<{ fingerprint: string; canCommit: boolean }>(
    `${imports}/preview`,
    'POST',
    body,
  );
  expect(preview.canCommit).toBe(true);
  await api.json(imports, 'POST', {
    ...body,
    expectedFingerprint: preview.fingerprint,
  });

  await page.goto('/flow');
  await page
    .getByText('Inflow and throughput signals', { exact: true })
    .press('Enter');
  for (const name of ['Inflow', 'Throughput']) {
    const latest = page
      .getByRole('table', { name, exact: true })
      .getByRole('row')
      .last();
    await expect(
      latest.getByRole('cell', { name: 'Above limit' }),
    ).toBeVisible();
    await expect(
      latest.getByRole('cell', { name: '12', exact: true }),
    ).toBeVisible();
  }
  await expectAccessible(page, 'flow-signals');
});
