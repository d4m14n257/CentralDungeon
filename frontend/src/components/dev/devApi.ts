import { api, setAccessToken } from '@/api/client'

interface TestLoginResponse {
  accessToken: string
  expiresIn: number
}

/**
 * Wraps /api/v1/auth/test-login (backend/auth/TestLoginController, @Profile("test")): a 404 means
 * the backend is not running with that profile, which is the sign that the panel can do nothing.
 */
export async function testLoginAndReload(discordId: string, asMaster: boolean, asAdmin = false): Promise<void> {
  const response = await api.post<TestLoginResponse>(
    `/api/v1/auth/test-login?discordId=${encodeURIComponent(discordId)}&asMaster=${asMaster}&asAdmin=${asAdmin}`,
  )
  setAccessToken(response.accessToken)
  // location.href and not reload(): coming from /auth/callback?error=..., a reload replays that URL
  // and puts the error on top of the session that was just created (pruebas-e1.md).
  window.location.href = '/'
}
