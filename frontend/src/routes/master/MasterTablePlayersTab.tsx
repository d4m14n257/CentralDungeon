import { Crown, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router'
import { toast } from 'sonner'

import { CollapsibleSection } from '@/components/CollapsibleSection'
import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { IconAction } from '@/components/IconAction'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath } from '@/config/paths'
import { useAddMaster, useRemoveMaster } from '@/features/tables'
import type { MasterSummary } from '@/features/tables'
import { useTablePlayers } from '@/features/registrations'
import { UserPicker } from '@/features/users'
import type { UserSummary } from '@/features/users'
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
              <span className="min-w-0 flex-1 truncate text-sm">{master.name}</span>
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
              <Link to={helpPath('masters', 'co-masters')} className="underline underline-offset-2">
                {t('masters.helpLink')}
              </Link>
            </p>
          </div>
        )}
      </div>
    </CollapsibleSection>
  )
}

/** The table's roster: who is actually playing there right now. */
function PlayersSection({ tableId }: { tableId: string }) {
  const { t } = useTranslation('master')
  // isLoadingError, not isError: a failed background refetch must not blank a list that loaded (#150).
  const { data, isPending, isLoadingError, refetch } = useTablePlayers(tableId)

  return (
    <CollapsibleSection title={t('players.title')} summary={data ? t('players.summary', { count: data.length }) : undefined} defaultOpen>
      {isPending && <Skeleton className="h-20 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.length === 0 && <EmptyState title={t('players.emptyTitle')} description={t('players.emptyDescription')} />}
      {data && data.length > 0 && (
        <ul className="divide-border divide-y">
          {data.map((player) => (
            <li key={player.userId} className="flex items-center justify-between gap-4 py-2 text-sm">
              <span className="min-w-0 truncate">{player.userName}</span>
              <span className="text-fg-muted shrink-0 text-xs">{t('players.karma', { karma: player.userKarma })}</span>
            </li>
          ))}
        </ul>
      )}
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
      <PlayersSection tableId={context.tableId} />
    </div>
  )
}

export { MasterTablePlayersTab as Component }
