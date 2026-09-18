import { masterTableDetailPath, myTableDetailPath, playerApplicationsPath, playerProfilePath, tableDetailPath } from '@/config/paths'

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
 * **It stopped being only about tables with F3.2.** Until then every notice pointed at a
 * `game_table`, so one guard up front could cut everything else — and that guard is exactly what
 * would have made the two request notices unclickable the day they arrived, since a resolved request
 * points at the person who made it (`user`). A notice that reads, marks itself read and opens
 * nothing is the defect this module exists to prevent, so the reference is now read per type
 * instead of filtered once for all of them.
 *
 * @param notification the one that was clicked
 * @returns the absolute path to open, or null when it opens nothing
 */
export function notificationTarget(notification: Notification): string | null {
  const { relatedEntityType, relatedEntityId } = notification
  // Every type below needs something to open. What kind of thing it is differs; that there is one
  // does not.
  if (!relatedEntityId) {
    return null
  }

  // A request that was resolved (#42). Its reference is the requester - `entityType = 'user'` - for
  // `MasterGrant` and `General`, and it is a `game_table` for a `TableOpen` whose table the admin
  // has already created. The destination follows the reference: the table when there is one, and
  // otherwise the reader's own profile, which is where a granted role becomes visible.
  // **Never `/admin/requests`**: whoever asked is not an admin and cannot enter it.
  if (notification.notificationType === 'ApprovalRequestApproved' || notification.notificationType === 'ApprovalRequestRejected') {
    return relatedEntityType === 'game_table' ? tableDetailPath(relatedEntityId) : playerProfilePath()
  }

  // Everything else is about one table, and a row pointing at anything else is one nothing can open.
  if (relatedEntityType !== 'game_table') {
    return null
  }
  switch (notification.notificationType) {
    case 'RegistrationAccepted':
    case 'RegistrationRejected':
      return tableDetailPath(relatedEntityId)
    // The public detail and not `/player/my-tables/:id`, because the same notice reaches a candidate
    // and a player alike: a candidate has no entry under their own tables for one they are not in
    // yet, and the public detail shows both of them what the table is asking of them (#63, #206).
    case 'TaskPublished':
      return tableDetailPath(relatedEntityId)
    case 'NewCandidate':
      return masterTableDetailPath(relatedEntityId)
    // The master's own screen, because that is what the news is: the table is now theirs to run
    // (#244). Sending them to the public detail would show them the table as anybody else sees it,
    // which is the one view that does not contain the thing they were just told.
    case 'MasterAssigned':
      return masterTableDetailPath(relatedEntityId)
    // The master's screen again, and for the same reason: what came out of review is a table they
    // run. A rejection has to land where the admin's reason is readable, which is its status tab
    // (#244) — the notification does not carry the reason, the history does.
    case 'TableApproved':
    case 'TableApprovedWithChanges':
    case 'TableChangesRequested':
      return masterTableDetailPath(relatedEntityId)
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
      return myTableDetailPath(relatedEntityId)
    default:
      return null
  }
}
