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

test('keeps Swedish overview cards within their columns on a phone', async ({
  page,
  api,
  account,
}) => {
  const suffix = account.email.split('@')[0]?.slice(-12) ?? 'run';
  await api.json('/projects', 'POST', { name: `Mobil översikt ${suffix}` });
  await api.json('/auth/preferences', 'PUT', {
    language: 'SV',
    theme: 'dark',
    onboardingCompleted: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/overview');

  const cards = page.getByRole('article').filter({
    has: page.getByText(/^(Genomströmning|Leveransstatus)$/),
  });
  await expect(cards).toHaveCount(2);
  await page.evaluate(() => document.fonts.ready);
  const overflow = await cards.evaluateAll((articles) =>
    articles
      .filter((article) => article.scrollWidth > article.clientWidth)
      .map((article) => article.textContent),
  );
  expect(overflow).toEqual([]);
});

test('finds, combines and clears board filters on a phone', async ({
  page,
  api,
  account,
}) => {
  const suffix = account.email.split('@')[0]?.slice(-12) ?? 'run';
  const project = await api.json<Project>('/projects', 'POST', {
    name: `Mobil uppgiftstavla ${suffix}`,
  });
  for (const [title, status] of [
    ['Planera släppet', 'To Do'],
    ['Uppdatera kalendern', 'Done'],
  ]) {
    await api.json('/tasks', 'POST', {
      projectId: project.id,
      title,
      status,
      storyPoints: 1,
    });
  }
  await api.json('/auth/preferences', 'PUT', {
    language: 'SV',
    onboardingCompleted: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/tasks');

  const search = page.getByRole('searchbox', { name: 'Sök uppgifter' });
  const filter = page.getByRole('combobox', { name: 'Filtrera status' });
  const disclosure = page.getByText('Filter och export', { exact: true });
  const planningTask = page.getByText('Planera släppet', { exact: true });
  const calendarTask = page.getByText('Uppdatera kalendern', { exact: true });
  const toDo = page.getByRole('heading', { name: 'Att göra', exact: true });
  await expect(search).toBeVisible();
  await expect(filter).toBeHidden();
  await expect(planningTask).toBeInViewport();

  await search.fill('kalendern');
  await expect(calendarTask).toBeInViewport();
  await expect(toDo).toBeHidden();
  const calendarCard = page.getByRole('article').filter({ has: calendarTask });
  await calendarCard.dispatchEvent('dragstart');
  await expect(toDo).toBeVisible();
  await page
    .locator('.task-board > section')
    .filter({ has: toDo })
    .dispatchEvent('drop');
  const moveTo = calendarCard.getByRole('combobox', { name: 'Flytta till' });
  await expect(moveTo).toHaveValue('To Do');
  await moveTo.selectOption('Review');
  await expect(toDo).toBeHidden();
  await expect(moveTo).toHaveValue('Review');

  await search.fill('släpp');
  await expect(page.getByText('Visar 1 av 2 uppgifter')).toBeVisible();
  await expect(calendarTask).toBeHidden();
  await disclosure.press('Enter');
  await expect(filter).toBeVisible();
  await filter.selectOption('Done');
  await expect(page.getByText('Visar 0 av 2 uppgifter')).toBeVisible();
  await expect(planningTask).toBeHidden();
  await expect(page.locator('a[href*="export.csv"]')).toHaveAttribute(
    'href',
    /status=Done.*q=sl%C3%A4pp/,
  );
  await disclosure.press('Enter');
  await expect(filter).toBeHidden();
  await expect(page.getByText('Visar 0 av 2 uppgifter')).toBeVisible();

  await page.getByRole('button', { name: 'Rensa filter' }).click();
  await expect(search).toBeFocused();
  await expect(search).toHaveValue('');
  await expect(planningTask).toBeVisible();
  await expect(calendarTask).toBeVisible();
});
