import type { TagCategory } from './types'

/**
 * Every tag category, in the order the screens show them: what the table is and how it plays
 * first, who it is for after. The `satisfies` keeps the list in step with the union - a code the
 * backend adds and this list forgets is a type error, not a tag that silently has no block.
 */
export const TAG_CATEGORIES = [
  'Theme',
  'PlayStyle',
  'Format',
  'Experience',
  'Language',
  'Age',
  'ContentWarning',
  'SafetyTool',
] as const satisfies readonly TagCategory[]

/**
 * The categories a player has to see before joining, set apart from the rest on a table's page:
 * what the table may show, and how it keeps people safe while it does.
 */
export const SAFETY_TAG_CATEGORIES = ['ContentWarning', 'SafetyTool'] as const satisfies readonly TagCategory[]

/**
 * Orders tags by category, in the order of {@link TAG_CATEGORIES}, then by name; the ones without a
 * category go last. What the combobox and the table's page both group by.
 *
 * @param a one tag
 * @param b the other
 * @returns negative, zero or positive, as `Array.prototype.sort` expects
 */
export function compareByCategory(
  a: { category: TagCategory | null; name: string },
  b: { category: TagCategory | null; name: string },
): number {
  const rank = (category: TagCategory | null) => (category === null ? TAG_CATEGORIES.length : TAG_CATEGORIES.indexOf(category))
  return rank(a.category) - rank(b.category) || a.name.localeCompare(b.name)
}

/** Tags that share a category, under it. */
export interface TagCategoryGroup<T> {
  /** The category, or null for the tags nobody classified. */
  category: TagCategory | null
  /** The tags in it, by name. */
  values: T[]
}

/**
 * Splits tags into one group per category, in the order of {@link TAG_CATEGORIES}, with the
 * unclassified ones last. Categories with no tag are left out.
 *
 * @param values the tags, in any order
 * @returns the non-empty groups, in display order
 */
export function groupByCategory<T extends { category: TagCategory | null; name: string }>(values: readonly T[]): TagCategoryGroup<T>[] {
  const groups: TagCategoryGroup<T>[] = []
  for (const value of [...values].sort(compareByCategory)) {
    const last = groups.at(-1)
    if (last && last.category === value.category) last.values.push(value)
    else groups.push({ category: value.category, values: [value] })
  }
  return groups
}
