import { useTranslation } from 'react-i18next'

/**
 * What a screen paints on a 403.
 *
 * It exists because the context layouts do **not** check roles (#103): the navigation is UI
 * organisation, the backend is the authorization, and someone who forces a route they cannot use
 * has to land on an explanation rather than on a blank page.
 *
 * @param props.description optional override of the default explanation
 */
export function ForbiddenState({ description }: { description?: string }) {
  const { t } = useTranslation('common')

  return (
    <div className="border-border flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-center">
      <p className="font-medium">{t('states.forbiddenTitle')}</p>
      <p className="text-muted-foreground max-w-sm text-sm">{description ?? t('states.forbiddenDescription')}</p>
    </div>
  )
}
