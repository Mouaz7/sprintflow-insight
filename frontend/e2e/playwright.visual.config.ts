import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './visual',
  testMatch: '**/*.visual.ts',
  outputDir: './test-results/visual',
  snapshotPathTemplate: '{testDir}/snapshots/{arg}{ext}',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30000,
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.002, animations: 'disabled' },
  },
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report/visual' }],
  ],
  use: {
    baseURL: 'http://127.0.0.1:6006',
    viewport: { width: 1000, height: 900 },
    locale: 'en-GB',
    timezoneId: 'Europe/Stockholm',
    colorScheme: 'light',
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node support/storybook-server.ts',
    url: 'http://127.0.0.1:6006/index.json',
    reuseExistingServer: !process.env.CI,
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
