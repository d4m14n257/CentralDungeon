import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { authBlockedPath, paths } from '@/config/paths'

import { authApi } from '@/features/auth/api/authApi'

import { api, refreshSession, setAccessToken } from './client'

const assign = vi.fn()
const fetchMock = vi.fn()

function problem(status: number, errorCode: string): Response {
  return new Response(JSON.stringify({ title: 'x', status, detail: 'for the log', errorCode }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function calledPaths(): string[] {
  return fetchMock.mock.calls.map(([url]) => new URL(url as string).pathname)
}

describe('the client when a call comes back 401', () => {
  beforeEach(() => {
    assign.mockClear()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', { pathname: '/player', assign })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('goes to the blocked screen, without trying to refresh, when the account is blocked', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'ACCOUNT_BLOCKED'))

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ problem: { errorCode: 'ACCOUNT_BLOCKED' } })

    expect(calledPaths()).toEqual(['/api/v1/users/me'])
    expect(assign).toHaveBeenCalledExactlyOnceWith(authBlockedPath())
  })

  it('goes to the blocked screen, without retrying, when the refresh itself says the account is blocked', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'UNAUTHORIZED')).mockResolvedValueOnce(problem(401, 'ACCOUNT_BLOCKED'))

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ problem: { errorCode: 'ACCOUNT_BLOCKED' } })

    expect(calledPaths()).toEqual(['/api/v1/users/me', '/api/v1/auth/refresh'])
    expect(assign).toHaveBeenCalledExactlyOnceWith(authBlockedPath())
  })

  it('does not reload the blocked screen it is already on', async () => {
    vi.stubGlobal('location', { pathname: paths.authCallback, assign })
    fetchMock.mockResolvedValueOnce(problem(401, 'ACCOUNT_BLOCKED'))

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ problem: { errorCode: 'ACCOUNT_BLOCKED' } })

    expect(assign).not.toHaveBeenCalled()
  })

  it('keeps going to /login when the session is simply gone', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'UNAUTHORIZED')).mockResolvedValueOnce(problem(401, 'UNAUTHORIZED'))

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ problem: { errorCode: 'UNAUTHORIZED' } })

    expect(calledPaths()).toEqual(['/api/v1/users/me', '/api/v1/auth/refresh'])
    expect(assign).toHaveBeenCalledExactlyOnceWith(paths.login)
  })

  it('fails the call with RATE_LIMITED, and does not send to /login, when the refresh was rate limited', async () => {
    fetchMock.mockResolvedValueOnce(problem(401, 'UNAUTHORIZED')).mockResolvedValueOnce(problem(429, 'RATE_LIMITED'))

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ status: 429, problem: { errorCode: 'RATE_LIMITED' } })

    expect(calledPaths()).toEqual(['/api/v1/users/me', '/api/v1/auth/refresh'])
    expect(assign).not.toHaveBeenCalled()
  })
})

function token(accessToken: string): Response {
  return new Response(JSON.stringify({ accessToken, expiresInSeconds: 900 }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('the client renewing the session', () => {
  beforeEach(() => {
    assign.mockClear()
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', { pathname: '/player', assign })
    setAccessToken('expired')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setAccessToken(null)
  })

  it('sends a single refresh when two calls hit a 401 at the same time, and retries both', async () => {
    fetchMock.mockImplementation((url: string) => {
      const path = new URL(url).pathname
      if (path === '/api/v1/auth/refresh') {
        return Promise.resolve(token('fresh'))
      }
      return Promise.resolve(
        fetchMock.mock.calls.some(([called]) => new URL(called as string).pathname === '/api/v1/auth/refresh')
          ? new Response(JSON.stringify({ ok: path }), { status: 200, headers: { 'Content-Type': 'application/json' } })
          : problem(401, 'UNAUTHORIZED'),
      )
    })

    await expect(Promise.all([api.get('/api/v1/users/me'), api.get('/api/v1/notifications')])).resolves.toEqual([
      { ok: '/api/v1/users/me' },
      { ok: '/api/v1/notifications' },
    ])

    expect(calledPaths().filter((path) => path === '/api/v1/auth/refresh')).toHaveLength(1)
  })

  it('shares one renewal between callers that ask while it is on the wire, and sends a new one afterwards', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(token('fresh')))

    await expect(Promise.all([refreshSession(), refreshSession()])).resolves.toEqual(['fresh', 'fresh'])
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await refreshSession()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

/** A `navigator.locks` that grants each name to one holder at a time, in request order, like the browser's. */
function simulatedLocks(): Pick<LockManager, 'request'> {
  const tails = new Map<string, Promise<unknown>>()
  return {
    request: ((name: string, callback: () => Promise<unknown>) => {
      const run = (tails.get(name) ?? Promise.resolve()).then(callback)
      tails.set(
        name,
        run.catch(() => undefined),
      )
      return run
    }) as LockManager['request'],
  }
}

describe('the client renewing the session from several tabs', () => {
  let running = 0
  let mostRunningAtOnce = 0

  beforeEach(() => {
    running = 0
    mostRunningAtOnce = 0
    assign.mockClear()
    fetchMock.mockReset()
    // A renewal that takes a moment, so two of them would overlap if nothing held them apart.
    fetchMock.mockImplementation(async () => {
      running += 1
      mostRunningAtOnce = Math.max(mostRunningAtOnce, running)
      await new Promise((resolve) => setTimeout(resolve, 10))
      running -= 1
      return token('fresh')
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', { pathname: '/player', assign })
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renews one tab after the other, and the tab that waited still sends its own renewal', async () => {
    vi.stubGlobal('navigator', { locks: simulatedLocks() })
    // Two module instances stand for two tabs: each has its own in-flight promise, but they share the lock.
    const tabA = await import('./client')
    vi.resetModules()
    const tabB = await import('./client')

    await expect(Promise.all([tabA.refreshSession(), tabB.refreshSession()])).resolves.toEqual(['fresh', 'fresh'])

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(mostRunningAtOnce).toBe(1)
  })

  it('still renews, once per tab, when the browser has no navigator.locks', async () => {
    vi.stubGlobal('navigator', {})
    const tab = await import('./client')

    await expect(Promise.all([tab.refreshSession(), tab.refreshSession()])).resolves.toEqual(['fresh', 'fresh'])

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('logging out', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-123'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('sends the CSRF header and the cookies, since the cookie is what it is authenticated by', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await authApi.logout()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(new URL(url).pathname).toBe('/api/v1/auth/logout')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'csrf-123' } })
  })
})
