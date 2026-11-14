import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { launchBrowser, type BrowserContext } from './browser.ts';
import { versionHeaders } from './version-headers.ts';

// Real server and database. Only the accounts and projects created here are touched.
const base = process.env.SPRINTFLOW_QA_URL || 'http://127.0.0.1:8081';
const out = fileURLToPath(new URL('../../artifacts/business-rules/', import.meta.url));
mkdirSync(out, { recursive: true });
const browser = await launchBrowser();
const contexts: { context: BrowserContext; email: string }[] = [];
const password = randomUUID() + '-Qa1';
const projects: number[] = [];
const stamp = Date.now();
async function call(ctx: BrowserContext, path: string, method = 'GET', data?: any) {
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    const csrf = await (await ctx.request.get(base + '/api/auth/csrf')).json();
    headers[csrf.headerName] = csrf.token;
  }
  return ctx.request.fetch(base + '/api' + path, {
    method, data, headers: { ...headers, ...await versionHeaders(ctx.request, base, path, method, data) },
  });
}
async function checked(ctx: BrowserContext, path: string, method = 'GET', data?: any) {
  const response = await call(ctx, path, method, data);
  assert.ok(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`);
  const body = await response.text();
  return body ? JSON.parse(body) : undefined;
}
try {
  for (const role of ['Owner', 'Admin', 'Member', 'Viewer', 'Outsider']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const email = `logic-${role.toLowerCase()}-${stamp}@example.com`;
    await checked(context, '/auth/register', 'POST', { email, password, displayName: role });
    contexts.push({ context, email });
  }
  const [owner, admin, member, viewer, outsider] = contexts;
  for (const name of ['Test – login', 'Separate project']) {
    projects.push((await checked(owner.context, '/projects', 'POST', { name })).id);
  }
  const [projectId, otherProject] = projects;
  for (const [account, role] of [[admin, 'Admin'], [member, 'Member'], [viewer, 'Viewer']] as const) {
    await checked(owner.context, `/projects/${projectId}/members`, 'POST', { email: account.email, role });
  }
  for (const account of [admin, member, viewer]) {
    assert.equal((await call(account.context, `/tasks?projectId=${otherProject}`)).status(), 403);
  }
  assert.equal((await call(outsider.context, `/tasks?projectId=${projectId}`)).status(), 403);
  for (const account of [owner, admin, member]) {
    await checked(account.context, '/tasks', 'POST', { projectId, title: `Allowed ${account.email}`, status: 'To Do' });
  }
  assert.equal((await call(viewer.context, '/tasks', 'POST', { projectId, title: 'Forbidden', status: 'To Do' })).status(), 403);
  assert.equal((await call(member.context, `/projects/${projectId}/members`, 'POST', { email: outsider.email, role: 'Viewer' })).status(), 403);
  const today = new Date().toISOString().slice(0, 10);
  const end = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  const sprint = await checked(owner.context, '/sprints', 'POST', { projectId, name: 'Login sprint', status: 'Active', startDate: today, endDate: end });
  assert.equal((await call(owner.context, '/sprints', 'POST', { projectId, name: 'Second active', status: 'Active', startDate: today, endDate: end })).status(), 409);
  const foreignSprint = await checked(owner.context, '/sprints', 'POST', { projectId: otherProject, name: 'Foreign sprint', status: 'Planned', startDate: today, endDate: end });
  const story = await checked(owner.context, '/stories', 'POST', { projectId, title: 'User can log in', acceptanceCriteria: 'Valid credentials open workspace', storyPoints: 3, priorityId: 2 });
  const taskBody = { projectId, title: 'Build login form', status: 'To Do', storyId: story.id, storyPoints: 3, dueAt: `${today}T10:00:00Z` };
  assert.equal((await call(owner.context, '/tasks', 'POST', { ...taskBody, sprintId: sprint.id })).status(), 400);
  assert.equal((await call(owner.context, '/tasks', 'POST', { ...taskBody, sprintId: foreignSprint.id })).status(), 400);
  assert.equal((await call(owner.context, '/tasks', 'POST', { ...taskBody, assigneeEmail: outsider.email })).status(), 400);
  const task = await checked(owner.context, '/tasks', 'POST', taskBody);
  const trashed = await checked(owner.context, '/tasks', 'POST', { ...taskBody, title: 'Trashed linked task' });
  await checked(owner.context, `/tasks/${trashed.id}`, 'DELETE');
  const page = await owner.context.newPage();
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(base);
  await page.locator('.project-control select').selectOption(String(projectId));
  async function nav(name: string) {
    await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name, exact: true }).click();
  }
  for (const target of [sprint.id, null]) {
    await nav('Backlog');
    const card = page.locator('tbody tr').filter({ has: page.getByRole('heading', { name: story.title, exact: true }) });
    await card.getByRole('button', { name: 'Edit', exact: true }).click();
    await page.getByRole('dialog').getByLabel('Sprint', { exact: true }).selectOption(target === null ? '' : String(target));
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    const saved = await checked(owner.context, `/tasks/${task.id}`);
    assert.equal(saved.sprintId, target);
    assert.equal(saved.projectId, projectId);
    assert.ok(saved.version > task.version);
    // Restore verifies that the hidden child moved too, without losing its trash status.
    await checked(owner.context, `/workspace/trash/task/${trashed.id}`, 'PUT', { restore: true });
    assert.equal((await checked(owner.context, `/tasks/${trashed.id}`)).sprintId, target);
    await checked(owner.context, `/tasks/${trashed.id}`, 'DELETE');
    const csv = await call(owner.context, `/projects/${projectId}/export.csv?sprintId=${sprint.id}`);
    assert.equal(csv.status(), 200);
    assert.equal((await csv.text()).includes(task.title), target !== null);
    await nav('Tasks');
    const taskCard = page.locator('.task-board article').filter({ has: page.locator('strong').getByText(task.title, { exact: true }) });
    await taskCard.getByRole('button', { name: 'Edit', exact: true }).click();
    assert.equal(await page.getByRole('dialog').getByLabel('Sprint', { exact: true }).isDisabled(), true);
    assert.equal(await page.getByRole('dialog').getByLabel('Sprint', { exact: true }).inputValue(), target === null ? '' : String(target));
    await page.screenshot({ path: out + (target === null ? 'backlog-inheritance.png' : 'sprint-inheritance.png') });
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await nav('Calendar');
    await page.locator('.calendar-day').filter({ hasText: task.title }).waitFor();
  }
  await page.locator('.project-control select').selectOption(String(otherProject));
  await nav('Tasks');
  await page.getByRole('heading', { name: 'Tasks', exact: true }).waitFor();
  assert.equal(await page.locator('.task-board article').count(), 0);
  assert.equal((await checked(owner.context, `/tasks/${task.id}`)).projectId, projectId);
  assert.equal((await checked(owner.context, `/tasks/${task.id}`)).sprintId, null);
  await page.reload();
  assert.equal((await checked(owner.context, `/stories/${story.id}`)).sprintId, null);
  const adminPage = await admin.context.newPage();
  await adminPage.goto(base);
  await adminPage.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name: 'Projects', exact: true }).click();
  await adminPage.getByRole('button', { name: 'Add member', exact: true }).waitFor();
  assert.equal(await adminPage.getByRole('button', { name: 'Make owner', exact: true }).count(), 0);
  await page.locator('.project-control select').selectOption(String(projectId));
  await nav('Projects');
  await page.locator('.member-list article').filter({ hasText: admin.email }).getByRole('button', { name: 'Make owner', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Confirm', exact: true }).click();
  await page.getByRole('alertdialog').waitFor({ state: 'hidden' });
  await page.waitForFunction(() => !document.querySelector('.member-manager'));
  assert.equal((await checked(owner.context, `/projects/${projectId}/role`)).role, 'Member');
  assert.equal((await checked(admin.context, `/projects/${projectId}/role`)).role, 'Owner');
  assert.equal((await call(owner.context, `/projects/${projectId}/owner`, 'PUT', { email: owner.email })).status(), 403);
  await checked(admin.context, `/projects/${projectId}/owner`, 'PUT', { email: owner.email });
  assert.deepEqual(errors, []);
  console.log('PASS: real database role matrix, cross-project/account isolation, invalid associations, one active sprint, UI story moves to sprint/backlog, linked trashed children, inherited task form, filtered CSV, calendar, ownership transfer, admin UI permissions and persistence.');
} finally {
  if (contexts.length) {
    for (const id of projects) {
      const project = await checked(contexts[0].context, `/projects/${id}`);
      const projectOwner = contexts.find(account => account.email === project.ownerEmail)!;
      await checked(projectOwner.context, `/projects/${id}`, 'DELETE');
    }
    for (const { context, email } of contexts.reverse()) {
      await checked(context, '/auth/account', 'DELETE', { email, currentPassword: password });
    }
  }
  await browser.close();
}
