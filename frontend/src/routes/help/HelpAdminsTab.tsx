import { useTranslation } from 'react-i18next'

import { HelpList, HelpSection } from './HelpSection'

/** Lo que necesita saber quien modera (decisiones.md #168). Owner incluido: puede todo lo de admin. */
export function HelpAdminsTab() {
  const { t } = useTranslation('help')

  return (
    <div className="space-y-6">
      <HelpSection id="reviewing" title={t('admins.reviewing.title')}>
        <HelpList items={['queue', 'approve', 'gone'].map((key) => t(`admins.reviewing.${key}`))} />
      </HelpSection>

      <HelpSection id="assign-masters" title={t('admins.assignMasters.title')}>
        <HelpList items={['create', 'search', 'order', 'opens'].map((key) => t(`admins.assignMasters.${key}`))} />
      </HelpSection>

      <HelpSection id="owner" title={t('admins.owner.title')}>
        <HelpList items={['same', 'soon'].map((key) => t(`admins.owner.${key}`))} />
      </HelpSection>
    </div>
  )
}

export { HelpAdminsTab as Component }
