import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { groupByCategory, SAFETY_TAG_CATEGORIES, type TagCategoryGroup } from '../tagCategories'
import type { CatalogValue, TagCategory } from '../types'
import { CatalogChip } from './CatalogChip'

/** What the block needs. */
export interface TagGroupsProps {
  /** The table's tags, in any order. */
  tags: CatalogValue[]
  /** The heading of the block that holds content warnings and safety tools, in the screen's words. */
  safetyHeading: string
}

/** Whether a category belongs in the block a player reads before joining. */
function isSafety(category: TagCategory | null): boolean {
  return SAFETY_TAG_CATEGORIES.some((safety) => safety === category)
}

/**
 * A table's tags grouped by category, each group under its name and the unclassified ones last.
 *
 * **Content warnings and safety tools go in a block of their own, with its own heading**: they are
 * what a player has to know before asking to join, and mixed in with the themes they would read as
 * one more flavour of the table.
 *
 * @param props.tags          the table's tags
 * @param props.safetyHeading the heading of the content warnings and safety tools block
 */
export function TagGroups({ tags, safetyHeading }: TagGroupsProps) {
  const safetyId = useId()
  const general = groupByCategory(tags.filter((tag) => !isSafety(tag.category)))
  const safety = groupByCategory(tags.filter((tag) => isSafety(tag.category)))

  if (tags.length === 0) return null

  return (
    <div className="space-y-4">
      {general.length > 0 && <CategoryList groups={general} />}
      {safety.length > 0 && (
        <section aria-labelledby={safetyId}>
          <h2 id={safetyId} className="section-label">
            {safetyHeading}
          </h2>
          <div className="mt-1.5">
            <CategoryList groups={safety} />
          </div>
        </section>
      )}
    </div>
  )
}

/** Each group as a term - its category - and its tags. */
function CategoryList({ groups }: { groups: TagCategoryGroup<CatalogValue>[] }) {
  const { t } = useTranslation('catalogs')
  return (
    <dl className="space-y-2">
      {groups.map(({ category, values }) => (
        <div key={category ?? 'none'}>
          <dt className="text-fg-muted text-xs">{t(`tagCategory.${category ?? 'none'}`)}</dt>
          <dd className="mt-1 flex flex-wrap gap-1.5">
            {values.map((value) => (
              <CatalogChip key={value.id} value={value} />
            ))}
          </dd>
        </div>
      ))}
    </dl>
  )
}
