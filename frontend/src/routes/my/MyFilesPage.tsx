import { PencilIcon, Trash2Icon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { IconAction } from '@/components/IconAction'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  EditFileDialog,
  FileCategoryBadge,
  FileCategoryChoice,
  FileCategoryFilter,
  FileDropzone,
  StagedFileList,
  FileList,
  FileTypeBadge,
  FileUsageChips,
  useCommitStagedFiles,
  useDeleteFile,
  useMyCategories,
  useMyFiles,
  useUpdateFile,
  type FileCategory,
  type StagedFile,
  type StoredFile,
  type UpdateFileInput,
} from '@/features/files'
import { useDebounce } from '@/hooks/useDebounce'
import { useDisclosure } from '@/hooks/useDisclosure'
import { formatRelativeDate } from '@/lib/date'

/**
 * `/my/files` — everything this person has uploaded, and what each file is doing.
 *
 * **It belongs to no context**, beside `/my/schedule` (#222): the sheet somebody applied with and
 * the map they attached to a table they run are the same library, and splitting it by role would
 * ask them to remember which hat they were wearing when they uploaded something.
 *
 * The screen exists to make the cost strategy of #75 legible from the one place a person can act on
 * it. Three things it says that no other screen does:
 *
 * - **Where each file is used** (#232). That is the classification, shown as chips rather than
 *   stored as a column — a file attached to two tables shows two, and neither of them is a lie.
 * - **A file nothing points at is "sin usar"**, which is the honest warning that the purge will
 *   reach it first, without the screen ever having to explain retention.
 * - **Which flows each file belongs to** (#233), which is what makes a library of thirty navigable.
 *
 * **It is the personal library and not the platform's** (#237). What an admin publishes for the
 * community lives in `/admin/files`, which is a different screen with a different job — so the
 * `Admin` role adds no cajón here and takes none away, and `Announcement` never appears at all. What
 * somebody may file under is their roles plus the tables they run, and the server decides it.
 *
 * **What was searched and which page are in the URL**, like /admin/files (#185): a tidying session
 * survives a refresh, and a filtered view can be linked to.
 */
export function MyFilesPage() {
  const { t, i18n } = useTranslation('files')
  const [searchParams, setSearchParams] = useSearchParams()
  const confirm = useConfirm()

  const page = Number(searchParams.get('page') ?? '0')
  const category = (searchParams.get('category') as FileCategory | null) ?? null

  const [search, setSearch] = useState(() => searchParams.get('q') ?? '')
  const debouncedSearch = useDebounce(search, 300)

  // isLoadingError, not isError: a background refetch that fails must not blank a list that already
  // loaded (#150).
  const { data, isPending, isLoadingError, refetch } = useMyFiles(debouncedSearch || undefined, category ?? undefined, page)
  // Which cajones are this person's to use (#237). The server decides: it depends on their roles
  // *and* on whether they run a table, and a co-master an admin assigned has no Master role (#135).
  const { data: myCategories } = useMyCategories()
  const update = useUpdateFile()
  const remove = useDeleteFile()
  const editDialog = useDisclosure<StoredFile>()
  const uploadPanel = useDisclosure()

  // Staged in the browser until the button below is pressed (#238).
  const [staged, setStaged] = useState<StagedFile[]>([])
  const [cajon, setCajon] = useState<FileCategory | null>(null)
  const commit = useCommitStagedFiles()

  function removeStaged(key: string) {
    setStaged((current) => current.filter((entry) => (entry.kind === 'new' ? entry.localId : entry.fileId) !== key))
  }

  /**
   * Sends everything staged, and says what happened to each part.
   *
   * A file that failed does not undo the ones that went through: they are already in the library and
   * the person is told which one to try again — retrying costs nothing, because the server
   * recognises content it already has (#75, #238).
   */
  function send() {
    const chosen = cajon ?? myCategories?.[0]
    if (chosen === undefined) return
    commit.mutate(
      { staged, fileCategory: chosen },
      {
        onSuccess: ({ failed, reused }) => {
          setStaged(staged.filter((entry) => entry.kind === 'new' && failed.includes(entry.name)))
          if (reused.length > 0) {
            toast.info(t('mine.reused', { names: reused.join(', ') }))
          }
          if (failed.length > 0) {
            toast.error(t('mine.someFailed', { names: failed.join(', ') }))
            return
          }
          uploadPanel.close()
        },
      },
    )
  }

  /** Writes the screen's state into the URL, resetting the page whenever the filters change. */
  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  function handleEdit(input: UpdateFileInput) {
    const file = editDialog.item
    if (!file) return
    update.mutate(
      { fileId: file.id, input },
      {
        onSuccess: () => {
          editDialog.close()
          toast.success(t('edit.saved'))
        },
      },
    )
  }

  async function handleDelete(file: StoredFile) {
    const confirmed = await confirm({
      title: t('mine.deleteTitle'),
      // Named, because "delete the file?" on a list of thirty is a question about which one.
      description: t('mine.deleteDescription', { name: file.name }),
      // The button names the action instead of saying "Confirmar": what is about to happen should
      // be readable on the thing you press, not only in the paragraph above it.
      confirmLabel: t('mine.deleteConfirm'),
    })
    if (!confirmed) return
    remove.mutate(file.id)
  }

  const isFiltered = debouncedSearch !== '' || category !== null

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-serif text-2xl font-semibold">{t('mine.title')}</h1>
          <p className="text-fg-muted text-sm">{t('mine.description')}</p>
        </div>
        {/* Absent, not disabled, when this person has no cajón of their own — an account that is
            neither player nor master has no flow to file anything under, and a button that cannot
            work is worse than no button (principio 2 de frontend-diseno.md §1). While the list is
            still loading it is disabled rather than gone, so it does not appear and jump. */}
        {(myCategories === undefined || myCategories.length > 0) && (
          <Button
            type="button"
            disabled={myCategories === undefined}
            onClick={() => (uploadPanel.isOpen ? uploadPanel.close() : uploadPanel.open())}
          >
            {uploadPanel.isOpen ? t('mine.uploadClose') : t('mine.upload')}
          </Button>
        )}
      </div>

      {/* **The panel stays open after an upload**, and no toast fires. Closing it on success would
          unmount the zone along with the one thing it had to say — that the file was recognised
          rather than stored again (#234) — and a "file uploaded" toast would flatly contradict it.
          The row appearing in the list below is the confirmation; the zone says the rest, and the
          person is left where they are if they have another file to add. */}
      {/* **The one place that asks which cajón** (#233): every other upload happens inside a flow that
          already knows, and this one has no flow to observe. */}
      {uploadPanel.isOpen && myCategories !== undefined && myCategories.length > 0 && (
        <div className="border-border space-y-3 rounded-lg border p-4">
          {/* Nothing uploads on pick (#238). The button below is the confirm — here the operation is
              just "upload these", so the confirm is that and nothing more. */}
          <FileDropzone onStaged={(file) => setStaged((current) => [...current, file])} isBusy={commit.isPending} />
          <StagedFileList files={staged} onRemove={removeStaged} />
          <div className="space-y-2">
            <p className="text-sm font-medium">{t('dropzone.categoryLabel')}</p>
            <FileCategoryChoice
              value={cajon ?? myCategories[0]!}
              onChange={setCajon}
              options={myCategories}
              label={t('dropzone.categoryLabel')}
              disabled={commit.isPending}
            />
            <p className="text-fg-muted text-xs">{t('dropzone.categoryHint')}</p>
          </div>
          <div className="flex justify-end">
            <Button type="button" disabled={staged.length === 0 || commit.isPending} onClick={send}>
              {t('mine.send', { count: staged.length })}
            </Button>
          </div>
        </div>
      )}

      <div className="space-y-3">
        <Input
          type="search"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            updateParams({ q: event.target.value })
          }}
          placeholder={t('mine.searchPlaceholder')}
          aria-label={t('mine.searchLabel')}
        />
        {/* Only the cajones that can hold something of theirs: a plain member never files table
            material, and `Announcement` is nobody's — it lives in the platform's library (#237). */}
        <FileCategoryFilter value={category} onChange={(next) => updateParams({ category: next ?? '' })} options={myCategories ?? []} />
      </div>

      {isPending && <Skeleton className="h-64 w-full" />}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={isFiltered ? t('mine.noResultsTitle') : t('mine.emptyTitle')}
          description={isFiltered ? t('mine.noResultsDescription') : t('mine.emptyDescription')}
        />
      )}
      {data && data.content.length > 0 && (
        <>
          <FileList
            files={data.content.map((file) => ({ ...file, fileId: file.id }))}
            renderMeta={(file) => (
              <div className="space-y-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  {file.categories.map((category) => (
                    <FileCategoryBadge key={category} category={category} />
                  ))}
                  <FileTypeBadge fileType={file.fileType} />
                  {file.lastUsedAt && (
                    <span className="text-fg-muted text-xs">
                      {t('mine.lastUsed', { when: formatRelativeDate(file.lastUsedAt, i18n.language) })}
                    </span>
                  )}
                </div>
                <FileUsageChips usages={file.usages} />
              </div>
            )}
            renderActions={(file) => (
              <>
                {/* A published file is the platform's: only an admin renames or removes it (#64). */}
                {file.fileType !== 'Public' && (
                  <>
                    <IconAction
                      label={t('actions.edit')}
                      icon={<PencilIcon className="size-4" />}
                      disabled={update.isPending}
                      onClick={() => editDialog.open(file)}
                    />
                    <IconAction
                      label={t('actions.delete')}
                      icon={<Trash2Icon className="size-4" />}
                      disabled={remove.isPending}
                      onClick={() => void handleDelete(file)}
                    />
                  </>
                )}
              </>
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

      <EditFileDialog
        file={editDialog.item ?? null}
        onOpenChange={(open) => !open && editDialog.close()}
        onConfirm={handleEdit}
        isPending={update.isPending}
      />
    </div>
  )
}

export { MyFilesPage as Component }
