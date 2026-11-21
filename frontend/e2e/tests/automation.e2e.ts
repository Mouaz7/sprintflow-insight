import { expect, test } from '../support/fixtures.ts';
import { expectAccessible } from '../support/a11y.ts';

test.use({ actionTimeout: 10_000 });

test('builds a move rule and reads the applied action in the run log', async ({
  api,
  page,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Rules project',
    description: account.email,
    key: 'AU',
  });
  const { id } = await api.json<{ id: number }>('/tasks', 'POST', {
    projectId: project.id,
    title: 'Review contract',
    status: 'To Do',
    storyPoints: 3,
  });
  await page.goto('/projects');
  await page.getByRole('link', { name: 'Automation rules' }).click();
  await page.getByRole('button', { name: 'New rule' }).click();
  const editor = page.getByRole('dialog', { name: 'New rule' });
  await editor.getByLabel('Rule name').fill('Mark review work');
  await editor
    .getByRole('combobox', { name: 'To column', exact: true })
    .selectOption('Review');
  await editor.getByLabel('Action value').fill('ready-for-review');
  await expectAccessible(page, 'automation-rule-editor');
  await editor.getByRole('button', { name: 'Save rule' }).click();
  await expect(
    page.getByRole('heading', { name: 'Mark review work' }),
  ).toBeVisible();

  await page.goto('/tasks');
  const task = page.getByRole('article').filter({ hasText: 'Review contract' });
  await task.getByLabel('Move to', { exact: true }).selectOption('Review');
  await expect
    .poll(async () => {
      const current = await api.json<{ labels: string[] }>(`/tasks/${id}`);
      return current.labels;
    })
    .toContain('ready-for-review');

  await page.goto('/automation');
  const run = page.getByRole('row').filter({ hasText: 'Mark review work' });
  await expect(run.getByText(/Applied/)).toBeVisible();
  await expect(run.getByRole('link', { name: 'AU-1' })).toHaveAttribute(
    'href',
    '/tasks?task=AU-1',
  );
  await expectAccessible(page, 'automation-run-log');
});
