import { createHmac } from 'node:crypto';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

function authenticatorCode(encoded: string): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bits = [...encoded]
    .map((character) =>
      alphabet.indexOf(character).toString(2).padStart(5, '0'),
    )
    .join('');
  const secret = Buffer.from(
    (bits.match(/.{8}/g) ?? []).map((byte) => Number.parseInt(byte, 2)),
  );
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const digest = createHmac('sha1', secret).update(counter).digest();
  return String(
    (digest.readUInt32BE((digest[19] ?? 0) & 15) & 0x7fffffff) % 1_000_000,
  ).padStart(6, '0');
}

test('enrolls with a local QR code and verifies sign-in with a one-time recovery code', async ({
  account,
  api,
  page,
}) => {
  const policySlug = `two-factor-${Date.now()}`;
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Security' }).click();
  const settings = page.locator('#two-factor');
  await settings
    .getByLabel('Password for two-factor settings')
    .fill(account.password);
  await settings
    .getByRole('button', { name: 'Set up two-factor authentication' })
    .click();
  await expect(
    settings.getByRole('img', { name: 'Authenticator QR code' }),
  ).toBeVisible();
  const secret = await settings.getByLabel('Setup key').inputValue();
  await settings
    .getByLabel('Password for two-factor settings')
    .fill(account.password);
  await settings
    .getByLabel('Authentication code')
    .fill(authenticatorCode(secret));
  await settings
    .getByRole('button', { name: 'Enable two-factor authentication' })
    .click();
  const codes = settings.getByRole('list').locator('code');
  await expect(codes).toHaveCount(10);
  const recovery = await codes.allTextContents();
  const first = recovery[0];
  const disableWith = recovery[1];
  if (!first || !disableWith)
    throw new Error('Recovery codes were not issued.');
  await expectAccessible(page, 'two-factor-security');

  await api.json('/organizations', 'POST', {
    name: 'Authenticator policy',
    slug: policySlug,
  });
  await page.goto(`/o/${policySlug}/security`);
  const required = page.getByRole('checkbox', {
    name: 'Require two-factor authentication',
  });
  await required.click();
  await expect(required).toBeChecked();
  await expect
    .poll(
      async () =>
        (
          await api.json<{ requireTwoFactor: boolean }>(
            `/organizations/${policySlug}/security`,
          )
        ).requireTwoFactor,
    )
    .toBe(true);
  await required.click();
  await expect(required).not.toBeChecked();

  await page.goto('/profile');
  await page.getByRole('button', { name: 'Log out' }).click();
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Two-factor verification' }),
  ).toBeVisible();
  expect(await api.status('/auth/me')).toBe(401);
  await expectAccessible(page, 'two-factor-challenge');
  await page.getByLabel('Authentication code').fill(first);
  await page.getByRole('button', { name: 'Verify and sign in' }).click();
  await expect(page.getByRole('tab', { name: 'Security' })).toBeVisible();
  await page.getByRole('tab', { name: 'Security' }).click();
  await expect(settings.getByText('Recovery codes remaining: 9')).toBeVisible();
  await settings
    .getByLabel('Password for two-factor settings')
    .fill(account.password);
  await settings.getByLabel('Authentication code').fill(disableWith);
  await settings
    .getByRole('button', { name: 'Disable two-factor authentication' })
    .click();
  await expect(
    settings.getByText('Two-factor authentication disabled.'),
  ).toBeVisible();
});
