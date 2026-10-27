import {
  masterTableDetailPath,
  masterTableHistoryPath,
  masterTablePlayersPath,
  myTableDetailPath,
  playerApplicationsPath,
  playerProfilePath,
  tableDetailPath,
} from '@/config/paths'

import type { Notification } from '../types'

/**
 * Where clicking a notification leads, resolved from its `relatedEntityType` and
 * `relatedEntityId`. Null-safe by construction: a notification that points nowhere is not a bug,
 * it is one that simply has nothing to open.
 *
 * It returns a path and nothing else. The context used to travel with it, so the reader would not
 * land on a master screen with the switcher pointing at the player one (ADR-18 §5) - now the path carries
 * that on its own, because every context owns a prefix and the header reads the context off the URL
 * (ADR-20 §2).
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

  // A request that was resolved. Its reference says where the requester finds the outcome:
  // - `user` (`MasterGrant`, `TableOpen`, `General`): their own profile, where a granted role becomes visible.
  // - `game_table` (`TablePause`): the requester is a master of that table, so their own view of it,
  //   whose status notice shows the outcome. Never the public detail, which is the player's view.
  // - `table_registration` (`PlayerBan`): the veto was asked from the table's players tab, so that is where
  //   it lands, through the table id the notification carries. A row written before it travelled has none
  //   and opens nothing, which is better than the wrong place.
  // **Never `/admin/requests`**: whoever asked is not an admin and cannot enter it.
  if (notification.notificationType === 'ApprovalRequestApproved' || notification.notificationType === 'ApprovalRequestRejected') {
    if (relatedEntityType === 'game_table') {
      return masterTableDetailPath(relatedEntityId)
    }
    if (relatedEntityType === 'table_registration') {
      return notification.params?.tableId ? masterTablePlayersPath(notification.params.tableId) : null
    }
    return playerProfilePath()
  }

  // Everything else is about one table, and a row pointing at anything else is one nothing can open.
  if (relatedEntityType !== 'game_table') {
    return null
  }
  switch (notification.notificationType) {
    // Now a player there: what is theirs (calendar, sessions, attendance) lives in their own table screen.
    case 'RegistrationAccepted':
      return myTableDetailPath(relatedEntityId)
    // The reason for the rejection is only shown in the applications list.
    case 'RegistrationRejected':
      return playerApplicationsPath()
    // The public detail and not `/player/my-tables/:id`, because the same notice reaches a candidate
    // and a player alike: a candidate has no entry under their own tables for one they are not in
    // yet, and the public detail shows both of them what the table is asking of them (ADR-15 §5, ADR-14 §2).
    case 'TaskPublished':
      return tableDetailPath(relatedEntityId)
    case 'NewCandidate':
      return masterTableDetailPath(relatedEntityId)
    // The master's own screen, because that is what the news is: the table is now theirs to run
    // (ADR-18 §3). Sending them to the public detail would show them the table as anybody else sees it,
    // which is the one view that does not contain the thing they were just told.
    case 'MasterAssigned':
      return masterTableDetailPath(relatedEntityId)
    // The master's screen again, and for the same reason: what came out of review is a table they
    // run. Any tab of it shows the status notice (ADR-21 §10), which says what follows and, for a
    // table sent back, points at the history where the admin's reason is (ADR-18 §3).
    case 'TableApproved':
    case 'TableChangesRequested':
      return masterTableDetailPath(relatedEntityId)
    // An admin changed somebody else's table (ADR-06 §8). The rewrite and the return to play land on the
    // table; the pause and the cancellation on its history tab, because that is where the admin's
    // reason is - the notification does not carry it (ADR-23 §3). A removed table travels with no id at all
    // and stops at the guard above: there is nothing left to open.
    case 'TableEditedByAdmin':
    case 'TableResumedByAdmin':
      return masterTableDetailPath(relatedEntityId)
    case 'TablePausedByAdmin':
    case 'TableCanceledByAdmin':
      return masterTableHistoryPath(relatedEntityId)
    // R4's clash notice (ADR-08 §2) names the *other* table but asks for an action on neither: the
    // fix is withdrawing one of the two pending applications, and that only happens from the
    // applications list (ADR-08 §2 again) - opening either table's detail would show the clash without
    // offering the one thing this notification is asking the reader to do about it.
    case 'ScheduleConflict':
      return playerApplicationsPath()
    // Both reach only the players of the table (candidates cannot see sessions, so the backend does not
    // send them): what changed is a date on their own calendar. The player's own table screen is where
    // the sessions and the reader's attendance live, already converted to their local time - the public
    // detail has neither.
    case 'SessionScheduled':
    case 'SessionCanceled':
      return myTableDetailPath(relatedEntityId)
    // The table changed under someone joined to it. The same type reaches masters and players, so the
    // row says which one the reader is (`membership`) and each lands on their own screen: a master on
    // the history tab for a pause or a cancellation, because that is where the reason is, and on the
    // table for the rest; a player on their own table screen, which still opens once the table is
    // finished or cancelled (it asks only whether they played there). A row without `membership` is
    // one this build cannot place, and opens nothing rather than the wrong screen.
    case 'TablePaused':
    case 'TableCanceled':
      return byMembership(notification, masterTableHistoryPath, myTableDetailPath)
    case 'TableResumed':
    case 'TableFinished':
    case 'TableScheduleChanged':
      return byMembership(notification, masterTableDetailPath, myTableDetailPath)
    default:
      return null
  }
}

/**
 * Picks between the master's screen and the player's by how the reader is joined to the table.
 *
 * @param notification the one that was clicked; its `relatedEntityId` is the table
 * @param master       the master's destination
 * @param player       the player's destination
 * @returns the path for the reader's membership, or null when the row does not say
 */
function byMembership(notification: Notification, master: (tableId: string) => string, player: (tableId: string) => string): string | null {
  const tableId = notification.relatedEntityId
  if (!tableId) {
    return null
  }
  switch (notification.params?.membership) {
    case 'Master':
      return master(tableId)
    case 'Player':
      return player(tableId)
    default:
      return null
  }
}
