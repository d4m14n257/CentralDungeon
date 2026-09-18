/**
 * Mirror of NotificationParams — the names a notification's sentence needs filled in (#197).
 *
 * Every field is optional because each notification type fills in a different subset: a clash names
 * two tables, a new candidate names a person and a table, an acceptance names only the table.
 */
export interface NotificationParams {
  tableName?: string | null
  otherTableName?: string | null
  actorName?: string | null
  /** What a table is asking for, so the bell names the request rather than announcing one (#77). */
  taskTitle?: string | null
}

/**
 * Every kind of notice the backend can emit today — the mirror of `NotificationType.java`.
 *
 * **A written-out tuple and not a union alone**, so the list has a runtime value: the regression
 * guard of `notificationTarget.test.ts` walks it, which is what turns "a new type was added and
 * nobody gave it a destination" into a failing test instead of a notice that reads, marks itself
 * read and opens nothing.
 *
 * The last two are F3.2's (#42): a request is resolved and the person who made it is told, which is
 * the only thing the mechanism notifies. The request itself notifies nobody — an admin work item is
 * not duplicated as a notification (#100).
 */
export const NOTIFICATION_TYPES = [
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
  'ApprovalRequestApproved',
  'ApprovalRequestRejected',
] as const

/** The kinds of notice this build knows about. Derived from the list, never written out twice. */
export type NotificationType = (typeof NOTIFICATION_TYPES)[number]

/**
 * Espejo de NotificationResponse.
 *
 * **It carries no rendered sentence** (#197): `notificationType` plus `params` is what the frontend
 * turns into words, in the reader's language. `title` and `message` are the frozen text of a row
 * written before that change, and they are the fallback for exactly those rows — see
 * `lib/notificationText.ts`.
 */
export interface Notification {
  id: string
  /**
   * Deliberately a `string` and not {@link NotificationType}: a backend one release ahead can send a
   * kind this build has never heard of, and the renderer is built to survive exactly that — it falls
   * back to a readable label rather than a raw key. Typing the field to the union would make that
   * fallback unreachable by construction and leave it untestable.
   */
  notificationType: string
  params: NotificationParams | null
  title: string | null
  message: string | null
  relatedEntityType: string | null
  relatedEntityId: string | null
  readStatus: 'Unread' | 'Read'
  createdAt: string
}
