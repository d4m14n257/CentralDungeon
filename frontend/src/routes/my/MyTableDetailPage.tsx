import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath, tableDetailPath } from '@/config/paths'
import { AttendanceSummaryView, SessionList, TableStatusBadge, useGameTable, useMySessions, type MySessions } from '@/features/tables'
import { browserTimeZone, formatSlot, utcSlotToLocal } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * Mi calendario y mi asistencia en esta mesa. Bloque propio con su query, no algo que la página le
 * pase hacia abajo: cada bloque de una pantalla compuesta recibe un id y busca lo suyo (§3.1.5).
 */
function MySessionsSection({ tableId }: { tableId: string }) {
  const { t } = useTranslation('tables')
  // isLoadingError, no isError: ver docs/decisiones.md #150.
  const { data, isPending, error, isLoadingError, refetch } = useMySessions(tableId)

  if (isPending) {
    return <Skeleton className="h-40 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !data) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  const mine: MySessions = data

  return (
    <div className="space-y-6">
      <section className="space-y-2">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('sessions.myAttendanceTitle')}</h2>
        <AttendanceSummaryView summary={mine.summary} />
        {/* Los tres números se explican en la ayuda, con su #ref estable (#137, #167, #168). */}
        <Link to={helpPath('players', 'my-sessions')} className="text-fg-muted inline-block text-xs underline">
          {t('sessions.myAttendanceHelp')}
        </Link>
      </section>

      <section className="space-y-2">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('sessions.myCalendarTitle')}</h2>
        {mine.sessions.length === 0 ? (
          <EmptyState title={t('sessions.myCalendarEmptyTitle')} description={t('sessions.myCalendarEmptyDescription')} />
        ) : (
          <SessionList sessions={mine.sessions} />
        )}
      </section>
    </div>
  )
}

/**
 * `/my/tables/:id` — mi mesa: su agenda, sus sesiones y **mi** asistencia, de solo lectura.
 *
 * Es el mínimo del jugador que F1 necesita para poder probarse de punta a punta; el resto de la
 * pantalla —peticiones, archivos, compañeros— llega con F2 y F1.5.
 *
 * Todo lo que se ve está en la hora de quien lee; lo que guarda el servidor es UTC (#22).
 */
export function MyTableDetailPage() {
  const { t, i18n } = useTranslation('tables')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  const { data: table, isPending, isLoadingError } = useGameTable(tableId)

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }

  if (isLoadingError || !table) {
    return <ErrorState message={t('detail.notFoundDescription')} />
  }

  const timeZone = browserTimeZone()
  const localSchedule = table.schedule.map((slot) => utcSlotToLocal(slot, timeZone))

  return (
    <div className="border-border-strong bg-surface space-y-6 rounded-xl border p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
          <Link to={tableDetailPath(table.id)} className="text-fg-muted text-sm underline">
            {t('sessions.seePublicDetail')}
          </Link>
        </div>
        <TableStatusBadge status={table.status} />
      </div>

      {localSchedule.length > 0 && (
        <section className="border-border border-t pt-4">
          <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.schedule')}</h2>
          <ul className="mt-1.5 space-y-0.5 text-sm">
            {localSchedule.map((slot) => (
              <li key={`${slot.weekday}-${slot.hourtime}`}>{formatSlot(slot, i18n.language, table.duration)}</li>
            ))}
          </ul>
          <p className="text-fg-subtle mt-1 text-xs">{t('detail.scheduleTimeZone', { timeZone })}</p>
        </section>
      )}

      <div className="border-border border-t pt-4">
        <MySessionsSection tableId={tableId} />
      </div>
    </div>
  )
}

export { MyTableDetailPage as Component }
