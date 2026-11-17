import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { launchBrowser, type Page } from './browser.ts';
import { responsiveFixture } from './responsive-fixtures.ts';

const base = process.env.SPRINTFLOW_QA_URL || 'http://127.0.0.1:8081';
const out = fileURLToPath(new URL('../../artifacts/responsive/', import.meta.url));
const widths = [320, 390, 767, 768, 1023, 1024, 1279, 1280, 1920, 2560];
const views = ['overview', 'projects', 'backlog', 'sprints', 'tasks', 'metrics', 'updates', 'profile', 'calendar', 'retrospective', 'trash'];
fs.mkdirSync(out, { recursive: true });
const results: { label: string; width: number; height: number }[] = [];

async function checkLayout(page: Page, label: string, workspace = true) {
  const geometry = await page.evaluate(() => {
    const visible = (el: Element) => el.getClientRects().length && getComputedStyle(el).visibility !== 'hidden';
    const shell = document.querySelector('.workspace-shell');
    const footer = document.querySelector('.workspace-footer')?.getBoundingClientRect();
    const content = document.querySelector('.workspace-content')?.getBoundingClientRect();
    return {
      width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth,
      scrollHeight: document.documentElement.scrollHeight,
      shellDisplay: shell && getComputedStyle(shell).display,
      footerBottom: footer && footer.bottom + scrollY,
      footerTop: footer && footer.top + scrollY,
      contentBottom: content && content.bottom + scrollY,
      clipped: [...document.querySelectorAll('button:not(.notification-bell),h1,h2,h3,p')].filter(el => visible(el) && el.clientWidth > 0 && el.scrollWidth > el.clientWidth + 2).map(el => ({ tag: el.tagName, class: el.className, text: el.textContent?.slice(0, 70) })),
      smallTargets: matchMedia('(pointer: coarse)').matches || innerWidth < 768
        ? [...document.querySelectorAll('button,summary,.export-actions a,nav a,.account-profile')].filter(el => visible(el) && (el.getBoundingClientRect().height < 43.9 || el.getBoundingClientRect().width < 43.9)).map(el => el.textContent?.slice(0, 50)) : [],
      tinyFields: innerWidth < 768 ? [...document.querySelectorAll('input:not([type=checkbox]):not([type=hidden]),select,textarea')].filter(el => visible(el) && getComputedStyle(el).opacity !== '0' && parseFloat(getComputedStyle(el).fontSize) < 16).map(el => el.className) : [],
    };
  });
  try {
    assert.ok(geometry.scrollWidth <= geometry.width, `${label}: horizontal overflow ${JSON.stringify(geometry)}`);
    assert.deepEqual(geometry.clipped, [], `${label}: clipped content`);
    assert.deepEqual(geometry.smallTargets, [], `${label}: touch target below 44px`);
    assert.deepEqual(geometry.tinyFields, [], `${label}: field text below 16px`);
    if (workspace) {
      assert.equal(geometry.shellDisplay, 'flex', 'The server must serve the updated CSS');
      assert.ok(Math.abs(geometry.footerBottom! - geometry.scrollHeight) <= 1, `${label}: footer not at document bottom`);
      assert.ok(geometry.footerBottom! >= geometry.height - 1, `${label}: footer above viewport bottom`);
      assert.ok(geometry.footerTop! >= geometry.contentBottom! - 1, `${label}: footer overlaps content`);
      const mobile = geometry.width < 1024;
      assert.equal(await page.locator('.menu-toggle').isVisible(), mobile, `${label}: menu breakpoint`);
      assert.equal(await page.locator('.header-navigation').isVisible(), !mobile, `${label}: desktop navigation breakpoint`);
      const contentWidth = await page.locator('.page').evaluate(el => el.getBoundingClientRect().width);
      assert.ok(contentWidth <= 1800, `${label}: content exceeds 1800px`);
      if (geometry.width >= 768 && geometry.width < 1024) {
        // Controls have different heights (the presence dot is intentionally small).
        // Compare centre lines so actual wrapping fails without treating centred icons as a row.
        const row = await page.locator('.header-controls').evaluate(el => [...el.children].map(child => {
          const bounds = child.getBoundingClientRect();
          return bounds.top + bounds.height / 2;
        }));
        assert.ok(Math.max(...row) - Math.min(...row) < 10, `${label}: tablet controls wrap`);
      }
    }
    results.push({ label, width: geometry.width, height: geometry.height });
  } catch (error) {
    await page.screenshot({ path: path.join(out, 'failure.png'), fullPage: true });
    throw error;
  }
}

async function navigate(page: Page, view: number) {
  // Switch views at desktop width; then exercise the responsive layout itself.
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.locator('.header-navigation nav a').nth(view).click();
  // Views are lazy route chunks: wait until the new route has committed.
  await page.waitForFunction(i => document.querySelectorAll('.header-navigation nav a')[i]?.getAttribute('aria-current') === 'page', view);
  await page.locator('.page h1').waitFor();
  await page.waitForLoadState('networkidle');
}

async function checkOverlay(page: Page, selector: string) {
  const geometry = await page.locator(selector).evaluate(el => {
    const r = el.getBoundingClientRect();
    return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, height: innerHeight, width: innerWidth, overflow: getComputedStyle(el).overflowY };
  });
  assert.ok(geometry.top >= -1 && geometry.bottom <= geometry.height + 1, `${selector}: outside viewport ${JSON.stringify(geometry)}`);
  assert.ok(geometry.left >= -1 && geometry.right <= geometry.width + 1, `${selector}: outside viewport`);
  assert.ok(['auto', 'scroll'].includes(geometry.overflow), `${selector}: must scroll`);
  const scrollable = page.locator(selector === '.editor-drawer' ? '.drawer-body' : selector);
  await scrollable.evaluate(el => { el.scrollTop = el.scrollHeight; });
}

(async () => {
  const browser = await launchBrowser();
  const errors: string[] = [];
  try {
    for (const language of ['EN', 'SV'] as const) for (const theme of ['light', 'dark'] as const) for (const empty of [false, true]) {
      const context = await browser.newContext({ reducedMotion: 'reduce' });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await responsiveFixture(page, { language, theme, empty });
      await page.goto(base);
      await page.locator('.page h1').waitFor();
      await page.evaluate(() => document.fonts.ready);
      for (let view = 0; view < views.length; view++) {
        await navigate(page, view);
        for (const width of widths) {
          await page.setViewportSize({ width, height: 900 });
          const label = `${language}/${theme}/${empty ? 'empty' : 'populated'}/${views[view]}`;
          await checkLayout(page, label);
          if (!empty && view === 4) {
            const columns = await page.locator('.task-board').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length);
            assert.equal(columns, width >= 1280 ? 4 : width >= 768 ? 2 : 1, `${label}: board columns`);
          }
          if (!empty && view === 8) {
            const columns = await page.locator('.calendar-grid').evaluate(el => getComputedStyle(el).gridTemplateColumns.split(' ').length);
            assert.equal(columns, width < 768 ? 1 : 7, `${label}: calendar columns`);
          }
          if (language === 'SV' && theme === 'light' && !empty && [0, 4, 8].includes(view) && [320, 768, 1024, 2560].includes(width)) {
            await page.screenshot({ path: path.join(out, `${views[view]}-${width}.png`), fullPage: true });
          }
        }
        await page.setViewportSize({ width: 844, height: 390 });
        await checkLayout(page, `${language}/${theme}/${empty}/${views[view]}/landscape`);
        // Emulate the effective CSS viewport and pixel density of a 1280×900
        // browser at 200% zoom; CSS zoom alone would not test media queries.
        const cdp = await context.newCDPSession(page);
        await cdp.send('Emulation.setDeviceMetricsOverride', { width: 640, height: 450, deviceScaleFactor: 2, mobile: false });
        await checkLayout(page, `${language}/${theme}/${empty}/${views[view]}/200%-1280x900`);
        await cdp.send('Emulation.clearDeviceMetricsOverride');
        await cdp.detach();
      }
      console.log(`PASS workspace ${language}/${theme}/${empty ? 'empty' : 'populated'}`);
      await context.close();
    }
    // Overlay interactions and focus transitions, including coarse-pointer tablets.
    for (const touch of [false, true]) {
      const context = await browser.newContext({ hasTouch: touch, viewport: { width: 768, height: 900 } });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await responsiveFixture(page, { language: 'SV', theme: 'dark' });
      await page.goto(base); await page.locator('.page h1').waitFor();
      assert.equal(await page.evaluate(() => matchMedia('(pointer: coarse)').matches), touch, 'Touch emulation must activate coarse-pointer CSS');
      await checkLayout(page, `touch=${touch}`);
      for (const width of [320, 768, 1023, 844]) {
        await page.setViewportSize({ width, height: width === 844 ? 390 : 900 });
        await page.locator('.menu-toggle').click(); await page.locator('.mobile-menu').waitFor();
        await checkOverlay(page, '.mobile-menu');
        await page.keyboard.press('Tab');
        assert.ok(await page.locator('.mobile-menu').evaluate(el => el.contains(document.activeElement)));
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('.mobile-menu').count(), 0);
        assert.ok(await page.locator('.menu-toggle').evaluate(el => el === document.activeElement));
      }
      await page.setViewportSize({ width: 1023, height: 900 });
      await page.locator('.menu-toggle').click();
      await page.setViewportSize({ width: 768, height: 900 });
      assert.equal(await page.locator('.mobile-menu').isVisible(), true, 'Menu must stay open below 1024px');
      await page.setViewportSize({ width: 1023, height: 900 });
      assert.equal(await page.locator('.mobile-menu').isVisible(), true, 'Menu must stay open at 1023px');
      await page.setViewportSize({ width: 1024, height: 900 });
      await page.locator('.mobile-menu').waitFor({ state: 'detached' });
      await page.waitForFunction(() => document.activeElement?.matches('.header-navigation [aria-current="page"]'));
      await navigate(page, 4);
      await page.locator('.page-heading .primary-action').focus(); await page.keyboard.press('Enter');
      await page.locator('.editor-drawer').waitFor();
      for (const viewport of [{ width: 320, height: 700 }, { width: 844, height: 390 }]) {
        await page.setViewportSize(viewport);
        // The drawer body scrolls while its header remains visible.
        const drawer = await page.locator('.editor-drawer').boundingBox();
        assert.ok(drawer && drawer.y >= 0 && drawer.y + drawer.height <= viewport.height + 1);
        await checkLayout(page, `drawer/touch=${touch}/${viewport.width}`);
        await page.locator('.drawer-body').evaluate(el => { el.scrollTop = el.scrollHeight; });
        await page.screenshot({ path: path.join(out, `drawer-${touch}-${viewport.width}.png`), fullPage: false });
      }
      await page.keyboard.press('Escape');
      for (const viewport of [{ width: 320, height: 700 }, { width: 844, height: 390 }]) {
        await page.setViewportSize(viewport);
        await page.locator('.notification-bell').click(); await checkOverlay(page, '.notification-popover');
        await checkLayout(page, `notifications/touch=${touch}/${viewport.width}`);
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('.notification-popover').count(), 0);
      }
      await context.close();
    }
    for (const language of ['EN', 'SV'] as const) for (const theme of ['light', 'dark'] as const) {
      const page = await browser.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await responsiveFixture(page, { language, theme, signedIn: false });
      await page.goto(base); await page.locator('.auth-card h2').waitFor();
      for (const width of widths) { await page.setViewportSize({ width, height: 900 }); await checkLayout(page, `auth/${language}/${theme}`, false); }
      await page.setViewportSize({ width: 844, height: 390 }); await checkLayout(page, `auth/${language}/${theme}/landscape`, false);
      await page.close();
    }
    assert.deepEqual(errors, [], 'Browser JavaScript errors');
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ base, cases: results.length, errors, results }, null, 2));
    console.log(`PASS ${results.length} layout checks, menu breakpoints, keyboard focus, overlays and board/calendar columns. API fixtures only. Screenshots: ${out}`);
  } catch (error) {
    const lastPage = browser.contexts().at(-1)?.pages().at(-1);
    if (lastPage) await lastPage.screenshot({ path: path.join(out, 'failure.png'), fullPage: true }).catch(() => {});
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
