import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useOutletContext } from 'react-router'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useAcceptRegistration, useCandidates, RejectRegistrationDialog } from '@/features/registrations'
import type { Registration } from '@/features/registrations'

interface OutletContext {
  tableId: string
  maxPlayers: number | null
  playerCount: number
}

function CandidatesList({ tableId, maxPlayers, playerCount }: OutletContext) {
  const { t } = useTranslation('registrations')
  const { data, isPending, isError, refetch } = useCandidates(tableId)
  const acceptRegistration = useAcceptRegistration(tableId)
  const confirm = useConfirm()
  const rejectDialog = useDisclosure<Registration>()

  if (isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (isError) {
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
          <li key={candidate.id} className="flex items-center justify-between gap-4 px-4 py-3">
            <span className="text-sm">
              {index + 1}. {candidate.userName} · {candidate.userKarma}
            </span>
            <div className="flex gap-2">
              <Button size="sm" onClick={() => void handleAccept(candidate)}>
                {t('candidates.accept')}
              </Button>
              <Button size="sm" variant="outline" onClick={() => rejectDialog.open(candidate)}>
                {t('candidates.reject')}
              </Button>
            </div>
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

export function MasterTableCandidatesTab() {
  const context = useOutletContext<OutletContext>()
  return <CandidatesList {...context} />
}

export { MasterTableCandidatesTab as Component }
