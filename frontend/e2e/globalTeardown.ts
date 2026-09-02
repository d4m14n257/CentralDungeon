import { request } from '@playwright/test'

const BACKEND_URL = 'http://localhost:8080'

/**
 * Borra lo que la corrida dejó: mesas con "E2E" en el nombre y usuarios `e2e-*` (decisiones.md #172).
 * Sin esto cada corrida sumaba filas a la base de desarrollo, y una base llena no es solo ruido —
 * fue lo que hizo fallar a registration-flow.spec.ts cuando el explorador todavía no tenía orden (#171).
 *
 * No falla la suite si no puede limpiar: el endpoint solo existe con el perfil "test" y los tests
 * ya terminaron. Se avisa por consola y listo.
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
