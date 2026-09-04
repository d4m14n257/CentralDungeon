import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { helpPath } from '@/config/paths'
import { useDebounce } from '@/hooks/useDebounce'
import { useDisclosure } from '@/hooks/useDisclosure'
import {
  AcceptCatalogValueDialog,
  CatalogStatusBadge,
  DisableCatalogValueDialog,
  MergeCatalogGroupsDialog,
  useAdminCatalog,
  useRejectCatalogValue,
  useRestoreCatalogValue,
  useSplitCatalogGroup,
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
 * The actions one row offers, which depend entirely on where the value is in its lifecycle.
 *
 * The mapping is the point of the screen, so it is worth stating plainly:
 *
 * - **pending** - accept (and classify), or reject
 * - **accepted** - merge if it is a group, leave its group if it is an alias, or disable
 * - **rejected** - accept, because turning one down is not meant to be final
 * - **disabled** - restore
 *
 * A row never shows an action its status would make the server refuse. An operation that is not
 * available is absent rather than greyed out: a disabled button that does not say why is worse than
 * no button (frontend-diseno.md 1, principio 2).
 *
 * @param props.kind  which catalog
 * @param props.value the row's value
 */
function CatalogRowActions({ kind, value }: { kind: CatalogKind; value: AdminCatalogValue }) {
  const { t } = useTranslation('catalogs')
  const confirm = useConfirm()
  const reject = useRejectCatalogValue(kind)
  const split = useSplitCatalogGroup(kind)
  const restore = useRestoreCatalogValue(kind)
  const acceptDialog = useDisclosure()
  const mergeDialog = useDisclosure()
  const disableDialog = useDisclosure()

  async function handleReject() {
    const confirmed = await confirm({
      title: t('admin.rejectConfirmTitle', { name: value.name }),
      description: t('admin.rejectConfirmDescription'),
    })
    if (!confirmed) return
    reject.mutate(value.id, { onSuccess: () => toast.success(t('admin.rejectSuccess', { name: value.name })) })
  }

  async function handleSplit() {
    const confirmed = await confirm({
      title: t('admin.splitConfirmTitle', { name: value.name }),
      description: t('admin.splitConfirmDescription'),
    })
    if (!confirmed) return
    split.mutate(value.id, { onSuccess: () => toast.success(t('admin.splitSuccess', { name: value.name })) })
  }

  const isPending = value.status === 'Created'
  const isAccepted = value.status === 'Accepted'
  const isRejected = value.status === 'Rejected'
  const isDisabled = value.status === 'Disabled'
  const isCanonical = value.canonicalId === null

  return (
    <div className="flex flex-wrap justify-end gap-2">
      {(isPending || isRejected) && (
        <Button size="sm" onClick={() => acceptDialog.open()}>
          {t('admin.accept')}
        </Button>
      )}
      {isPending && (
        <Button size="sm" variant="outline" onClick={() => void handleReject()} disabled={reject.isPending}>
          {t('admin.reject')}
        </Button>
      )}
      {isAccepted && isCanonical && (
        <Button size="sm" variant="outline" onClick={() => mergeDialog.open()}>
          {t('admin.merge')}
        </Button>
      )}
      {isAccepted && !isCanonical && (
        <Button size="sm" variant="outline" onClick={() => void handleSplit()} disabled={split.isPending}>
          {t('admin.split')}
        </Button>
      )}
      {isAccepted && (
        <Button size="sm" variant="outline" onClick={() => disableDialog.open()}>
          {t('admin.disable')}
        </Button>
      )}
      {isDisabled && (
        <Button
          size="sm"
          variant="outline"
          onClick={() => restore.mutate(value.id, { onSuccess: () => toast.success(t('admin.restoreSuccess', { name: value.name })) })}
          disabled={restore.isPending}
        >
          {t('admin.restore')}
        </Button>
      )}

      <AcceptCatalogValueDialog kind={kind} value={value} open={acceptDialog.isOpen} onOpenChange={acceptDialog.close} />
      <MergeCatalogGroupsDialog kind={kind} source={value} open={mergeDialog.isOpen} onOpenChange={mergeDialog.close} />
      <DisableCatalogValueDialog kind={kind} value={value} open={disableDialog.isOpen} onOpenChange={disableDialog.close} />
    </div>
  )
}

/**
 * /admin/catalogs - systems, tags and platforms, and the synonym groups that hold them together.
 *
 * It is the screen that makes the rest of the catalog design work. A master can propose a value
 * from the wizard, but a proposal shows to nobody and filters nothing until somebody accepts it
 * (#57) - so without this screen, proposing would be half a capability and every table tagged with
 * something new would carry an invisible tag (#179).
 *
 * **Which catalog, what was searched and which page are all in the URL.** A row of a catalog is
 * something an admin sends to another admin, and state that only lives in `useState` cannot be
 * linked to.
 *
 * One of the wide tables of frontend-diseno.md 5.b: below `md` it stops being a table and each row
 * becomes a card. `DataTable` handles that, from the same column definitions - never horizontal
 * scroll.
 */
export function AdminCatalogsPage() {
  const { t } = useTranslation('catalogs')
  const [searchParams, setSearchParams] = useSearchParams()

  const kind = toKind(searchParams.get('kind'))
  const query = searchParams.get('q') ?? ''
  const page = Number(searchParams.get('page') ?? '0')
  const debouncedQuery = useDebounce(query, 300)

  // isLoadingError, not isError: see docs/decisiones.md #150.
  const { data, isPending, isLoadingError, error, refetch } = useAdminCatalog(kind, debouncedQuery, undefined, page)

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
    { id: 'name', header: t('admin.columnName'), role: 'title', cell: (value) => value.name },
    { id: 'status', header: t('admin.columnStatus'), role: 'badge', cell: (value) => <CatalogStatusBadge status={value.status} /> },
    {
      id: 'group',
      header: t('admin.columnGroup'),
      // The alias is what a table shows (#58); the group is what the search resolves (#54). Saying
      // "canonical" out loud here is the only place that distinction has to be visible to anyone.
      cell: (value) =>
        value.canonicalName ? (
          <span className="text-fg-muted">{value.canonicalName}</span>
        ) : (
          <span className="text-fg-subtle">{t('admin.isCanonical')}</span>
        ),
    },
    { id: 'uses', header: t('admin.columnUses'), cell: (value) => value.uses },
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('admin.title')}</h1>
        <Link to={helpPath('admins', 'catalogs')} className="text-fg-muted hover:text-fg text-sm underline">
          {t('admin.helpLink')}
        </Link>
      </div>

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
          />
        </>
      )}
    </div>
  )
}

export { AdminCatalogsPage as Component }
