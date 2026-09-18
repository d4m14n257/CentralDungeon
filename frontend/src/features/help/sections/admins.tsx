import { useTranslation } from 'react-i18next'

import { HelpList, HelpSteps } from '../components/HelpBlocks'

/**
 * The help of somebody moderating. Bodies only - the heading lives in the registry (#231).
 *
 * Owner reads this rather than an audience of its own: it is an admin with more privileges (#169).
 */

/** A section that is a list of facts plus its numbered steps. */
function ListAndSteps({ block, keys, stepCount }: { block: string; keys: string[]; stepCount: number }) {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={keys.map((key) => t(`admins.${block}.${key}`))} />
      {stepCount > 0 && (
        <HelpSteps
          title={t('stepsTitle')}
          items={Array.from({ length: stepCount }, (_, index) => t(`admins.${block}.steps.step${index + 1}`))}
        />
      )}
    </>
  )
}

/** The review queue and what each outcome does to the table. */
export function ReviewingHelp() {
  return <ListAndSteps block="reviewing" keys={['queue', 'approve', 'gone']} stepCount={4} />
}

/** How an unassigned table gets its masters. */
export function AssignMastersHelp() {
  return <ListAndSteps block="assignMasters" keys={['create', 'search', 'order', 'opens', 'delete']} stepCount={6} />
}

/** Accepting, grouping, merging and retiring catalog values (#57, #58, #59). */
export function CatalogsHelp() {
  return <ListAndSteps block="catalogs" keys={['what', 'pending', 'groups', 'alias', 'merge', 'disable', 'successor']} stepCount={6} />
}

/** Publishing files to the community and what unpublishing does not undo. */
export function AdminFilesHelp() {
  return (
    <ListAndSteps
      block="files"
      keys={['what', 'publish', 'audience', 'notALock', 'unpublish', 'remove', 'purge', 'commands']}
      stepCount={4}
    />
  )
}

/** What Owner can do that Admin cannot (#169). */
export function OwnerHelp() {
  return <ListAndSteps block="owner" keys={['same', 'exclusive', 'grants', 'soon']} stepCount={0} />
}

/**
 * Who may hand out which role, and the two invariants around it (F3.1).
 *
 * **`whoGrants` is the reason this section exists.** An admin opens the role dialog and finds two of
 * the four roles simply absent — that is principio 2 working as intended (a button that cannot be
 * used is not shown), but from the reader's side an absence and a bug look identical until something
 * says which it is. The rest of the list is what they will ask next: why the other chip vanished
 * (#169), and why the platform refused to let them unmake the last owner.
 */
export function RolesHelp() {
  return <ListAndSteps block="roles" keys={['what', 'whoGrants', 'exclusive', 'lastOwner', 'reason', 'history']} stepCount={6} />
}

/**
 * The request tray and what approving each kind actually does (#42, F3.2).
 *
 * **`masterGrant` and `tableOpen` are the reason this section exists.** The two approvals look
 * identical on screen — same button, same note — and do completely different things: one hands out a
 * role through the very same road `/admin/users` uses, and the other grants nothing at all, because
 * a request carries no name, no system, no seats and no agenda to build a table from. An admin who
 * assumes the second creates the table will approve it, tell nobody, and leave whoever asked waiting
 * for a table that is never coming.
 */
export function RequestsAdminHelp() {
  return (
    <ListAndSteps
      block="requests"
      keys={['what', 'tray', 'pendingFirst', 'note', 'masterGrant', 'tableOpen', 'general', 'resolvedOnce', 'gone']}
      stepCount={5}
    />
  )
}

/**
 * What closing an account does, and who is out of reach of it (#84, §3).
 *
 * The two halves of `what` and `keepsData` are deliberately adjacent: "block" is a word people read
 * as "delete", and learning the second half afterwards is learning it too late.
 */
export function BlockingHelp() {
  return (
    <ListAndSteps block="blocking" keys={['what', 'keepsData', 'immediate', 'noPeers', 'noAppeal', 'reason', 'unblock']} stepCount={6} />
  )
}
