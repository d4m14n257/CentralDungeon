import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'

interface BatchActionBarProps {
  /** How many rows are marked. */
  count: number
  /** Opens the one confirmation that runs the batch. */
  onApply: () => void
  /** Drops the batch without running anything. */
  onCancel: () => void
  /** Whether the batch is running, which switches both buttons off. */
  isPending?: boolean
}

/**
 * The bar that appears while rows are marked for a batch action: how many, and «Apply» or «Cancel».
 *
 * A named region, so a screen reader can find it; the count sits in a polite live region, so marking
 * and unmarking is announced without moving focus off the row being worked on. It says nothing about
 * what the action is — the confirmation does, with the count — which keeps it free of any domain.
 *
 * @param props.count     how many rows are marked
 * @param props.onApply   opens the confirmation that runs the batch
 * @param props.onCancel  drops the batch
 * @param props.isPending whether the batch is running
 */
export function BatchActionBar({ count, onApply, onCancel, isPending = false }: BatchActionBarProps) {
  const { t } = useTranslation('common')

  return (
    <section aria-label={t('batch.region')} className="batch-bar">
      <p aria-live="polite" aria-atomic="true" className="text-sm font-medium">
        {t('batch.marked', { count })}
      </p>
      <div className="flex gap-2">
        <Button type="button" variant="outline" size="sm" disabled={isPending} onClick={onCancel}>
          {t('actions.cancel')}
        </Button>
        <Button type="button" size="sm" disabled={isPending} onClick={onApply}>
          {t('batch.apply')}
        </Button>
      </div>
    </section>
  )
}
