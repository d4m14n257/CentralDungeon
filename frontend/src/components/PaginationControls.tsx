import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/components/ui/button'

interface PaginationControlsProps {
  /** Página actual, base 0 — la misma base que usa el backend. */
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

/**
 * Paginación de una lista de trabajo (decisiones.md #173): acá sí hacen falta páginas con número,
 * porque quien modera necesita saber cuánto falta y poder volver a donde estaba. Anterior y
 * siguiente, no una tira de números: con el orden fijo del listado, saltar a la página 7 no
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
