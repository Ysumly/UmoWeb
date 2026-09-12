import { defineConfig } from '@playwright/test'

const baseURL = 'http://127.0.0.1:4173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: {
    timeout: 5_000,
    toHaveScreenshot: {
      animations: 'disabled',
      caret: 'hide',
      maxDiffPixelRatio: 0.005,
    },
  },
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
  ],
  use: {
    baseURL,
    channel: 'chrome',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    reducedMotion: 'reduce',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'functional',
      testIgnore: /visual\.spec\.js/,
      use: {
        viewport: { width: 1280, height: 800 },
      },
    },
    {
      name: 'visual-desktop',
      testMatch: /visual\.spec\.js/,
      use: {
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'visual-mobile',
      testMatch: /visual\.spec\.js/,
      use: {
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
  ],
  webServer: process.env.PLAYWRIGHT_EXTERNAL_SERVER
    ? undefined
    : {
        command: 'node ./e2e/support/previewServer.js',
        url: baseURL,
        reuseExistingServer: false,
        timeout: 120_000,
      },
})
