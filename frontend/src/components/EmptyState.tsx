import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description?: string
  action?: ReactNode
}

/**
 * One of the four states every screen has to cover (frontend-diseno.md 5): there is nothing to show,
 * and that is fine.
 *
 * It has to read as an answer, not as a failure - an empty inbox says nothing is waiting, it does
 * not say something broke.
 *
 * @param props.title  what is empty, in the user's words
 * @param props.description optional context, or what to do about it
 * @param props.action optional call to action - the button that fills the emptiness
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="border-border flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{title}</p>
      {description && <p className="text-muted-foreground max-w-sm text-sm">{description}</p>}
      {action}
    </div>
  )
}
