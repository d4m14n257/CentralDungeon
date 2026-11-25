import type { ReactNode } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

/** What {@link StatusNotice} takes. */
export interface StatusNoticeProps {
  /** Where the entity stands, said as a situation the reader recognizes: "This table is waiting for your review". */
  title: string
  /** One sentence on what each of the actions below does, or what follows. */
  description: ReactNode
  /**
   * A second sentence on its own line under the description: what is going on with an action below, or why
   * one of them is off. Kept apart from `description` so a caller never glues two sentences into one
   * string. Absent when there is nothing to add.
   */
  hint?: ReactNode
  /** The decisions that change where the entity stands, as buttons with their text. Absent when there is none. */
  actions?: ReactNode
  /**
   * The destructive ones - cancel, remove - kept apart at the right end of the row, so a press
   * aimed at "Approve" never lands on "Cancel". The confirmation still catches it; this is the help
   * before it. On a narrow screen they wrap below, still on the right. Only when there are other
   * buttons to keep them away from: a destructive action that is the only one goes in `actions`.
   */
  destructiveActions?: ReactNode
}

/**
 * The notice that says where an entity stands and carries the decisions that move it, above the
 * screen's tabs.
 *
 * **A named pattern because the alternative looked broken**: the admin's decisions about
 * a table were first a section of their own on a "Status" tab - a heading and one button, or none -
 * which the user found ugly, and which put the decision at the end of the last tab, where it was not
 * found. A decision belongs where the situation is said, and the situation belongs above everything
 * else: this block says both at once, and is visible from every tab.
 *
 * Text buttons and not icons: they are the screen's decisions, not a row's actions. Built on
 * the inventory's `Alert`, default variant, so it follows both themes with no colour of its own.
 *
 * @param props.title              where the entity stands
 * @param props.description        what the actions do, or what follows
 * @param props.hint               a second sentence under it, or nothing
 * @param props.actions            the decisions, as buttons
 * @param props.destructiveActions the destructive ones, apart at the right
 */
export function StatusNotice({ title, description, hint, actions, destructiveActions }: StatusNoticeProps) {
  return (
    <Alert>
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="space-y-3">
        <p>{description}</p>
        {hint && <p className="text-fg-muted text-sm">{hint}</p>}
        {(actions || destructiveActions) && (
          <div className="flex w-full flex-wrap items-center gap-x-2 gap-y-4">
            {actions}
            {destructiveActions && <div className="ml-auto flex flex-wrap gap-2">{destructiveActions}</div>}
          </div>
        )}
      </AlertDescription>
    </Alert>
  )
}
