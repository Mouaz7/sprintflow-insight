import { Api, newAccount, register, removeAccount } from '../support/api.ts';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

test.use({ actionTimeout: 10_000 });

test('edits all five field types, filters board and search, exports exact values and preserves conflicting drafts', async ({
  page,
  api,
  account,
}, testInfo) => {
  const project = await api.json<{ id: number }>('/projects', 'POST', {
    name: 'Custom field delivery',
    description: account.email,
    key: 'CF',
  });
  const task = await api.json<{ id: number }>('/tasks', 'POST', {
    projectId: project.id,
    title: 'Precise delivery',
    status: 'To Do',
    storyPoints: 1,
  });
  await api.json('/tasks', 'POST', {
    projectId: project.id,
    title: 'Other delivery',
    status: 'To Do',
    storyPoints: 1,
  });
  await page.goto('/fields');
  await expect(
    page.getByRole('heading', { name: 'Custom fields', exact: true }),
  ).toBeVisible();
  for (const [name, kind] of [
    ['Reference', 'TEXT'],
    ['Budget', 'NUMBER'],
    ['Review date', 'DATE'],
    ['Stage', 'SELECT'],
    ['Reviewer', 'USER'],
  ] as const) {
    await page
      .getByRole('button', { name: 'New custom field', exact: true })
      .click();
    const dialog = page.getByRole('dialog');
    await expect(
      dialog.getByRole('textbox', { name: 'Field name', exact: true }),
    ).toHaveValue('');
    await dialog
      .getByRole('textbox', { name: 'Field name', exact: true })
      .fill(name);
    await dialog
      .getByRole('combobox', { name: 'Field type', exact: true })
      .selectOption(kind);
    if (kind === 'SELECT')
      await dialog
        .getByRole('textbox', { name: 'Options, one per line', exact: true })
        .fill('Ready\nWaiting');
    await expectAccessible(page, `custom-definition-${kind}`);
    await dialog
      .getByRole('button', { name: 'Save field', exact: true })
      .click();
    await expect(dialog).toHaveCount(0);
    await expect(
      page.getByRole('heading', { name, exact: true }),
    ).toBeVisible();
  }
  await page.reload();
  await expect(
    page.getByText('5 of 20 project fields', { exact: true }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Back to tasks', exact: true }).click();
  const card = page
    .locator('.task-board article')
    .filter({ has: page.getByText('Precise delivery', { exact: true }) });
  await card
    .getByRole('button', { name: 'Custom fields', exact: true })
    .click();
  const drawer = page.getByRole('dialog');
  await drawer
    .getByRole('textbox', { name: 'Reference', exact: true })
    .fill('=1+1');
  await drawer
    .getByRole('textbox', { name: 'Budget', exact: true })
    .fill('9007199254740993.123456');
  await drawer
    .getByRole('textbox', { name: 'Review date', exact: true })
    .fill('2026-11-18');
  await drawer
    .getByRole('combobox', { name: 'Stage', exact: true })
    .selectOption('Ready');
  await drawer
    .getByRole('combobox', { name: 'Reviewer', exact: true })
    .selectOption({ label: account.email });
  await expectAccessible(page, 'custom-values-all-types');
  await drawer
    .getByRole('button', { name: 'Save values', exact: true })
    .click();
  await expect(drawer).toHaveCount(0);
  const snapshot = await api.json<{
    version: number;
    values: { fieldId: number; value: string }[];
  }>(`/tasks/${task.id}/custom-fields`);
  expect(snapshot.version).toBe(1);
  expect(snapshot.values.map((value) => value.value)).toContain(
    '9007199254740993.123456',
  );
  await page
    .getByRole('combobox', { name: 'Custom field', exact: true })
    .selectOption({ label: 'Budget' });
  await page
    .getByRole('combobox', { name: 'Field condition', exact: true })
    .selectOption('GREATER_THAN');
  await page
    .getByRole('textbox', { name: 'Filter value', exact: true })
    .fill('9007199254740993');
  await expect(
    page.getByText('Precise delivery', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('Other delivery', { exact: true })).toHaveCount(
    0,
  );
  const exportLink = page.getByRole('link', {
    name: 'Export filtered tasks (CSV)',
    exact: true,
  });
  const csvPath = await exportLink.getAttribute('href');
  if (!csvPath) throw new Error('The CSV export link must have a URL.');
  const csv = await page.request.get(csvPath);
  expect(csv.ok()).toBe(true);
  expect(await csv.text()).toContain('9007199254740993.123456');
  expect(await csv.text()).toContain("'=1+1");
  expect(await csv.text()).toContain(account.email);
  expect(await csv.text()).not.toContain('Other delivery');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  const palette = page.getByRole('dialog', {
    name: 'Search and commands',
    exact: true,
  });
  await palette
    .getByRole('combobox', { name: 'Custom field', exact: true })
    .selectOption({ label: 'Budget' });
  await palette
    .getByRole('combobox', { name: 'Field condition', exact: true })
    .selectOption('LESS_THAN');
  await palette
    .getByRole('textbox', { name: 'Filter value', exact: true })
    .fill('0');
  const search = palette.getByRole('combobox', {
    name: 'Search or run a command',
    exact: true,
  });
  await search.fill('CF-1');
  await expect(
    palette.getByText('No matches. Try fewer or different words.', {
      exact: true,
    }),
  ).toBeVisible();
  await search.press('Enter');
  await expect(palette).toBeVisible();
  await expectAccessible(page, 'custom-search-filter');
  await search.press('Escape');
  await page
    .getByRole('button', { name: 'Clear field filter', exact: true })
    .click();
  await card
    .getByRole('button', { name: 'Custom fields', exact: true })
    .click();
  await drawer
    .getByRole('textbox', { name: 'Budget', exact: true })
    .fill('777');
  await api.json(`/tasks/${task.id}`, 'PUT', {
    projectId: project.id,
    title: 'Precise delivery',
    status: 'To Do',
    storyPoints: 2,
  });
  await drawer
    .getByRole('button', { name: 'Save values', exact: true })
    .click();
  await expect(drawer.getByRole('alert')).toContainText('Your draft is kept');
  await expect(
    drawer.getByRole('textbox', { name: 'Budget', exact: true }),
  ).toHaveValue('777');
  await drawer
    .getByRole('button', { name: 'Reload saved values', exact: true })
    .click();
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirm', exact: true })
    .click();
  await expect(
    drawer.getByRole('textbox', { name: 'Budget', exact: true }),
  ).toHaveValue('9007199254740993.123456');
  await drawer.getByRole('button', { name: 'Cancel', exact: true }).click();
  await page
    .getByRole('combobox', { name: 'Custom field', exact: true })
    .selectOption({ label: 'Budget' });
  await expect(
    page.getByText('Precise delivery', { exact: true }),
  ).toBeVisible();
  const definitions = await api.json<
    { id: number; name: string; version: number }[]
  >(`/projects/${project.id}/custom-fields`);
  const budget = definitions.find((field) => field.name === 'Budget');
  if (!budget) throw new Error('Budget definition is required.');
  const csrf = await api.json<{ headerName: string; token: string }>(
    '/auth/csrf',
  );
  const deleted = await page.request.delete(
    `/api/projects/${project.id}/custom-fields/${budget.id}`,
    {
      headers: {
        [csrf.headerName]: csrf.token,
        'If-Match': `"${budget.version}"`,
      },
    },
  );
  expect(deleted.status()).toBe(204);
  await expect(
    page.getByRole('option', {
      name: 'Previous value is no longer available',
      exact: true,
    }),
  ).toBeAttached();
  await expect(page.getByRole('alert')).toContainText('Custom field not found');
  await expect(page.getByText('Precise delivery', { exact: true })).toHaveCount(
    0,
  );
  await expect(
    page.getByText('Export filtered tasks (CSV)', { exact: true }),
  ).toHaveAttribute('aria-disabled', 'true');
  await page
    .getByRole('button', { name: 'Clear field filter', exact: true })
    .click();
  await expect(
    page.getByText('Precise delivery', { exact: true }),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Switch to dark mode', exact: true })
    .click();
  await page
    .getByRole('combobox', { name: 'Language', exact: true })
    .selectOption('SV');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('link', { name: 'Anpassade fält', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Anpassade fält', exact: true }),
  ).toBeVisible();
  await expectAccessible(page, 'custom-fields-swedish-dark-mobile');
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath('custom-fields-sv-dark-mobile.png'),
    fullPage: true,
  });
});

test('allows viewer inspection, clears removed memberships and keeps archived values read-only', async ({
  page,
  browser,
  api,
  account,
  baseURL,
}) => {
  const context = await browser.newContext({ baseURL });
  const viewer = newAccount('fields-viewer');
  const peerApi = new Api(context.request);
  let registered = false;
  try {
    await register(peerApi, viewer);
    registered = true;
    const project = await api.json<{ id: number }>('/projects', 'POST', {
      name: 'Field role boundaries',
      description: account.email,
    });
    await api.json(`/projects/${project.id}/members`, 'POST', {
      email: viewer.email,
      role: 'Viewer',
    });
    await api.json(`/projects/${project.id}/custom-fields`, 'POST', {
      name: 'Reviewer',
      kind: 'USER',
      options: [],
    });
    const task = await api.json<{ id: number }>('/tasks', 'POST', {
      projectId: project.id,
      title: 'Review assignment',
      status: 'To Do',
      storyPoints: 1,
    });
    await page.goto('/tasks');
    await page
      .locator('.task-board article')
      .getByRole('button', { name: 'Custom fields', exact: true })
      .click();
    const drawer = page.getByRole('dialog');
    await drawer
      .getByRole('combobox', { name: 'Reviewer', exact: true })
      .selectOption({ label: viewer.email });
    await drawer
      .getByRole('button', { name: 'Save values', exact: true })
      .click();
    await expect(drawer).toHaveCount(0);
    const peer = await context.newPage();
    await peer.goto('/fields');
    await expect(
      peer.getByRole('button', { name: 'New custom field', exact: true }),
    ).toBeDisabled();
    await peer
      .getByRole('link', { name: 'Back to tasks', exact: true })
      .click();
    await peer
      .locator('.task-board article')
      .getByRole('button', { name: 'Custom fields', exact: true })
      .click();
    await expect(
      peer.getByRole('dialog').getByText(viewer.email, { exact: true }),
    ).toBeVisible();
    await expect(
      peer
        .getByRole('dialog')
        .getByRole('button', { name: 'Save values', exact: true }),
    ).toHaveCount(0);
    await expectAccessible(peer, 'custom-fields-viewer');
    await api.json(
      `/projects/${project.id}/members/${encodeURIComponent(viewer.email)}`,
      'DELETE',
    );
    const cleared = await api.json<{ values: unknown[] }>(
      `/tasks/${task.id}/custom-fields`,
    );
    expect(cleared.values).toEqual([]);
    await api.json(`/projects/${project.id}/archive`, 'PUT', {
      archived: true,
    });
    await page.reload();
    await page
      .locator('.task-board article')
      .getByRole('button', { name: 'Custom fields', exact: true })
      .click();
    await expect(
      page.getByRole('dialog').getByText('Not set', { exact: true }),
    ).toBeVisible();
    await expect(
      page
        .getByRole('dialog')
        .getByRole('button', { name: 'Save values', exact: true }),
    ).toHaveCount(0);
    await expectAccessible(page, 'custom-fields-archived');
  } finally {
    if (registered) await removeAccount(peerApi, viewer);
    await context.close();
  }
});
