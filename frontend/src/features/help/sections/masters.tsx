import { useTranslation } from 'react-i18next'

import { HelpList, HelpSteps } from '../components/HelpBlocks'

/** The help of somebody running a table. Bodies only - the heading lives in the registry (#231). */

/** A section that is a list of facts plus its numbered steps, which most of these are. */
function ListAndSteps({ block, keys, stepCount }: { block: string; keys: string[]; stepCount: number }) {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={keys.map((key) => t(`masters.${block}.${key}`))} />
      {stepCount > 0 && (
        <HelpSteps
          title={t('stepsTitle')}
          items={Array.from({ length: stepCount }, (_, index) => t(`masters.${block}.steps.step${index + 1}`))}
        />
      )}
    </>
  )
}

/** What creating a table takes and what happens after sending it. */
export function CreatingHelp() {
  return <ListAndSteps block="creating" keys={['role', 'wizard', 'submit']} stepCount={5} />
}

/** How the weekly agenda works, in whose zone, and what clashes with what. */
export function ScheduleHelp() {
  return (
    <ListAndSteps block="schedule" keys={['local', 'duration', 'interval', 'chained', 'conflict', 'paused', 'players']} stepCount={4} />
  )
}

/** How the calendar is laid out and the three ways a master moves it. */
export function SessionsHelp() {
  return (
    <ListAndSteps
      block="sessions"
      keys={[
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
      ]}
      stepCount={5}
    />
  )
}

/** What a request is and how it is published, from the master's side. */
export function MasterTasksHelp() {
  return (
    <ListAndSteps
      block="tasks"
      keys={['what', 'audience', 'single', 'notify', 'edit', 'answers', 'accumulate', 'noJudgement', 'mandatory', 'close']}
      stepCount={4}
    />
  )
}

/** How files are attached to a table, what each one is, and who ends up seeing them. */
export function MasterFilesHelp() {
  return (
    <ListAndSteps
      block="files"
      keys={['what', 'category', 'reuse', 'dedup', 'library', 'published', 'shared', 'private', 'detach', 'limits']}
      stepCount={4}
    />
  )
}

/** How to propose a catalog value that does not exist yet. */
export function ProposeCatalogHelp() {
  return <ListAndSteps block="proposeCatalog" keys={['what', 'pending', 'marked', 'existing']} stepCount={4} />
}

/** What an admin's review can come back with. */
export function ReviewHelp() {
  return <ListAndSteps block="review" keys={['approve', 'changes', 'history']} stepCount={0} />
}

/** How candidates are accepted or turned down. */
export function CandidatesHelp() {
  return <ListAndSteps block="candidates" keys={['order', 'accept', 'reject']} stepCount={4} />
}

/** Starting, cancelling and finishing a table. */
export function RunningHelp() {
  return <ListAndSteps block="running" keys={['start', 'cancel', 'who']} stepCount={4} />
}

/** When a table can be deleted rather than cancelled. */
export function DeletingHelp() {
  return <ListAndSteps block="deleting" keys={['draft', 'public', 'gone']} stepCount={3} />
}

/** What a co-master is, and how it differs from the Primary (#67, #71, #89). */
export function CoMastersHelp() {
  return (
    <ListAndSteps
      block="coMasters"
      keys={['one', 'primary', 'assign', 'add', 'cannotPlay', 'promote', 'removeKeeps', 'cannotRemovePrimary']}
      stepCount={4}
    />
  )
}

/** How to read the tray of what is waiting on you (#136). */
export function DashboardHelp() {
  return <ListAndSteps block="dashboard" keys={['what', 'order', 'kinds', 'noReservation', 'empty', 'notMetrics']} stepCount={3} />
}

/** What editing a table replaces and when it has to go back for review. */
export function EditTableHelp() {
  return <ListAndSteps block="editTable" keys={['when', 'who', 'replaces', 'sets', 'conflict', 'resubmit']} stepCount={4} />
}
