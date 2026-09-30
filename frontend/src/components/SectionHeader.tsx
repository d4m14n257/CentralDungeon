import type { ReactNode } from 'react'

import { HelpButton, type HelpSectionId } from '@/features/help'

/** What the header of a section shows. */
interface SectionHeaderProps {
  /** The section's name, already translated. Rendered as an `h2` with `.section-label`. */
  title: ReactNode
  /** The help section that explains this part of the screen, opened by «Cómo funciona». */
  help?: HelpSectionId
  /** Whatever acts on the whole section, after the help — a small button, a count. */
  actions?: ReactNode
}

/**
 * The top of a section inside a screen — a tab of a table, a block of a detail — with its
 * «Cómo funciona» at the right end, the same place `PageHeader` puts it for a whole screen (#280).
 *
 * It exists because a section's help used to land wherever the section ended: at the bottom of the
 * files tab, under the tasks board, next to a title on one tab and below the list on the next.
 *
 * @param props.title   the section's name
 * @param props.help    optional help section for «Cómo funciona»
 * @param props.actions optional controls for the whole section, after the help
 */
export function SectionHeader({ title, help, actions }: SectionHeaderProps) {
  return (
    <div className="flex items-center justify-between gap-2">
      <h2 className="section-label min-w-0">{title}</h2>
      {(help || actions) && (
        <div className="flex shrink-0 items-center gap-2">
          {help && <HelpButton section={help} size="sm" />}
          {actions}
        </div>
      )}
    </div>
  )
}
