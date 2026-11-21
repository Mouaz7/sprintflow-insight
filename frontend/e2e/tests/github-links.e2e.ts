import { createHmac, randomUUID } from 'node:crypto';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test('links signed GitHub events to the task board and records status changes', async ({
  account,
  api,
  page,
}) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'GitHub integration',
    key: 'GH',
    description: account.email,
  });
  const task = await api.json<{ id: number; key: string }>('/tasks', 'POST', {
    projectId: project.id,
    title: 'Publish the sample',
    status: 'To Do',
    storyPoints: 3,
  });
  await page.goto('/automation');
  await page.getByText('GitHub webhook', { exact: true }).click();
  await page.getByLabel('GitHub repository').fill('owner/repo');
  await page.getByRole('button', { name: 'Generate webhook' }).click();
  const hook = await page.getByLabel('Payload URL').inputValue();
  const secret = await page.getByLabel('Webhook secret').inputValue();
  await page.getByRole('button', { name: 'Hide secret' }).click();
  await page.getByLabel(/Move to Review when/).check();
  await page.getByLabel(/Move to Done when/).check();
  const saved = page.waitForResponse((response) =>
    response.url().endsWith('/github/rules'),
  );
  await page.getByRole('button', { name: 'Save GitHub rules' }).click();
  expect((await saved).status()).toBe(204);

  const deliver = async (
    event: 'pull_request' | 'push',
    payload: unknown,
    id = randomUUID(),
  ) => {
    const data = JSON.stringify(payload);
    const signature = createHmac('sha256', secret).update(data).digest('hex');
    expect(
      (
        await page.request.post(hook, {
          data,
          headers: {
            'Content-Type': 'application/json',
            'X-GitHub-Event': event,
            'X-GitHub-Delivery': id,
            'X-Hub-Signature-256': `sha256=${signature}`,
          },
        })
      ).status(),
    ).toBe(204);
  };
  const repository = { full_name: 'owner/repo' };
  const pull = {
    id: 88,
    title: `Implement ${task.key}`,
    head: { ref: `work/${task.key}` },
  };
  const opened = {
    repository,
    action: 'opened',
    number: 17,
    pull_request: pull,
  };
  const delivery = randomUUID();
  await deliver('pull_request', opened, delivery);
  await deliver('pull_request', opened, delivery);
  expect((await api.json<{ status: string }>(`/tasks/${task.id}`)).status).toBe(
    'Review',
  );
  const hash = 'abcdef0123'.repeat(4);
  await deliver('push', {
    repository,
    ref: 'refs/heads/main',
    commits: [{ id: hash, message: `Finish ${task.key}` }],
  });
  await deliver('pull_request', {
    ...opened,
    action: 'closed',
    pull_request: { ...pull, merged: true },
  });
  expect((await api.json<{ status: string }>(`/tasks/${task.id}`)).status).toBe(
    'Done',
  );

  await page.goto('/tasks');
  const card = page
    .getByRole('article')
    .filter({ hasText: 'Publish the sample' });
  await card.getByText('GitHub links', { exact: true }).click();
  await expect(
    card.getByRole('link', { name: /#17 Implement/ }),
  ).toHaveAttribute('href', 'https://github.com/owner/repo/pull/17');
  await expect(
    card.getByRole('link', { name: /abcdef0 Finish/ }),
  ).toHaveAttribute('href', `https://github.com/owner/repo/commit/${hash}`);
  await expect(card.getByText(/Merged/)).toBeVisible();
  await expectAccessible(page, 'github-linked-task');

  await page.goto('/automation');
  await page.getByText('GitHub webhook', { exact: true }).click();
  const history = page.getByRole('region', { name: 'GitHub deliveries' });
  await expect(history.getByRole('listitem')).toHaveCount(3);
  await expect(history.getByText(/Push.*1 task linked, 0 moved/)).toBeVisible();
  await expectAccessible(page, 'github-deliveries');
});
