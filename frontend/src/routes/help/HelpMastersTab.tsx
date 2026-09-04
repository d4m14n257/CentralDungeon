import { useTranslation } from 'react-i18next'

import { HelpAudienceGate } from './HelpAudienceGate'
import { HelpList, HelpSection, HelpSteps } from './HelpSection'

/** What somebody running a table needs to know (decisiones.md #168), and how each thing is done (#170). */
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

        <HelpSection id="sessions" title={t('masters.sessions.title')}>
          <HelpList
            items={[
              'materialize',
              'missing',
              'local',
              'correct',
              'notes',
              'hold',
              'cancel',
              'cancelRecord',
              'locked',
              'paused',
              'resumeConflict',
              'attendance',
            ].map((key) => t(`masters.sessions.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5].map((n) => t(`masters.sessions.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="tasks" title={t('masters.tasks.title')}>
          <HelpList
            items={['what', 'audience', 'single', 'notify', 'edit', 'answers', 'accumulate', 'noJudgement', 'mandatory', 'close'].map(
              (key) => t(`masters.tasks.${key}`),
            )}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.tasks.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="files" title={t('masters.files.title')}>
          <HelpList
            items={['what', 'reuse', 'published', 'shared', 'private', 'detach', 'limits'].map((key) => t(`masters.files.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.files.steps.step${n}`))} />
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
          <HelpList
            items={['one', 'primary', 'assign', 'add', 'cannotPlay', 'promote', 'removeKeeps', 'cannotRemovePrimary'].map((key) =>
              t(`masters.coMasters.${key}`),
            )}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.coMasters.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="dashboard" title={t('masters.dashboard.title')}>
          <HelpList
            items={['what', 'order', 'kinds', 'noReservation', 'empty', 'notMetrics'].map((key) => t(`masters.dashboard.${key}`))}
          />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`masters.dashboard.steps.step${n}`))} />
        </HelpSection>

        <HelpSection id="edit-table" title={t('masters.editTable.title')}>
          <HelpList items={['when', 'who', 'replaces', 'sets', 'conflict', 'resubmit'].map((key) => t(`masters.editTable.${key}`))} />
          <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`masters.editTable.steps.step${n}`))} />
        </HelpSection>
      </div>
    </HelpAudienceGate>
  )
}

export { HelpMastersTab as Component }
