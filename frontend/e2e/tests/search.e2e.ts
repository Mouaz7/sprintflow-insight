import type { Locator } from '@playwright/test';
import { expectAccessible } from '../support/a11y.ts';
import { expect, test } from '../support/fixtures.ts';

interface Project {
  id: number;
}
interface Task {
  id: number;
  key: string;
}

/** A word no other run writes, so hits from a shared database cannot interfere. */
function uniqueWord(): string {
  const letters = 'abcdefghijklmnopqrstuvwxyz';
  let word = 'zq';
  for (let i = 0; i < 10; i++)
    word += letters[Math.floor(Math.random() * letters.length)];
  return word;
}

async function expectFilterContrast(palette: Locator) {
  const contrast = await palette
    .getByRole('combobox', { name: 'Custom field' })
    .evaluate((select) => {
      const style = getComputedStyle(select);
      const dialog = select.closest('[role="dialog"]');
      if (!dialog) throw new Error('The filter must be inside the palette.');
      const luminance = (color: string) => {
        const [red = 0, green = 0, blue = 0] = (color.match(/\d+/g) ?? []).map(
          (channel) => {
            const value = Number(channel) / 255;
            return value <= 0.04045
              ? value / 12.92
              : ((value + 0.055) / 1.055) ** 2.4;
          },
        );
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
      };
      const border = luminance(style.borderTopColor);
      return [style.backgroundColor, getComputedStyle(dialog).backgroundColor]
        .map(luminance)
        .map(
          (background) =>
            (Math.max(background, border) + 0.05) /
            (Math.min(background, border) + 0.05),
        );
    });
  for (const ratio of contrast) expect(ratio).toBeGreaterThanOrEqual(3);
}

test('searches from the command palette, jumps to a task key and shows the shortcut help', async ({
  page,
  api,
  account,
}) => {
  const word = uniqueWord();
  const suffix = account.email.split('@')[0]?.slice(-12) ?? 'run';
  const project = await api.json<Project>('/projects', 'POST', {
    name: `E2E search ${suffix}`,
  });
  const found = await api.json<Task>('/tasks', 'POST', {
    title: `Fix ${word} redirect`,
    status: 'To Do',
    projectId: project.id,
  });
  const other = await api.json<Task>('/tasks', 'POST', {
    title: 'Unrelated work',
    status: 'To Do',
    projectId: project.id,
  });

  await page.goto('/');
  await expect(
    page.getByRole('button', { name: 'Search', exact: true }),
  ).toBeVisible();

  // Ctrl+K opens the palette with focus in the combobox.
  await page.keyboard.press('Control+K');
  const palette = page.getByRole('dialog', { name: 'Search and commands' });
  const input = palette.getByRole('combobox', {
    name: 'Search or run a command',
  });
  await expect(input).toBeFocused();
  await input.fill(word);
  const option = palette.getByRole('option', {
    name: new RegExp(`Fix ${word} redirect`),
  });
  await expect(option).toBeVisible();
  await expect(option).toHaveAttribute('aria-selected', 'true');
  await expect(option.locator('mark')).toHaveText(word);
  await expectFilterContrast(palette);
  await expectAccessible(page, 'search-palette');

  // The same palette in dark mode.
  await page.keyboard.press('Escape');
  await expect(palette).toBeHidden();
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await page.keyboard.press('Control+K');
  await input.fill(word);
  await expect(option).toBeVisible();
  await expectFilterContrast(palette);
  await expectAccessible(page, 'search-palette-dark');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Switch to light mode' }).click();
  await page.keyboard.press('Control+K');
  await input.fill(word);
  await expect(option).toHaveAttribute('aria-selected', 'true');

  // Enter opens the board in the task's project, narrowed to the task.
  await input.press('Enter');
  await expect(palette).toBeHidden();
  const board = page.getByRole('searchbox', { name: /Search tasks/ });
  await expect(board).toHaveValue(found.key);
  await expect(page.getByText(`Fix ${word} redirect`)).toBeVisible();
  await expect(page.getByText('Unrelated work')).toBeHidden();

  // A task key jumps straight to that task.
  await page.getByRole('heading', { level: 1 }).click();
  await page.keyboard.press('Control+K');
  await input.fill(other.key.toLowerCase());
  await input.press('Enter');
  await expect(palette).toBeHidden();
  await expect(board).toHaveValue(other.key);
  await expect(page.getByText('Unrelated work')).toBeVisible();

  // ? shows the shortcut help as a modal dialog; Escape closes it.
  await page.getByRole('heading', { level: 1 }).click();
  await page.keyboard.press('Shift+?');
  const help = page.getByRole('dialog', { name: 'Keyboard shortcuts' });
  await expect(help).toBeVisible();
  await expect(help.getByRole('button', { name: 'Close' })).toBeFocused();
  await expectAccessible(page, 'shortcut-help');
  await page.keyboard.press('Escape');
  await expect(help).toBeHidden();

  // g then o navigates to the overview.
  await page.keyboard.press('g');
  await page.keyboard.press('o');
  await expect(
    page
      .getByRole('navigation', { name: 'Main navigation' })
      .getByRole('link', { name: 'Overview', exact: true }),
  ).toHaveAttribute('aria-current', 'page');
});

test('turns off character shortcuts by keyboard and remembers the choice after reload', async ({
  page,
  api,
  account,
}) => {
  await api.json('/projects', 'POST', {
    name: 'Character shortcut settings',
    description: account.email,
  });
  const search = page.getByRole('button', { name: 'Search', exact: true });
  const main = page.getByRole('main');
  const help = page.getByRole('dialog', { name: 'Keyboard shortcuts' });
  const preference = help.getByRole('checkbox', {
    name: 'Enable shortcuts made only of letters or symbols',
  });
  const showHelp = async () => {
    await search.waitFor();
    await page.keyboard.press('Control+K');
    const palette = page.getByRole('dialog', { name: 'Search and commands' });
    const input = palette.getByRole('combobox', {
      name: 'Search or run a command',
    });
    await expect(input).toBeFocused();
    await input.fill('Keyboard shortcuts');
    await expect(
      palette.getByRole('option', { name: 'Keyboard shortcuts' }),
    ).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Enter');
    await expect(help.getByRole('button', { name: 'Close' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(preference).toBeFocused();
  };

  await page.goto('/tasks');
  await expect(page.getByRole('button', { name: /New task/ })).toBeEnabled();
  await main.focus();
  await page.keyboard.press('c');
  const newTask = page.getByRole('dialog', { name: 'New task' });
  await expect(newTask).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(newTask).toBeHidden();

  await showHelp();
  await expect(preference).toBeChecked();
  await page.keyboard.press('Space');
  await expect(preference).not.toBeChecked();
  await page.keyboard.press('Escape');
  await expect(help).toBeHidden();

  await page.reload();
  await showHelp();
  await expect(preference).not.toBeChecked();
  await page.keyboard.press('Escape');
  await expect(help).toBeHidden();

  await main.focus();
  for (const key of ['c', '/', 'Shift+?']) {
    await page.keyboard.press(key);
    await expect(page.getByRole('dialog')).toHaveCount(0);
  }
  await page.keyboard.press('g');
  await page.keyboard.press('m');
  await expect(page).toHaveURL(/\/tasks$/);

  await showHelp();
  await page.keyboard.press('Space');
  await expect(preference).toBeChecked();
  await page.keyboard.press('Escape');
  await expect(help).toBeHidden();
  await main.focus();
  await page.keyboard.press('g');
  await page.keyboard.press('m');
  await expect(page).toHaveURL(/\/metrics$/);
});

test('keeps the active search option visible when keyboard navigation crosses the scroll window', async ({
  page,
  api,
  account,
}) => {
  const word = uniqueWord();
  const project = await api.json<Project>('/projects', 'POST', {
    name: `Keyboard scroll ${account.email.slice(-20)}`,
  });
  for (let index = 0; index < 12; index++)
    await api.json('/tasks', 'POST', {
      title: `${word} result ${index}`,
      status: 'To Do',
      projectId: project.id,
    });
  await page.goto('/overview');
  await page.getByRole('button', { name: 'Search', exact: true }).waitFor();
  await page.keyboard.press('Control+K');
  const palette = page.getByRole('dialog', { name: 'Search and commands' });
  const input = palette.getByRole('combobox', {
    name: 'Search or run a command',
    exact: true,
  });
  await input.fill(word);
  const options = palette.getByRole('listbox').getByRole('option');
  await expect(options).toHaveCount(12);
  const visibleSelection = () =>
    palette
      .locator('[role="option"][aria-selected="true"]')
      .evaluate((option) => {
        const list = option.closest('[role="listbox"]');
        if (!list) return false;
        const selected = option.getBoundingClientRect();
        const viewport = list.getBoundingClientRect();
        return (
          selected.top >= viewport.top - 1 &&
          selected.bottom <= viewport.bottom + 1
        );
      });
  await input.press('Control+End');
  await expect(input).toHaveAttribute(
    'aria-activedescendant',
    (await options.last().getAttribute('id')) ?? '',
  );
  await expect.poll(visibleSelection).toBe(true);
  await input.press('Control+Home');
  await expect(input).toHaveAttribute(
    'aria-activedescendant',
    (await options.first().getAttribute('id')) ?? '',
  );
  await expect.poll(visibleSelection).toBe(true);
  await expect(input).toBeFocused();
});
