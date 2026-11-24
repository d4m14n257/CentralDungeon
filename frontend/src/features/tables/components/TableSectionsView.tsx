import { useTranslation } from 'react-i18next'

import { RichTextView } from '@/components/RichTextView'

import { TABLE_SECTION_KEYS } from '../lib/tableSections'
import type { TableSections } from '../types'

interface TableSectionsViewProps {
  /** The table's four optional sections, as its detail carries them. */
  sections: TableSections
}

/**
 * The optional sections of a table on its detail, each under its own heading. Only the ones the
 * master wrote are shown: an unwritten section is not an empty heading.
 *
 * @param props.sections the table's four optional sections
 */
export function TableSectionsView({ sections }: TableSectionsViewProps) {
  const { t } = useTranslation('tables')

  return (
    <>
      {TABLE_SECTION_KEYS.map((key) => {
        const html = sections[key]
        if (!html) return null
        return (
          <section key={key}>
            <h2 className="section-label">{t(`sections.${key}.label`)}</h2>
            <RichTextView html={html} className="mt-1.5 max-w-prose" />
          </section>
        )
      })}
    </>
  )
}
