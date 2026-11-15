import { defineConfig, devices } from '@playwright/test';

// The suite runs against a real server and PostgreSQL database, started by
// Docker Compose in CI or by ./start.sh locally. It never starts the app itself.
const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8081';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.e2e.ts',
  outputDir: './test-results',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [
        ['list'],
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
        ['github'],
      ]
    : [
        ['list'],
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
      ],
  use: {
    baseURL,
    locale: 'en-GB',
    timezoneId: 'Europe/Stockholm',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 1000 },
      },
    },
  ],
});
