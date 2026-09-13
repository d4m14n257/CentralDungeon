import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Link, useOutletContext } from 'react-router'

import { useConfirm } from '@/hooks/useConfirm'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { playerUserProfilePath } from '@/config/paths'
import { useDisclosure } from '@/hooks/useDisclosure'
import { FileList } from '@/features/files'
import { useAcceptRegistration, useCandidates, RejectRegistrationDialog } from '@/features/registrations'
import type { Registration } from '@/features/registrations'

interface OutletContext {
  tableId: string
  maxPlayers: number | null
  playerCount: number
}

function CandidatesList({ tableId, maxPlayers, playerCount }: OutletContext) {
  const { t } = useTranslation('registrations')
  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, refetch } = useCandidates(tableId)
  const acceptRegistration = useAcceptRegistration(tableId)
  const confirm = useConfirm()
  const rejectDialog = useDisclosure<Registration>()

  if (isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (isLoadingError) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  if (data.content.length === 0) {
    return <EmptyState title={t('candidates.emptyTitle')} description={t('candidates.emptyDescription')} />
  }

  async function handleAccept(candidate: Registration) {
    const fillsTable = maxPlayers !== null && playerCount + 1 >= maxPlayers
    const confirmed = await confirm({
      title: t('candidates.acceptConfirmTitle', { name: candidate.userName }),
      description: t(fillsTable ? 'candidates.acceptConfirmFillsTable' : 'candidates.acceptConfirmDefault'),
    })
    if (!confirmed) return
    acceptRegistration.mutate(candidate.id, {
      onSuccess: () => toast.success(t('candidates.acceptSuccess')),
    })
  }

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-sm font-medium">{t('candidates.title')}</h2>
        <p className="text-muted-foreground text-xs">{t('candidates.order')}</p>
      </div>
      <ol className="divide-border divide-y rounded-lg border">
        {data.content.map((candidate, index) => (
          <li key={candidate.id} className="space-y-2 px-4 py-3">
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm">
                {index + 1}.{' '}
                {/* #41: whoever applies is opening themselves up to the master's evaluation, so the
                    master can always see the candidate's profile from here on. */}
                <Link to={playerUserProfilePath(candidate.userId)} className="hover:text-fg underline">
                  {candidate.userName}
                </Link>{' '}
                · {candidate.userKarma}
              </span>
              <div className="flex gap-2">
                <Button size="sm" onClick={() => void handleAccept(candidate)}>
                  {t('candidates.accept')}
                </Button>
                <Button size="sm" variant="outline" onClick={() => rejectDialog.open(candidate)}>
                  {t('candidates.reject')}
                </Button>
              </div>
            </div>
            {/* Only when there is something to show: an application with nothing attached carries no
                empty section, the same convention the table's own read-only file list uses. */}
            {candidate.attachedFiles.length > 0 && (
              <div className="pl-5">
                <p className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('candidates.filesTitle')}</p>
                <FileList files={candidate.attachedFiles} />
              </div>
            )}
          </li>
        ))}
      </ol>
      {rejectDialog.item && (
        <RejectRegistrationDialog
          tableId={tableId}
          registrationId={rejectDialog.item.id}
          candidateName={rejectDialog.item.userName}
          open={rejectDialog.isOpen}
          onOpenChange={rejectDialog.close}
        />
      )}
    </div>
  )
}

/**
 * The candidates tab: who applied, in the order they did, and the accept/reject actions.
 *
 * FIFO is the backend's order and this screen never re-sorts it - it is what decides who gets
 * auto-rejected when the last seat goes (#28, #34).
 */
export function MasterTableCandidatesTab() {
  const context = useOutletContext<OutletContext>()
  return <CandidatesList {...context} />
}

export { MasterTableCandidatesTab as Component }
