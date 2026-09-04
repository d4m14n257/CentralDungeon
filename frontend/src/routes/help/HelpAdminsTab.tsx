import { useTranslation } from 'react-i18next'

import { HelpAudienceGate } from './HelpAudienceGate'
import { HelpList, HelpSection, HelpSteps } from './HelpSection'

/**
 * What somebody moderating needs to know (decisiones.md #168), and how each thing is done (#170).
 * Owner reads this rather than an audience of its own: it is an admin with more privileges (#169).
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

        <HelpSection id="catalogs" title={t('admins.catalogs.title')}>
          <HelpList
            items={['what', 'pending', 'groups', 'alias', 'merge', 'disable', 'successor'].map((key) => t(`admins.catalogs.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5, 6].map((n) => t(`admins.catalogs.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="files" title={t('admins.files.title')}>
          <HelpList
            items={['what', 'publish', 'audience', 'notALock', 'unpublish', 'remove', 'purge'].map((key) => t(`admins.files.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`admins.files.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="owner" title={t('admins.owner.title')}>
          <HelpList items={['same', 'exclusive', 'soon'].map((key) => t(`admins.owner.${key}`))} />
        </HelpSection>
      </div>
    </HelpAudienceGate>
  )
}

export { HelpAdminsTab as Component }
