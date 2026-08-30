import { defineConfig, devices } from '@playwright/test'

/**
 * Runs against the real backend, not mocks (arquitectura.md 3.4). The backend must already be
 * running separately with the "test" profile active (exposes POST /api/v1/auth/test-login,
 * TestLoginController - see backend/README.md), since Playwright only owns the frontend here.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
})
