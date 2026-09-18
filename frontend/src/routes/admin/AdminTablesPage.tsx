import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import {
  CreateUnassignedTableDialog,
  TableStatusBadge,
  adminTableSearchFields,
  useAdminTables,
  useDeleteTable,
  type AdminTableSummary,
} from '@/features/tables'
import { useConfirm } from '@/hooks/useConfirm'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

import { AssignMastersDialog } from './AssignMastersDialog'

/**
 * The actions a single row offers, with the one mutation they need.
 *
 * A component of its own rather than a closure in the column list, because deleting is per-row state
 * — `useDeleteTable` is keyed by the table's id — and hoisting it to the screen would mean one
 * mutation shared by every row.
 *
 * **Approving and requesting changes are not here any more** (#176, F3.3). They moved to
 * `/admin/queue` with the work they belong to, and they moved rather than being copied: a table
 * sitting in `Preparation` is work waiting on somebody, and work waiting on somebody is the tray's.
 * Two screens offering the same decision under different rules is exactly what the move removes.
 */
function AdminTableRowActions({ table }: { table: AdminTableSummary }) {
  const { t } = useTranslation('admin')
  const confirm = useConfirm()
  const removeTable = useDeleteTable(table.id)
  const assignDialog = useDisclosure()

  // A table with no master was never public: it is deleted, not cancelled (decisiones.md #175).
  async function handleDelete() {
    const confirmed = await confirm({ title: t('tables.deleteConfirmTitle'), description: t('tables.deleteConfirmDescription') })
    if (!confirmed) return
    removeTable.mutate(undefined, { onSuccess: () => toast.success(t('tables.deleteSuccess')) })
  }

  return (
    <div className="flex flex-wrap justify-end gap-2">
      {/* Only on a table with no master: everything else already has one, and the two acts are about
          giving it one or admitting it will never have one (principio 2). */}
      {table.status === 'Unassigned' && (
        <>
          <Button size="sm" onClick={() => assignDialog.open()}>
            {t('tables.assignMasters')}
          </Button>
          <Button size="sm" variant="outline" onClick={() => void handleDelete()} disabled={removeTable.isPending}>
            {t('tables.delete')}
          </Button>
        </>
      )}
      <AssignMastersDialog tableId={table.id} tableName={table.name} open={assignDialog.isOpen} onOpenChange={assignDialog.close} />
    </div>
  )
}

/**
 * `/admin/tables` — **every table the platform has**, with its status (#176, F3.3).
 *
 * It used to be "Mesas por revisar": a listing that defaulted to the review statuses, which made it a
 * second tray with rules of its own. Reviewing moved to `/admin/queue` with the two buttons that
 * performed it, and what is left here is the question nothing else answered — *which tables exist,
 * and what state is each one in*. An admin looking for the table somebody is complaining about had
 * nowhere to look before this.
 *
 * **Showing everything is only useful with a way to narrow it**, which is why the search box arrived
 * in the same slice: six commands (#164, #240), declared once in `adminTableSearchFields` so that the
 * box, the help and the `?q=` cannot disagree. `/table_status` offers its ten values because they are
 * a closed set; the two catalog commands do not, because catalogs grow whenever a master proposes one
 * (#246).
 *
 * **What was searched and which page are in the URL** (#185), like the other admin screens. Until
 * F3.3 the page number lived in `useState`, which meant the second page of a listing could not be
 * linked to and was lost on reload.
 *
 * **The reservation shows here too** (#100): a table somebody is reviewing right now says so, even
 * though this screen offers no action that needs it. Without that an admin sees a table in
 * `Preparation`, goes to the tray to review it, and finds it is not there.
 *
 * A work list, so it pages by number with the total in view instead of "load more" (#173).
 *
 * One of the wide tables of frontend-diseno.md §5.b: below `md` it stops being a table and each row
 * becomes a card, from the same column definitions — never horizontal scroll.
 *
 * **No role guard in front of it** (#103): the backend answers `403` and the screen paints
 * `ForbiddenState`, which is an explanation rather than a blank page.
 */
export function AdminTablesPage() {
  const { t, i18n } = useTranslation('admin')
  const { t: tTables } = useTranslation('tables')
  const [searchParams, setSearchParams] = useSearchParams()
  const createDialog = useDisclosure()
  const timeZone = browserTimeZone()

  const page = Number(searchParams.get('page') ?? '0')

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of #164. Nothing is filtered by default: this screen answers "which tables exist", and a listing
  // that silently hides some of them is the thing #176 took away.
  const fields = useMemo(() => adminTableSearchFields(t, tTables), [t, tTables])
  const search = useSearchQuery({
    fields,
    initialQuery: searchParams.get('q') ?? '',
    onQueryChange: (query) => updateParams({ q: query }),
  })

  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, error, refetch } = useAdminTables(search.debouncedQuery, undefined, page)

  /** Writes the screen's state into the URL, resetting the page whenever the search changes. */
  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  // Whether the reader is looking at the whole platform or at something they narrowed themselves.
  // The two have different empty states because they are different facts: "there are no tables" is
  // about the platform, "nothing matched" is about what was typed.
  const showsEverything = search.debouncedQuery.trim() === ''

  const columns: DataTableColumn<AdminTableSummary>[] = [
    { id: 'name', header: t('tables.columns.name'), role: 'title', cell: (table) => table.name },
    { id: 'status', header: t('tables.columns.status'), role: 'badge', cell: (table) => <TableStatusBadge status={table.status} /> },
    { id: 'master', header: t('tables.columns.master'), cell: (table) => table.primaryMasterName ?? t('tables.noPrimaryMaster') },
    {
      id: 'players',
      header: t('tables.columns.players'),
      cell: (table) => (table.maxPlayers === null ? table.playerCount : `${table.playerCount}/${table.maxPlayers}`),
    },
    {
      id: 'claim',
      header: t('tables.columns.claim'),
      // Nothing at all when nobody has it, rather than a dash: an empty line in a card is quieter
      // than a placeholder, and the fact worth showing is the exception.
      cell: (table) =>
        table.claimedByName ? <span className="text-fg-muted">{t('tables.claimedBy', { name: table.claimedByName })}</span> : null,
    },
    {
      id: 'createdAt',
      header: t('tables.columns.createdAt'),
      role: 'hidden',
      cell: (table) => formatDate(table.createdAt, i18n.language, timeZone),
    },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('tables.title')}</h1>
        <div className="flex flex-wrap items-center gap-4">
          {/* The screen that lost the review is where somebody will look for it: an admin who
              learned the old `/admin/tables` comes here for "Aprobar", finds a listing, and has no
              way to discover the buttons moved. `admins.reviewing` is the section whose text moved
              with them, and its `listing` line says what this screen is now (#176). */}
          <HelpLink section="admins.reviewing" className="text-sm">
            {t('tables.reviewHelpLink')}
          </HelpLink>
          <HelpLink section="admins.assign-masters" className="text-sm">
            {t('tables.helpLink')}
          </HelpLink>
          <Button size="sm" variant="outline" onClick={() => createDialog.open()}>
            {t('tables.createUnassigned')}
          </Button>
        </div>
      </div>
      <p className="text-fg-muted text-sm">{t('tables.description')}</p>

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        placeholder={t('tables.searchPlaceholder')}
        label={t('tables.searchLabel')}
      />

      {isPending && <Skeleton className="h-64 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={showsEverything ? t('tables.emptyTitle') : t('tables.noResultsTitle')}
          description={showsEverything ? t('tables.emptyDescription') : t('tables.noResultsDescription')}
        />
      )}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('tables.title')}
            columns={columns}
            rows={data.content}
            getRowId={(table) => table.id}
            renderActions={(table) => <AdminTableRowActions table={table} />}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
          />
        </>
      )}
      <CreateUnassignedTableDialog open={createDialog.isOpen} onOpenChange={createDialog.close} />
    </div>
  )
}

export { AdminTablesPage as Component }
