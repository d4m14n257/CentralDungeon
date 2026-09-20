import { Crown, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { CollapsibleSection } from '@/components/CollapsibleSection'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { IconAction } from '@/components/IconAction'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { playerUserProfilePath } from '@/config/paths'
import { BanRequestsSection } from '@/features/approvals'
import { HelpLink } from '@/features/help'
import { useAddMaster, useRemoveMaster } from '@/features/tables'
import type { MasterSummary } from '@/features/tables'
import { BlockPlayerDialog, RegistrationStatusBadge, useTablePlayers, type TablePlayer, type VetoAction } from '@/features/registrations'
import { UserPicker } from '@/features/users'
import type { UserSummary } from '@/features/users'
import { browserTimeZone, formatDate } from '@/lib/date'
import { cn } from '@/lib/utils'

interface OutletContext {
  tableId: string
  isPrimary: boolean
  masters: MasterSummary[]
}

/**
 * Who runs the table. Adding, handing the table over and removing all live here.
 *
 * **On screen these are "master" and "co-master", never `Primary`/`Secondary`** (#166) — the wire
 * words say nothing to somebody who did not read the schema.
 *
 * Only the current master gets the controls, because the backend only accepts them from that person
 * (#73). Everybody else reads the list, which is information they legitimately have.
 */
function MastersSection({ tableId, isPrimary, masters }: OutletContext) {
  const { t } = useTranslation('master')
  const confirm = useConfirm()
  const addMaster = useAddMaster(tableId)
  const removeMaster = useRemoveMaster(tableId)

  function handleAdd(user: UserSummary) {
    addMaster.mutate(
      { userId: user.id, masterType: 'Secondary' },
      { onSuccess: () => toast.success(t('masters.addSuccess', { name: user.discordUsername })) },
    )
  }

  async function handlePromote(master: MasterSummary) {
    // Handing the table over demotes whoever holds it — including the person doing it. Saying so
    // before it happens is the point of the confirmation, not the click itself (principio 3).
    const confirmed = await confirm({
      title: t('masters.promoteConfirmTitle', { name: master.name }),
      description: t('masters.promoteConfirmDescription'),
    })
    if (!confirmed) return
    addMaster.mutate(
      { userId: master.userId, masterType: 'Primary' },
      { onSuccess: () => toast.success(t('masters.promoteSuccess', { name: master.name })) },
    )
  }

  async function handleRemove(master: MasterSummary) {
    const confirmed = await confirm({
      title: t('masters.removeConfirmTitle', { name: master.name }),
      description: t('masters.removeConfirmDescription'),
    })
    if (!confirmed) return
    removeMaster.mutate(master.userId, { onSuccess: () => toast.success(t('masters.removeSuccess', { name: master.name })) })
  }

  return (
    <CollapsibleSection title={t('masters.title')} summary={t('masters.summary', { count: masters.length })} defaultOpen>
      <div className="space-y-4">
        <ul className="divide-border divide-y">
          {masters.map((master) => (
            <li key={master.userId} className="flex items-center gap-3 py-2">
              <Crown
                aria-hidden="true"
                className={cn('size-4 shrink-0', master.masterType === 'Primary' ? 'text-state-active-fg' : 'text-fg-subtle')}
              />
              {/* #41: a master's profile is visible to anyone looking at their table — the co-master
                  reading this list included. */}
              <Link to={playerUserProfilePath(master.userId)} className="min-w-0 flex-1 truncate text-sm hover:underline">
                {master.name}
              </Link>
              <span className="text-fg-muted shrink-0 text-xs">
                {t(master.masterType === 'Primary' ? 'masters.roleMaster' : 'masters.roleCoMaster')}
              </span>
              {isPrimary && master.masterType === 'Secondary' && (
                <>
                  <IconAction
                    icon={<Crown className="size-4" />}
                    label={t('masters.promote', { name: master.name })}
                    onClick={() => void handlePromote(master)}
                    disabled={addMaster.isPending}
                  />
                  <IconAction
                    icon={<X className="size-4" />}
                    label={t('masters.remove', { name: master.name })}
                    onClick={() => void handleRemove(master)}
                    disabled={removeMaster.isPending}
                  />
                </>
              )}
            </li>
          ))}
        </ul>

        {isPrimary && (
          <div className="space-y-2">
            <p className="text-sm font-medium">{t('masters.addLabel')}</p>
            <UserPicker onSelect={handleAdd} excludedIds={masters.map((master) => master.userId)} tableId={tableId} />
            <p className="text-fg-subtle text-xs">
              {t('masters.hint')} {/* To the exact #ref and not the whole page: that is what makes it worth opening (#168). */}
              <HelpLink section="masters.co-masters">{t('masters.helpLink')}</HelpLink>
            </p>
          </div>
        )}
      </div>
    </CollapsibleSection>
  )
}

/**
 * One row of the roster, with whatever the reader may do to it (#29, #39, F3.4).
 *
 * **A vetoed row stays here, and that is the decision rather than an oversight.** #39 requires the
 * veto to be reversible; an interface that removed the row would leave the only act that reverses it
 * with nothing to hang off, and "reversible" would be true of the database and false of the
 * platform. So the row stays, marked, saying whose decision it was and when — which is also the
 * only place a master can see a colleague's veto at all.
 *
 * **The button says what it will do before it is pressed** (`fase-3-admin-owner.md:169`). A
 * co-master gets the same action in the same place, labelled as the request it actually is; learning
 * that in the dialog afterwards is learning it after deciding.
 */
function PlayerRow({
  player,
  isPrimary,
  onVeto,
}: {
  player: TablePlayer
  isPrimary: boolean
  onVeto: (player: TablePlayer, action: VetoAction) => void
}) {
  const { t, i18n } = useTranslation('master')
  const isBlocked = player.status === 'Blocked'

  return (
    <li className="space-y-1 py-2 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2">
          {/* #47: a master already sees the roster to run the table, and #41's asymmetry does
              not apply between a master and a player who is sitting at their own table. */}
          <Link to={playerUserProfilePath(player.userId)} className="min-w-0 truncate hover:underline">
            {player.userName}
          </Link>
          {isBlocked && <RegistrationStatusBadge status={player.status} />}
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <span className="text-fg-muted text-xs">{t('players.karma', { karma: player.userKarma })}</span>
          {isBlocked ? (
            // Lifting is the Primary's alone, like applying it. A co-master asking for a veto to be
            // lifted is not a mechanism that exists, and offering the button would promise one.
            isPrimary && (
              <Button size="sm" variant="outline" onClick={() => onVeto(player, 'unblock')}>
                {t('veto.unblock')}
              </Button>
            )
          ) : (
            // Offered to both, worded for each: the Primary vetoes, the co-master asks (#39, #71).
            <Button size="sm" variant={isPrimary ? 'destructive' : 'outline'} onClick={() => onVeto(player, 'block')}>
              {t(isPrimary ? 'veto.block' : 'veto.request')}
            </Button>
          )}
        </div>
      </div>
      {isBlocked && player.blockedByName !== null && (
        <p className="text-fg-muted text-xs">
          {t('veto.blockedBy', {
            name: player.blockedByName,
            date: player.blockedAt === null ? '' : formatDate(player.blockedAt, i18n.language, browserTimeZone()),
          })}
        </p>
      )}
      {/* The reason, on the row and not behind a click. It is the whole point of making it
          obligatory (#39): whoever is weighing whether to lift the veto — often a different master
          from the one who applied it — decides on this sentence. */}
      {isBlocked && player.blockJustification !== null && (
        // The quotation marks come from the translation, not from the JSX: Spanish opens with « and
        // English with “, and a hard-coded " would be wrong in both (#117).
        <p className="text-fg text-xs italic">{t('veto.quotedReason', { reason: player.blockJustification })}</p>
      )}
    </li>
  )
}

/**
 * The table's roster: who is playing there right now, and who was thrown out of it.
 *
 * **It stopped being a read-only list in F3.4.** The veto lives here because this is where the
 * people are: it is per table (#29), so it belongs to the table's own screen and not to a moderation
 * panel — the same account may be playing happily at four others, and nothing about those changes.
 *
 * @param props.tableId   the table
 * @param props.isPrimary whether the reader runs it or co-runs it — what the veto button does (#71)
 */
function PlayersSection({ tableId, isPrimary }: { tableId: string; isPrimary: boolean }) {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: a failed background refetch must not blank a list that loaded (#150).
  const { data, isPending, isLoadingError, refetch } = useTablePlayers(tableId)
  const vetoDialog = useDisclosure<{ player: TablePlayer; action: VetoAction }>()

  /**
   * How many people are actually playing — which is no longer `data.length`.
   *
   * **The list widened when the veto arrived**: a vetoed row stays on it so that the act which
   * reverses the veto has somewhere to live, so counting the rows now answers a different question
   * from the one the heading asks. A mesa with three players and one vetoed would read «4
   * jugadores», and a count that disagrees with what the master can see is worse than no count.
   */
  const playerCount = data === undefined ? undefined : data.filter((player) => player.status === 'Player').length

  return (
    <CollapsibleSection
      title={t('players.title')}
      summary={playerCount === undefined ? undefined : t('players.summary', { count: playerCount })}
      defaultOpen
    >
      <div className="space-y-4">
        {/* What is waiting on the Primary, above the list it is about. Renders nothing when empty. */}
        <BanRequestsSection
          tableId={tableId}
          isPrimary={isPrimary}
          help={
            <p className="text-fg-subtle text-xs">
              <HelpLink section="masters.banning">{t('veto.helpLink')}</HelpLink>
            </p>
          }
        />

        {isPending && <Skeleton className="h-20 w-full" />}
        {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
        {data && data.length === 0 && <EmptyState title={t('players.emptyTitle')} description={t('players.emptyDescription')} />}
        {data && data.length > 0 && (
          <>
            {/* Said before anything is pressed, not inside the dialog that follows the press
                (fase-3-admin-owner.md:169). The dialog repeats it; this is where it is learned. */}
            <p className="text-fg-subtle text-xs">
              {t(isPrimary ? 'veto.hintPrimary' : 'veto.hintSecondary')} <HelpLink section="masters.banning">{t('veto.helpLink')}</HelpLink>
            </p>
            <ul className="divide-border divide-y">
              {data.map((player) => (
                <PlayerRow
                  key={player.registrationId}
                  player={player}
                  isPrimary={isPrimary}
                  onVeto={(target, action) => vetoDialog.open({ player: target, action })}
                />
              ))}
            </ul>
          </>
        )}

        {vetoDialog.item && (
          <BlockPlayerDialog
            tableId={tableId}
            registrationId={vetoDialog.item.player.registrationId}
            playerName={vetoDialog.item.player.userName}
            action={vetoDialog.item.action}
            isPrimary={isPrimary}
            open={vetoDialog.isOpen}
            onOpenChange={(open) => !open && vetoDialog.close()}
            help={
              <p className="text-fg-subtle text-xs">
                <HelpLink section="masters.banning">{t('veto.helpLink')}</HelpLink>
              </p>
            }
          />
        )}
      </div>
    </CollapsibleSection>
  )
}

/**
 * The people tab of `/master/tables/:id` — who runs the table and who plays at it.
 *
 * The two blocks share a tab because the question they answer is one: who is at this table. Running
 * it and playing at it are mutually exclusive (#154), so no name can appear in both.
 *
 * It lives in `routes/` and not in a feature because it composes three domains — masters and the
 * roster from `tables` and `registrations`, the people search from `users` — and a feature never
 * imports from another (regla dura 16).
 */
export function MasterTablePlayersTab() {
  const context = useOutletContext<OutletContext>()
  return (
    <div className="space-y-4">
      <MastersSection {...context} />
      <PlayersSection tableId={context.tableId} isPrimary={context.isPrimary} />
    </div>
  )
}

export { MasterTablePlayersTab as Component }
