import type { ReactNode } from 'react'

/** What the header of a screen shows. */
interface PageHeaderProps {
  /** The screen's name, already translated. Rendered as its only `h1`. */
  title: ReactNode
  /** One line under the title saying what the screen is for, already translated. */
  description?: ReactNode
  /** What sits across from the title: the help link, the screen's main button. */
  actions?: ReactNode
}

/**
 * The top of a screen: its title, what it is for, and the one or two things that belong next to the
 * title rather than inside the content (#273).
 *
 * It exists because the same header was written by hand on every screen - the admin ones alone had
 * seven copies - and each copy drifted a little: the gap, whether the row wraps, where the
 * description sat. One component means changing how a screen introduces itself is one edit.
 *
 * The title uses `.page-title`; a screen whose top is not this shape (a table's detail, a profile)
 * uses the class directly instead of bending this component.
 *
 * @param props.title       the screen's name
 * @param props.description optional one-line purpose
 * @param props.actions     optional help link or main button, across from the title
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0 space-y-1">
        <h1 className="page-title">{title}</h1>
        {description && <p className="text-fg-muted text-sm">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </div>
  )
}
