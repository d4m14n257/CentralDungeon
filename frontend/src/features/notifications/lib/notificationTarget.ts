import { masterTableDetailPath, myTableDetailPath, playerApplicationsPath, tableDetailPath } from '@/config/paths'

import type { Notification } from '../types'

/**
 * Where clicking a notification leads, resolved from its `relatedEntityType` and
 * `relatedEntityId`. Null-safe by construction: a notification that points nowhere is not a bug,
 * it is one that simply has nothing to open.
 *
 * It returns a path and nothing else. The context used to travel with it, so the reader would not
 * land on a master screen with the switcher pointing at the player one (#156) - now the path carries
 * that on its own, because every context owns a prefix and the header reads the context off the URL
 * (#222).
 *
 * @param notification the one that was clicked
 * @returns the absolute path to open, or null when it opens nothing
 */
export function notificationTarget(notification: Notification): string | null {
  if (notification.relatedEntityType !== 'game_table' || !notification.relatedEntityId) {
    return null
  }
  switch (notification.notificationType) {
    case 'RegistrationAccepted':
    case 'RegistrationRejected':
      return tableDetailPath(notification.relatedEntityId)
    // The public detail and not `/player/my-tables/:id`, because the same notice reaches a candidate
    // and a player alike: a candidate has no entry under their own tables for one they are not in
    // yet, and the public detail shows both of them what the table is asking of them (#63, #206).
    case 'TaskPublished':
      return tableDetailPath(notification.relatedEntityId)
    case 'NewCandidate':
      return masterTableDetailPath(notification.relatedEntityId)
    // The master's own screen, because that is what the news is: the table is now theirs to run
    // (#244). Sending them to the public detail would show them the table as anybody else sees it,
    // which is the one view that does not contain the thing they were just told.
    case 'MasterAssigned':
      return masterTableDetailPath(notification.relatedEntityId)
    // The master's screen again, and for the same reason: what came out of review is a table they
    // run. A rejection has to land where the admin's reason is readable, which is its status tab
    // (#244) — the notification does not carry the reason, the history does.
    case 'TableApproved':
    case 'TableApprovedWithChanges':
    case 'TableChangesRequested':
      return masterTableDetailPath(notification.relatedEntityId)
    // R4's clash notice (#178) names the *other* table but asks for an action on neither: the
    // fix is withdrawing one of the two pending applications, and that only happens from the
    // applications list (#178 again) - opening either table's detail would show the clash without
    // offering the one thing this notification is asking the reader to do about it.
    case 'ScheduleConflict':
      return playerApplicationsPath()
    // Both reach only the people already signed up to play there (NotificationType.java): what
    // changed is a date on their own calendar, not the table's standing. The player's own table
    // screen is where the sessions and the reader's attendance live, already converted to their
    // local time (#22) - the public detail has neither.
    case 'SessionScheduled':
    case 'SessionCanceled':
      return myTableDetailPath(notification.relatedEntityId)
    default:
      return null
  }
}
