import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

interface LoadMoreProps {
  hasMore: boolean
  isLoading: boolean
  onLoadMore: () => void
  shown: number
  total: number
}

/**
 * Paginación de un listado de lectura: se trae más, no se salta de página (decisiones.md #173).
 *
 * Botón explícito y no scroll infinito: el scroll infinito se dispara solo, deja el pie de página
 * inalcanzable y no avisa cuánto falta. Acá siempre se ve cuántos hay de cuántos, que es la mitad
 * de la información que una paginación tiene que dar.
 */
export function LoadMore({ hasMore, isLoading, onLoadMore, shown, total }: LoadMoreProps) {
  const { t } = useTranslation('common')

  return (
    <div className="flex flex-col items-center gap-2 py-2">
      <p className="text-fg-muted text-xs">{t('pagination.showing', { shown, total })}</p>
      {hasMore && (
        <Button variant="outline" size="sm" onClick={onLoadMore} disabled={isLoading}>
          {isLoading ? t('pagination.loading') : t('pagination.loadMore')}
        </Button>
      )}
    </div>
  )
}
