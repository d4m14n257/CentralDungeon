import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { tableDetailPath } from '@/config/paths'
import { browserTimeZone, formatDate } from '@/lib/date'
import { RegistrationStatusBadge, useMyApplications, useWithdrawApplication } from '@/features/registrations'
import type { Registration } from '@/features/registrations'

/**
 * /my/applications - everything they applied to and how it went, rejections and their reasons
 * included.
 *
 * It is also where a pending application is taken back (#178). That action exists because R4's
 * clash notice needs somewhere to lead: being told that two of your tables now fall at the same
 * hour is only useful if there is a way out, and this is it.
 */
export function MyApplicationsPage() {
  const { t, i18n } = useTranslation('registrations')
  const withdraw = useWithdrawApplication()
  // Retirarse no tiene vuelta atrás: la fila queda marcada y hay que postularse de nuevo, así que
  // pasa por ConfirmDialog explicando la consecuencia (principio 3 de frontend-diseno.md 1).
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
  // isLoadingError, no isError: un refetch de fondo que falla no debe tapar una lista ya
  // cargada (docs/decisiones.md #150).
  const { data, isPending, isLoadingError, refetch } = useMyApplications()

  return (
    <div className="space-y-4">
      <h1 className="font-serif text-2xl font-semibold">{t('myApplications.title')}</h1>
      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-16 rounded-lg" />
          ))}
        </div>
      )}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState title={t('myApplications.emptyTitle')} description={t('myApplications.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <ul className="divide-border divide-y rounded-lg border">
          {data.content.map((registration) => (
            <li key={registration.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="space-y-1">
                <p className="text-sm font-medium">{registration.gameTableName}</p>
                <p className="text-fg-subtle text-xs">
                  {/* La fecha pasa por lib/date.ts: locale y zona son parámetros, nunca constantes (#111). */}
                  {t('myApplications.appliedOn', {
                    date: formatDate(registration.createdAt, i18n.language, browserTimeZone()),
                  })}
                </p>
                {/* A rejection always has a reason (#34). A master's own words are shown exactly as
                    typed; the one the application writes itself is a code, so it can be read in the
                    reader's language (#197). */}
                {registration.rejectionJustification && (
                  <p className="text-state-canceled-fg text-xs">{registration.rejectionJustification}</p>
                )}
                {registration.rejectionReasonCode && (
                  <p className="text-state-canceled-fg text-xs">
                    {t(`rejectionReason.${registration.rejectionReasonCode}`, { defaultValue: registration.rejectionReasonCode })}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-3">
                <RegistrationStatusBadge status={registration.status} />
                {/* Solo mientras esté pendiente: una vez aceptado hay una mesa contando con vos, y
                    salirse es una conversación con el master, no un botón (#178). */}
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
    </div>
  )
}

export { MyApplicationsPage as Component }
