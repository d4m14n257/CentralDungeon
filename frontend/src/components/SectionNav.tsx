import { NavLink } from 'react-router'

import { cn } from '@/lib/utils'

/** One section of a context's navigation. */
export interface SectionNavItem {
  /** Where the section lives, from a builder of `config/paths.ts`. */
  to: string
  /** Its name, already passed through `t()`. */
  label: string
  /** Match only this exact path - for a context's home, or it would stay underlined everywhere. */
  end?: boolean
}

/** What the navigation takes. */
export interface SectionNavProps {
  /** The navigation's accessible name, already passed through `t()`. */
  label: string
  /** The sections, in the order they are read. */
  sections: SectionNavItem[]
}

/**
 * The row of sections under the header of a context - Player, Master, Admin - with the current one
 * underlined (skill `diseno` §5.c, #273). The three contexts each had their own copy of these
 * classes; one component means the next fix lands in all three.
 *
 * **It never overflows the screen** (skill `diseno` §5.b): when the sections do not fit - admin's
 * eight on a phone - they wrap to a second line instead of pushing the page sideways. Wrapping and
 * not scrolling inside the bar, because a section scrolled out of sight is a section nobody finds.
 * On any wider screen it stays inside the layout's `max-w-5xl` like everything else, however wide the
 * window is.
 *
 * @param props.label    the navigation's accessible name
 * @param props.sections the sections to link to
 */
export function SectionNav({ label, sections }: SectionNavProps) {
  return (
    <nav aria-label={label} className="border-border flex min-w-0 flex-wrap gap-x-1 border-b">
      {sections.map((section) => (
        <NavLink
          key={section.to}
          to={section.to}
          end={section.end ?? false}
          className={({ isActive }) =>
            cn(
              '-mb-px border-b-2 px-3 py-2 text-sm whitespace-nowrap transition-colors',
              isActive ? 'border-brand-400 text-fg font-medium' : 'text-fg-muted hover:text-fg border-transparent',
            )
          }
        >
          {section.label}
        </NavLink>
      ))}
    </nav>
  )
}
