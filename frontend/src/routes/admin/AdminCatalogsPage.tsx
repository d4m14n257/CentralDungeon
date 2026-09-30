import { useTranslation } from 'react-i18next'
import { Check, Network, RotateCcw, X } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/hooks/useConfirm'
import { IconAction } from '@/components/IconAction'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/PageHeader'
import { adminPageSizeFrom, pageSize } from '@/config/pagination'
import { adminCatalogGroupPath } from '@/config/paths'
import { useDebounce } from '@/hooks/useDebounce'
import { useDisclosure } from '@/hooks/useDisclosure'
import {
  CatalogStatusBadge,
  CreateCatalogValueDialog,
  useAcceptCatalogValue,
  useAdminCatalog,
  useRejectCatalogValue,
  useRestoreCatalogValue,
  type AdminCatalogValue,
  type CatalogKind,
} from '@/features/catalogs'
import { ApiError } from '@/types/api'

/** The three catalogs, in the order the tabs show them. */
const KINDS: CatalogKind[] = ['systems', 'tags', 'platforms']

/**
 * Narrows an arbitrary query-string value to a catalog. Anything unknown falls back to systems: a
 * hand-edited URL should land somewhere sensible, not on an error.
 *
 * @param value the raw `?kind=` parameter
 * @returns a valid catalog kind
 */
function toKind(value: string | null): CatalogKind {
  return KINDS.find((kind) => kind === value) ?? 'systems'
}

/**
 * The actions one group row offers. The table is a list of groups (#275); everything that moves a
 * value between groups - merge, split, move, promote - happens on the group's canvas, where both
 * ends of the move are in sight.
 *
 * - **pending** - approve it as a group of its own, straight from the row, or reject it
 * - **rejected** - approve it after all, because turning one down is not meant to be final (#55)
 * - **disabled** - restore
 * - **every group** - open its canvas
 *
 * Absent rather than greyed out when the status would make the server refuse it (principio 2).
 *
 * @param props.kind  which catalog
 * @param props.value the group's head
 */
function CatalogRowActions({ kind, value }: { kind: CatalogKind; value: AdminCatalogValue }) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const accept = useAcceptCatalogValue(kind)
  const reject = useRejectCatalogValue(kind)
  const restore = useRestoreCatalogValue(kind)
  const navigate = useNavigate()

  async function handleReject() {
    const confirmed = await confirm({
      title: t('admin.rejectConfirmTitle', { name: value.name }),
      description: t('admin.rejectConfirmDescription'),
    })
    if (!confirmed) return
    reject.mutate(value.id, { onSuccess: () => toast.success(t('admin.rejectSuccess', { name: value.name })) })
  }

  const isPending = value.status === 'Created'
  const isRejected = value.status === 'Rejected'
  const isDisabled = value.status === 'Disabled'

  return (
    <>
      {(isPending || isRejected) && (
        <IconAction
          icon={<Check className="size-4" />}
          label={t('admin.acceptAsGroup')}
          onClick={() =>
            accept.mutate(
              { id: value.id, canonicalId: null },
              { onSuccess: () => toast.success(t('admin.acceptSuccess', { name: value.name })) },
            )
          }
          disabled={accept.isPending}
        />
      )}
      {isPending && (
        <IconAction
          icon={<X className="size-4" />}
          label={t('admin.reject')}
          onClick={() => void handleReject()}
          disabled={reject.isPending}
          className="text-destructive hover:text-destructive"
        />
      )}
      {isDisabled && (
        <IconAction
          icon={<RotateCcw className="size-4" />}
          label={t('admin.restore')}
          onClick={() => restore.mutate(value.id, { onSuccess: () => toast.success(t('admin.restoreSuccess', { name: value.name })) })}
          disabled={restore.isPending}
        />
      )}
      <IconAction
        icon={<Network className="size-4" />}
        label={t('admin.openGroup', { name: value.name })}
        onClick={() => void navigate(adminCatalogGroupPath(kind, value.id))}
      />
    </>
  )
}

/**
 * /admin/catalogs - systems, tags and platforms, **one row per synonym group** (#275).
 *
 * It is the screen that makes the rest of the catalog design work. A master can propose a value
 * from the wizard, but a proposal shows to nobody and filters nothing until somebody accepts it
 * (#57) - so without this screen, proposing would be half a capability and every table tagged with
 * something new would carry an invisible tag (#179).
 *
 * A row is a group's head - its canonical entry - or a proposal nobody has classified yet, which is a
 * group of one until somebody does: that is where an admin approves something new straight away.
 * Searching finds a group by the name of any of its members (#54). What the group holds, and every
 * move between groups, is on its canvas, one click away.
 *
 * **Which catalog, what was searched and which page are all in the URL.** A row of a catalog is
 * something an admin sends to another admin, and state that only lives in `useState` cannot be
 * linked to.
 *
 * One of the wide tables of skill `diseno` §5.b: below `md` it stops being a table and each row
 * becomes a card. `DataTable` handles that, from the same column definitions - never horizontal
 * scroll.
 */
export function AdminCatalogsPage() {
  const { t } = useTranslation('catalogs')
  const [searchParams, setSearchParams] = useSearchParams()

  const kind = toKind(searchParams.get('kind'))
  const query = searchParams.get('q') ?? ''
  const page = Number(searchParams.get('page') ?? '0')
  // Rows per page (#271): in the URL like the page, so a link carries the view it was sent from.
  const size = adminPageSizeFrom(searchParams.get('size'))
  const debouncedQuery = useDebounce(query, 300)
  const createDialog = useDisclosure()

  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, error, refetch } = useAdminCatalog(kind, debouncedQuery, undefined, page, size, {
    groupsOnly: true,
  })

  /**
   * Writes the screen's state back into the URL, resetting the page whenever the thing being paged
   * through changes. Without that reset, switching catalog while on page 3 asks for a page that may
   * not exist and answers with an empty table.
   */
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

  const columns: DataTableColumn<AdminCatalogValue>[] = [
    {
      id: 'name',
      header: t('admin.columnGroupName'),
      role: 'title',
      cell: (value) => (
        <Link to={adminCatalogGroupPath(kind, value.id)} className="hover:underline">
          {value.name}
        </Link>
      ),
    },
    { id: 'status', header: t('admin.columnStatus'), role: 'badge', cell: (value) => <CatalogStatusBadge status={value.status} /> },
    { id: 'aliases', header: t('admin.columnAliases'), cell: (value) => value.aliasCount },
    { id: 'uses', header: t('admin.columnUses'), cell: (value) => value.uses },
  ]

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('admin.title')}
        description={t('admin.description')}
        help="admins.catalogs"
        action={{ label: t('admin.create'), onClick: () => createDialog.open() }}
      />

      <CreateCatalogValueDialog
        kind={kind}
        open={createDialog.isOpen}
        onOpenChange={(open) => (open ? createDialog.open() : createDialog.close())}
      />

      <Tabs value={kind} onValueChange={(value) => updateParams({ kind: value })}>
        <TabsList>
          {KINDS.map((option) => (
            <TabsTrigger key={option} value={option}>
              {t(`kind.${option}.label`)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <Input
        value={query}
        onChange={(event) => updateParams({ q: event.target.value })}
        placeholder={t('admin.searchPlaceholder')}
        aria-label={t('admin.searchLabel')}
      />

      {isPending && <Skeleton className="h-64 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState title={t('admin.emptyTitle')} description={query ? t('admin.emptySearchDescription') : t('admin.emptyDescription')} />
      )}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('admin.tableLabel', { kind: t(`kind.${kind}.label`) })}
            columns={columns}
            rows={data.content}
            getRowId={(value) => value.id}
            renderActions={(value) => <CatalogRowActions kind={kind} value={value} />}
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
    </div>
  )
}

export { AdminCatalogsPage as Component }
