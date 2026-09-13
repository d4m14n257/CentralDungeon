import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/hooks/useConfirm'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import {
  FileCategoryBadge,
  FileCategoryFilter,
  FileDropzone,
  StagedFileList,
  FileTypeBadge,
  PublishFileDialog,
  formatFileSize,
  useAdminFiles,
  useCommitStagedFiles,
  useDeleteFileAsAdmin,
  usePublishFile,
  useUnpublishFile,
  type AdminFile,
  type FileCategory,
  type StagedFile,
  adminFileSearchFields,
} from '@/features/files'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

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
  // (#185), the same property /admin/catalogs has. One list of commands, given to the box, used to
  // read `?q=` and shown in the help; the wiring is the shared hook (#240).
  const fields = useMemo(() => adminFileSearchFields(t), [t])
  const search = useSearchQuery({ fields, initialQuery: searchParams.get('q') ?? '', onQueryChange: (query) => updateParams({ q: query }) })

  // isLoadingError, not isError: see docs/decisiones.md #150.
  // The category is a filter and not a search term (#233): five known values are chosen from, never
  // typed at, so offering "contains" over them would let one letter match four categories.
  const category = (searchParams.get('category') as FileCategory | null) ?? null
  const { data, isPending, isLoadingError, error, refetch } = useAdminFiles(
    search.debouncedQuery,
    undefined,
    undefined,
    category ?? undefined,
    page,
  )
  const publish = usePublishFile()
  const unpublish = useUnpublishFile()
  const remove = useDeleteFileAsAdmin()
  const publishDialog = useDisclosure<AdminFile>()
  const uploadPanel = useDisclosure()

  // Staged in the browser until the button below is pressed (#238).
  const [staged, setStaged] = useState<StagedFile[]>([])
  const commit = useCommitStagedFiles()

  function removeStaged(key: string) {
    setStaged((current) => current.filter((entry) => (entry.kind === 'new' ? entry.localId : entry.fileId) !== key))
  }

  /** Sends what is staged into the platform's library. What failed stays listed, to try again. */
  function send() {
    commit.mutate(
      { staged },
      {
        onSuccess: ({ failed }) => {
          setStaged(staged.filter((entry) => entry.kind === 'new' && failed.includes(entry.name)))
          void refetch()
          if (failed.length > 0) {
            toast.error(t('admin.someFailed', { names: failed.join(', ') }))
            return
          }
          uploadPanel.close()
        },
      },
    )
  }

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

  function handlePublish(categories: FileCategory[]) {
    const file = publishDialog.item
    if (!file) return
    publish.mutate(
      { fileId: file.id, input: { categories } },
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
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('admin.title')}</h1>
        <div className="flex items-center gap-3">
          <HelpLink section="admins.files" className="text-sm">
            {t('table.helpLink')}
          </HelpLink>
          <Button type="button" onClick={() => (uploadPanel.isOpen ? uploadPanel.close() : uploadPanel.open())}>
            {uploadPanel.isOpen ? t('admin.uploadClose') : t('admin.upload')}
          </Button>
        </div>
      </div>
      <p className="text-fg-muted text-sm">{t('admin.description')}</p>

      {/* **The admin's own upload box** (#237). Until this existed, the only way to get a file into
          the platform's library was to attach it to a table first and publish it from there - so the
          file arrived carrying a cajón it got by accident. It asks no cajón: what it is offered for
          is said when it is published, which is the deliberate act. */}
      {uploadPanel.isOpen && (
        <div className="border-border space-y-3 rounded-lg border p-4">
          {/* Staged until the button below (#238). No cajón is asked for: what a published file is
              offered for is said when it is published, which is the deliberate act (#233). */}
          <FileDropzone onStaged={(file) => setStaged((current) => [...current, file])} isBusy={commit.isPending} />
          <StagedFileList files={staged} onRemove={removeStaged} />
          <div className="flex justify-end">
            <Button type="button" disabled={staged.length === 0 || commit.isPending} onClick={send}>
              {t('admin.send', { count: staged.length })}
            </Button>
          </div>
        </div>
      )}

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        placeholder={t('admin.searchPlaceholder')}
        label={t('admin.searchLabel')}
      />

      <FileCategoryFilter value={category} onChange={(next) => updateParams({ category: next ?? '' })} />

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
