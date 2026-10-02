import type { ReactNode } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

/** What {@link StatusNotice} takes. */
export interface StatusNoticeProps {
  /** Where the entity stands, said as a situation the reader recognizes: «Esta mesa espera tu revisión». */
  title: string
  /** One sentence on what each of the actions below does, or what follows. */
  description: ReactNode
  /** The decisions that change where the entity stands, as buttons with their text. Absent when there is none. */
  actions?: ReactNode
}

/**
 * The notice that says where an entity stands and carries the decisions that move it, above the
 * screen's tabs (#287).
 *
 * **A named pattern because the alternative looked broken** (#273, #287): the admin's decisions about
 * a table were first a section of their own on a "Status" tab - a heading and one button, or none -
 * which the user found ugly, and which put the decision at the end of the last tab, where it was not
 * found. A decision belongs where the situation is said, and the situation belongs above everything
 * else: this block says both at once, and is visible from every tab.
 *
 * Text buttons and not icons: they are the screen's decisions, not a row's actions (#272). Built on
 * the inventory's `Alert`, default variant, so it follows both themes with no colour of its own.
 *
 * @param props.title       where the entity stands
 * @param props.description what the actions do, or what follows
 * @param props.actions     the decisions, as buttons
 */
export function StatusNotice({ title, description, actions }: StatusNoticeProps) {
  return (
    <Alert>
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{description}</p>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </AlertDescription>
    </Alert>
  )
}
