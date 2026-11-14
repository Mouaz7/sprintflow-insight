import { createRequire } from 'node:module';

// Use the local tooling dependency, or an existing installation selected by the developer.
const require = createRequire(import.meta.url);
const { chromium }: typeof import('playwright') = require(
  process.env.PLAYWRIGHT_MODULE || 'playwright',
);
export type { BrowserContext, Page, Route } from 'playwright';

export function launchBrowser() {
  return chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
      : {}),
  });
}
