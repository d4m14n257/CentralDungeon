import { useTranslation } from 'react-i18next'

/**
 * What a screen paints when the thing it was asked for does not exist (a 404).
 *
 * It is not an error: nothing failed and trying again will not change the answer, so it has no retry.
 * A resource the reader may not know about also answers 404 on purpose, so the wording does not
 * distinguish a deleted resource from one that was never there.
 *
 * @param props.description optional override of the default explanation
 */
export function NotFoundState({ description }: { description?: string }) {
  const { t } = useTranslation('common')

  return (
    <div className="border-border flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{t('states.notFoundTitle')}</p>
      <p className="text-muted-foreground max-w-sm text-sm">{description ?? t('states.notFoundDescription')}</p>
    </div>
  )
}
