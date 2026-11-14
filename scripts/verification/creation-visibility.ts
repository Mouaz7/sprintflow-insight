import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { launchBrowser } from './browser.ts';

// Real local server/database flow, limited to a disposable account and its projects.
const base = process.env.SPRINTFLOW_QA_URL || 'http://127.0.0.1:8081';
const out = fileURLToPath(new URL('../../artifacts/creation-visibility/', import.meta.url));
fs.mkdirSync(out, { recursive: true });
const email = `creation-qa-${Date.now()}@example.com`;
const password = randomUUID() + '-Qa1';
const browser = await launchBrowser();
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
let registered = false;
const errors: string[] = [];
page.on('pageerror', error => errors.push(error.message));
async function call(path: string, method = 'GET', data?: unknown) {
  const token = await (await context.request.get(base + '/api/auth/csrf')).json();
  return context.request.fetch(base + '/api' + path, { method, data, headers: method === 'GET' ? {} : { [token.headerName]: token.token } });
}
async function nav(index: number) { await page.locator('.header-navigation nav button').nth(index).click(); }
async function closed() { await page.getByRole('dialog').waitFor({ state: 'hidden' }); }
let blockedRead = '';
try {
  const registration = await call('/auth/register', 'POST', { email, password, displayName: 'Creation QA' });
  assert.ok(registration.ok(), await registration.text()); registered = true;
  assert.ok((await call('/projects', 'POST', { name: 'Creation QA Alpha' })).ok());
  await page.goto(base); await page.locator('.page h1').waitFor();
  await nav(1);
  await page.getByLabel('Search projects').fill('Alpha');
  await page.getByRole('button', { name: 'Archived projects' }).click();
  await page.getByRole('button', { name: /Create project/ }).click();
  assert.equal(await page.locator('.editor-project').count(), 0);
  await page.getByLabel('Project name').fill('Creation QA Beta');
  await page.getByRole('dialog').getByRole('button', { name: 'Create project', exact: true }).click();
  await closed();
  assert.equal(await page.getByLabel('Search projects').inputValue(), '');
  await page.waitForFunction(() => document.querySelector('.project-control select option:checked')?.textContent === 'Creation QA Beta');
  const projects = await (await call('/projects')).json();
  const beta = projects.find(item => item.name === 'Creation QA Beta');
  const alpha = projects.find(item => item.name === 'Creation QA Alpha');
  assert.ok(beta && alpha);
  // Inject only a hypothetical post-save refresh failure. Writes remain real.
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url());
    if (blockedRead && route.request().method() === 'GET' && url.pathname + url.search === blockedRead) {
      return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Injected refresh failure.' }) });
    }
    return route.continue();
  });
  await nav(3); await page.getByRole('button', { name: /New sprint/ }).click();
  assert.equal(await page.locator('.editor-project select').inputValue(), String(beta.id));
  await page.getByLabel('Name', { exact: true }).fill('Visible QA sprint');
  await page.getByLabel('Start date').fill('2026-10-22'); await page.getByLabel('End date').fill('2026-10-30');
  blockedRead = `/api/sprints?projectId=${beta.id}`;
  await page.getByRole('button', { name: 'Create sprint', exact: true }).click(); await closed();
  await page.getByRole('heading', { name: 'Visible QA sprint', exact: true }).waitFor(); blockedRead = '';
  const sprints = await (await call(`/sprints?projectId=${beta.id}`)).json(); assert.equal(sprints.length, 1);
  await nav(2); await page.getByLabel('Search stories').fill('Unrelated search');
  await page.getByLabel('Filter backlog by sprint').selectOption(String(sprints[0].id));
  await page.getByRole('button', { name: /New story/ }).click();
  assert.equal(await page.locator('.editor-project select').inputValue(), String(beta.id));
  await page.getByLabel('Title', { exact: true }).fill('Visible QA story');
  await page.getByLabel('Description', { exact: true }).fill('Saved in the selected Beta project');
  await page.getByLabel('Acceptance criteria').fill('Visible after save and reload');
  await page.getByLabel('Story points', { exact: true }).selectOption('2');
  blockedRead = `/api/stories/project/${beta.id}`;
  await page.getByRole('button', { name: 'Create story', exact: true }).click(); await closed();
  await page.getByRole('heading', { name: 'Visible QA story', exact: true }).waitFor(); blockedRead = '';
  assert.equal(await page.getByLabel('Search stories').inputValue(), '');
  assert.equal(await page.getByLabel('Filter backlog by sprint').inputValue(), 'all');
  const stories = await (await call(`/stories/project/${beta.id}`)).json();
  assert.equal(stories.length, 1); assert.equal(stories[0].projectId, beta.id); assert.equal(stories[0].sprintId, null);
  assert.deepEqual(await (await call(`/stories/project/${alpha.id}`)).json(), []);
  await page.screenshot({ path: out + 'story-after-save.png', fullPage: true });
  await page.reload(); await page.locator('.page h1').waitFor(); await nav(2);
  await page.getByRole('heading', { name: 'Visible QA story', exact: true }).waitFor();
  await nav(4); await page.getByLabel('Saved filters').selectOption('blocked');
  await page.getByLabel('Search tasks').fill('Unrelated search');
  await page.getByRole('button', { name: /New task/ }).click();
  assert.equal(await page.locator('.editor-project select').inputValue(), String(beta.id));
  await page.getByLabel('Title', { exact: true }).fill('Visible QA task');
  await page.getByRole('button', { name: 'Create task', exact: true }).click(); await closed();
  await page.locator('.task-board article > strong').filter({ hasText: 'Visible QA task' }).waitFor();
  assert.equal(await page.getByLabel('Saved filters').inputValue(), '');
  assert.equal(await page.getByLabel('Search tasks').inputValue(), '');
  assert.equal(await page.getByLabel('Blocked', { exact: true }).isChecked(), false);
  const tasks = await (await call(`/tasks?projectId=${beta.id}`)).json();
  assert.equal(tasks.length, 1); assert.equal(tasks[0].projectId, beta.id);
  assert.deepEqual(await (await call(`/tasks?projectId=${alpha.id}`)).json(), []);
  await page.screenshot({ path: out + 'task-after-save.png', fullPage: true });
  await page.reload(); await page.locator('.page h1').waitFor(); await nav(4);
  await page.locator('.task-board article > strong').filter({ hasText: 'Visible QA task' }).waitFor();
  // Choose a different destination inside each creation form; the editor must
  // retain text while replacing project-specific relationships.
  await nav(2); await page.getByRole('button', { name: /New story/ }).click();
  await page.getByLabel('Title', { exact: true }).fill('Story in Alpha');
  await page.getByLabel('Acceptance criteria').fill('Preserved when changing destination');
  await page.getByLabel('Sprint', { exact: true }).selectOption(String(sprints[0].id));
  await page.locator('.editor-project select').selectOption(String(alpha.id));
  assert.equal(await page.getByLabel('Title', { exact: true }).inputValue(), 'Story in Alpha');
  assert.equal(await page.getByLabel('Sprint', { exact: true }).inputValue(), '');
  await page.waitForFunction(() => !document.querySelector<HTMLButtonElement>('.editor-form .primary-action')?.disabled);
  await page.screenshot({ path: out + 'select-project-story.png', fullPage: true });
  await page.getByRole('button', { name: 'Create story', exact: true }).click(); await closed();
  await page.getByRole('heading', { name: 'Story in Alpha', exact: true }).waitFor();
  assert.equal(await page.locator('.project-control select').inputValue(), String(alpha.id));
  const alphaStories = await (await call(`/stories/project/${alpha.id}`)).json();
  assert.equal(alphaStories.length, 1); assert.equal(alphaStories[0].sprintId, null);
  await nav(3); await page.getByRole('button', { name: /New sprint/ }).click();
  await page.getByLabel('Name', { exact: true }).fill('Sprint chosen for Beta');
  await page.getByLabel('Start date').fill('2026-10-22'); await page.getByLabel('End date').fill('2026-10-30');
  await page.locator('.editor-project select').selectOption(String(beta.id));
  assert.equal(await page.getByLabel('Name', { exact: true }).inputValue(), 'Sprint chosen for Beta');
  await page.waitForFunction(() => !document.querySelector<HTMLButtonElement>('.editor-form .primary-action')?.disabled);
  await page.getByRole('button', { name: 'Create sprint', exact: true }).click(); await closed();
  await page.getByRole('heading', { name: 'Sprint chosen for Beta', exact: true }).waitFor();
  assert.equal(await page.locator('.project-control select').inputValue(), String(beta.id));
  assert.equal((await (await call(`/sprints?projectId=${beta.id}`)).json()).length, 2);
  assert.equal((await (await call(`/sprints?projectId=${alpha.id}`)).json()).length, 0);
  await nav(4); await page.getByRole('button', { name: /New task/ }).click();
  await page.getByLabel('Title', { exact: true }).fill('Task chosen for Alpha');
  await page.getByLabel('User story', { exact: true }).selectOption(String(stories[0].id));
  await page.locator('.editor-project select').selectOption(String(alpha.id));
  assert.equal(await page.getByLabel('Title', { exact: true }).inputValue(), 'Task chosen for Alpha');
  assert.equal(await page.getByLabel('User story', { exact: true }).inputValue(), '');
  assert.equal(await page.getByLabel('Sprint', { exact: true }).inputValue(), '');
  await page.getByLabel('User story', { exact: true }).selectOption(String(alphaStories[0].id));
  await page.waitForFunction(() => !document.querySelector<HTMLButtonElement>('.editor-form .primary-action')?.disabled);
  await page.getByRole('button', { name: 'Create task', exact: true }).click(); await closed();
  await page.locator('.task-board article > strong').filter({ hasText: 'Task chosen for Alpha' }).waitFor();
  assert.equal(await page.locator('.project-control select').inputValue(), String(alpha.id));
  const alphaTasks = await (await call(`/tasks?projectId=${alpha.id}`)).json();
  assert.equal(alphaTasks.length, 1); assert.equal(alphaTasks[0].storyId, alphaStories[0].id);
  assert.equal(alphaTasks[0].sprintId, null);
  assert.deepEqual(errors, []);
  console.log('PASS real project/story/sprint/task creation, choosing destination projects inside all three forms, draft preservation, relationship resets, selected-project isolation, save visibility and persistence.');
} catch (error) {
  await page.screenshot({ path: out + 'failure.png', fullPage: true });
  throw error;
} finally {
  try {
    if (registered) {
      const projects = await (await call('/projects')).json();
      for (const project of projects) {
        assert.equal(project.ownerEmail, email);
        assert.ok(project.name.startsWith('Creation QA '));
        const removal = await call(`/projects/${project.id}`, 'DELETE'); assert.ok(removal.ok(), await removal.text());
      }
      const removal = await call('/auth/account', 'DELETE', { email, currentPassword: password });
      assert.ok(removal.ok(), await removal.text());
      console.log('Disposable test projects and account removed.');
    }
  } finally { await browser.close(); }
}
