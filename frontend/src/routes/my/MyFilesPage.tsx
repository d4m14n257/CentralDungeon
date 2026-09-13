import { PencilIcon, Trash2Icon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'

import { useConfirm } from '@/hooks/useConfirm'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { IconAction } from '@/components/IconAction'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  EditFileDialog,
  FileCategoryBadge,
  FileCategoryChoice,
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
  myFileSearchFields,
} from '@/features/files'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useHasPersonalLibrary } from '@/hooks/useHasPersonalLibrary'
import { useSearchQuery } from '@/hooks/useSearchQuery'
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
 * **And an account that is neither player nor master has no screen here** (#241): filling a library
 * is what those two flows do, so a pure admin or the owner never fills one. They get the reason
 * written out rather than an empty library with an upload button that cannot work — and the entry in
 * the account menu is absent for them too. The roles are cumulative: what is asked is whether they
 * hold one of the two, never whether they hold only it.
 *
 * **The search box is the only filter** (#242). The row of cajón toggles that used to sit under it
 * asked one question the search already answers better — `/file_categories` narrows by cajón from the
 * same line, combinable with the rest — while each row carries its cajón as a badge anyway. One way
 * to narrow, one place to look at what is narrowing.
 *
 * **What was searched and which page are in the URL**, like /admin/files (#185): a tidying session
 * survives a refresh, and a filtered view can be linked to. The list is paged at 20, the server's
 * default, most recently used first (#171): the controls appear from the second page on.
 */
export function MyFilesPage() {
  const { t, i18n } = useTranslation('files')
  const [searchParams, setSearchParams] = useSearchParams()
  const confirm = useConfirm()

  const page = Number(searchParams.get('page') ?? '0')

  // Whether this account is one of the two that fill a library at all (#241). By role, like the
  // switcher's contexts; the screen below says so rather than 404ing.
  const libraryAccess = useHasPersonalLibrary()

  // And *which* cajones are theirs to use (#237), which is a finer question than the one above and
  // only the server can answer it: it depends on the roles *and* on the tables they run. The search
  // box needs them before it can offer `/file_categories`, so it is asked first.
  const { data: myCategories } = useMyCategories()

  // The search box holds a structured value, but what travels - to the URL and to the API - is the
  // raw string of #164. One list of commands, given to the box, used to read `?q=` and shown in the
  // help (#240); the wiring around it is the shared hook, the same one /admin/files uses.
  const fields = useMemo(() => myFileSearchFields(t, myCategories ?? []), [t, myCategories])
  const search = useSearchQuery({
    fields,
    initialQuery: searchParams.get('q') ?? '',
    onQueryChange: (query) => updateParams({ q: query }),
  })

  // isLoadingError, not isError: a background refetch that fails must not blank a list that already
  // loaded (#150).
  // No `category`: the cajón is one more criterion of the search now, and it travels inside `?q=`
  // like the rest of them (#242).
  const { data, isPending, isLoadingError, refetch } = useMyFiles(search.debouncedQuery || undefined, undefined, page)
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

  const isFiltered = search.debouncedQuery !== ''

  /**
   * **The screen is for players and masters** (#241).
   *
   * A personal library is filled by those two flows — an application, a submission, a table's
   * material. An account that holds neither role, a pure admin or the owner, never fills one, so
   * there is nothing here to search and nothing to upload: what the platform publishes is
   * `/admin/files`, which is a different screen with a different job (#237).
   *
   * **Decided by role**, like the contexts of the switcher: the roles are cumulative, so what is
   * asked is whether they hold `Player` or `Master`, never whether they hold *only* it — an admin who
   * also plays is on the screen, and so is a co-master an admin assigned without the role (#135).
   * Showing or hiding is all it does; authorization stays the backend's, endpoint by endpoint (#103),
   * which is why this is a state on the screen and not a guard on the route.
   */
  if (!libraryAccess.isPending && !libraryAccess.hasPersonalLibrary) {
    return (
      <div className="space-y-6">
        <div className="space-y-1">
          <h1 className="font-serif text-2xl font-semibold">{t('mine.title')}</h1>
        </div>
        <EmptyState title={t('mine.noLibraryTitle')} description={t('mine.noLibraryDescription')} />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-serif text-2xl font-semibold">{t('mine.title')}</h1>
          <p className="text-fg-muted text-sm">{t('mine.description')}</p>
        </div>
        {/* Disabled rather than gone while the cajones are still loading, so it does not appear and
            jump. With none of them the screen never gets this far: it says so above (#241). */}
        <Button
          type="button"
          disabled={myCategories === undefined}
          onClick={() => (uploadPanel.isOpen ? uploadPanel.close() : uploadPanel.open())}
        >
          {uploadPanel.isOpen ? t('mine.uploadClose') : t('mine.upload')}
        </Button>
      </div>

      {/* **The panel stays open after an upload**, and no toast fires. Closing it on success would
          unmount the zone along with the one thing it had to say — that the file was recognised
          rather than stored again (#234) — and a "file uploaded" toast would flatly contradict it.
          The row appearing in the list below is the confirmation; the zone says the rest, and the
          person is left where they are if they have another file to add. */}
      {/* **The one place that asks which cajón** (#233): every other upload happens inside a flow that
          already knows, and this one has no flow to observe. */}
      {uploadPanel.isOpen && myCategories !== undefined && (
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

      {/* **The library is not shown while the upload panel is open.** Uploading and browsing are two
          things somebody is doing one at a time, and leaving the whole library underneath the drop
          zone puts a search box and thirty rows between the person and the button they came for. */}
      {!uploadPanel.isOpen && (
        <>
          {/* The application's one search box (#164): free text searches the filename, and a
              `/campo` searches that field. A plain `<input>` here was the only buscador in the app
              that did not speak the shared language.
              **And it is the only filter on this screen** (#242). The row of cajón toggles that used
              to sit under it said the same thing twice: `/file_categories` already narrows by cajón,
              from the same line as everything else and combinable with the rest, and each row already
              carries its cajón as a badge — so the toggles were a second way to ask one question the
              search answers better, taking the width of the screen to do it. */}
          <SearchQueryInput
            fields={search.fields}
            value={search.value}
            onChange={search.onChange}
            placeholder={t('mine.searchPlaceholder')}
            label={t('mine.searchLabel')}
          />

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
