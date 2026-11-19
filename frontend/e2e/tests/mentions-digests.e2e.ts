import type { APIRequestContext } from '@playwright/test';
import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';
test.use({ actionTimeout: 10_000 });

type Digest = {
  frequency: 'OFF' | 'DAILY' | 'WEEKLY';
  timeZone: string;
  version: number;
  mailConfigured: boolean;
};
async function saveDigest(
  request: APIRequestContext,
  frequency: Digest['frequency'],
  timeZone: string,
) {
  const saved = (await (
    await request.get('/api/auth/digest')
  ).json()) as Digest;
  const csrf = (await (await request.get('/api/auth/csrf')).json()) as {
    headerName: string;
    token: string;
  };
  const response = await request.put('/api/auth/digest', {
    headers: {
      [csrf.headerName]: csrf.token,
      'If-Match': `"${saved.version}"`,
    },
    data: { frequency, timeZone },
  });
  expect(response.status()).toBe(200);
}
test('inserts an unambiguous mention with the keyboard and delivers a real-time notification to a Viewer', async ({
  page,
  api,
  account,
  browser,
  baseURL,
}, testInfo) => {
  const peerContext = await browser.newContext({
    baseURL,
    viewport: { width: 360, height: 800 },
    reducedMotion: 'reduce',
  });
  const peerApi = new Api(peerContext.request);
  const teammate = newAccount('mention-viewer');
  let registered = false;
  try {
    await register(peerApi, teammate);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Mention delivery',
      description: account.email,
      key: 'ME',
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: teammate.email,
      role: 'Viewer',
    });
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      title: 'Mention target',
      status: 'To Do',
    });
    await peerContext.addInitScript(() => {
      localStorage.setItem('sprintflow-language', 'SV');
      localStorage.setItem('sprintflow-theme', 'dark');
    });
    const peer = await peerContext.newPage();
    await peer.goto('/tasks');
    await page.goto('/tasks');
    await expect(peer.locator('.presence')).toHaveClass(/presence-live/);
    await page
      .locator('.task-board article')
      .filter({ hasText: 'Mention target' })
      .getByRole('button', { name: 'Comments', exact: true })
      .click();
    const discussion = page.getByRole('dialog', {
      name: 'Comments · Mention target',
    });
    const input = discussion.getByRole('textbox', { name: 'New comment' });
    await input.fill(`Please review @${teammate.email.split('@')[0]}`);
    await expect(
      discussion.getByRole('button', { name: teammate.email, exact: true }),
    ).toBeVisible();
    await input.press('ArrowDown');
    await expect(
      discussion.getByRole('button', { name: teammate.email, exact: true }),
    ).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(input).toHaveValue(`Please review @${teammate.email} `);
    await expectAccessible(page, 'mention-comment-editor');
    await discussion
      .getByRole('button', { name: 'Post comment', exact: true })
      .click();
    await expect(
      discussion.getByText(`Please review @${teammate.email}`, { exact: true }),
    ).toBeVisible();
    const bell = peer.getByRole('button', { name: /Notiser/ });
    await expect(bell).toHaveAccessibleName(/1/);
    await bell.click();
    await expect(
      peer.getByRole('button', {
        name: /Du nämndes i en kommentar på “Mention target”\./,
      }),
    ).toBeVisible();
    const received = () =>
      peerApi.json<{ message: string }[]>('/notifications');
    expect(
      (await received()).filter((n) => n.message.startsWith('Mentioned you')),
    ).toHaveLength(1);
    await discussion.getByRole('button', { name: 'Edit', exact: true }).click();
    await discussion
      .getByRole('textbox', { name: 'Edit comment' })
      .fill(`Please review @${teammate.email} today`);
    await discussion
      .getByRole('button', { name: 'Save comment', exact: true })
      .click();
    await expect(
      discussion.getByText(`Please review @${teammate.email} today`, {
        exact: true,
      }),
    ).toBeVisible();
    expect(
      (await received()).filter((n) => n.message.startsWith('Mentioned you')),
    ).toHaveLength(1);
    expect(
      await peerApi.status(`/tasks/${task.id}/comments`, 'POST', {
        message: 'Viewer cannot write',
      }),
    ).toBe(403);
    await expectAccessible(peer, 'mention-notification-swedish-mobile');
    expect(
      await peer.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await testInfo.attach('mention-mobile-dark', {
      body: await peer.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
  } finally {
    if (registered) await removeAccount(peerApi, teammate);
    await peerContext.close();
  }
});

test('persists opt-in digest settings, preserves conflicts and confirms public opt-out without signing in', async ({
  page,
  api,
  account,
  browser,
  baseURL,
}, testInfo) => {
  expect((await api.json<{ email: string }>('/auth/me')).email).toBe(
    account.email,
  );
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Settings', exact: true }).click();
  const frequency = page.getByRole('combobox', {
    name: 'Summary frequency',
    exact: true,
  });
  const zone = page.getByRole('combobox', {
    name: 'Time zone (IANA)',
    exact: true,
  });
  await expect(frequency).toHaveValue('OFF');
  await expect(page.getByText(/Email is not configured/)).toBeVisible();
  expect((await api.json<Digest>('/auth/digest')).mailConfigured).toBe(false);
  await frequency.selectOption('DAILY');
  await zone.fill('Europe/Stockholm');
  await page
    .getByRole('button', { name: 'Save email summaries', exact: true })
    .click();
  await expect(
    page.getByText('Email summary settings saved.', { exact: true }),
  ).toBeVisible();
  await page.reload();
  await page.getByRole('tab', { name: 'Settings', exact: true }).click();
  await expect(frequency).toHaveValue('DAILY');
  await expect(zone).toHaveValue('Europe/Stockholm');
  await frequency.selectOption('WEEKLY');
  await saveDigest(page.request, 'OFF', 'UTC');
  await page
    .getByRole('button', { name: 'Save email summaries', exact: true })
    .click();
  await expect(page.getByRole('alert')).toContainText(
    'Your draft is still here',
  );
  await expect(frequency).toHaveValue('WEEKLY');
  await expect(
    page.getByRole('button', { name: 'Save email summaries', exact: true }),
  ).toBeDisabled();
  await page
    .getByRole('button', { name: 'Load saved settings', exact: true })
    .click();
  await expect(frequency).toHaveValue('OFF');
  await frequency.selectOption('WEEKLY');
  await zone.fill('Europe/Stockholm');
  await page
    .getByRole('button', { name: 'Save email summaries', exact: true })
    .click();
  await expect(
    page.getByText('Email summary settings saved.', { exact: true }),
  ).toBeVisible();
  const persisted = await api.json<Digest>('/auth/digest');
  expect(persisted.frequency).toBe('WEEKLY');
  expect(persisted.timeZone).toBe('Europe/Stockholm');
  await expectAccessible(page, 'digest-settings');
  await page.setViewportSize({ width: 360, height: 800 });
  await page.addInitScript(() => {
    localStorage.setItem('sprintflow-language', 'SV');
    localStorage.setItem('sprintflow-theme', 'dark');
  });
  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Inställningar', exact: true }).click();
  await expect(
    page.getByRole('combobox', {
      name: 'Sammanfattningsintervall',
      exact: true,
    }),
  ).toHaveValue('WEEKLY');
  await expectAccessible(page, 'digest-settings-swedish-mobile');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await testInfo.attach('digest-mobile-dark', {
    body: await page.screenshot({ fullPage: true }),
    contentType: 'image/png',
  });
  const anonymous = await browser.newContext({
    baseURL,
    reducedMotion: 'reduce',
  });
  try {
    const publicPage = await anonymous.newPage();
    await publicPage.goto('/email-preferences?token=broken');
    await expect(
      publicPage.getByRole('heading', {
        name: 'Stop email summaries',
        exact: true,
      }),
    ).toBeVisible();
    expect((await api.json<Digest>('/auth/digest')).frequency).toBe('WEEKLY');
    await publicPage
      .getByRole('button', { name: 'Stop email summaries', exact: true })
      .click();
    await expect(publicPage.getByRole('alert')).toContainText(
      'invalid or has expired',
    );
    await expectAccessible(publicPage, 'unsubscribe-public-invalid');
    expect((await anonymous.request.get('/api/auth/digest')).status()).toBe(
      401,
    );
    expect((await api.json<Digest>('/auth/digest')).frequency).toBe('WEEKLY');
  } finally {
    await anonymous.close();
  }
});
