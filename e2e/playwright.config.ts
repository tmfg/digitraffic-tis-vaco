import { defineConfig } from '@playwright/test';

/**
 * API-only Playwright project for the VACO E2E suite. No browser is launched.
 *
 * The specs share one test company and run in order, so the suite runs with a
 * single worker.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['html', { open: 'never' }],
    ['list'],
    process.env.CI ? ['github'] : ['null'],
    process.env.CI ? ['junit', { outputFile: 'test-results/junit.xml' }] : ['null'],
  ],
  projects: [
    {
      name: 'api',
      testMatch: /.*\.spec\.ts/,
    },
  ],
});
