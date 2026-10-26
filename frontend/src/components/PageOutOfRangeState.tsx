import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

interface PageOutOfRangeStateProps {
  /** How many pages the listing has. */
  totalPages: number
  /** How many rows the listing has, across every page. */
  totalElements: number
  /** Called with the zero-based page to go to. */
  onPageChange: (page: number) => void
}

/**
 * What a paged list paints when the page asked for (a hand-edited `?page=`, or the rows of the last
 * page deleted) lies past the end while the list is not empty.
 *
 * It is not the empty state: saying "there is nothing" about a list that has rows hides where they
 * went, and the pager is no help here, since it hides itself when the list fits on one page.
 *
 * @param props.totalPages    how many pages there are
 * @param props.totalElements how many rows there are
 * @param props.onPageChange  called with the last page
 */
export function PageOutOfRangeState({ totalPages, totalElements, onPageChange }: PageOutOfRangeStateProps) {
  const { t } = useTranslation('common')

  return (
    <div className="border-border flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{t('pagination.outOfRangeTitle')}</p>
      <p className="text-muted-foreground max-w-sm text-sm">
        {t('pagination.outOfRangeDescription', { pages: totalPages, total: totalElements })}
      </p>
      <Button variant="outline" size="sm" onClick={() => onPageChange(Math.max(totalPages - 1, 0))}>
        {t('pagination.goToLast')}
      </Button>
    </div>
  )
}
