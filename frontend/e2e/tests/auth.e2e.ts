import { expectAccessible } from '../support/a11y.ts';
import { Api, newAccount, removeAccount } from '../support/api.ts';
import { expect, navigationItem, test } from '../support/fixtures.ts';

test('registers, signs out and signs in again through the UI', async ({
  page,
  context,
}) => {
  const account = newAccount('login');
  const api = new Api(context.request);
  let registered = false;
  try {
    await page.goto('/');
    await expect(
      page.getByRole('button', { name: 'Log in', exact: true }),
    ).toBeVisible();
    await expectAccessible(page, 'sign-in-light');
    await page.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expectAccessible(page, 'sign-in-dark');
    await page.getByRole('button', { name: 'Switch to light mode' }).click();

    await page
      .getByRole('button', { name: 'Create account', exact: true })
      .click();
    await page.getByLabel('Name', { exact: true }).fill(account.displayName);
    await page.getByLabel('Email', { exact: true }).fill(account.email);
    await page.getByLabel('Password', { exact: true }).fill(account.password);
    await page
      .getByRole('button', { name: 'Create account', exact: true })
      .click();
    await expect(navigationItem(page, 'Overview')).toBeVisible();
    registered = true;
    await expectAccessible(page, 'overview-empty');

    await page.getByRole('button', { name: /· Profile$/ }).click();
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await expect(
      page.getByRole('button', { name: 'Log in', exact: true }),
    ).toBeVisible();
    expect(await api.status('/auth/me')).toBe(401);

    await page.getByLabel('Email', { exact: true }).fill(account.email);
    await page
      .getByLabel('Password', { exact: true })
      .fill(`${account.password}-wrong`);
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    await expect(page.getByRole('alert')).toBeVisible();

    await page.getByLabel('Password', { exact: true }).fill(account.password);
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    await expect(navigationItem(page, 'Overview')).toBeVisible();
    await page.reload();
    await expect(navigationItem(page, 'Overview')).toBeVisible();
  } finally {
    if (registered) await removeAccount(api, account);
  }
});
