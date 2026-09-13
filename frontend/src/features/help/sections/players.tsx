import { useTranslation } from 'react-i18next'

import type { RegistrationStatus } from '@/features/registrations'

import { HelpList, HelpSteps, HelpTerms } from '../components/HelpBlocks'

/** The help of somebody playing. Bodies only - the heading lives in the registry (#231). */

const APPLICATION_STATUSES: RegistrationStatus[] = ['Candidate', 'Player', 'Rejected']

/** Where you apply and what applying commits you to. */
export function ApplyingHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={['where', 'oneAtATime', 'blocked', 'sheet', 'review', 'noEdit'].map((key) => t(`players.applying.${key}`))} />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5].map((n) => t(`players.applying.steps.step${n}`))} />
    </>
  )
}

/** What each state of an application means. */
export function ApplicationStatusHelp() {
  const { t } = useTranslation(['help', 'registrations'])

  return (
    <>
      <p className="text-fg-muted text-sm">{t('help:players.applicationStatus.intro')}</p>
      <HelpTerms
        termWidth="w-28"
        terms={APPLICATION_STATUSES.map((status) => ({
          term: t(`registrations:status.${status}`),
          description: t(`help:players.applicationStatus.${status}`),
        }))}
      />
      <HelpList items={[t('help:players.applicationStatus.auto')]} />
    </>
  )
}

/** Why a table can clash with another one you already play (#178). */
export function ScheduleConflictsHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={['local', 'blocked', 'candidate', 'notified', 'withdraw'].map((key) => t(`players.scheduleConflict.${key}`))} />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`players.scheduleConflict.steps.step${n}`))} />
    </>
  )
}

/** What the list of your tables shows. */
export function MyTablesHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['list', 'status'].map((key) => t(`players.myTables.${key}`))} />
}

/** Your calendar, and the three numbers on it. */
export function MySessionsHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList
        items={['where', 'local', 'paused', 'cancelled', 'threeNumbers', 'denominator', 'notKarma'].map((key) =>
          t(`players.mySessions.${key}`),
        )}
      />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`players.mySessions.steps.step${n}`))} />
    </>
  )
}

/** What a request is and how it is answered, from the player's side. */
export function PlayerTasksHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList
        items={['what', 'where', 'beforeApplying', 'howToAnswer', 'accumulate', 'noJudgement', 'mandatory', 'closed', 'due'].map((key) =>
          t(`players.tasks.${key}`),
        )}
      />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`players.tasks.steps.step${n}`))} />
    </>
  )
}

/** Which files you can see and download. */
export function PlayerFilesHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['where', 'download', 'private', 'shared'].map((key) => t(`players.files.${key}`))} />
}

/**
 * Who can see a profile, and why one stops being visible (#41, #44, #47).
 *
 * **The expiry is the part that needs explaining and the only part nobody would guess.** A profile
 * that was readable last month and is not today looks like a bug unless somebody says the window
 * closed — and the screen that refuses it cannot say much, because confirming the person exists is
 * exactly what the refusal withholds (#249).
 */
export function ProfileHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={['yours', 'attendance', 'unregistered'].map((key) => t(`players.profile.${key}`))} />
      <HelpList items={['whoSeesYours', 'whoYouSee', 'expiry', 'paused'].map((key) => t(`players.profile.${key}`))} />
      <p className="text-fg-muted text-sm">{t('players.profile.karma')}</p>
    </>
  )
}

/** Where a table goes once it is over, and why it left the other list (#133a). */
export function PlayerHistoryHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['what', 'moves', 'paused', 'attendance'].map((key) => t(`players.history.${key}`))} />
}
