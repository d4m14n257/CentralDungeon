import type { TFunction } from 'i18next'

import type { Notification } from '../types'

/** The two lines a notification renders to: the bell shows the first, the inbox shows both. */
export interface NotificationText {
  title: string
  message: string | null
}

/**
 * Turns a notification row into the sentence a person reads, in whatever language they chose (ADR-23 §2).
 *
 * **The row stores what happened, not the sentence** (ADR-23 §3): a type and the names involved. That is
 * what lets a notification written months ago still be read in today's language — a stored sentence
 * would be frozen in the language of the day it was emitted.
 *
 * Rows written before ADR-23 §3 have no parameters and carry their frozen text instead; those are shown
 * exactly as they were stored, because the alternative is showing nothing at all.
 *
 * @param notification the row, as the API sent it
 * @param t            the translator, bound to the `notifications` namespace
 * @returns the title and, when the type has one, the message
 */
export function notificationText(notification: Notification, t: TFunction<'notifications'>): NotificationText {
  const { params } = notification
  if (!params) {
    // Pre-ADR-23 §3 row: the text it froze is all there is.
    return { title: notification.title ?? '', message: notification.message }
  }

  const changes = params.changes?.map((code) => t(`field.${code}`, { defaultValue: code })) ?? []
  const values = {
    tableName: params.tableName ?? '',
    otherTableName: params.otherTableName ?? '',
    actorName: params.actorName ?? '',
    taskTitle: params.taskTitle ?? '',
    changes,
    // i18next picks `title_changes` / `message_Master` when it exists and the plain key otherwise: an
    // edit lists what changed, and the same type reads differently to a master than to a player.
    context: changes.length > 0 ? 'changes' : (params.membership ?? undefined),
  }
  // A type this build does not know about still has to render as something readable, so it falls
  // back to its own label rather than showing a raw key.
  const title = t(`sentence.${notification.notificationType}.title`, {
    ...values,
    defaultValue: t(`type.${notification.notificationType}`, { defaultValue: notification.notificationType }),
  })
  const message = t(`sentence.${notification.notificationType}.message`, { ...values, defaultValue: '' })

  return { title, message: message === '' ? null : message }
}
