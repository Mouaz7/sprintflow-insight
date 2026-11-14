import assert from 'node:assert/strict';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { launchBrowser, type Page } from './browser.ts';
import { responsiveFixture } from './responsive-fixtures.ts';

// UI fixtures only: no real account, project or API write is used.
const base = process.env.SPRINTFLOW_QA_URL || 'http://localhost:8081';
const out = fileURLToPath(new URL('../../artifacts/guidance/', import.meta.url));
fs.mkdirSync(out, { recursive: true });
const browser = await launchBrowser();
const errors: string[] = [];
let layouts = 0;
let forms = 0;
async function layout(page: Page) {
  const geometry = await page.evaluate(() => ({
    width: innerWidth, scroll: document.documentElement.scrollWidth,
    clipped: [...document.querySelectorAll('.guidance p,.guidance dd,.form-guide > p')]
      .filter(el => el.getClientRects().length && el.scrollWidth > el.clientWidth + 1)
      .map(el => el.textContent),
  }));
  assert.ok(geometry.scroll <= geometry.width, JSON.stringify(geometry));
  assert.deepEqual(geometry.clipped, []);
  layouts++;
}
try {
  for (const language of ['SV', 'EN'] as const) for (const theme of ['light', 'dark'] as const) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await responsiveFixture(page, { language, theme });
    await page.goto(base);
    await page.locator('.page h1').waitFor();
    async function nav(index: number) {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.locator('.header-navigation nav button').nth(index).click();
      await page.locator('.page h1').waitFor();
    }
    for (let index = 0; index < 11; index++) {
      await nav(index);
      const guide = page.locator('.page-guide');
      assert.equal(await guide.count(), 1);
      const summary = guide.locator('summary');
      assert.equal(await summary.innerText(), language === 'SV' ? 'Så fungerar sidan' : 'How this page works');
      if (!await guide.evaluate(el => (el as HTMLDetailsElement).open)) {
        await summary.focus();
        await page.keyboard.press('Enter');
      }
      assert.equal(await guide.evaluate(el => (el as HTMLDetailsElement).open), true);
      assert.ok((await guide.locator('p').innerText()).length > 40);
      for (const width of [320, 390, 768, 1440, 844]) {
        await page.setViewportSize({ width, height: width === 844 ? 390 : 900 });
        await layout(page);
      }
      if (language === 'SV' && theme === 'light' && [0, 2, 8].includes(index)) {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.screenshot({ path: `${out}page-${index}-sv.png`, fullPage: true });
      }
    }
    for (const [view, trigger, topic] of [
      [1, /Skapa projekt|Create project/, 'project'],
      [2, /Ny berättelse|New story/, 'story'],
      [3, /Ny sprint|New sprint/, 'sprint'],
      [4, /Ny uppgift|New task/, 'task'],
      [9, /Ny anteckning|New note/, 'retro'],
    ] as const) {
      await nav(view);
      const button = page.getByRole('button', { name: trigger }).first();
      await button.waitFor();
      await button.click();
      const dialog = page.getByRole('dialog');
      await dialog.waitFor();
      const guide = dialog.locator('.form-guide');
      assert.equal(await guide.count(), 1);
      const values = () => dialog.locator('input,textarea,select').evaluateAll(elements => elements.map(el => (el as HTMLInputElement).value));
      const before = await values();
      const freeText = dialog.locator('textarea,input:not([type]),input[type=text]:not([readonly])');
      for (const field of await freeText.all()) {
        assert.equal(await field.inputValue(), '', `${topic}: examples must not become values`);
        assert.ok(await field.getAttribute('placeholder'), `${topic}: missing example`);
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await layout(page);
      if (language === 'SV' && theme === 'light') {
        await page.screenshot({ path: `${out}${topic}-examples-sv.png` });
      }
      await guide.locator('summary').focus();
      await page.keyboard.press('Enter');
      assert.equal(await guide.locator('details').evaluate(el => (el as HTMLDetailsElement).open), true);
      assert.deepEqual(await values(), before, 'Reading help must preserve the draft');
      await page.keyboard.press('Tab');
      assert.ok(await dialog.evaluate(el => el.contains(document.activeElement)), 'Focus stays in the panel');
      for (const width of [320, 390, 768, 1440, 844]) {
        await page.setViewportSize({ width, height: width === 844 ? 390 : 900 });
        await layout(page);
      }
      await page.setViewportSize({ width: 1440, height: 900 });
      if (language === 'SV' && theme === 'light') {
        await guide.screenshot({ path: `${out}${topic}-help-sv.png` });
      }
      await page.keyboard.press('Escape');
      await dialog.waitFor({ state: 'hidden' });
      assert.equal(await page.getByRole('alertdialog').count(), 0, 'Help must not dirty the form');
      forms++;
    }
    await context.close();
  }
  assert.deepEqual(errors, []);
  fs.writeFileSync(`${out}results.json`, JSON.stringify({ layouts, forms, errors, api: 'fixtures only' }, null, 2));
  console.log(`PASS: guidance on all 11 pages, ${forms} empty forms, ${layouts} expanded-help layouts, SV/EN and both themes; keyboard, unchanged drafts, empty values and screenshots verified. API fixtures only.`);
} finally {
  await browser.close();
}
