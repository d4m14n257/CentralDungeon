import { api, setAccessToken } from '@/api/client'

interface TestLoginResponse {
  accessToken: string
  expiresIn: number
}

/**
 * Envuelve /api/v1/auth/test-login (backend/auth/TestLoginController, @Profile("test")): 404 si
 * el backend no corre con ese perfil, señal de que el panel no puede hacer nada por ahora. Cuando
 * haga falta Admin/Owner, test-login necesita un parámetro de roles en vez de solo `asMaster` -
 * no se anticipa acá.
 */
export async function testLoginAndReload(discordId: string, asMaster: boolean): Promise<void> {
  const response = await api.post<TestLoginResponse>(
    `/api/v1/auth/test-login?discordId=${encodeURIComponent(discordId)}&asMaster=${asMaster}`,
  )
  setAccessToken(response.accessToken)
  // location.href, no reload(): si venís de /auth/callback?error=... un reload repite esa URL con
  // el error puesto encima de la sesión nueva (pruebas-e1.md).
  window.location.href = '/'
}
