import assert from 'node:assert/strict';
import { launchBrowser } from './browser.ts';

// Loads the sign-in page of a running, built app in Chromium under the app's own enforced CSP and
// fails on any CSP or Trusted Types violation. By default it also loads Google Identity Services
// (GIS) and renders its button, which needs network access to accounts.google.com. No account is
// used and nothing is written: the Google config request is answered with a placeholder client id
// unless SPRINTFLOW_TT_GOOGLE_CLIENT_ID is set. Pass --offline to skip GIS and check only the app.
const base = process.env.SPRINTFLOW_QA_URL || 'http://localhost:8081';
const offline = process.argv.includes('--offline');
const clientId =
  process.env.SPRINTFLOW_TT_GOOGLE_CLIENT_ID || '000000000000-verification.apps.googleusercontent.com';

const violations: string[] = [];
const browser = await launchBrowser();
try {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  // Runs before any page script; listening is not a Trusted Types sink.
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', event => {
      console.error(
        `CSP violation: ${event.effectiveDirective} blocked ${event.blockedURI || '(inline)'}` +
          ` at ${event.sourceFile}:${event.lineNumber} ${event.sample}`,
      );
    });
  });
  page.on('console', message => {
    if (message.type() !== 'error') return;
    const text = message.text();
    if (/CSP violation|Trusted ?Type|TrustedHTML|TrustedScript|Content Security Policy|trusted-types/i.test(text))
      violations.push(text);
  });
  page.on('pageerror', error => {
    if (/Trusted ?Type|TrustedHTML|TrustedScript|createPolicy|Content Security Policy/i.test(error.message))
      violations.push(`Uncaught: ${error.message}`);
  });
  await page.route('**/api/auth/google/config', route =>
    route.fulfill({ json: { clientId: offline ? '' : clientId } }),
  );
  if (offline) await page.route('https://accounts.google.com/**', route => route.abort());

  const response = await page.goto(base, { waitUntil: 'load' });
  assert.ok(response, `GET ${base}: no response`);
  assert.ok(response.ok(), `GET ${base}: HTTP ${response.status()}`);
  const csp = response.headers()['content-security-policy'] ?? '';
  assert.match(csp, /require-trusted-types-for 'script'/, 'Trusted Types must be enforced.');
  assert.match(csp, /trusted-types sprintflow-google goog#html 'allow-duplicates'/);
  assert.ok(!response.headers()['content-security-policy-report-only'], 'No report-only policy.');

  if (!offline) {
    await page.locator('.google-button-slot.ready').waitFor({ timeout: 30000 });
    await page
      .locator('.google-button-slot iframe[src^="https://accounts.google.com/gsi/"]')
      .first()
      .waitFor({ state: 'attached', timeout: 30000 });
    assert.ok(
      await page.evaluate(() => typeof (window as { google?: unknown }).google === 'object'),
      'GIS did not load.',
    );
  } else {
    await page.locator('input[type="email"], input[type="password"]').first().waitFor();
  }
  // Let late GIS work (iframe messages, button styles) finish before judging.
  await page.waitForTimeout(3000);
  assert.deepEqual(violations, [], 'CSP or Trusted Types violations on the sign-in page.');
  console.log(
    `PASS: sign-in page under enforced Trusted Types${offline ? ' (GIS skipped)' : ' with GIS rendered'}.`,
  );
} catch (error) {
  // A blocked GIS often surfaces as a timeout; show the violations that caused it.
  if (violations.length) console.error(`Violations seen:\n${violations.join('\n')}`);
  throw error;
} finally {
  await browser.close();
}
