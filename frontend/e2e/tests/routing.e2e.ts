import { expectAccessible } from '../support/a11y.ts';
import { expect, openView, test } from '../support/fixtures.ts';

test('deep links survive reload and navigation moves keyboard focus within one shell', async ({
  page,
  account,
}) => {
  expect(account.email).toContain('@example.com');
  await page.goto('/tasks');
  await expect(
    page.getByRole('heading', { name: 'Tasks', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('main')).toHaveCount(1);
  await expect(
    page.getByRole('combobox', { name: 'Project', exact: true }),
  ).toHaveCount(1);
  await expect(
    page.getByRole('link', { name: 'Skip to content' }),
  ).toHaveAttribute('href', '#workspace-page');
  await openView(page, 'Backlog');
  await expect(page).toHaveURL(/\/backlog$/);
  await expect(
    page.getByRole('heading', { name: 'Backlog', exact: true }),
  ).toBeFocused();
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Backlog', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Backlog', exact: true }),
  ).toHaveAttribute('aria-current', 'page');
  await expectAccessible(page, 'a6-deep-link');
});

test('legacy public links redirect without requesting a session for reports', async ({
  page,
}) => {
  const sessionRequests: string[] = [];
  page.on('request', (request) => {
    if (request.url().endsWith('/api/auth/me'))
      sessionRequests.push(request.url());
  });
  await page.goto('/?report=a6-expired-report');
  await expect(page).toHaveURL(/\/report\/a6-expired-report$/);
  await expect(page.getByRole('main')).toBeVisible();
  await expect(page.getByRole('alert')).toBeVisible();
  expect(sessionRequests).toEqual([]);
  await page.goto('/?resetToken=a6-expired-reset');
  await expect(page).toHaveURL(/\/reset-password\?token=a6-expired-reset$/);
  await expect(
    page.getByRole('heading', { name: 'Choose a new password.' }),
  ).toBeVisible();
  await expectAccessible(page, 'a6-reset-link');
});
