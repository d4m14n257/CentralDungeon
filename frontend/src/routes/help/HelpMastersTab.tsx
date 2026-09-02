import { useTranslation } from 'react-i18next'

import { HelpList, HelpSection } from './HelpSection'

/** Lo que necesita saber quien dirige (decisiones.md #168). */
export function HelpMastersTab() {
  const { t } = useTranslation('help')

  return (
    <div className="space-y-6">
      <HelpSection id="creating" title={t('masters.creating.title')}>
        <HelpList items={['role', 'wizard', 'submit'].map((key) => t(`masters.creating.${key}`))} />
      </HelpSection>

      <HelpSection id="review" title={t('masters.review.title')}>
        <HelpList items={['approve', 'changes', 'history'].map((key) => t(`masters.review.${key}`))} />
      </HelpSection>

      <HelpSection id="candidates" title={t('masters.candidates.title')}>
        <HelpList items={['order', 'accept', 'reject'].map((key) => t(`masters.candidates.${key}`))} />
      </HelpSection>

      <HelpSection id="running" title={t('masters.running.title')}>
        <HelpList items={['start', 'cancel', 'who'].map((key) => t(`masters.running.${key}`))} />
      </HelpSection>

      <HelpSection id="co-masters" title={t('masters.coMasters.title')}>
        <HelpList items={['one', 'primary', 'assign'].map((key) => t(`masters.coMasters.${key}`))} />
      </HelpSection>
    </div>
  )
}

export { HelpMastersTab as Component }
