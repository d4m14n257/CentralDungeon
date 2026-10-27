import { describe, expect, it } from 'vitest'

import { masterTableDetailPath, masterTableHistoryPath, masterTablePlayersPath, myTableDetailPath } from '@/config/paths'

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
   * ADR-08 §2: the clash names another table but asks for an action on neither - withdrawing one of the
   * two pending applications, which only happens from the list of applications.
   */
  it('sends a schedule clash to the applications list, where a pending one can be withdrawn', () => {
    const clash = notification({ notificationType: 'ScheduleConflict' })

    expect(notificationTarget(clash)).toBe('/player/applications')
  })

  /**
   * Both reach only people already signed up to play at the table (`NotificationType.java`): the
   * player's own table screen is where the calendar and their attendance live, in local time (ADR-03 §6).
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
   * A resolved `TablePause` points at the table, and whoever asked is a master of it: their own view of
   * the table shows the outcome, the public detail is the player's.
   */
  it.each(['ApprovalRequestApproved', 'ApprovalRequestRejected'] as const)('sends %s about a table to the master’s view of it', (type) => {
    const resolved = notification({ notificationType: type, relatedEntityType: 'game_table', relatedEntityId: 't9' })

    expect(notificationTarget(resolved)).toBe(masterTableDetailPath('t9'))
  })

  /** A resolved veto points at the application; the table travels in the params so the click lands on its players. */
  it.each(['ApprovalRequestApproved', 'ApprovalRequestRejected'] as const)(
    'sends %s about a veto to the players tab of its table',
    (type) => {
      const resolved = notification({
        notificationType: type,
        params: { tableId: 't4' },
        relatedEntityType: 'table_registration',
        relatedEntityId: 'reg-1',
      })

      expect(notificationTarget(resolved)).toBe(masterTablePlayersPath('t4'))
    },
  )

  it('opens nothing for a veto outcome written before the table travelled with it', () => {
    const old = notification({
      notificationType: 'ApprovalRequestApproved',
      params: null,
      relatedEntityType: 'table_registration',
      relatedEntityId: 'reg-1',
    })

    expect(notificationTarget(old)).toBeNull()
  })

  /** Now a player: what is theirs lives in their own table screen, not in the public detail. */
  it('sends an acceptance to the player’s own table detail', () => {
    expect(notificationTarget(notification({ notificationType: 'RegistrationAccepted', relatedEntityId: 't3' }))).toBe(
      '/player/my-tables/t3',
    )
  })

  /** The reason for the rejection is shown in the applications list and nowhere else. */
  it('sends a rejection to the applications list, where the reason is', () => {
    expect(notificationTarget(notification({ notificationType: 'RegistrationRejected' }))).toBe('/player/applications')
  })

  /**
   * **Never the tray.** Whoever asked is not an admin and cannot enter `/admin/requests`: sending
   * them there would be a link to a `403`.
   */
  it('never sends whoever asked to the admin tray', () => {
    for (const type of ['ApprovalRequestApproved', 'ApprovalRequestRejected']) {
      for (const entityType of ['user', 'game_table', 'table_registration']) {
        const resolved = notification({
          notificationType: type,
          params: { tableId: 't1' },
          relatedEntityType: entityType,
          relatedEntityId: 'x1',
        })
        expect(notificationTarget(resolved)).not.toContain('/admin')
      }
    }
  })

  it('opens nothing for a notification with no related table', () => {
    const untargeted = notification({ relatedEntityType: null, relatedEntityId: null })

    expect(notificationTarget(untargeted)).toBeNull()
  })

  it.each(['TableEditedByAdmin', 'TableResumedByAdmin'] as const)('sends %s to the table its master runs', (type) => {
    expect(notificationTarget(notification({ notificationType: type }))).toBe(masterTableDetailPath('t1'))
  })

  /** The admin's reason is in the status history and not in the notification (ADR-23 §3, ADR-06 §8). */
  it.each(['TablePausedByAdmin', 'TableCanceledByAdmin'] as const)('sends %s to the history tab, where the reason is', (type) => {
    expect(notificationTarget(notification({ notificationType: type }))).toBe(masterTableHistoryPath('t1'))
  })

  it('opens nothing for a table an admin removed, which has nothing left to open', () => {
    const removed = notification({ notificationType: 'TableDeletedByAdmin', relatedEntityType: null, relatedEntityId: null })
    expect(notificationTarget(removed)).toBeNull()
  })

  /**
   * The same plain type reaches masters and players, so the row says which one the reader is. A pause
   * and a cancellation send a master to the history, where the reason is; everything else to the table.
   */
  it.each([
    ['TablePaused', masterTableHistoryPath('t5')],
    ['TableCanceled', masterTableHistoryPath('t5')],
    ['TableResumed', masterTableDetailPath('t5')],
    ['TableFinished', masterTableDetailPath('t5')],
    ['TableScheduleChanged', masterTableDetailPath('t5')],
  ] as const)('sends %s to a master on their own screens', (type, expected) => {
    const row = notification({ notificationType: type, relatedEntityId: 't5', params: { tableName: 'X', membership: 'Master' } })

    expect(notificationTarget(row)).toBe(expected)
  })

  /** A player has no history tab and no master screen: all five land on their own table, which still opens once it is closed. */
  it.each(['TablePaused', 'TableCanceled', 'TableResumed', 'TableFinished', 'TableScheduleChanged'] as const)(
    'sends %s to a player on their own table screen',
    (type) => {
      const row = notification({ notificationType: type, relatedEntityId: 't5', params: { tableName: 'X', membership: 'Player' } })

      expect(notificationTarget(row)).toBe(myTableDetailPath('t5'))
    },
  )

  /** Nothing writes these without a membership, so one without it is not a row this build can place. */
  it.each(['TablePaused', 'TableCanceled', 'TableResumed', 'TableFinished', 'TableScheduleChanged'] as const)(
    'opens nothing for a %s row with no membership',
    (type) => {
      expect(notificationTarget(notification({ notificationType: type, params: { tableName: 'X' } }))).toBeNull()
      expect(notificationTarget(notification({ notificationType: type, params: null }))).toBeNull()
    },
  )

  /**
   * Regression guard: every type the backend can send today has to resolve to a real path. A type
   * silently falling through to the `default` branch is exactly the bug this feature was built to
   * fix - a notification that reads, marks itself read, and opens nothing.
   */
  it('never falls through to the default for a type the backend actually sends', () => {
    // The one deliberate dead end: a removed table cannot be opened (ADR-03 §7), so the backend sends it with
    // no id at all - tested on its own below.
    for (const type of ALL_TYPES.filter((candidate) => candidate !== 'TableDeletedByAdmin')) {
      for (const membership of ['Master', 'Player'] as const) {
        const row = notification({ notificationType: type, params: { tableName: 'La Cripta', membership } })
        expect(notificationTarget(row), `${type} for a ${membership} should not resolve to null`).not.toBeNull()
      }
    }
  })
})
