import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'

interface PaginationControlsProps {
  /** The current page, zero-based — the same base the backend uses. */
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

/**
 * Pagination for a working list (decisiones.md #173): numbered pages do earn their place here,
 * because somebody moderating needs to know how much is left and to get back to where they were.
 * Previous and next, not a strip of numbers: with the listing's fixed order, jumping to page 7 does not
 * significa nada estable.
 */
export function PaginationControls({ page, totalPages, totalElements, onPageChange }: PaginationControlsProps) {
  const { t } = useTranslation('common')

  if (totalPages <= 1) return null

  return (
    <nav aria-label={t('pagination.label')} className="flex items-center justify-between gap-4 pt-2">
      <p className="text-fg-muted text-xs">
        {t('pagination.page', { page: page + 1, pages: totalPages })} · {t('pagination.total', { total: totalElements })}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page === 0}>
          <ChevronLeft className="size-4" />
          {t('pagination.previous')}
        </Button>
        <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages}>
          {t('pagination.next')}
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  )
}
