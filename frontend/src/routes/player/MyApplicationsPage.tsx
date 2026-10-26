import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/hooks/useConfirm'
import { EmptyState } from '@/components/EmptyState'
import { PageOutOfRangeState } from '@/components/PageOutOfRangeState'
import { PaginationControls } from '@/components/PaginationControls'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { tableDetailPath } from '@/config/paths'
import { browserTimeZone, formatDate } from '@/lib/date'
import { RegistrationStatusBadge, useMyApplications, useWithdrawApplication } from '@/features/registrations'
import type { Registration } from '@/features/registrations'

/**
 * /player/applications - everything they applied to and how it went, rejections and their reasons
 * included.
 *
 * It is also where a pending application is taken back (ADR-08 §2). That action exists because R4's
 * clash notice needs somewhere to lead: being told that two of your tables now fall at the same
 * hour is only useful if there is a way out, and this is it.
 */
export function MyApplicationsPage() {
  const { t, i18n } = useTranslation('registrations')
  // The page lives in the URL like every work list (ADR-21 §11): reloading or sharing keeps it.
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Number(searchParams.get('page') ?? '0')
  const withdraw = useWithdrawApplication()
  // Withdrawing has no way back: the row is marked and applying again is a new application, so it
  // goes through ConfirmDialog explaining the consequence (principle 3 of diseno §1).
  const confirm = useConfirm()

  async function onWithdraw(registration: Registration) {
    const confirmed = await confirm({
      title: t('myApplications.withdrawTitle'),
      description: t('myApplications.withdrawDescription', { name: registration.gameTableName }),
      confirmLabel: t('myApplications.withdrawConfirm'),
    })
    if (!confirmed) {
      return
    }
    withdraw.mutate(registration.id, {
      onSuccess: () => toast.success(t('myApplications.withdrawSuccess')),
    })
  }
  // isLoadingError, not isError: a background refetch that fails must not hide a list that already
  // loaded (ADR-19 §4).
  const { data, isPending, isLoadingError, refetch } = useMyApplications(page)

  return (
    <div className="space-y-4">
      <PageHeader title={t('myApplications.title')} help="players.application-status" />
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-16 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && data.totalElements > 0 && (
        <PageOutOfRangeState
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onPageChange={(next) => setSearchParams({ page: String(next) })}
        />
      )}
      {data && data.totalElements === 0 && (
        <EmptyState title={t('myApplications.emptyTitle')} description={t('myApplications.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <ul className="list-divided">
          {data.content.map((registration) => (
            <li key={registration.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
              <div className="min-w-0 flex-1 basis-56 space-y-1 wrap-anywhere">
                <p className="text-sm font-medium">{registration.gameTableName}</p>
                <p className="text-fg-subtle text-xs">
                  {/* The date goes through lib/date.ts: locale and zone are parameters, never constants (ADR-19 §7). */}
                  {t('myApplications.appliedOn', {
                    date: formatDate(registration.createdAt, i18n.language, browserTimeZone()),
                  })}
                </p>
                {/* A rejection always has a reason (ADR-09 §3). A master's own words are shown exactly as
                    typed; the one the application writes itself is a code, so it can be read in the
                    reader's language (ADR-23 §3). */}
                {registration.rejectionJustification && (
                  <p className="text-state-canceled-fg text-xs">{registration.rejectionJustification}</p>
                )}
                {registration.rejectionReasonCode && (
                  <p className="text-state-canceled-fg text-xs">
                    {t(`rejectionReason.${registration.rejectionReasonCode}`, { defaultValue: registration.rejectionReasonCode })}
                  </p>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <RegistrationStatusBadge status={registration.status} />
                {/* Only while it is pending: once accepted there is a table counting on you, and
                    leaving is a conversation with the master, not a button (ADR-08 §2). */}
                {registration.status === 'Candidate' && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-xs"
                    disabled={withdraw.isPending}
                    onClick={() => void onWithdraw(registration)}
                  >
                    {t('myApplications.withdraw')}
                  </Button>
                )}
                <Link to={tableDetailPath(registration.gameTableId)} className="text-brand-fg text-xs whitespace-nowrap hover:underline">
                  {t('myApplications.viewTable')}
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
      {data && (
        <PaginationControls
          page={data.page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onPageChange={(next) => setSearchParams({ page: String(next) })}
        />
      )}
    </div>
  )
}

export { MyApplicationsPage as Component }
