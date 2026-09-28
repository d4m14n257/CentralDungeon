/**
 * The page size of each kind of listing (decisiones.md #173). It lives here rather than in each hook
 * so that "how much a page brings" is a decision and not a loose number repeated in ten places.
 */
export const pageSize = {
  /** The three-column grid of cards: four complete rows. */
  explorer: 12,
  /** Single-column reading lists: my tables, my applications, notifications. */
  list: 20,
  /**
   * An admin's working lists, before the reader picks another size (#271): denser, and with the
   * total in view. Serves every admin table, not only the tray it was once named after.
   */
  admin: 25,
  /** Results inside a dialog, where the space is whatever is left. */
  picker: 8,
} as const

/**
 * The sizes an admin's working list lets the reader pick (#271). The last one is the backend's cap
 * (`spring.data.web.pageable.max-page-size`, #173): offering more would be offering a size the
 * server silently shrinks.
 */
export const adminPageSizeOptions = [10, 25, 50, 100] as const

/**
 * Reads the `?size=` of an admin listing (#185, #271).
 *
 * Anything that is not one of the offered sizes - a hand-edited URL, an old link - falls back to the
 * default instead of reaching the API: the selector could not show it, and the reader would be on a
 * size they cannot see.
 *
 * @param raw the parameter as it came from the URL, or null when absent
 * @returns the size to request
 */
export function adminPageSizeFrom(raw: string | null): number {
  const size = Number(raw)
  return (adminPageSizeOptions as readonly number[]).includes(size) ? size : pageSize.admin
}
