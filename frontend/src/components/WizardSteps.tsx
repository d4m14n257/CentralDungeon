import { CheckIcon } from 'lucide-react'

import { cn } from '@/lib/utils'

/**
 * One step of a wizard, as the indicator needs to know it.
 *
 * @typeParam Id - the caller's own union of step names, so `onGoTo` hands back that union and not a
 * bare `string` the caller has to cast (regla dura 6)
 */
export interface WizardStepDescriptor<Id extends string> {
  /** Stable identifier, used as the React key and handed back by `onGoTo`. */
  id: Id
  /** What the step is called, already translated. */
  label: string
}

/**
 * Props of {@link WizardSteps}.
 *
 * @typeParam Id - the caller's own union of step names
 */
export interface WizardStepsProps<Id extends string> {
  /** The steps, in the order they are walked. */
  steps: readonly WizardStepDescriptor<Id>[]
  /** Index into `steps` of the step being filled in right now. */
  currentIndex: number
  /** Accessible name of the list as a whole, translated. */
  label: string
  /** What to append to a step's accessible name for each of its three states, translated. */
  statusLabels: { done: string; current: string; pending: string }
  /** Asked to jump to a step. The caller decides whether the jump is allowed and what it costs. */
  onGoTo: (id: Id) => void
}

/**
 * The progress indicator of a multi-step form: which steps are behind you, which one you are on,
 * and how many are left.
 *
 * **The state is the point, not the numbering.** A plain list of numbered names says where you are
 * but not what you have already settled, so a long form reads as equally unfinished on the last
 * step as on the first. A step that is behind you gets a tick and a filled circle; the one you are
 * on is ringed; the ones ahead stay outlined.
 *
 * **Every step is a button, including the ones ahead.** Going back to change an answer is free, and
 * a forward jump is a legitimate request the owner of the form answers - it either takes you there
 * or lands you on the first thing you still owe. Which of the two happens is not this component's
 * decision: it only reports the click.
 *
 * @typeParam Id - the caller's own union of step names
 */
export function WizardSteps<Id extends string>({ steps, currentIndex, label, statusLabels, onGoTo }: WizardStepsProps<Id>) {
  return (
    <ol aria-label={label} className="flex items-start">
      {steps.map((step, index) => {
        const done = index < currentIndex
        const current = index === currentIndex
        return (
          <li key={step.id} className="relative flex min-w-0 flex-1 flex-col items-center">
            {/* The track, drawn behind the circles: coloured up to the step you are on, so the
                filled length is the progress and not just a decoration between dots. */}
            {index > 0 && (
              <span
                aria-hidden
                className={cn(
                  'absolute top-3.5 right-1/2 left-0 h-px -translate-y-1/2',
                  index <= currentIndex ? 'bg-brand-500' : 'bg-border',
                )}
              />
            )}
            {index < steps.length - 1 && (
              <span
                aria-hidden
                className={cn('absolute top-3.5 right-0 left-1/2 h-px -translate-y-1/2', done ? 'bg-brand-500' : 'bg-border')}
              />
            )}
            <button
              type="button"
              onClick={() => onGoTo(step.id)}
              aria-current={current ? 'step' : undefined}
              className="group focus-visible:ring-ring flex w-full flex-col items-center gap-1.5 rounded-md px-1 py-1 focus-visible:ring-2 focus-visible:outline-none"
            >
              <span
                className={cn(
                  'relative flex size-7 shrink-0 items-center justify-center rounded-full border text-xs font-medium transition-colors',
                  done && 'border-brand-500 bg-primary text-primary-foreground group-hover:bg-brand-400',
                  current && 'border-brand-500 text-brand-fg bg-canvas ring-brand-500/25 ring-4',
                  !done && !current && 'border-border text-fg-subtle bg-canvas group-hover:border-border-strong',
                )}
              >
                {done ? <CheckIcon className="size-3.5" aria-hidden /> : index + 1}
              </span>
              <span
                className={cn(
                  'text-center text-[11px] leading-tight',
                  current ? 'text-fg font-medium' : done ? 'text-fg-muted' : 'text-fg-subtle',
                )}
              >
                {step.label}
              </span>
              <span className="sr-only">{done ? statusLabels.done : current ? statusLabels.current : statusLabels.pending}</span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}
