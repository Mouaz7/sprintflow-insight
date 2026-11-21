import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test('creates a scoped personal token and revokes its public API access', async ({
  account,
  api,
  page,
}) => {
  await api.json('/projects', 'POST', {
    name: 'Public API browser fixture',
    description: account.email,
  });
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Security' }).click();
  const tokens = page.locator('#api-tokens');
  await tokens.getByText('Personal access tokens', { exact: true }).click();
  await tokens.getByLabel('Token name').fill('Reporting client');
  await tokens.getByRole('checkbox', { name: 'Read projects' }).check();
  await tokens.getByRole('button', { name: 'Create access token' }).click();
  const input = tokens.getByLabel('New access token');
  const token = await input.inputValue();
  expect(token).toMatch(/^sf_pat_/);
  await tokens.getByRole('button', { name: 'Hide token' }).click();
  await expect(input).toHaveCount(0);
  const headers = { Authorization: `Bearer ${token}` };
  const response = await page.request.get('/api/v1/projects', { headers });
  expect(response.status()).toBe(200);
  expect((await response.json()).items).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ name: 'Public API browser fixture' }),
    ]),
  );
  expect((await page.request.get('/api/v1/projects')).status()).toBe(401);
  expect((await page.request.get('/api/v1/openapi')).status()).toBe(200);
  await expectAccessible(page, 'personal-access-token');
  await tokens.getByRole('button', { name: 'Revoke token' }).click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm' })
    .click();
  await expect(tokens.getByText('No access tokens yet.')).toBeVisible();
  expect(
    (await page.request.get('/api/v1/projects', { headers })).status(),
  ).toBe(401);
});

test('configures an outgoing webhook and reads its delivery history', async ({
  account,
  api,
  page,
}) => {
  await api.json('/projects', 'POST', {
    name: 'Webhook browser fixture',
    description: account.email,
  });
  await page.goto('/automation');
  await page.getByText('Outgoing webhooks', { exact: true }).click();
  await page
    .getByLabel('Webhook HTTPS URL')
    .fill('https://1.1.1.1/sprintflow-fixture');
  await page.getByRole('button', { name: 'Create webhook' }).click();
  await expect(page.getByLabel('New signing secret')).toBeVisible();
  await page.getByRole('button', { name: 'Hide signing secret' }).click();
  await expect(page.getByLabel('New signing secret')).toHaveCount(0);
  await page.getByText('Delivery history', { exact: true }).click();
  await expect(page.getByText('No deliveries yet.')).toBeVisible();
  await expectAccessible(page, 'outbound-webhook');
  await page.getByRole('button', { name: 'Remove webhook' }).click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm' })
    .click();
  await expect(page.getByText('No outgoing webhooks yet.')).toBeVisible();
});
