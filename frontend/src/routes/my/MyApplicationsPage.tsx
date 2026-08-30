import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { RegistrationStatusBadge, useMyApplications } from '@/features/registrations'

export function MyApplicationsPage() {
  const { t, i18n } = useTranslation('registrations')
  const { data, isPending, isError, refetch } = useMyApplications()

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
      {isError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState title={t('myApplications.emptyTitle')} description={t('myApplications.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <ul className="divide-border divide-y rounded-lg border">
          {data.content.map((registration) => (
            <li key={registration.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <span className="text-sm font-medium">
                {t('myApplications.appliedOn', {
                  date: new Date(registration.createdAt).toLocaleDateString(i18n.language),
                })}
              </span>
              <RegistrationStatusBadge status={registration.status} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export { MyApplicationsPage as Component }
