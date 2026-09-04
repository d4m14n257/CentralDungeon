import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath } from '@/config/paths'
import { useDebounce } from '@/hooks/useDebounce'
import { useDisclosure } from '@/hooks/useDisclosure'
import {
  FileAudienceBadge,
  FileTypeBadge,
  PublishFileDialog,
  formatFileSize,
  useAdminFiles,
  useDeleteFileAsAdmin,
  usePublishFile,
  useUnpublishFile,
  type AdminFile,
  type PublicAudience,
} from '@/features/files'
import { browserTimeZone, formatDate } from '@/lib/date'
import { buildSearchQuery, parseSearchQuery, type SearchQueryValue } from '@/lib/searchQuery'
import { ApiError } from '@/types/api'

/** The three fields the search box accepts behind a `/`, mirroring the backend's `FileSearchField`. */
const SEARCH_FIELD_NAMES = ['name', 'owner', 'type'] as const

/**
 * /admin/files — every file the community has uploaded, and the power to publish one (#64, #79).
 *
 * The screen exists for one capability the rest of the file story depends on: **publishing**. Once
 * the community's default character sheet is here, masters attach *that* file instead of uploading
 * their own copy, so correcting it corrects every table at once and the same bytes are stored once
 * rather than once per master. Without this screen, #79 would be a rule nothing could exercise.
 *
 * The `uses` column is not decoration either: one file showing "3 tables" is what makes "linking is
 * not copying" something an admin can see rather than something a document claims.
 *
 * **What was searched and which page are in the URL**, like /admin/catalogs (#185): a row is
 * something one admin sends to another, and state that only lives in `useState` cannot be linked to.
 *
 * One of the wide tables of frontend-diseno.md §5.b: below `md` it stops being a table and each row
 * becomes a card, from the same column definitions — never horizontal scroll.
 */
export function AdminFilesPage() {
  const { t, i18n } = useTranslation('files')
  const [searchParams, setSearchParams] = useSearchParams()
  const confirm = useConfirm()

  const page = Number(searchParams.get('page') ?? '0')
  const timeZone = browserTimeZone()

  // The search box holds a structured value, but what travels - to the URL and to the API - is the
  // raw string of #164. Hydrating from the URL on mount is what makes a filtered view linkable
  // (#185), the same property /admin/catalogs has.
  const [search, setSearch] = useState<SearchQueryValue>(() => ({
    terms: parseSearchQuery(searchParams.get('q') ?? '', SEARCH_FIELD_NAMES),
    activeField: null,
    draft: '',
    pendingConnector: 'and',
  }))
  const query = buildSearchQuery(search)
  const debouncedQuery = useDebounce(query, 300)

  // isLoadingError, no isError: ver docs/decisiones.md #150.
  const { data, isPending, isLoadingError, error, refetch } = useAdminFiles(debouncedQuery, undefined, undefined, page)
  const publish = usePublishFile()
  const unpublish = useUnpublishFile()
  const remove = useDeleteFileAsAdmin()
  const publishDialog = useDisclosure<AdminFile>()

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

  function handlePublish(audience: PublicAudience) {
    const file = publishDialog.item
    if (!file) return
    publish.mutate(
      { fileId: file.id, input: { publicAudience: audience } },
      {
        onSuccess: () => {
          publishDialog.close()
          toast.success(t('actions.publish'))
        },
      },
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
    { id: 'type', header: t('admin.columns.type'), role: 'badge', cell: (file) => <FileTypeBadge fileType={file.fileType} /> },
    {
      id: 'audience',
      header: t('admin.columns.audience'),
      cell: (file) => (file.publicAudience ? <FileAudienceBadge audience={file.publicAudience} /> : null),
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
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('admin.title')}</h1>
        <Link to={helpPath('admins', 'files')} className="text-fg-muted hover:text-fg text-sm underline">
          {t('table.helpLink')}
        </Link>
      </div>
      <p className="text-fg-muted text-sm">{t('admin.description')}</p>

      <SearchQueryInput
        fields={[
          { name: 'name', label: t('search.name') },
          { name: 'owner', label: t('search.owner') },
          { name: 'type', label: t('search.type') },
        ]}
        value={search}
        onChange={(value) => {
          setSearch(value)
          updateParams({ q: buildSearchQuery(value) })
        }}
        placeholder={t('admin.searchPlaceholder')}
        label={t('admin.searchLabel')}
      />

      {isPending && <Skeleton className="h-64 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={query ? t('admin.noResultsTitle') : t('admin.emptyTitle')}
          description={query ? t('admin.noResultsDescription') : t('admin.emptyDescription')}
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
              <div className="flex flex-wrap justify-end gap-2">
                {/* An action a row's state would make the server refuse is absent, never greyed out:
                    a disabled button that does not say why is worse than no button (principio 2). */}
                {file.status === 'Current' && file.fileType !== 'Public' && (
                  <Button size="sm" onClick={() => publishDialog.open(file)}>
                    {t('actions.publish')}
                  </Button>
                )}
                {file.status === 'Current' && file.fileType === 'Public' && (
                  <Button size="sm" variant="outline" disabled={unpublish.isPending} onClick={() => void handleUnpublish(file)}>
                    {t('actions.unpublish')}
                  </Button>
                )}
                {file.status === 'Current' && (
                  <Button size="sm" variant="outline" disabled={remove.isPending} onClick={() => void handleDelete(file)}>
                    {t('actions.delete')}
                  </Button>
                )}
              </div>
            )}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
          />
        </>
      )}

      <PublishFileDialog
        file={publishDialog.item ?? null}
        onOpenChange={(open) => !open && publishDialog.close()}
        onConfirm={handlePublish}
        isPending={publish.isPending}
      />
    </div>
  )
}

export { AdminFilesPage as Component }
