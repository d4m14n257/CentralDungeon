/**
 * The page size of each kind of listing (decisiones.md #173). It lives here rather than in each hook
 * so that "how much a page brings" is a decision and not a loose number repeated in ten places.
 */
export const pageSize = {
  /** The three-column grid of cards: four complete rows. */
  explorer: 12,
  /** Single-column reading lists: my tables, my applications, notifications. */
  list: 20,
  /** An admin's working lists: denser, and with the total in view. */
  adminQueue: 25,
  /** Results inside a dialog, where the space is whatever is left. */
  picker: 8,
} as const
