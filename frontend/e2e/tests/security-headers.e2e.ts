import { expect, test } from '../support/fixtures.ts';

// Offline counterpart of scripts/verification/trusted-types.ts: the app's own
// policy, without Google Identity Services. The policyViolations fixture fails
// the test on any CSP or Trusted Types violation while the page renders.
test('serves the sign-in page under an enforced CSP with Trusted Types', async ({
  page,
}) => {
  await page.route('https://accounts.google.com/**', (route) => route.abort());
  const response = await page.goto('/');
  expect(response?.ok()).toBe(true);
  const headers = response?.headers() ?? {};
  const csp = headers['content-security-policy'] ?? '';
  expect(csp).toMatch(/require-trusted-types-for 'script'/);
  expect(csp).toMatch(/script-src 'nonce-[\w-]+' 'strict-dynamic'/);
  expect(csp).toMatch(/frame-ancestors 'none'/);
  expect(headers['content-security-policy-report-only']).toBeUndefined();
  expect(headers['x-content-type-options']).toBe('nosniff');
  await expect(
    page.getByRole('button', { name: 'Log in', exact: true }),
  ).toBeVisible();
});
