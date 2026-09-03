import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'

import { notificationText } from './notificationText'
import type { Notification } from '../types'

/** The translator bound to the namespace the renderer expects. */
const t = i18n.getFixedT(null, 'notifications')

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

describe('notificationText', () => {
  it('builds the sentence from the type and the names', () => {
    expect(notificationText(notification({}), t).title).toBe('Te aceptaron en La Cripta')
  })

  it('fills in every name a type needs, not only the table', () => {
    const clash = notification({
      notificationType: 'ScheduleConflict',
      params: { tableName: 'El Faro', otherTableName: 'La Cripta' },
    })
    const { title, message } = notificationText(clash, t)

    expect(title).toBe('Tu horario en El Faro choca con otra mesa')
    expect(message).toContain('La Cripta')
  })

  it('leaves the message null for a type that has only a title', () => {
    expect(notificationText(notification({}), t).message).toBeNull()
  })

  /**
   * #197: the same row reads in whichever language is active. A stored sentence could not do this —
   * it would be frozen in the language of the day it was written.
   */
  it('renders the same row in the language that is active', async () => {
    const row = notification({})
    expect(notificationText(row, i18n.getFixedT('es', 'notifications')).title).toBe('Te aceptaron en La Cripta')
    expect(notificationText(row, i18n.getFixedT('en', 'notifications')).title).toBe('You were accepted into La Cripta')
  })

  /** Rows written before #197 have no parameters; their frozen text is all there is to show. */
  it('falls back to the frozen text of a row written before the change', () => {
    const legacy = notification({ params: null, title: 'Te aceptaron en La Cripta', message: 'Un mensaje viejo' })

    expect(notificationText(legacy, t)).toEqual({ title: 'Te aceptaron en La Cripta', message: 'Un mensaje viejo' })
  })

  /** A type from a newer backend still has to read as something, never as a raw key. */
  it('falls back to a readable label for a type it does not know', () => {
    const unknown = notification({ notificationType: 'SomethingNew' })

    expect(notificationText(unknown, t).title).toBe('SomethingNew')
  })
})
