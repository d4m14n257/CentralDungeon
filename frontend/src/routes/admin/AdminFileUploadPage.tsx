import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { adminFilesPath } from '@/config/paths'
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
 * cajón, and uploading is publishing: nothing lands in the library without saying which flow it is
 * for.
 *
 * Nothing is sent until «Subir» (#238). What failed stays listed with its cajón, to try again; when
 * everything went up, the page goes back to the library, where the new rows are.
 */
export function AdminFileUploadPage() {
  const { t } = useTranslation('files')
  const navigate = useNavigate()
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

  function send() {
    if (missing.length > 0) {
      setShowMissing(true)
      return
    }
    upload.mutate(
      staged.map((entry) => ({ staged: entry, category: categories[stagedKey(entry)]! })),
      {
        onSuccess: ({ published, failed, reused }) => {
          if (published.length > 0) {
            toast.success(t('upload.published', { count: published.length }))
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
        actions={
          <Button variant="outline" asChild>
            <Link to={adminFilesPath()}>
              <ArrowLeft className="size-4" aria-hidden="true" />
              {t('upload.back')}
            </Link>
          </Button>
        }
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
        <Button type="button" disabled={staged.length === 0 || upload.isPending} onClick={send}>
          {t('upload.send', { count: staged.length })}
        </Button>
      </div>
    </div>
  )
}

export { AdminFileUploadPage as Component }
