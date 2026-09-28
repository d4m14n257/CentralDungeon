import { useTranslation } from 'react-i18next'

/**
 * What a screen paints on a 403.
 *
 * A context the reader does not have never gets this far - its layout sends them home first (#269).
 * What is left is the other refusal: a concrete resource, inside a context they do have, that is not
 * theirs (#121). The backend is the authorization, and that answer has to land on an explanation
 * rather than on a blank page.
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
