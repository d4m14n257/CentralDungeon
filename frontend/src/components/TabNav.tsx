import { NavLink } from 'react-router'

import { cn } from '@/lib/utils'

/** One tab of a {@link TabNav}. */
export interface TabNavItem {
  /** Where the tab goes, relative to the screen that owns the tabs: `.` for the first, then each child route. */
  to: string
  /** What the tab is called, already passed through `t()`. */
  label: string
  /** Whether it is active only on its exact path - true for the index tab, or it would stay lit under every other. */
  end?: boolean
}

/** What {@link TabNav} takes. */
export interface TabNavProps {
  /** The tabs, in the order they are read. */
  items: readonly TabNavItem[]
  /** What the row of tabs is, for a screen reader - the tabs alone do not say whose they are. */
  label: string
}

/**
 * A screen's tabs, as links to its child routes (arquitectura §3.1.6 regla 5): each tab has a URL, can
 * be shared and survives the back button.
 *
 * **A named pattern** (#273) since a second screen needed it (#284): the table as its master sees it
 * and as an admin sees it draw the same row of tabs, and the underline that marks the active one was a
 * string of utilities written into the first of them.
 *
 * @param props.items the tabs
 * @param props.label what the row of tabs is
 */
export function TabNav({ items, label }: TabNavProps) {
  return (
    <nav aria-label={label} className="border-border-strong flex flex-wrap gap-4 border-b">
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end ?? false}
          className={({ isActive }) =>
            cn(
              '-mb-px border-b-2 px-1 pb-2 text-sm font-medium',
              isActive ? 'border-brand-fg text-fg' : 'border-transparent text-fg-muted hover:text-fg',
            )
          }
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
