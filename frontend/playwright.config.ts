import { defineConfig } from '@playwright/test';

if (process.env.AML_E2E_RUN !== 'isolated' ||
    process.env.AML_BACKEND_PROXY_TARGET !== 'http://127.0.0.1:18080' ||
    process.env.AML_DB_URL !== 'jdbc:postgresql://127.0.0.1:55432/aml_frontend_e2e') {
  throw new Error('E2E tests require the isolated local database/backend launcher');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: '*.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:15173',
    browserName: 'chromium',
    channel: 'msedge',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
    url: 'http://127.0.0.1:15173/login',
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
