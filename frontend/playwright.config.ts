import { defineConfig, devices } from '@playwright/test'

/**
 * Runs against the real backend, not mocks (arquitectura.md 3.4). The backend must already be
 * running separately with the "test" profile active (exposes POST /api/v1/auth/test-login,
 * TestLoginController - see backend/README.md), since Playwright only owns the frontend here.
 */
export default defineConfig({
  testDir: './e2e',
  // Cada corrida borra sus propias filas al terminar (decisiones.md #172).
  globalTeardown: './e2e/globalTeardown.ts',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    // Every spec runs in Spanish unless it says otherwise. Without this the language would come
    // from the browser's own locale (#198), so a spec asserting on a label would pass or fail
    // depending on the machine running it. `language.spec.ts` overrides it per context, which is
    // exactly what it is testing.
    locale: 'es-AR',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
})
