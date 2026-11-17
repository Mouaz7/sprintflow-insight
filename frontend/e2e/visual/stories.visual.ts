import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
const stories = [
  'charts--line',
  'charts--bar',
  'charts--scatter',
  'charts--cumulative-flow',
  'charts--distribution',
  'charts--empty',
  'charts--single-point',
  'states--empty',
  'states--loading',
  'states--failure',
];
for (const theme of ['light', 'dark'])
  for (const story of stories) {
    test(`${story} ${theme}: visual, semantics and keyboard`, async ({
      page,
    }) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await page.goto(
        `/iframe.html?id=design-system-${story}&viewMode=story&globals=theme:${theme};language:EN`,
      );
      const surface = page.locator('.story-surface');
      await expect(surface).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await page.evaluate(() => document.fonts.ready);
      await expect(surface).toHaveScreenshot(`${story}-${theme}.png`);
      const slider = page.getByRole('slider');
      if (await slider.count()) {
        const before = await slider.getAttribute('aria-valuetext');
        await slider.focus();
        await page.keyboard.press('End');
        if (story !== 'charts--single-point')
          expect(await slider.getAttribute('aria-valuetext')).not.toBe(before);
        await page.keyboard.press('Home');
        expect(await slider.getAttribute('aria-valuetext')).toBe(before);
        await page.getByText('View data as a table', { exact: true }).click();
        await expect(page.getByRole('table')).toBeVisible();
        expect(await page.getByRole('row').count()).toBeGreaterThan(1);
      }
      const result = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
        .analyze();
      expect(result.violations).toEqual([]);
      expect(errors).toEqual([]);
    });
  }
