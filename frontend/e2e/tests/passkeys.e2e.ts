import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

const origin = new URL(process.env.E2E_BASE_URL ?? 'http://localhost:8081');
if (origin.hostname === '127.0.0.1') origin.hostname = 'localhost';
test.use({ baseURL: origin.toString() });

test('registers a browser passkey and signs in with user verification', async ({
  account,
  api,
  page,
}) => {
  const browser = await page.context().newCDPSession(page);
  await browser.send('WebAuthn.enable');
  const { authenticatorId } = await browser.send(
    'WebAuthn.addVirtualAuthenticator',
    {
      options: {
        protocol: 'ctap2',
        transport: 'internal',
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
        automaticPresenceSimulation: true,
      },
    },
  );
  try {
    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Security' }).click();
    const settings = page.locator('#passkeys');
    await settings.getByLabel('Passkey name').fill('Browser authenticator');
    await settings.getByRole('button', { name: 'Add passkey' }).click();
    await expect(
      settings.getByText('Browser authenticator', { exact: true }),
    ).toBeVisible();
    await expectAccessible(page, 'registered-passkey');
    const before =
      await api.json<{ id: string; lastUsed: string | null }[]>(
        '/auth/passkeys',
      );
    expect(before).toHaveLength(1);

    await page.getByRole('button', { name: 'Log out' }).click();
    await page.getByRole('button', { name: 'Sign in with a passkey' }).click();
    await expect(page.getByRole('tab', { name: 'Security' })).toBeVisible();
    expect((await api.json<{ email: string }>('/auth/me')).email).toBe(
      account.email,
    );
    const after =
      await api.json<{ id: string; lastUsed: string | null }[]>(
        '/auth/passkeys',
      );
    expect(after[0]?.lastUsed).toBeTruthy();
    const id = after[0]?.id;
    if (!id) throw new Error('Expected the registered passkey.');
    expect(
      await api.status(`/auth/passkeys/${encodeURIComponent(id)}`, 'DELETE'),
    ).toBe(204);
    expect(await api.json<unknown[]>('/auth/passkeys')).toHaveLength(0);
  } finally {
    await browser.send('WebAuthn.removeVirtualAuthenticator', {
      authenticatorId,
    });
    await browser.detach();
  }
});
