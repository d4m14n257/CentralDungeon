import { UploadCloudIcon } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import { FILE_CATEGORIES } from '../categories'
import { MAX_FILE_BYTES, rejectionOf } from '../limits'
import { FileCategoryChoice } from './FileCategoryChoice'
import type { FileCategory, StagedFile } from '../types'

interface FileDropzoneProps {
  /**
   * Called with the file that was picked, **which is not on the server yet** (#238).
   *
   * Nothing uploads here any more: the confirm that creates the table, sends the answer or publishes
   * the request is what uploads, through `useCommitStagedFiles`. Picking a file stages it, and
   * abandoning the flow leaves nothing behind.
   */
  onStaged: (staged: StagedFile) => void
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
 * @param props.onStaged       called with the file that was picked, not yet on the server
 * @param props.isBusy         true while the caller is acting on the result
 * @param props.askForCategory whether to ask which cajón it goes in — only where there is no flow
 * @param props.categories     which cajones to offer when it asks; only the actor's own (#237)
 */
export function FileDropzone({ onStaged, isBusy = false, askForCategory = false, categories = FILE_CATEGORIES }: FileDropzoneProps) {
  const { t } = useTranslation('files')
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)

  const [isOver, setIsOver] = useState(false)
  // The first one offered, so the select never opens on something this person may not use.
  const [category, setCategory] = useState<FileCategory | null>(null)
  const chosen = category ?? categories[0] ?? null
  const [error, setError] = useState<string | null>(null)

  const isPending = isBusy

  /**
   * Takes a file in, or refuses it on the spot.
   *
   * **The cap and the whitelist are checked here now**, which they have to be: with the upload
   * deferred to the confirm, the server's refusal would arrive after four wizard steps. The rule is
   * the same rule and the message is the same message - `rejectionOf` returns the error code the
   * backend would have used - so a file refused now and one refused later read identically (#197).
   */
  function handleFile(file: File) {
    const rejection = rejectionOf(file)
    if (rejection !== null) {
      setError(t([`errors.${rejection}`, 'errors.uploadFailed'], { maxBytes: MAX_FILE_BYTES, sizeBytes: file.size }))
      return
    }
    setError(null)
    onStaged({ kind: 'new', localId: crypto.randomUUID(), name: file.name, file })
  }

  return (
    <div className="space-y-3">
      {askForCategory && chosen !== null && (
        <div className="space-y-2">
          <p className="text-sm font-medium">{t('dropzone.categoryLabel')}</p>
          <FileCategoryChoice
            value={chosen}
            onChange={setCategory}
            options={categories}
            label={t('dropzone.categoryLabel')}
            disabled={isPending}
          />
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
          <UploadCloudIcon className="size-6" />
        </span>
        <span className="text-sm font-medium">{t('dropzone.prompt')}</span>
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
            // Cleared immediately: nothing is in flight, and keeping the value would stop the same
            // file from firing a change event again after being removed from the list.
            event.target.value = ''
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
    </div>
  )
}
