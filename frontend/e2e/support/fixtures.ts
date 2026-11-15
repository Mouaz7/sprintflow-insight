import {
  test as base,
  expect,
  type Locator,
  type Page,
} from '@playwright/test';
import {
  Api,
  newAccount,
  register,
  removeAccount,
  type Account,
} from './api.ts';

interface Fixtures {
  /** API client sharing cookies with `page`. */
  api: Api;
  /** A registered account whose session is already in the browser context. */
  account: Account;
  /** Uncaught page errors; asserted to be empty after each test. */
  pageErrors: string[];
  /** CSP and Trusted Types violations; asserted to be empty after each test. */
  policyViolations: string[];
}

const policyMessage =
  /CSP violation|Trusted ?Type|TrustedHTML|TrustedScript|Content Security Policy|trusted-types/i;

export const test = base.extend<Fixtures>({
  api: async ({ context }, use) => {
    await use(new Api(context.request));
  },
  account: async ({ api }, use, testInfo) => {
    const account = newAccount(
      testInfo.title.replace(/\W+/g, '-').toLowerCase().slice(0, 24),
    );
    await register(api, account);
    await use(account);
    await removeAccount(api, account);
  },
  pageErrors: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await use(errors);
      expect(errors, 'Uncaught errors in the page').toEqual([]);
    },
    { auto: true },
  ],
  // Every flow runs under the app's own enforced CSP and Trusted Types policy.
  // Violations raised by code injected through the DevTools protocol (axe-core
  // runs that way) have no source file and are not the app's.
  policyViolations: [
    async ({ context }, use) => {
      const violations: string[] = [];
      await context.addInitScript(() => {
        document.addEventListener('securitypolicyviolation', (event) => {
          if (!event.sourceFile) return;
          console.error(
            `CSP violation: ${event.effectiveDirective} blocked ${event.blockedURI || '(inline)'}` +
              ` at ${event.sourceFile}:${event.lineNumber} ${event.sample}`,
          );
        });
      });
      context.on('console', (message) => {
        if (
          message.type() === 'error' &&
          message.text().startsWith('CSP violation:')
        )
          violations.push(message.text());
      });
      context.on('weberror', (error) => {
        if (policyMessage.test(error.error().message))
          violations.push(`Uncaught: ${error.error().message}`);
      });
      await use(violations);
      expect(violations, 'CSP or Trusted Types violations').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Main navigation entry, whether it is rendered as a button or a router link. */
export function navigationItem(page: Page, name: string): Locator {
  const navigation = page.getByRole('navigation', { name: 'Main navigation' });
  return navigation
    .getByRole('button', { name, exact: true })
    .or(navigation.getByRole('link', { name, exact: true }));
}

export async function openView(page: Page, name: string): Promise<void> {
  await navigationItem(page, name).click();
  await expect(
    page.getByRole('heading', { level: 1, name, exact: true }),
  ).toBeVisible();
}

export function isoDate(offsetDays: number): string {
  return new Date(Date.now() + offsetDays * 86_400_000)
    .toISOString()
    .slice(0, 10);
}
