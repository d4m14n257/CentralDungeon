import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * The state tones of the design system (`frontend-diseno.md` §3), which is what a badge picks from.
 *
 * These are **not** domain statuses and that is the whole point of naming them separately: `open`
 * means "the colour family for something that is open", and eight different features decide on their
 * own which of their states wears it. A table's `Opened`, a catalog value's `Accepted`, a task's
 * `Open` and a file's `Public` all land here, and none of them belongs in this file.
 */
export type StatusTone = 'draft' | 'pending' | 'open' | 'active' | 'paused' | 'done' | 'warning' | 'canceled' | 'blocked'

/**
 * Tone to classes, written out as complete literals **on purpose**.
 *
 * Tailwind 4 scans the source for class names and cannot see one built out of a template string, so
 * `bg-state-${tone}-bg` would compile, pass every test that looks for text, and render an unstyled
 * badge in the browser. Ten call sites used to each carry their own copy of this hazard; now it is in
 * one file, which is the second reason for the tone prop and not a pair of class names as the API.
 */
const TONE_CLASSES: Record<StatusTone, { badge: string; dot: string }> = {
  draft: { badge: 'bg-state-draft-bg text-state-draft-fg', dot: 'bg-state-draft-dot' },
  pending: { badge: 'bg-state-pending-bg text-state-pending-fg', dot: 'bg-state-pending-dot' },
  open: { badge: 'bg-state-open-bg text-state-open-fg', dot: 'bg-state-open-dot' },
  active: { badge: 'bg-state-active-bg text-state-active-fg', dot: 'bg-state-active-dot' },
  paused: { badge: 'bg-state-paused-bg text-state-paused-fg', dot: 'bg-state-paused-dot' },
  done: { badge: 'bg-state-done-bg text-state-done-fg', dot: 'bg-state-done-dot' },
  warning: { badge: 'bg-state-warning-bg text-state-warning-fg', dot: 'bg-state-warning-dot' },
  canceled: { badge: 'bg-state-canceled-bg text-state-canceled-fg', dot: 'bg-state-canceled-dot' },
  blocked: { badge: 'bg-state-blocked-bg text-state-blocked-fg', dot: 'bg-state-blocked-dot' },
}

interface StatusBadgeProps {
  /** Which colour family to wear. The feature maps its own status onto it. */
  tone: StatusTone
  /**
   * The label, **already translated**.
   *
   * A `ReactNode` and not a `string` so a feature can interpolate — `ClaimBadge` says who holds a row
   * — but it is never optional: colour is never the only carrier of meaning (`frontend-diseno.md` §3),
   * and a badge with a dot and nothing to read is exactly that.
   */
  label: ReactNode
  /** Trailing detail after the label, inside the same badge. Used for the "how long ago" of a claim. */
  children?: ReactNode
  /** Extra classes on the badge itself, for a caller that has to place it. */
  className?: string
}

/**
 * A status as a badge: a coloured dot and a label, in the one shape the whole application uses.
 *
 * **It knows nothing about any domain, and it cannot** (`arquitectura.md` §3.1.2: «al subir se le
 * quita el dominio»). It receives the tone already chosen and the label already translated, so the
 * two things that are genuinely each feature's — which of its states looks how, and what the state is
 * called in two languages — stay in the feature, next to the union they belong to.
 *
 * **Why it exists** (#261). It was written **ten** times before this: `TableStatusBadge`,
 * `SessionStatusBadge`, `RegistrationStatusBadge`, `UserStatusBadge`, `RequestStatusBadge`,
 * `CatalogStatusBadge`, `TaskStatusBadge`, `FileTypeBadge`, `ClaimBadge`, and once more inline in
 * `NotificationsPage` - each carrying this markup byte for byte. §3.1.2 puts the threshold at **two**
 * real uses and says why it is lower than the backend's: «acá la alternativa a subir no es un poco de
 * duplicación: es un import prohibido». Ten was not a borderline case.
 *
 * What the ten actually differed in was three things - the i18n namespace, the key prefix, and the map
 * from status to tone - and all three stay where they were.
 *
 * @param props.tone      the colour family
 * @param props.label     the translated label; never omitted
 * @param props.children  trailing detail inside the badge
 * @param props.className extra classes on the badge
 */
export function StatusBadge({ tone, label, children, className }: StatusBadgeProps) {
  const classes = TONE_CLASSES[tone]

  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium', classes.badge, className)}>
      <span className={cn('size-1.5 rounded-full', classes.dot)} />
      {label}
      {children}
    </span>
  )
}
