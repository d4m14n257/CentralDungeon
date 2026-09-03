import { useTranslation } from 'react-i18next'

import { HelpAudienceGate } from './HelpAudienceGate'
import { HelpList, HelpSection, HelpSteps } from './HelpSection'

/**
 * Lo que necesita saber quien modera (decisiones.md #168), y cómo se hace cada cosa (#170).
 * Owner entra acá y no en una audiencia propia: es un admin con más privilegios (#169).
 */
export function HelpAdminsTab() {
  const { t } = useTranslation('help')

  return (
    <HelpAudienceGate audience="admins">
      <div className="space-y-6">
        <HelpSection id="reviewing" title={t('admins.reviewing.title')}>
          <HelpList items={['queue', 'approve', 'gone'].map((key) => t(`admins.reviewing.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`admins.reviewing.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="assign-masters" title={t('admins.assignMasters.title')}>
          <HelpList items={['create', 'search', 'order', 'opens', 'delete'].map((key) => t(`admins.assignMasters.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5, 6].map((n) => t(`admins.assignMasters.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="owner" title={t('admins.owner.title')}>
          <HelpList items={['same', 'exclusive', 'soon'].map((key) => t(`admins.owner.${key}`))} />
        </HelpSection>
      </div>
    </HelpAudienceGate>
  )
}

export { HelpAdminsTab as Component }
