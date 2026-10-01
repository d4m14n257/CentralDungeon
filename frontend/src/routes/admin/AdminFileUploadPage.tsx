import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { adminFilesPath } from '@/config/paths'
import { useConfirm } from '@/hooks/useConfirm'
import {
  FileDropzone,
  PublishCategorySelect,
  StagedFileList,
  stagedKey,
  useUploadToLibrary,
  type FileCategory,
  type StagedFile,
} from '@/features/files'

/**
 * /admin/files/upload — putting files into the platform's library (#278).
 *
 * **Its own page and not a panel toggled open over the list.** Uploading and browsing are two things
 * an admin does one at a time, and a page is what the rest of the application uses for "go do this
 * one thing and come back": it has its own URL, the browser's back button leaves it, and the list
 * underneath does not have to carry the state of a half-finished upload.
 *
 * **The upload list is the same one every flow uses** (`StagedFileList`, #238), with one addition: a
 * select on each row saying what that file is. Each file is its own document — the blank sheet is
 * table material, the rules are an announcement — so they can go up together, each into its own
 * cajón, and nothing lands in the library without saying which flow it is for.
 *
 * **Two ways to send, and both are confirmed** (#282). «Subir sin publicar» leaves the files in the
 * library for later; «Subir y publicar» puts them in front of masters at once — and since a table that
 * attaches a file keeps it even if it is hidden afterwards (#79), the confirmation says so before it
 * happens, not after.
 *
 * Nothing is sent until one of the two is confirmed (#238). What failed stays listed with its cajón,
 * to try again; when everything went up, the page goes back to the library, where the new rows are.
 */
export function AdminFileUploadPage() {
  const { t } = useTranslation('files')
  const navigate = useNavigate()
  const confirm = useConfirm()
  const [staged, setStaged] = useState<StagedFile[]>([])
  // Keyed by the staged entry, so removing a file takes its choice with it.
  const [categories, setCategories] = useState<Record<string, FileCategory>>({})
  // Only after a first attempt: a select nobody has reached yet is not an error.
  const [showMissing, setShowMissing] = useState(false)
  const upload = useUploadToLibrary()

  const missing = staged.filter((entry) => categories[stagedKey(entry)] === undefined)

  function remove(key: string) {
    setStaged((current) => current.filter((entry) => stagedKey(entry) !== key))
    setCategories(({ [key]: _dropped, ...rest }) => rest)
  }

  /**
   * Sends what is staged, published or not, once the admin confirmed what that does. Every file has
   * to say what it is first; the missing ones are marked instead of asking to confirm an upload that
   * could not go through.
   */
  async function send(publish: boolean) {
    if (missing.length > 0) {
      setShowMissing(true)
      return
    }
    const count = staged.length
    const confirmed = await confirm(
      publish
        ? {
            title: t('upload.confirmPublishTitle', { count }),
            description: t('upload.confirmPublishDescription', { count }),
            confirmLabel: t('upload.sendAndPublish'),
          }
        : {
            title: t('upload.confirmDraftTitle', { count }),
            description: t('upload.confirmDraftDescription', { count }),
            confirmLabel: t('upload.sendDraft'),
          },
    )
    if (!confirmed) return
    upload.mutate(
      { uploads: staged.map((entry) => ({ staged: entry, category: categories[stagedKey(entry)]! })), publish },
      {
        onSuccess: ({ published, failed, reused }) => {
          if (published.length > 0) {
            toast.success(t(publish ? 'upload.published' : 'upload.savedDraft', { count: published.length }))
          }
          // Reuse is worth saying (#234): the admin already had that content, so no second copy exists.
          if (reused.length > 0) {
            toast.info(t('upload.reused', { names: reused.join(', ') }))
          }
          if (failed.length > 0) {
            toast.error(t('upload.someFailed', { names: failed.join(', ') }))
            setStaged(staged.filter((entry) => failed.includes(entry.name)))
            return
          }
          void navigate(adminFilesPath())
        },
      },
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('upload.title')}
        description={t('upload.description')}
        back={{ to: adminFilesPath(), label: t('upload.back') }}
        help="admins.files"
      />

      <FileDropzone multiple onStaged={(file) => setStaged((current) => [...current, file])} isBusy={upload.isPending} />

      <StagedFileList
        files={staged}
        onRemove={remove}
        renderControls={(entry) => (
          <PublishCategorySelect
            value={categories[stagedKey(entry)] ?? null}
            onChange={(category) => setCategories((current) => ({ ...current, [stagedKey(entry)]: category }))}
            fileName={entry.name}
            invalid={showMissing && categories[stagedKey(entry)] === undefined}
            disabled={upload.isPending}
          />
        )}
      />

      {showMissing && missing.length > 0 && <p className="inline-error">{t('upload.missingCategory', { count: missing.length })}</p>}

      <div className="flex justify-end gap-2">
        <Button variant="ghost" asChild>
          <Link to={adminFilesPath()}>{t('upload.cancel')}</Link>
        </Button>
        <Button type="button" variant="outline" disabled={staged.length === 0 || upload.isPending} onClick={() => void send(false)}>
          {t('upload.sendDraft')}
        </Button>
        <Button type="button" disabled={staged.length === 0 || upload.isPending} onClick={() => void send(true)}>
          {t('upload.sendAndPublish')}
        </Button>
      </div>
    </div>
  )
}

export { AdminFileUploadPage as Component }
