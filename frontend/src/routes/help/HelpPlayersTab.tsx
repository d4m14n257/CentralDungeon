import { useTranslation } from 'react-i18next'

import type { RegistrationStatus } from '@/features/registrations'

import { HelpAudienceGate } from './HelpAudienceGate'
import { HelpList, HelpSection, HelpSteps, HelpTerms } from './HelpSection'

const APPLICATION_STATUSES: RegistrationStatus[] = ['Candidate', 'Player', 'Rejected']

/** Lo que necesita saber quien juega (decisiones.md #168), y cómo se hace cada cosa (#170). */
export function HelpPlayersTab() {
  const { t } = useTranslation(['help', 'registrations'])

  return (
    <HelpAudienceGate audience="players">
      <div className="space-y-6">
        <HelpSection id="applying" title={t('help:players.applying.title')}>
          <HelpList items={['where', 'oneAtATime', 'blocked'].map((key) => t(`help:players.applying.${key}`))} />
          <HelpSteps title={t('help:stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`help:players.applying.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="application-status" title={t('help:players.applicationStatus.title')}>
          <p className="text-fg-muted text-sm">{t('help:players.applicationStatus.intro')}</p>
          <HelpTerms
            termWidth="w-28"
            terms={APPLICATION_STATUSES.map((status) => ({
              term: t(`registrations:status.${status}`),
              description: t(`help:players.applicationStatus.${status}`),
            }))}
          />
          <HelpList items={[t('help:players.applicationStatus.auto')]} />
        </HelpSection>

        <HelpSection id="my-tables" title={t('help:players.myTables.title')}>
          <HelpList items={['list', 'status'].map((key) => t(`help:players.myTables.${key}`))} />
        </HelpSection>
      </div>
    </HelpAudienceGate>
  )
}

export { HelpPlayersTab as Component }
