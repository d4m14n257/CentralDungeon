import { AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { helpPath } from '@/config/paths'
import { RichTextView } from '@/components/RichTextView'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useDisclosure } from '@/hooks/useDisclosure'
import { CatalogChip } from '@/features/catalogs'
import { FileList } from '@/features/files'
import { ApplyToTableDialog, useMyApplications } from '@/features/registrations'
import { SessionList, TableStatusBadge, useGameTable } from '@/features/tables'
import { useMe } from '@/features/users'
import type { GameTableDetail, MasterSummary } from '@/features/tables'
import { browserTimeZone, formatDateTime, formatSlot, utcSlotToLocal } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'

/**
 * Por qué el botón está como está. Cada rama devuelve el motivo, no solo el estado: un botón gris
 * que no dice por qué está gris es peor que no tener botón (principio 2 de frontend-diseno.md 1).
 *
 * El choque de horarios (R2 de #178) va **antes** del cupo: es el bloqueo que el backend va a
 * aplicar de todos modos con un 409, y es el único de la lista que el lector puede resolver por su
 * cuenta - retirando una postulación o dejando una mesa.
 */
function applyState(
  t: (key: string) => string,
  table: GameTableDetail,
  roles: string[],
  hasActiveApplication: boolean,
  hasScheduleConflict: boolean,
) {
  if (!roles.includes('Player')) {
    return { disabled: true, label: t('detail.needsPlayerRole') }
  }
  if (table.status !== 'Opened') {
    return { disabled: true, label: t('detail.notOpen') }
  }
  if (hasActiveApplication) {
    return { disabled: true, label: t('detail.alreadyApplied') }
  }
  if (hasScheduleConflict) {
    return { disabled: true, label: t('detail.scheduleConflict') }
  }
  if (table.maxPlayers != null && table.playerCount >= table.maxPlayers) {
    return { disabled: true, label: t('detail.tableFull') }
  }
  return { disabled: false, label: t('detail.apply') }
}

/** El principal: cualquier co-master queda relegado a la línea de masters (frontend-diseno.md 4). */
function primaryMasterOf(masters: MasterSummary[]) {
  return masters.find((master) => master.masterType === 'Primary') ?? masters[0]
}

/**
 * A table's public detail, /tables/:id - what a prospective player reads before applying.
 *
 * It composes: the page owns only the table query, and each block is a section that fetches its own
 * data from an id (#3.1.5). That is what keeps a feature from ever importing another.
 */
export function TableDetailPage() {
  const { t, i18n } = useTranslation('tables')
  // A second namespace rather than copying the file labels into `tables`: the words belong to the
  // files domain and are the same ones the master's tab shows (regla dura 18).
  const { t: tFiles } = useTranslation('files')
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // isLoadingError, no isError: si ya se cargó la mesa, un refetch de fondo que falla no debe
  // reemplazar la pantalla por un error - dejaría de verse algo que hasta hace un segundo andaba
  // bien (docs/decisiones.md #150).
  const { data: table, isPending, isLoadingError } = useGameTable(tableId)
  const { data: me } = useMe()
  const { data: myApplications } = useMyApplications()
  const applyDialog = useDisclosure()

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

  const hasActiveApplication =
    myApplications?.content.some((registration) => registration.gameTableId === table.id && registration.status !== 'Rejected') ?? false

  // El choque lo calcula el servidor para el actor del token y viaja en el detalle (#121, #178):
  // la agenda de las otras mesas del lector no está de este lado, y adivinarla sería escribir la
  // regla por segunda vez, en el idioma equivocado.
  const hasScheduleConflict = table.scheduleConflict
  const state = applyState(t, table, me?.roles ?? [], hasActiveApplication, hasScheduleConflict)
  const localSchedule = table.schedule.map((slot) => utcSlotToLocal(slot, browserTimeZone()))
  const primaryMaster = primaryMasterOf(table.masters)
  const coMasters = table.masters.filter((master) => master.userId !== primaryMaster?.userId)

  return (
    <div className="border-border-strong bg-surface rounded-xl border p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-serif text-2xl font-semibold">{table.name}</h1>
          {primaryMaster && (
            <p className="text-fg-muted mt-1 flex flex-wrap items-center gap-x-1 text-sm">
              <span>{t('detail.masterLabel')}:</span>
              <span>{primaryMaster.name}</span>
              <span>·</span>
              <span className="text-brand-fg">{primaryMaster.karma.toLocaleString(i18n.language)}</span>
              {coMasters.length > 0 && (
                <>
                  <span>·</span>
                  <span>{t('detail.coMasterLabel')}:</span>
                  <span>{coMasters.map((master) => master.name).join(', ')}</span>
                </>
              )}
            </p>
          )}
        </div>
        <TableStatusBadge status={table.status} />
      </div>

      <div className="border-border mt-4 flex flex-col gap-4 border-t pt-4">
        {(table.systems.length > 0 || table.tags.length > 0 || table.platforms.length > 0) && (
          <div className="flex flex-wrap gap-1.5">
            {[...table.systems, ...table.tags, ...table.platforms].map((value: CatalogValue) => (
              <CatalogChip key={value.id} value={value} />
            ))}
          </div>
        )}

        <RichTextView html={table.description} className="text-fg-muted max-w-prose" />

        {table.permitted && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.permitted')}</h2>
            <RichTextView html={table.permitted} className="mt-1.5" />
          </section>
        )}

        {table.requirements && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.requirements')}</h2>
            <RichTextView html={table.requirements} className="mt-1.5" />
          </section>
        )}

        {localSchedule.length > 0 && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.schedule')}</h2>
            <ul className="mt-1.5 space-y-0.5 text-sm">
              {localSchedule.map((slot) => (
                <li key={`${slot.weekday}-${slot.hourtime}`}>{formatSlot(slot, i18n.language, table.duration)}</li>
              ))}
            </ul>
            {/* La agenda se guarda en UTC (#22); acá se dice en qué zona se está mostrando. */}
            <p className="text-fg-subtle mt-1 text-xs">{t('detail.scheduleTimeZone', { timeZone: browserTimeZone() })}</p>
          </section>
        )}

        {/* The real calendar, not the weekly shape: once a table has been paused and resumed the two
            stop matching, and the dates are what somebody deciding whether to apply needs (#26, #33). */}
        {table.sessions.length > 0 && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('sessions.calendarTitle')}</h2>
            <div className="mt-1.5">
              <SessionList sessions={table.sessions} />
            </div>
          </section>
        )}

        {/* The files the table shares (#79). Only the shared ones ever arrive here — an attachment
            the master kept private is absent from the response, not hidden by the screen. */}
        {table.files.length > 0 && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{tFiles('table.readOnlyTitle')}</h2>
            <div className="mt-1.5">
              <FileList
                files={table.files}
                renderMeta={(file) => <span className="text-fg-muted text-xs">{tFiles(`tableFileType.${file.tableFileType}`)}</span>}
              />
            </div>
          </section>
        )}

        {table.startDate && (
          <section>
            <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.startDate')}</h2>
            <p className="mt-1.5 text-sm">{formatDateTime(table.startDate, i18n.language, browserTimeZone())}</p>
            {table.totalSessions != null && (
              <p className="text-fg-subtle mt-1 text-xs">{t('detail.totalSessions', { count: table.totalSessions })}</p>
            )}
          </section>
        )}

        <div>
          <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.capacity')}</h2>
          <p className="mt-1.5 text-sm">
            {table.maxPlayers != null
              ? t('explorer.players', { current: table.playerCount, max: table.maxPlayers })
              : t('explorer.playersUnlimited', { current: table.playerCount })}
          </p>
        </div>
      </div>

      <div className="border-border mt-4 flex flex-col items-end gap-2 border-t pt-4">
        {/* R2 explicada, no insinuada: el bloqueo dice con qué choca y qué se puede hacer (#178). */}
        {hasScheduleConflict && (
          <p className="text-state-canceled-fg flex items-start gap-1.5 text-xs">
            <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
            <span>
              {t('detail.scheduleConflictExplained')} {/* El aviso lleva a la explicación completa, con su #ref estable (#167, #168). */}
              <Link to={helpPath('players', 'schedule-conflicts')} className="underline">
                {t('detail.scheduleConflictHelp')}
              </Link>
            </span>
          </p>
        )}
        <Button disabled={state.disabled} onClick={() => applyDialog.open()}>
          {state.label}
        </Button>
      </div>

      <ApplyToTableDialog tableId={table.id} tableName={table.name} open={applyDialog.isOpen} onOpenChange={applyDialog.close} />
    </div>
  )
}

export { TableDetailPage as Component }
