import { describe, expect, it } from 'vitest'

import { notificationTarget } from './notificationTarget'
import { NOTIFICATION_TYPES, type Notification } from '../types'

/**
 * Every type the backend can send today - the regression test walks this list.
 *
 * It is the mirror in `types.ts` and no longer a copy kept here: a second list free to fall behind
 * the first is how a type gets added, given no destination, and caught by nobody.
 */
const ALL_TYPES = NOTIFICATION_TYPES

function notification(overrides: Partial<Notification>): Notification {
  return {
    id: 'n1',
    notificationType: 'RegistrationAccepted',
    params: { tableName: 'La Cripta' },
    title: null,
    message: null,
    relatedEntityType: 'game_table',
    relatedEntityId: 't1',
    readStatus: 'Unread',
    createdAt: '2026-09-09T01:00:00',
    ...overrides,
  }
}

describe('notificationTarget', () => {
  /**
   * #178: the clash names another table but asks for an action on neither - withdrawing one of the
   * two pending applications, which only happens from the list of applications.
   */
  it('sends a schedule clash to the applications list, where a pending one can be withdrawn', () => {
    const clash = notification({ notificationType: 'ScheduleConflict' })

    expect(notificationTarget(clash)).toBe('/player/applications')
  })

  /**
   * Both reach only people already signed up to play at the table (`NotificationType.java`): the
   * player's own table screen is where the calendar and their attendance live, in local time (#22).
   */
  it.each(['SessionScheduled', 'SessionCanceled'] as const)('sends %s to the player’s own table detail', (type) => {
    const changed = notification({ notificationType: type, relatedEntityId: 't7' })

    expect(notificationTarget(changed)).toBe('/player/my-tables/t7')
  })

  /**
   * F3.2, and the guard that had to be rewritten for it: until then a single `if` cut everything
   * that was not a `game_table`, which is exactly what would have made these two unclickable the day
   * they arrived. A resolved `MasterGrant` or `General` points at the person who asked
   * (`entityType = 'user'`), and their own profile is where a granted role becomes visible.
   */
  it.each(['ApprovalRequestApproved', 'ApprovalRequestRejected'] as const)('sends %s about a person to their own profile', (type) => {
    const resolved = notification({ notificationType: type, relatedEntityType: 'user', relatedEntityId: 'user-1' })

    expect(notificationTarget(resolved)).toBe('/player/profile')
  })

  /**
   * A `TableOpen` whose table the admin already created points at the table instead, and then that
   * is where it leads: the public detail, because whoever asked is a player there and not its master.
   */
  it('sends a resolved request about a table to the table', () => {
    const resolved = notification({ notificationType: 'ApprovalRequestApproved', relatedEntityType: 'game_table', relatedEntityId: 't9' })

    expect(notificationTarget(resolved)).toBe('/player/tables/t9')
  })

  /**
   * **Never the tray.** Whoever asked is not an admin and cannot enter `/admin/requests`: sending
   * them there would be a link to a `403`.
   */
  it('never sends whoever asked to the admin tray', () => {
    for (const type of ['ApprovalRequestApproved', 'ApprovalRequestRejected']) {
      for (const entityType of ['user', 'game_table']) {
        const resolved = notification({ notificationType: type, relatedEntityType: entityType, relatedEntityId: 'x1' })
        expect(notificationTarget(resolved)).not.toContain('/admin')
      }
    }
  })

  it('opens nothing for a notification with no related table', () => {
    const untargeted = notification({ relatedEntityType: null, relatedEntityId: null })

    expect(notificationTarget(untargeted)).toBeNull()
  })

  /**
   * Regression guard: every type the backend can send today has to resolve to a real path. A type
   * silently falling through to the `default` branch is exactly the bug this feature was built to
   * fix - a notification that reads, marks itself read, and opens nothing.
   */
  it('never falls through to the default for a type the backend actually sends', () => {
    for (const type of ALL_TYPES) {
      const row = notification({ notificationType: type })
      expect(notificationTarget(row), `${type} should not resolve to null`).not.toBeNull()
    }
  })
})
