import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { EyeOff, Trash2 } from 'lucide-react'
import { useSearchParams } from 'react-router'

import { useConfirm } from '@/hooks/useConfirm'
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
import { adminFileUploadPath } from '@/config/paths'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import {
  FileCategoryBadge,
  FileCategoryFilter,
  PUBLISHABLE_CATEGORIES,
  formatFileSize,
  useAdminFiles,
  useDeleteFileAsAdmin,
  useUnpublishFile,
  type AdminFile,
  adminFileSearchFields,
} from '@/features/files'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * /admin/files — the platform's library: what the community publishes for every table (#64, #79,
 * #278).
 *
 * The screen exists for one capability the rest of the file story depends on: **publishing**. Once
 * the community's default character sheet is here, masters attach *that* file instead of uploading
 * their own copy, so correcting it corrects every table at once and the same bytes are stored once
 * rather than once per master. Without this screen, #79 would be a rule nothing could exercise.
 *
 * **Only what is published, and uploading is publishing** (#278). Uploading happens on its own page,
 * /admin/files/upload, where each file gets the cajón it goes into; and somebody's private file
 * — what a player applied with or handed in — never shows up, because it is theirs and not the
 * library's. That is also why the screen speaks of three cajones and not five: the two player-side
 * ones hold people's answers, and nothing is ever published into them (#233).
 *
 * The `uses` column is not decoration either: one file showing "3 tables" is what makes "linking is
 * not copying" something an admin can see rather than something a document claims.
 *
 * **What was searched and which page are in the URL**, like /admin/catalogs (#185): a row is
 * something one admin sends to another, and state that only lives in `useState` cannot be linked to.
 *
 * One of the wide tables of skill `diseno` §5.b: below `md` it stops being a table and each row
 * becomes a card, from the same column definitions — never horizontal scroll.
 */
export function AdminFilesPage() {
  const { t, i18n } = useTranslation('files')
  const [searchParams, setSearchParams] = useSearchParams()
  const confirm = useConfirm()

  const page = Number(searchParams.get('page') ?? '0')
  // Rows per page (#271): in the URL like the page, so a link carries the view it was sent from.
  const size = adminPageSizeFrom(searchParams.get('size'))
  const timeZone = browserTimeZone()

  // The search box holds a structured value, but what travels - to the URL and to the API - is the
  // raw string of #164. Hydrating from the URL on mount is what makes a filtered view linkable
  // (#185), the same property /admin/catalogs has. One list of commands, given to the box, used to
  // read `?q=` and shown in the help; the wiring is the shared hook (#240).
  const fields = useMemo(() => adminFileSearchFields(t), [t])
  const search = useSearchQuery({ fields, initialQuery: searchParams.get('q') ?? '', onQueryChange: (query) => updateParams({ q: query }) })

  // isLoadingError, not isError: see docs/decisiones.md #150.
  // The category is a filter and not a search term (#233): three known values are chosen from, never
  // typed at. A hand-edited URL naming one of the player-side cajones is dropped rather than sent,
  // since the library has no such shelf and the server would refuse it (#278).
  const requestedCategory = searchParams.get('category')
  const category = PUBLISHABLE_CATEGORIES.find((value) => value === requestedCategory) ?? null
  const { data, isPending, isLoadingError, error, refetch } = useAdminFiles(search.query, undefined, category ?? undefined, page, size)
  const unpublish = useUnpublishFile()
  const remove = useDeleteFileAsAdmin()

  /**
   * Writes the screen's state into the URL, resetting the page whenever the search changes.
   *
   * It builds on the **previous** params rather than the ones this render read: «clear filters» writes
   * the query and the category in the same click, and the second write would otherwise start from a
   * copy that does not have the first one yet (#268).
   */
  function updateParams(changes: Record<string, string>) {
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        for (const [key, value] of Object.entries(changes)) {
          if (value === '') next.delete(key)
          else next.set(key, value)
        }
        if (!('page' in changes)) next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  async function handleUnpublish(file: AdminFile) {
    const confirmed = await confirm({ title: t('admin.unpublishTitle'), description: t('admin.unpublishDescription') })
    if (!confirmed) return
    unpublish.mutate(file.id)
  }

  async function handleDelete(file: AdminFile) {
    const confirmed = await confirm({ title: t('admin.deleteTitle'), description: t('admin.deleteDescription') })
    if (!confirmed) return
    remove.mutate(file.id)
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  const columns: DataTableColumn<AdminFile>[] = [
    { id: 'name', header: t('admin.columns.name'), role: 'title', cell: (file) => file.name },
    {
      id: 'category',
      header: t('admin.columns.category'),
      // Plural: a published blank can serve more than one flow at once (#233).
      cell: (file) => (
        <div className="flex flex-wrap gap-1">
          {file.categories.map((category) => (
            <FileCategoryBadge key={category} category={category} />
          ))}
        </div>
      ),
    },
    { id: 'owner', header: t('admin.columns.owner'), cell: (file) => file.ownerName },
    // The number that makes #79 visible: one file, three tables.
    { id: 'uses', header: t('admin.columns.uses'), cell: (file) => t('admin.usesValue', { count: file.uses }) },
    {
      id: 'size',
      header: t('admin.columns.size'),
      cell: (file) => {
        const size = formatFileSize(file.sizeBytes, i18n.language)
        return t(`size.${size.unit}`, { value: size.value })
      },
    },
    {
      id: 'createdAt',
      header: t('admin.columns.createdAt'),
      role: 'hidden',
      cell: (file) => formatDate(file.createdAt, i18n.language, timeZone),
    },
    { id: 'status', header: t('admin.columns.status'), cell: (file) => t(`status.${file.status}`) },
  ]

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('admin.title')}
        description={t('admin.description')}
        help="admins.files"
        action={{ label: t('admin.upload'), to: adminFileUploadPath() }}
      />

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        searchedQuery={search.query}
        onSearch={search.onSearch}
        placeholder={t('admin.searchPlaceholder')}
        label={t('admin.searchLabel')}
        extraFiltersActive={category !== null}
        onClearExtraFilters={() => updateParams({ category: '' })}
      />

      <FileCategoryFilter value={category} options={PUBLISHABLE_CATEGORIES} onChange={(next) => updateParams({ category: next ?? '' })} />

      {isPending && <Skeleton className="h-64 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={search.query ? t('admin.noResultsTitle') : t('admin.emptyTitle')}
          description={search.query ? t('admin.noResultsDescription') : t('admin.emptyDescription')}
        />
      )}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('admin.title')}
            columns={columns}
            rows={data.content}
            getRowId={(file) => file.id}
            renderActions={(file) => (
              <>
                {/* An action a row's state would make the server refuse is absent, never greyed out:
                    a disabled button that does not say why is worse than no button (principio 2). */}
                {file.status === 'Current' && (
                  <IconAction
                    icon={<EyeOff className="size-4" />}
                    label={t('actions.unpublish')}
                    disabled={unpublish.isPending}
                    onClick={() => void handleUnpublish(file)}
                  />
                )}
                {file.status === 'Current' && (
                  <IconAction
                    icon={<Trash2 className="size-4" />}
                    label={t('actions.delete')}
                    disabled={remove.isPending}
                    onClick={() => void handleDelete(file)}
                    className="text-destructive hover:text-destructive"
                  />
                )}
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
    </div>
  )
}

export { AdminFilesPage as Component }
