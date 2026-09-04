import { XIcon } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { FormDialog } from '@/components/FormDialog'
import { IconAction } from '@/components/IconAction'
import { RichTextEditor } from '@/components/RichTextEditor'
import { Button } from '@/components/ui/button'

import type { ApplicableTask, CreateSubmissionInput } from '../types'

/** A file the person picked, as this dialog needs to show it back before sending. */
export interface PickedFile {
  fileId: string
  name: string
}

export interface TaskSubmitDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The task being answered. Its shape decides what the form offers. */
  task: ApplicableTask
  /** Whether the request is in flight. */
  isBusy: boolean
  /**
   * How to render the file picker, given the callback that records a pick.
   *
   * A render prop because the picker belongs to `features/files` and a feature never imports from
   * another: the screen composing the two is where they meet (regla dura 16, §3.1.5).
   */
  renderFilePicker: (onPick: (file: PickedFile) => void) => ReactNode
  onSubmit: (input: CreateSubmissionInput) => void
}

/**
 * Handing in an answer to what a table asked.
 *
 * **The form offers only the channels the request opened**: a task that takes files and not text has
 * no editor at all, rather than an editor that is refused on send. What the master asked for is what
 * the person is shown.
 *
 * **Sending never replaces anything** (#76). The dialog says so, because "entregar" reads like
 * "reemplazar lo anterior" unless something says otherwise — and what actually happens is that the
 * earlier answer stays exactly where it was, next to this one.
 *
 * @param props.task             the task being answered
 * @param props.isBusy           whether the request is in flight
 * @param props.renderFilePicker how to render the file picker
 * @param props.onSubmit         called with the answer to hand in
 */
export function TaskSubmitDialog({ open, onOpenChange, task, isBusy, renderFilePicker, onSubmit }: TaskSubmitDialogProps) {
  const { t } = useTranslation('tasks')
  const [content, setContent] = useState('')
  const [files, setFiles] = useState<PickedFile[]>([])

  // Cleared on open rather than on close: a dialog that keeps the previous answer would offer to
  // send it again by accident, and clearing on close would wipe the fields while they fade out.
  useEffect(() => {
    if (!open) return
    setContent('')
    setFiles([])
  }, [open, task.taskId])

  const hasText = task.acceptsText && content.trim() !== '' && content.trim() !== '<p></p>'
  const canSend = hasText || files.length > 0

  function send() {
    onSubmit({
      content: hasText ? content : null,
      fileIds: files.map((file) => file.fileId),
    })
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('submit.title', { title: task.title })}
      description={t('submit.description')}
    >
      <div className="space-y-4">
        {task.acceptsText && (
          <div className="space-y-2">
            <RichTextEditor value={content} onChange={setContent} ariaLabel={t('submit.contentLabel')} />
          </div>
        )}

        {task.acceptsFiles && (
          <div className="space-y-2">
            <p className="text-sm font-medium">{t('submit.filesLabel')}</p>
            {files.length > 0 && (
              <ul className="divide-border divide-y">
                {files.map((file) => (
                  <li key={file.fileId} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <span className="truncate">{file.name}</span>
                    <IconAction
                      label={t('submit.removeFile')}
                      icon={<XIcon className="size-4" />}
                      onClick={() => setFiles((current) => current.filter((picked) => picked.fileId !== file.fileId))}
                    />
                  </li>
                ))}
              </ul>
            )}
            {/* Uploading a new one and reusing one from the history end in the same place (#65). */}
            {renderFilePicker((file) =>
              setFiles((current) => (current.some((picked) => picked.fileId === file.fileId) ? current : [...current, file])),
            )}
          </div>
        )}

        {/* #76 said before the button is pressed, not discovered afterwards. */}
        <p className="text-fg-muted text-xs">{t('submit.accumulatesHint')}</p>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('form.cancel')}
          </Button>
          <Button type="button" disabled={isBusy || !canSend} onClick={send}>
            {t('submit.send')}
          </Button>
        </div>
      </div>
    </FormDialog>
  )
}
