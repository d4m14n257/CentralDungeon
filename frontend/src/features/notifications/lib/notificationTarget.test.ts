import { describe, expect, it } from 'vitest'

import { notificationTarget } from './notificationTarget'
import type { Notification } from '../types'

/** Every type the backend can send today (`NotificationType.java`) - the regression test walks this list. */
const ALL_TYPES = [
  'RegistrationAccepted',
  'RegistrationRejected',
  'NewCandidate',
  'ScheduleConflict',
  'SessionScheduled',
  'SessionCanceled',
  'TaskPublished',
  'MasterAssigned',
  'TableApproved',
  'TableApprovedWithChanges',
  'TableChangesRequested',
] as const

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
