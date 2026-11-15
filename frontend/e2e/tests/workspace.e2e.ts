import { expectAccessible } from '../support/a11y.ts';
import { expect, isoDate, openView, test } from '../support/fixtures.ts';

interface Project {
  id: number;
  name: string;
}
interface Sprint {
  id: number;
  name: string;
}
interface Story {
  id: number;
  title: string;
  projectId: number;
  sprintId: number | null;
}
interface Task {
  id: number;
  title: string;
  projectId: number;
  storyId: number | null;
  sprintId: number | null;
}

test('creates a project, sprint, story and task, then plans the story into the sprint', async ({
  page,
  api,
  account,
}) => {
  const suffix = account.email.split('@')[0]?.slice(-12) ?? 'run';
  const projectName = `E2E project ${suffix}`;
  const sprintName = `E2E sprint ${suffix}`;
  const storyTitle = `E2E story ${suffix}`;
  const taskTitle = `E2E task ${suffix}`;
  const dialog = page.getByRole('dialog');

  await page.goto('/');
  await openView(page, 'Projects');
  await page.getByRole('button', { name: /Create project/ }).click();
  await dialog.getByLabel('Project name').fill(projectName);
  await dialog
    .getByRole('button', { name: 'Create project', exact: true })
    .click();
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole('combobox', { name: 'Project', exact: true }),
  ).toHaveValue(/\d+/);
  const projects = await api.json<Project[]>('/projects');
  const project = projects.find((item) => item.name === projectName);
  expect(project, 'created project is returned by the API').toBeDefined();
  const projectId = project?.id ?? -1;
  await expectAccessible(page, 'projects');

  await openView(page, 'Sprints');
  await page.getByRole('button', { name: /New sprint/ }).click();
  await dialog.getByLabel('Name', { exact: true }).fill(sprintName);
  await dialog.getByLabel('Start date').fill(isoDate(0));
  await dialog.getByLabel('End date').fill(isoDate(13));
  await dialog
    .getByRole('button', { name: 'Create sprint', exact: true })
    .click();
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole('heading', { name: sprintName, exact: true }),
  ).toBeVisible();
  const sprints = await api.json<Sprint[]>(`/sprints?projectId=${projectId}`);
  expect(sprints.map((item) => item.name)).toEqual([sprintName]);
  const sprintId = sprints[0]?.id;
  await expectAccessible(page, 'sprints');

  await openView(page, 'Backlog');
  await page.getByRole('button', { name: /New story/ }).click();
  await dialog.getByLabel('Title', { exact: true }).fill(storyTitle);
  await dialog
    .getByLabel('Acceptance criteria')
    .fill('Visible after save and reload');
  await dialog.getByLabel('Story points', { exact: true }).selectOption('3');
  await dialog
    .getByRole('button', { name: 'Create story', exact: true })
    .click();
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole('heading', { name: storyTitle, exact: true }),
  ).toBeVisible();
  const stories = await api.json<Story[]>(`/stories/project/${projectId}`);
  expect(stories).toEqual([
    expect.objectContaining({ title: storyTitle, projectId, sprintId: null }),
  ]);
  const storyId = stories[0]?.id;
  await expectAccessible(page, 'backlog');

  await openView(page, 'Tasks');
  await page.getByRole('button', { name: /New task/ }).click();
  await dialog.getByLabel('Title', { exact: true }).fill(taskTitle);
  await dialog
    .getByLabel('User story', { exact: true })
    .selectOption(String(storyId));
  await dialog
    .getByRole('button', { name: 'Create task', exact: true })
    .click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText(taskTitle, { exact: true })).toBeVisible();
  const tasks = await api.json<Task[]>(`/tasks?projectId=${projectId}`);
  expect(tasks).toEqual([
    expect.objectContaining({
      title: taskTitle,
      projectId,
      storyId,
      sprintId: null,
    }),
  ]);
  await expectAccessible(page, 'tasks');

  // Persistence: the board is rebuilt from the database after a full reload.
  await page.reload();
  await openView(page, 'Tasks');
  await expect(page.getByText(taskTitle, { exact: true })).toBeVisible();

  // Planning the story into the sprint moves its task along with it.
  await openView(page, 'Backlog');
  const storyRow = page.getByRole('row').filter({
    has: page.getByRole('heading', { name: storyTitle, exact: true }),
  });
  await storyRow.getByRole('button', { name: 'Edit', exact: true }).click();
  await dialog
    .getByLabel('Sprint', { exact: true })
    .selectOption(String(sprintId));
  await dialog
    .getByRole('button', { name: 'Save changes', exact: true })
    .click();
  await expect(dialog).toBeHidden();
  await expect
    .poll(
      async () =>
        (await api.json<Task[]>(`/tasks?projectId=${projectId}`))[0]?.sprintId,
    )
    .toBe(sprintId);
  expect((await api.json<Story>(`/stories/${storyId}`)).sprintId).toBe(
    sprintId,
  );

  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expectAccessible(page, 'backlog-dark');
  await page.getByRole('button', { name: 'Switch to light mode' }).click();
});
