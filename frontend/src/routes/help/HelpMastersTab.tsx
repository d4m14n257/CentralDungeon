import { useTranslation } from 'react-i18next'

import { HelpAudienceGate } from './HelpAudienceGate'
import { HelpList, HelpSection, HelpSteps } from './HelpSection'

/** Lo que necesita saber quien dirige (decisiones.md #168), y cómo se hace cada cosa (#170). */
export function HelpMastersTab() {
  const { t } = useTranslation('help')

  return (
    <HelpAudienceGate audience="masters">
      <div className="space-y-6">
        <HelpSection id="creating" title={t('masters.creating.title')}>
          <HelpList items={['role', 'wizard', 'submit'].map((key) => t(`masters.creating.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5].map((n) => t(`masters.creating.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="schedule" title={t('masters.schedule.title')}>
          <HelpList
            items={['local', 'duration', 'interval', 'chained', 'conflict', 'paused', 'players'].map((key) => t(`masters.schedule.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.schedule.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="propose-catalog" title={t('masters.proposeCatalog.title')}>
          <HelpList items={['what', 'pending', 'marked', 'existing'].map((key) => t(`masters.proposeCatalog.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.proposeCatalog.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="review" title={t('masters.review.title')}>
          <HelpList items={['approve', 'changes', 'history'].map((key) => t(`masters.review.${key}`))} />
        </HelpSection>

        <HelpSection id="candidates" title={t('masters.candidates.title')}>
          <HelpList items={['order', 'accept', 'reject'].map((key) => t(`masters.candidates.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.candidates.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="running" title={t('masters.running.title')}>
          <HelpList items={['start', 'cancel', 'who'].map((key) => t(`masters.running.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.running.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="deleting" title={t('masters.deleting.title')}>
          <HelpList items={['draft', 'public', 'gone'].map((key) => t(`masters.deleting.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`masters.deleting.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="co-masters" title={t('masters.coMasters.title')}>
          <HelpList items={['one', 'primary', 'assign'].map((key) => t(`masters.coMasters.${key}`))} />
        </HelpSection>
      </div>
    </HelpAudienceGate>
  )
}

export { HelpMastersTab as Component }
