import { CheckCircle2Icon, LoaderCircleIcon, UploadCloudIcon } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { ApiError } from '@/types/api'

import { useUploadFile } from '../api/useUploadFile'
import { FILE_CATEGORIES } from '../categories'
import type { FileCategory, StoredFile } from '../types'

interface FileDropzoneProps {
  /**
   * Called with the file once it is on the server — **whether it was written or recognised**. To
   * whoever is uploading, the two are the same outcome; the difference only decides what the zone
   * says about it (#75, #234).
   */
  onUploaded: (file: StoredFile) => void
  /** True while the caller is doing something with the result, to keep the zone from firing twice. */
  isBusy?: boolean
  /**
   * Whether to ask which cajón the file goes in (#233).
   *
   * **False everywhere but `/my/files`**, and that is the whole point of the redesign: in a flow the
   * system already knows — attaching to a table makes it table material, answering a request makes it
   * a submission — so asking would be asking somebody to declare what cannot be declared. Only the
   * library has no flow to observe, and only there is the question honest.
   */
  askForCategory?: boolean
  /**
   * Which cajones to offer when it asks (#237). Only somebody's own: a plain member of the community
   * gets the two player-side ones, whoever runs a table also gets the two master-side ones, and
   * `Announcement` is nobody's — it lives only in the platform's library.
   */
  categories?: readonly FileCategory[]
}

/**
 * Putting a file on the server: drop it, or click to pick one.
 *
 * **This replaces a bare `<input type="file">`**, and the three things it adds are the three things
 * that were missing rather than decoration:
 *
 * - **The limits are stated before they are broken.** A cap somebody only discovers by hitting it
 *   reads as a bug rather than a rule (principio 2 de frontend-diseno.md §1).
 * - **A failure is answered where the person is looking.** The error sits under the zone and stays
 *   there while they pick another file — a toast that vanishes in four seconds is the wrong place
 *   for "that type is not accepted". The mutation opts out of the global toast through
 *   `meta.showsItsOwnError` so the same sentence is not delivered twice.
 * - **Deduplication is said out loud** (#234). Uploading something this person already had returns
 *   the row they had, and until the status told the two apart it happened in complete silence —
 *   which made the cheapest lever of #75 invisible to the only person who could learn from it.
 *
 * **A real `<input>`, hidden but focusable**, with the surrounding area as its `<label>`. A `<div>`
 * with an `onClick` looks identical and cannot be reached by keyboard, and the file dialog is the one
 * thing a browser will not open for a synthetic click from an untrusted context.
 *
 * **It does not ask what the file is** (#233). The flow it was uploaded from is what classifies it,
 * and in the four real flows that answer is already known — so the cajón select appears only when the
 * caller says there is no flow to observe, which today is `/my/files` and nothing else.
 *
 * @param props.onUploaded     called with the file once it is on the server
 * @param props.isBusy         true while the caller is acting on the result
 * @param props.askForCategory whether to ask which cajón it goes in — only where there is no flow
 * @param props.categories     which cajones to offer when it asks; only the actor's own (#237)
 */
export function FileDropzone({ onUploaded, isBusy = false, askForCategory = false, categories = FILE_CATEGORIES }: FileDropzoneProps) {
  const { t } = useTranslation('files')
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)

  const [isOver, setIsOver] = useState(false)
  // The first one offered, so the select never opens on something this person may not use.
  const [category, setCategory] = useState<FileCategory | null>(null)
  const chosen = category ?? categories[0] ?? null
  const [error, setError] = useState<string | null>(null)
  const [reused, setReused] = useState<string | null>(null)

  const upload = useUploadFile()
  const isPending = upload.isPending || isBusy

  function handleFile(file: File) {
    setError(null)
    setReused(null)
    upload.mutate(
      // `Private` and not `SingleUse`: somebody who took the trouble to upload a character sheet
      // will want it on the next table, and the history of #65 only works if it has anything in it.
      // No cajón unless the caller has none to infer: in a flow the link that follows classifies it.
      { file, input: { fileType: 'Private', fileCategory: askForCategory ? chosen : null } },
      {
        onSuccess: ({ file: uploaded, deduplicated }) => {
          if (deduplicated) {
            setReused(uploaded.name)
          }
          onUploaded(uploaded)
        },
        onError: (failure) => {
          setError(
            failure instanceof ApiError
              ? t([`errors.${failure.problem.errorCode}`, 'errors.uploadFailed'], { ...failure.problem.errorParams })
              : t('errors.uploadFailed'),
          )
        },
        // Cleared on failure too, not only on success: the input keeps the rejected file otherwise,
        // and picking the same one again fires no change event at all — so somebody who fixes what
        // the message told them to fix would find the control silently dead.
        onSettled: () => {
          if (input.current) {
            input.current.value = ''
          }
        },
      },
    )
  }

  return (
    <div className="space-y-3">
      {askForCategory && chosen !== null && (
        <div className="space-y-2">
          <Label htmlFor={`${inputId}-category`}>{t('dropzone.categoryLabel')}</Label>
          <Select value={chosen} onValueChange={(value) => setCategory(value as FileCategory)} disabled={isPending}>
            <SelectTrigger id={`${inputId}-category`}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {categories.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`category.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-fg-muted text-xs">{t('dropzone.categoryHint')}</p>
        </div>
      )}

      <label
        htmlFor={inputId}
        onDragOver={(event) => {
          event.preventDefault()
          setIsOver(true)
        }}
        onDragLeave={() => setIsOver(false)}
        onDrop={(event) => {
          event.preventDefault()
          setIsOver(false)
          const dropped = event.dataTransfer.files[0]
          if (dropped && !isPending) {
            handleFile(dropped)
          }
        }}
        className={cn(
          'border-border flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-4 py-10 text-center transition-colors',
          isOver && 'border-primary bg-muted',
          isPending && 'pointer-events-none opacity-60',
        )}
      >
        <span aria-hidden="true" className="text-fg-muted">
          {upload.isPending ? <LoaderCircleIcon className="size-6 animate-spin" /> : <UploadCloudIcon className="size-6" />}
        </span>
        <span className="text-sm font-medium">{upload.isPending ? t('dropzone.uploading') : t('dropzone.prompt')}</span>
        <span className="text-fg-muted text-xs">{t('dropzone.limits')}</span>
        <input
          ref={input}
          id={inputId}
          type="file"
          className="sr-only"
          disabled={isPending}
          onChange={(event) => {
            const picked = event.target.files?.[0]
            if (picked) {
              handleFile(picked)
            }
          }}
        />
      </label>

      {/* `alert` and not a plain paragraph: somebody using a screen reader dropped a file and needs
          to be told it was refused without going looking for the reason. */}
      {error && (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      )}

      {reused && (
        <p className="text-fg-muted flex items-center gap-1.5 text-sm">
          <CheckCircle2Icon aria-hidden="true" className="size-4 shrink-0" />
          {t('dropzone.reused', { name: reused })}
        </p>
      )}
    </div>
  )
}
