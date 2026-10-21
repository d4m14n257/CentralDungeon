import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Eye } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { IconAction } from '@/components/IconAction'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { adminPageSizeFrom, pageSize } from '@/config/pagination'
import { adminTableDetailPath } from '@/config/paths'
import {
  CreateUnassignedTableDialog,
  TableStatusBadge,
  adminTableSearchFields,
  useAdminTables,
  type AdminTableSummary,
} from '@/features/tables'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

import { AdminTableActions } from './AdminTableActions'

/**
 * `/admin/tables` — **every table the platform has**, with its status (ADR-16 §7, F3.3).
 *
 * It used to be "Mesas por revisar": a listing that defaulted to the review statuses, which made it a
 * second tray with rules of its own. Reviewing moved to `/admin/queue` with the two buttons that
 * performed it, and what is left here is the question nothing else answered — *which tables exist,
 * and what state is each one in*. An admin looking for the table somebody is complaining about had
 * nowhere to look before this.
 *
 * **Showing everything is only useful with a way to narrow it**, which is why the search box arrived
 * in the same slice: six commands (ADR-22 §1, §3), declared once in `adminTableSearchFields` so that the
 * box, the help and the `?q=` cannot disagree. `/table_status` offers its ten values because they are
 * a closed set; the two catalog commands do not, because catalogs grow whenever a master proposes one
 * (ADR-12 §8).
 *
 * **What was searched and which page are in the URL** (ADR-12 §7), like the other admin screens. Until
 * F3.3 the page number lived in `useState`, which meant the second page of a listing could not be
 * linked to and was lost on reload.
 *
 * **The reservation shows here too** (ADR-16 §5): a table somebody is reviewing right now says so, even
 * though this screen offers no action that needs it. Without that an admin sees a table in
 * `Preparation`, goes to the tray to review it, and finds it is not there.
 *
 * A work list, so it pages by number with the total in view instead of "load more" (ADR-04 §6).
 *
 * One of the wide tables of skill `diseno` §5.b: below `md` it stops being a table and each row
 * becomes a card, from the same column definitions — never horizontal scroll.
 *
 * **Behind the admin context's guard** (ADR-20 §2): an account without `Admin` or `Owner` is sent home by
 * `AdminLayout` before this paints. `ForbiddenState` stays for a `403` that still arrives - the
 * backend authorizes on its own (ADR-20 §1) and the page must not go blank if it refuses.
 */
export function AdminTablesPage() {
  const { t, i18n } = useTranslation('admin')
  const navigate = useNavigate()
  const { t: tTables } = useTranslation('tables')
  const [searchParams, setSearchParams] = useSearchParams()
  const createDialog = useDisclosure()
  const timeZone = browserTimeZone()

  const page = Number(searchParams.get('page') ?? '0')
  // Rows per page (ADR-21 §11): in the URL like the page, so a link carries the view it was sent from.
  const size = adminPageSizeFrom(searchParams.get('size'))

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of ADR-22 §1. Nothing is filtered by default: this screen answers "which tables exist", and a listing
  // that silently hides some of them is the thing ADR-16 §7 took away.
  const fields = useMemo(() => adminTableSearchFields(t, tTables), [t, tTables])
  const search = useSearchQuery({
    fields,
    initialQuery: searchParams.get('q') ?? '',
    onQueryChange: (query) => updateParams({ q: query }),
  })

  // isLoadingError, not isError: see ADR-19 §4.
  const { data, isPending, isLoadingError, error, refetch } = useAdminTables(search.query, undefined, page, size)

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
  const showsEverything = search.query.trim() === ''

  const columns: DataTableColumn<AdminTableSummary>[] = [
    // The name opens the table (ADR-06 §8): the row is where an admin finds it, the detail is where they read it.
    {
      id: 'name',
      header: t('tables.columns.name'),
      role: 'title',
      cell: (table) => (
        <Link to={adminTableDetailPath(table.id)} className="hover:underline">
          {table.name}
        </Link>
      ),
    },
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
      <PageHeader
        title={t('tables.title')}
        description={t('tables.description')}
        help="admins.reviewing"
        action={{ label: t('tables.createUnassigned'), onClick: () => createDialog.open() }}
      />

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        searchedQuery={search.query}
        onSearch={search.onSearch}
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
            renderActions={(table) => (
              <>
                {/* First and on every row, whatever its status: seeing a table is never refused (ADR-06 §8). */}
                <IconAction
                  icon={<Eye className="size-4" />}
                  label={t('tables.view')}
                  onClick={() => void navigate(adminTableDetailPath(table.id))}
                />
                <AdminTableActions table={table} />
              </>
            )}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
            pageSize={size}
            // The default leaves the URL, like an empty search does; any change of size starts over
            // at the first page, which `updateParams` does for every change that is not the page.
            onPageSizeChange={(next) => updateParams({ size: next === pageSize.admin ? '' : String(next) })}
          />
        </>
      )}
      <CreateUnassignedTableDialog open={createDialog.isOpen} onOpenChange={createDialog.close} />
    </div>
  )
}

export { AdminTablesPage as Component }
