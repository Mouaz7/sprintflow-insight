import { expect, isoDate, test } from '../support/fixtures.ts';
import { expectAccessible } from '../support/a11y.ts';

test('explains the late risk for unfinished work in the active sprint', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Task risk',
    description: account.email,
    key: 'RK',
  });
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Risk sprint',
    status: 'Active',
    totalStoryPoints: 0,
    startDate: isoDate(-2),
    endDate: isoDate(5),
  });
  const task = (title: string, status: string, sprintId?: number) =>
    api.json('/tasks', 'POST', {
      title,
      status,
      sprintId,
      projectId: project.id,
      storyPoints: 5,
    });
  await task('Open sprint task', 'To Do', sprint.id);
  await task('Completed sprint task', 'Done', sprint.id);
  await task('Backlog task', 'To Do');

  await page.goto('/tasks');
  const action = page.getByRole('button', { name: 'Sprint risk', exact: true });
  await expect(action).toHaveCount(1);
  await action.click();
  const risk = page.getByRole('dialog', { name: 'Sprint risk · Open sprint task' });
  await expect(risk.getByText('Estimate for a project with limited history.')).toBeVisible();
  await expect(risk.getByText(/chance the task is unfinished at sprint end/)).toBeVisible();
  await expect(risk.getByRole('heading', { name: 'Largest influences' })).toBeVisible();
  await expect(risk.getByRole('listitem')).toHaveCount(3);
  await expectAccessible(page, 'task-risk');
});
