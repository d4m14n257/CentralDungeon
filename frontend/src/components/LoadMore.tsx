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
 * Pagination for a reading list: it brings more, it does not jump between pages (decisiones.md #173).
 *
 * An explicit button and not infinite scroll: infinite scroll fires on its own, leaves the footer
 * unreachable and never says how much is left. Here you always see how many of how many, which is
 * half of what pagination has to tell you.
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
