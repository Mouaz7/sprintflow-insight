import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test('reads real burndown data with keyboard and table alternatives in both themes', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Chart E2E',
    description: account.email,
  });
  await api.json('/tasks', 'POST', {
    projectId: project.id,
    title: 'Verify burndown accessibility',
    status: 'To Do',
    storyPoints: 3,
  });
  await page.goto('/metrics');
  const chart = page.getByRole('figure', { name: /^Burndown:/ });
  await expect(chart.getByRole('img')).toBeVisible();
  const slider = chart.getByRole('slider', { name: 'Explore chart data' });
  const first = await slider.getAttribute('aria-valuetext');
  await slider.focus();
  await page.keyboard.press('End');
  await expect(slider).not.toHaveAttribute('aria-valuetext', first ?? '');
  await expect(slider).toHaveAttribute('aria-valuetext', /Remaining points 3$/);
  await page.keyboard.press('Home');
  await expect(slider).toHaveAttribute('aria-valuetext', first ?? '');
  await chart.getByText('View data as a table', { exact: true }).click();
  const table = chart.getByRole('table');
  await expect(table).toBeVisible();
  expect(await table.getByRole('row').count()).toBeGreaterThan(2);
  await expect(table.getByRole('row').last()).toContainText('3');
  await expectAccessible(page, 'metrics-shared-chart-light');
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expectAccessible(page, 'metrics-shared-chart-dark');
});
