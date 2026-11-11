import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { authBlockedPath, paths } from '@/config/paths'

import { api } from './client'

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
})
