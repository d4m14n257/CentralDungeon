import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { tableDetailPath } from '@/config/paths'
import { RegistrationStatusBadge, useMyApplications } from '@/features/registrations'

export function MyApplicationsPage() {
  const { t, i18n } = useTranslation('registrations')
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
                  {t('myApplications.appliedOn', {
                    date: new Date(registration.createdAt).toLocaleDateString(i18n.language),
                  })}
                </p>
                {/* La justificación de un rechazo es obligatoria en el modelo (#34) - se muestra siempre que exista. */}
                {registration.rejectionJustification && (
                  <p className="text-state-canceled-fg text-xs">{registration.rejectionJustification}</p>
                )}
              </div>
              <div className="flex items-center gap-3">
                <RegistrationStatusBadge status={registration.status} />
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
