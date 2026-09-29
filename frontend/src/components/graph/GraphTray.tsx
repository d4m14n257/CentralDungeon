import type { ReactNode } from 'react'

/** What a tray holds. */
export interface GraphTrayProps {
  /** The tray's heading, already passed through `t()`. */
  title: string
  /** A line under the heading that says what the tray is for. */
  description?: string
  /** The tray's content: a picker, a legend, a list. */
  children: ReactNode
}

/**
 * The side panel of a node canvas (skill `diseno` §5, #275): what the canvas needs next to it and
 * cannot draw as a node - bringing another group in, a legend, what floating means.
 *
 * Next to the canvas from `md` up and above it below, like every two-column layout (skill `diseno`
 * §5.b); the grid is the page's, the frame and the spacing are this component's.
 *
 * @param props.title       the heading
 * @param props.description what the tray is for
 * @param props.children    what it holds
 */
export function GraphTray({ title, description, children }: GraphTrayProps) {
  return (
    <aside className="graph-tray">
      <div className="space-y-1">
        <h2 className="section-title">{title}</h2>
        {description && <p className="text-fg-muted text-sm">{description}</p>}
      </div>
      {children}
    </aside>
  )
}
