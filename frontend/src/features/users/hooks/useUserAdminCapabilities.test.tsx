import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AdminUserSummary, User } from '../types'
import { useUserAdminCapabilities } from './useUserAdminCapabilities'

const me = vi.hoisted(() => vi.fn())

vi.mock('../api/useMe', () => ({ useMe: me }))

function signedInAs(...roles: string[]) {
  const profile: User = { id: 'me', name: 'Yo', country: 'AR', karma: 0, needsOnboarding: false, roles, hasManagedTables: false }
  me.mockReturnValue({ data: profile, isPending: false })
}

function account(roles: AdminUserSummary['roles'], status: AdminUserSummary['status'] = 'Allowed'): AdminUserSummary {
  return { id: 'user-1', discordUsername: 'dami', name: 'Damián', country: 'AR', status, roles, createdAt: '2026-01-01T12:00:00' }
}

beforeEach(() => {
  me.mockReset()
})

describe('useUserAdminCapabilities', () => {
  /**
   * The one difference between an admin and an owner in F3 (§3 of docs/fase-3-admin-owner.md): who
   * may hand out the rank. Everything the screen shows follows from this list, so this is the test
   * that keeps "an admin cannot make an admin" from becoming a comment nobody enforces.
   */
  it('an admin may only move Player and Master', () => {
    signedInAs('Admin')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.grantableRoles).toEqual(['Player', 'Master'])
    expect(result.current.grantableRoles).not.toContain('Admin')
    expect(result.current.grantableRoles).not.toContain('Owner')
  })

  /** And the owner sees the four, in the order of #165. */
  it('an owner may move all four roles, in the order of #165', () => {
    signedInAs('Owner')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.grantableRoles).toEqual(['Player', 'Master', 'Admin', 'Owner'])
  })

  it('somebody who is neither may move nothing', () => {
    signedInAs('Player', 'Master')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.grantableRoles).toEqual([])
    expect(result.current.canChangeStatus(account(['Player']))).toBe(false)
  })

  /** Until the profile lands, every answer is "no": acting on a guess is worse than waiting. */
  it('answers no while the profile has not arrived', () => {
    me.mockReturnValue({ data: undefined, isPending: true })
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.grantableRoles).toEqual([])
    expect(result.current.canChangeStatus(account(['Player']))).toBe(false)
    expect(result.current.isPending).toBe(true)
  })

  /** Between peers there is no authority — and that holds for the owner too, not just for admins. */
  it.each(['Admin', 'Owner'])('nobody may block an account holding %s', (privileged) => {
    signedInAs('Owner')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.canChangeStatus(account(['Player', privileged as 'Admin']))).toBe(false)
  })

  it('an admin may block an ordinary account', () => {
    signedInAs('Admin')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.canChangeStatus(account(['Player', 'Master']))).toBe(true)
    expect(result.current.canChangeStatus(account(['Player'], 'Blocked'))).toBe(true)
  })

  /** F3.1 neither produces `Deleted` nor undoes it, so the screen never offers a way into it. */
  it('leaves a deleted account alone in both directions', () => {
    signedInAs('Owner')
    const { result } = renderHook(() => useUserAdminCapabilities())

    expect(result.current.canChangeStatus(account(['Player'], 'Deleted'))).toBe(false)
  })
})
