import { useTranslation } from 'react-i18next'

import { CLAIM_TIMEOUT_MINUTES } from '@/config/adminQueue'

import { HelpList, HelpSteps } from '../components/HelpBlocks'

/**
 * The help of somebody moderating. Bodies only - the heading lives in the registry (#231).
 *
 * Owner reads this rather than an audience of its own: it is an admin with more privileges (#169).
 */

/**
 * A section that is a list of facts plus its numbered steps.
 *
 * @param props.block     the key under `admins` its text lives at
 * @param props.keys      the facts to list, in order
 * @param props.stepCount how many numbered steps follow, or zero for none
 * @param props.values    what the text interpolates, when it states a number the platform owns. The
 *                        reservation's timeout is the one case: the help promises "se libera a los 15
 *                        minutos", and a sentence that hard-codes a number the platform can change is
 *                        the one nobody remembers to update when it does
 */
function ListAndSteps({
  block,
  keys,
  stepCount,
  values,
}: {
  block: string
  keys: string[]
  stepCount: number
  values?: Record<string, unknown>
}) {
  const { t } = useTranslation('help')

  return (
    <>
      <HelpList items={keys.map((key) => t(`admins.${block}.${key}`, values ?? {}))} />
      {stepCount > 0 && (
        <HelpSteps
          title={t('stepsTitle')}
          items={Array.from({ length: stepCount }, (_, index) => t(`admins.${block}.steps.step${index + 1}`, values ?? {}))}
        />
      )}
    </>
  )
}

/**
 * Reviewing a table, and where that is done (#176, F3.3).
 *
 * **Its text moved with the actions.** Approving and requesting changes left `/admin/tables` for the
 * shared tray, and an explanation that stayed behind would be a set of instructions pointing at
 * buttons that are not there any more — which is worse than no explanation. `listing` is the new
 * line: it says what `/admin/tables` is now, because somebody who learned the old screen will look
 * for the buttons there first.
 */
export function ReviewingHelp() {
  return <ListAndSteps block="reviewing" keys={['queue', 'approve', 'gone', 'listing']} stepCount={4} />
}

/**
 * The reservation: what taking an item means, and what happens if you walk away (#100).
 *
 * **It exists because a reservation nobody understands is a button nobody presses.** Taking an item
 * is optional — anything in your tray can be resolved on the spot — so unless the help says what it
 * is *for*, "Tomar" reads as a pointless extra step and the whole mechanism goes unused, which is
 * exactly the two-admins-on-one-request problem #100 was built to prevent.
 *
 * **The section used to teach the opposite and that was worse than having none.** It said resolving
 * required holding the item first, which was the rule as it was originally written and which made
 * `/admin/requests` — a screen with no way to reserve anything — refuse every resolution. The rule
 * was corrected to what it always meant: you cannot work on what somebody else took. An explanation
 * that survives its own rule teaches people a ritual they will keep performing and blame themselves
 * for when it stops matching the screen.
 *
 * `timeout` is the other half nobody would guess: a reservation with no visible end reads as a lock,
 * and somebody who closed a tab by accident would assume they had broken something permanent.
 */
export function ClaimingHelp() {
  return (
    <ListAndSteps
      block="claiming"
      keys={['what', 'notRequired', 'whenToClaim', 'yours', 'timeout', 'release', 'race']}
      stepCount={4}
      values={{ minutes: CLAIM_TIMEOUT_MINUTES }}
    />
  )
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

/**
 * Pausing a table and bringing it back (#32, #33, #163, #193).
 *
 * **It exists because the two buttons had no screen for two phases** (#163) and arrive with nothing
 * around them: an admin meeting "Pausar" on a listing has no way to know whether it cancels the
 * sessions, hides the table, or notifies anybody.
 *
 * `clash` is the half that turns a refusal into something actionable. Resuming re-checks the
 * master's agenda, because they may have taken another table while this one was frozen — so the one
 * act on this screen that can genuinely fail is the one that looks least likely to, and an admin who
 * does not expect it will read the refusal as a broken button.
 */
export function PausingHelp() {
  return <ListAndSteps block="pausing" keys={['what', 'requested', 'freeze', 'reason', 'resume', 'clash', 'notCancel']} stepCount={5} />
}
