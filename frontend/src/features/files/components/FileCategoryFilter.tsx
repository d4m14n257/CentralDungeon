import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import { FILE_CATEGORIES } from '../categories'
import type { FileCategory } from '../types'

interface FileCategoryFilterProps {
  /** The category being shown, or null for all of them. */
  value: FileCategory | null
  /** Called with the new category, or null when the filter is cleared. */
  onChange: (value: FileCategory | null) => void
  /** Extra classes for the caller's layout. */
  className?: string
  /** Which cajones to offer, or undefined for all five. */
  options?: readonly FileCategory[]
}

/**
 * Narrowing a list of files to one cajón (#233).
 *
 * **A row of toggles and not a `<Select>`**, because the whole set is five short values and the
 * point is to see what is on offer without opening anything — the same reason a filter bar beats a
 * dropdown whenever the options fit on one line (`frontend-diseno.md` §5.b).
 *
 * Selecting the active one clears it, so there is no separate "all" button competing with the five
 * real answers, and no state the person can get stuck in.
 *
 * @param props.value     the category being shown, or null for all of them
 * @param props.onChange  called with the new category, or null when cleared
 * @param props.className extra classes for the caller's layout
 * @param props.options   which cajones to offer, or undefined for all five
 */
export function FileCategoryFilter({ value, onChange, className, options }: FileCategoryFilterProps) {
  const { t } = useTranslation('files')

  return (
    <div className={cn('flex flex-wrap gap-1.5', className)} role="group" aria-label={t('category.filterLabel')}>
      {(options ?? FILE_CATEGORIES).map((category) => {
        const isActive = value === category
        return (
          <Button
            key={category}
            type="button"
            size="sm"
            variant={isActive ? 'secondary' : 'ghost'}
            aria-pressed={isActive}
            className={cn('h-7 px-2.5 text-xs', isActive && 'border-border border')}
            onClick={() => onChange(isActive ? null : category)}
          >
            {t(`category.${category}`)}
          </Button>
        )
      })}
    </div>
  )
}
