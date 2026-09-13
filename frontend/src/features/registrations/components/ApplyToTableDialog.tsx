import { zodResolver } from '@hookform/resolvers/zod'
import { XIcon } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { IconAction } from '@/components/IconAction'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { stagedKey, type CommitStagedFiles, type StagedFile } from '@/types/file'

import { useApplyToTable } from '../api/useApplyToTable'
import { createRegistrationSchema, type CreateRegistrationForm } from '../schemas'

/** Which half of the dialog is showing. */
type ApplyStep = 'write' | 'review'

export interface ApplyToTableDialogProps {
  tableId: string
  tableName: string
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * How to render the file picker, given the callback that records a pick.
   *
   * A render prop because the picker belongs to `features/files` and a feature never imports from
   * another: the screen composing the two — `routes/player/TableDetailPage.tsx` — is where they meet
   * (regla dura 16, §3.1.5).
   */
  renderFilePicker: (onPick: (file: StagedFile) => void) => ReactNode
  /**
   * Uploads what was staged and hands back the ids (#238).
   *
   * A prop and not an import, for the same reason `renderFilePicker` is one: `features/registrations`
   * may never import `features/files` (regla dura 16), and the screen that composes both is the one
   * that wires them. Nothing reaches the server until the review step's confirm calls this.
   */
  commitStagedFiles: CommitStagedFiles
}

/**
 * The dialog that wraps an application. **Two steps, and the second is not decoration.**
 *
 * An application, once sent, cannot be edited and cannot have a file taken off it afterwards (#238,
 * #247): there is no `PUT` on a registration and no endpoint that detaches one of its files. Once the
 * mutation below succeeds, the character sheet that went with it is attached forever. The review step
 * is the only barrier between somebody and that mistake — it shows exactly the message and the file
 * names about to be sent, with nothing left to edit once the button that sends them exists.
 *
 * **Attaching does not upload** (#238). Picking a file in the write step only stages it in the
 * browser; the confirm button on the review step is what uploads it, through
 * {@link ApplyToTableDialogProps.commitStagedFiles}. Closing the dialog after a pick leaves nothing
 * on the server.
 *
 * **A file that fails to upload does not block the application.** It is sent with whatever else did
 * upload, and the applicant is told which name failed — there is no way to add a file to an
 * application that already exists, so the only remedy is applying again.
 *
 * @param props.tableId           the table being applied to
 * @param props.tableName         its name, for the dialog's title
 * @param props.open              whether the dialog is showing
 * @param props.onOpenChange      called to open or close it
 * @param props.renderFilePicker  how to render the file picker
 * @param props.commitStagedFiles uploads the staged files and returns their ids
 */
export function ApplyToTableDialog({
  tableId,
  tableName,
  open,
  onOpenChange,
  renderFilePicker,
  commitStagedFiles,
}: ApplyToTableDialogProps) {
  const { t } = useTranslation('tables')
  const applyToTable = useApplyToTable(tableId)
  const [step, setStep] = useState<ApplyStep>('write')
  const [files, setFiles] = useState<StagedFile[]>([])
  const [isSending, setIsSending] = useState(false)
  const form = useForm<CreateRegistrationForm>({
    resolver: zodResolver(createRegistrationSchema),
    defaultValues: { description: '' },
  })

  // Cleared on open rather than on close: clearing on close would wipe the fields while the dialog
  // is still fading out, and a dialog that kept the previous attempt would offer to send it again by
  // accident (same reasoning as `TaskSubmitDialog`).
  useEffect(() => {
    if (!open) return
    form.reset({ description: '' })
    setFiles([])
    setStep('write')
    // form.reset is stable across renders; re-running this only on open/tableId keeps it from firing
    // on every keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, tableId])

  /** Moves to the review step, unless the message written so far is invalid. */
  async function goToReview() {
    const valid = await form.trigger()
    if (valid) {
      setStep('review')
    }
  }

  function removeFile(key: string) {
    setFiles((current) => current.filter((file) => stagedKey(file) !== key))
  }

  async function send(values: CreateRegistrationForm) {
    // A step that is not the review one never submits: Enter in a text field fires the form's native
    // submit, and without this guard it would send the application while somebody is still writing
    // their message. The confirm button only exists on the review step; this covers the keyboard.
    if (step !== 'review') {
      return
    }
    setIsSending(true)
    // Nothing to upload is nothing to await: skipping the round trip when the applicant attached
    // nothing is the common case, and it is what most applications will be (#238).
    const { fileIds, failed } = files.length > 0 ? await commitStagedFiles(files) : { fileIds: [], failed: [] }
    applyToTable.mutate(
      { description: values.description ? values.description : null, fileIds },
      {
        onSuccess: () => {
          setIsSending(false)
          if (failed.length > 0) {
            // The application still went out with whatever did upload — losing it over one bad file
            // would be worse than the missing file, and there is no way to add to it afterwards, so
            // this says exactly which name to try again with in a fresh application.
            toast.error(t('detail.applyFilesFailed', { names: failed.join(', ') }))
          } else {
            toast.success(t('detail.applySuccess'))
          }
          onOpenChange(false)
        },
        onError: () => setIsSending(false),
      },
    )
  }

  const written = form.watch('description')
  const isBusy = isSending || applyToTable.isPending

  return (
    <FormDialog
      isDirty={form.formState.isDirty || files.length > 0}
      open={open}
      onOpenChange={onOpenChange}
      title={t('detail.applyDialogTitle', { name: tableName })}
      description={step === 'write' ? t('detail.applyDialogDescription') : t('detail.applyReviewDescription')}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(send)(event)} className="space-y-4">
          {step === 'write' && (
            <>
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('detail.applyDescriptionLabel')}</FormLabel>
                    <FormControl>
                      <Textarea rows={4} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className="space-y-2">
                <p className="text-sm font-medium">{t('detail.applyFilesLabel')}</p>
                {files.length > 0 && (
                  <ul className="divide-border divide-y">
                    {files.map((file) => (
                      <li key={stagedKey(file)} className="flex items-center justify-between gap-2 py-2 text-sm">
                        <span className="truncate">{file.name}</span>
                        <IconAction
                          label={t('detail.applyRemoveFile')}
                          icon={<XIcon className="size-4" />}
                          onClick={() => removeFile(stagedKey(file))}
                        />
                      </li>
                    ))}
                  </ul>
                )}
                {renderFilePicker((file) =>
                  setFiles((current) => (current.some((picked) => stagedKey(picked) === stagedKey(file)) ? current : [...current, file])),
                )}
              </div>

              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  {t('detail.applyCancel')}
                </Button>
                <Button key="next" type="button" onClick={() => void goToReview()}>
                  {t('detail.applyNext')}
                </Button>
              </div>
            </>
          )}

          {step === 'review' && (
            <>
              <section className="space-y-3">
                <div className="space-y-1">
                  <p className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.applyDescriptionLabel')}</p>
                  <p className="text-sm whitespace-pre-wrap">{written ? written : t('detail.applyReviewNoMessage')}</p>
                </div>

                <Separator />

                <div className="space-y-1">
                  <p className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('detail.applyFilesLabel')}</p>
                  {files.length === 0 ? (
                    <p className="text-sm">{t('detail.applyReviewNoFiles')}</p>
                  ) : (
                    <ul className="text-sm">
                      {files.map((file) => (
                        <li key={stagedKey(file)}>{file.name}</li>
                      ))}
                    </ul>
                  )}
                </div>
              </section>

              <div className="flex items-center justify-between gap-2">
                <Button type="button" variant="outline" onClick={() => setStep('write')} disabled={isBusy}>
                  {t('detail.applyBack')}
                </Button>
                <Button key="submit" type="submit" disabled={isBusy}>
                  {isBusy ? t('detail.applySubmitting') : t('detail.apply')}
                </Button>
              </div>
            </>
          )}
        </form>
      </Form>
    </FormDialog>
  )
}
