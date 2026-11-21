import { expect, test, isoDate } from '../support/fixtures.ts';
import { expectAccessible } from '../support/a11y.ts';

test.use({ actionTimeout: 10_000 });

test('proposes a dependency-aware sprint and compares a what-if capacity without saving it', async ({
  page,
  api,
  account,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Sprint planning',
    description: account.email,
    key: 'SO',
  });
  const sprint = await api.json<{ id: number }>('/sprints', 'POST', {
    projectId: project.id,
    name: 'Optimized sprint',
    status: 'Planned',
    totalStoryPoints: 0,
    startDate: isoDate(1),
    endDate: isoDate(14),
  });
  const prerequisites = await api.json<{ id: number }>('/stories', 'POST', {
    projectId: project.id,
    title: 'Prepare API',
    acceptanceCriteria: 'API contract approved',
    storyPoints: 3,
    priorityId: 2,
    businessValue: 2,
  });
  const value = await api.json<{ id: number }>('/stories', 'POST', {
    projectId: project.id,
    title: 'Deliver dashboard',
    acceptanceCriteria: 'Chart loads',
    storyPoints: 5,
    priorityId: 1,
    businessValue: 5,
  });
  const makeTask = (storyId: number, title: string, storyPoints: number) =>
    api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      storyId,
      title,
      storyPoints,
      status: 'To Do',
      assigneeEmail: account.email,
    });
  const first = await makeTask(prerequisites.id, 'Prepare API task', 3);
  const second = await makeTask(value.id, 'Deliver dashboard task', 5);
  await api.json(`/projects/${project.id}/dependencies`, 'POST', {
    blockerTaskId: first.id,
    blockedTaskId: second.id,
  });

  await page.goto('/sprints');
  await page.getByRole('button', { name: 'Suggest sprint' }).click();
  const planner = page.getByRole('dialog', {
    name: 'Suggest sprint · Optimized sprint',
  });
  await planner.getByLabel(account.email, { exact: true }).fill('8');
  await planner
    .getByRole('combobox', { name: /Story locks · Deliver dashboard/ })
    .selectOption('in');
  await planner.getByRole('button', { name: 'Calculate proposal' }).click();
  await expect(
    planner
      .getByRole('status', { name: '' })
      .filter({ hasText: 'Proven optimal' }),
  ).toBeVisible();
  await expect(
    planner.getByRole('listitem').filter({ hasText: /^Prepare API$/ }),
  ).toBeVisible();
  await expect(
    planner.getByRole('listitem').filter({ hasText: /^Deliver dashboard$/ }),
  ).toBeVisible();
  await expectAccessible(page, 'sprint-plan');

  await planner.getByLabel(account.email, { exact: true }).fill('7');
  await planner.getByRole('button', { name: 'Calculate proposal' }).click();
  await expect(
    planner.getByText('The scenario has conflicting locks', { exact: true }),
  ).toBeVisible();
  for (const capacity of ['Team', account.email]) {
    await expect(
      planner.getByText(
        `Required work exceeds capacity · Prepare API, Deliver dashboard · ${capacity}`,
        { exact: true },
      ),
    ).toBeVisible();
  }
  const stories = await api.json<{ sprintId: number | null }[]>(
    `/stories/project/${project.id}`,
  );
  expect(stories.every((story) => story.sprintId == null)).toBe(true);
  const capacity = await api.json<{ members: { configured: boolean }[] }>(
    `/projects/${project.id}/sprints/${sprint.id}/capacity`,
  );
  expect(capacity.members).toHaveLength(1);
  expect(capacity.members[0]?.configured).toBe(false);
});
