import type { TFunction } from 'i18next'

import type { Notification } from '../types'

/** The two lines a notification renders to: the bell shows the first, the inbox shows both. */
export interface NotificationText {
  title: string
  message: string | null
}

/**
 * Turns a notification row into the sentence a person reads, in whatever language they chose (#198).
 *
 * **The row stores what happened, not the sentence** (#197): a type and the names involved. That is
 * what lets a notification written months ago still be read in today's language — a stored sentence
 * would be frozen in the language of the day it was emitted.
 *
 * Rows written before #197 have no parameters and carry their frozen text instead; those are shown
 * exactly as they were stored, because the alternative is showing nothing at all.
 *
 * @param notification the row, as the API sent it
 * @param t            the translator, bound to the `notifications` namespace
 * @returns the title and, when the type has one, the message
 */
export function notificationText(notification: Notification, t: TFunction<'notifications'>): NotificationText {
  const { params } = notification
  if (!params) {
    // Pre-#197 row: the text it froze is all there is.
    return { title: notification.title ?? '', message: notification.message }
  }

  const values = {
    tableName: params.tableName ?? '',
    otherTableName: params.otherTableName ?? '',
    actorName: params.actorName ?? '',
    taskTitle: params.taskTitle ?? '',
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
