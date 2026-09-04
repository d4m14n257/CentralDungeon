import { request } from '@playwright/test'

const BACKEND_URL = 'http://localhost:8080'

/**
 * Deletes what the run left behind: tables with "E2E" in the name and `e2e-*` users
 * (decisiones.md #172). Without this every run added rows to the development database, and a full
 * database is not merely noise — it is what made registration-flow.spec.ts fail back when the
 * explorer had no fixed order (#171).
 *
 * It does not fail the suite when it cannot clean up: the endpoint only exists under the "test"
 * profile and the tests are over by then. It says so on the console and that is all.
 */
export default async function globalTeardown() {
  const context = await request.newContext()
  try {
    const response = await context.delete(`${BACKEND_URL}/api/v1/test-data/e2e`)
    if (!response.ok()) {
      console.warn(`[e2e] no se pudo limpiar la base (${response.status()})`)
      return
    }
    const { gameTables, users } = (await response.json()) as { gameTables: number; users: number }
    console.log(`[e2e] limpieza: ${gameTables} mesas y ${users} usuarios borrados`)
  } catch (error) {
    console.warn('[e2e] no se pudo limpiar la base:', error)
  } finally {
    await context.dispose()
  }
}
