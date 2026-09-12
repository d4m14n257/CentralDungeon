import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import type { FileCategory } from '../types'

interface FileCategoryChoiceProps {
  /** The cajón currently chosen. Always one — this is a choice, not a filter. */
  value: FileCategory
  /** Called with the cajón that was picked. */
  onChange: (value: FileCategory) => void
  /** The cajones to offer. Only the actor's own (#237). */
  options: readonly FileCategory[]
  /** The accessible name of the group, since the chips have no visible legend of their own. */
  label: string
  /** Disables the whole group while an upload is in flight. */
  disabled?: boolean
  /** Extra classes for the caller's layout. */
  className?: string
}

/**
 * Choosing which cajón a file goes in, as chips (#233).
 *
 * **Chips and not a `<Select>`**, for the reason {@link FileCategoryFilter} is one too: the whole set
 * is two to four short values, and a dropdown hides what is on offer behind a click. Here it matters
 * more than in a filter — this is a decision somebody has to make before the upload, so seeing every
 * option at once is what lets them make it without exploring.
 *
 * **It always has a value**, which is the difference from the filter: clicking the active chip does
 * nothing rather than clearing it. A file has to be filed somewhere, and "no cajón" is not an answer
 * this control may produce.
 *
 * `radio` and not `button` on each chip: one of a set, exactly one chosen, which is what a radio
 * group *is* — and it gives arrow-key navigation for free. The visual is a chip; the semantics are
 * what a screen reader needs.
 *
 * @param props.value     the cajón currently chosen
 * @param props.onChange  called with the cajón that was picked
 * @param props.options   the cajones to offer
 * @param props.label     the accessible name of the group
 * @param props.disabled  disables the group while an upload is in flight
 * @param props.className extra classes for the caller's layout
 */
export function FileCategoryChoice({ value, onChange, options, label, disabled, className }: FileCategoryChoiceProps) {
  const { t } = useTranslation('files')

  return (
    <div className={cn('flex flex-wrap gap-1.5', className)} role="radiogroup" aria-label={label}>
      {options.map((category) => {
        const isActive = value === category
        return (
          <Button
            key={category}
            type="button"
            role="radio"
            size="sm"
            variant={isActive ? 'secondary' : 'ghost'}
            aria-checked={isActive}
            disabled={disabled ?? false}
            className={cn('h-8 px-3 text-xs', isActive ? 'border-primary border' : 'border-border border')}
            onClick={() => onChange(category)}
          >
            {t(`category.${category}`)}
          </Button>
        )
      })}
    </div>
  )
}
