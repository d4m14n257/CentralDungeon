import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { FormDialog } from '@/components/FormDialog'
import { RichTextEditor } from '@/components/RichTextEditor'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { browserTimeZone, localInputToUtcIso, utcIsoToLocalInput } from '@/lib/date'

import { taskFormSchema, type TaskForm } from '../schemas'
import type { CreateTaskInput, TableTask, TaskAudience } from '../types'

/** How a person to address is offered to the picker: an id and a name, nothing else. */
export interface TaskFormRecipient {
  userId: string
  userName: string
}

/** One of the table's sessions, as the "tie it to an evening" selector offers it. */
export interface TaskFormSession {
  id: string
  sequenceNumber: number
}

export interface TaskFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The task being corrected, or null when publishing a new one. */
  task?: TableTask | null
  /**
   * The table's players, for addressing a task to one of them.
   *
   * A prop and not a query of this feature's own: the roster belongs to `registrations`, and a
   * feature never imports from another — the screen composing them is where they meet (regla dura
   * 16, §3.1.5).
   */
  players: TaskFormRecipient[]
  /** The table's sessions, for tying the ask to one evening (#63). Same reason it is a prop. */
  sessions: TaskFormSession[]
  /** Whether the request is in flight, so the submit button can say so. */
  isBusy: boolean
  onSubmit: (input: CreateTaskInput) => void
}

const AUDIENCES: TaskAudience[] = ['Candidates', 'Players', 'Single']

/**
 * Publishing a task, and correcting one. One dialog for both, because they take exactly the same
 * fields: a correction is a full replacement, not a patch (#189).
 *
 * **The form says what publishing will do.** Sending it notifies every recipient (#77), and
 * correcting deliberately does not — a request fixed three times ringing three times is how people
 * learn to ignore the bell. Both are said on screen rather than left to be discovered.
 *
 * The due date is typed and shown **in the reader's own zone** and travels as UTC (#22, #111); the
 * conversion is `lib/date.ts`'s, with the zone as a parameter and never a constant (#192).
 *
 * @param props.task     the task being corrected, or null when publishing
 * @param props.players  the table's players, for a task addressed to one person
 * @param props.sessions the table's sessions, for tying the ask to one evening
 * @param props.isBusy   whether the request is in flight
 * @param props.onSubmit called with what should be published or replaced
 */
export function TaskFormDialog({ open, onOpenChange, task, players, sessions, isBusy, onSubmit }: TaskFormDialogProps) {
  const { t } = useTranslation('tasks')
  const timeZone = browserTimeZone()

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<TaskForm>({
    resolver: zodResolver(taskFormSchema),
    defaultValues: emptyForm(),
  })

  // The dialog is mounted once and reused, so the values are pushed in when it opens rather than
  // through a `key` on the element: a form that keeps the previous task's title is the bug this
  // avoids, and remounting would throw away the focus ring mid-interaction.
  useEffect(() => {
    if (!open) return
    reset(task ? formOf(task, timeZone) : emptyForm())
  }, [open, task, timeZone, reset])

  const audience = watch('audience')
  const description = watch('description') ?? ''
  const acceptsText = watch('acceptsText')
  const acceptsFiles = watch('acceptsFiles')
  const isMandatory = watch('isMandatory')

  function submit(values: TaskForm) {
    onSubmit({
      title: values.title,
      description: values.description?.trim() ? values.description : null,
      audience: values.audience,
      targetUserId: values.audience === 'Single' ? (values.targetUserId ?? null) : null,
      tableSessionId: values.tableSessionId ? values.tableSessionId : null,
      acceptsText: values.acceptsText,
      acceptsFiles: values.acceptsFiles,
      isMandatory: values.isMandatory,
      dueAt: values.dueAtLocal ? localInputToUtcIso(values.dueAtLocal, timeZone) : null,
    })
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={task ? t('form.editTitle') : t('form.publishTitle')}
      description={task ? t('form.editDescription') : t('form.publishDescription')}
    >
      <form className="space-y-4" onSubmit={handleSubmit(submit)}>
        <div className="space-y-2">
          <Label htmlFor="task-title">{t('form.titleLabel')}</Label>
          <Input id="task-title" {...register('title')} aria-invalid={Boolean(errors.title)} />
          {errors.title && <p className="text-destructive text-xs">{t('form.titleRequired')}</p>}
        </div>

        <div className="space-y-2">
          <Label htmlFor="task-description">{t('form.descriptionLabel')}</Label>
          <RichTextEditor
            value={description}
            onChange={(html) => setValue('description', html, { shouldDirty: true })}
            ariaLabel={t('form.descriptionLabel')}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="task-audience">{t('form.audienceLabel')}</Label>
          <Select value={audience} onValueChange={(value) => setValue('audience', value as TaskAudience)}>
            <SelectTrigger id="task-audience">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {AUDIENCES.map((option) => (
                <SelectItem key={option} value={option}>
                  {t(`audience.${option}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-fg-muted text-xs">{t(`form.audienceHint.${audience}`)}</p>
        </div>

        {/* Only offered for a Single task, and only the table's own players: addressing material to
            somebody who does not play here would land in an inbox with no screen behind it. */}
        {audience === 'Single' && (
          <div className="space-y-2">
            <Label htmlFor="task-target">{t('form.targetLabel')}</Label>
            {players.length === 0 ? (
              <p className="text-fg-muted text-xs">{t('form.targetNoPlayers')}</p>
            ) : (
              <Select value={watch('targetUserId') ?? ''} onValueChange={(value) => setValue('targetUserId', value)}>
                <SelectTrigger id="task-target" aria-invalid={Boolean(errors.targetUserId)}>
                  <SelectValue placeholder={t('form.targetPlaceholder')} />
                </SelectTrigger>
                <SelectContent>
                  {players.map((player) => (
                    <SelectItem key={player.userId} value={player.userId}>
                      {player.userName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
            {errors.targetUserId && <p className="text-destructive text-xs">{t('form.targetRequired')}</p>}
          </div>
        )}

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t('form.answerLabel')}</legend>
          <div className="flex items-center gap-2">
            <Checkbox
              id="task-accepts-text"
              checked={acceptsText}
              onCheckedChange={(checked) => setValue('acceptsText', checked === true)}
            />
            <Label htmlFor="task-accepts-text">{t('form.acceptsText')}</Label>
          </div>
          <div className="flex items-center gap-2">
            <Checkbox
              id="task-accepts-files"
              checked={acceptsFiles}
              onCheckedChange={(checked) => setValue('acceptsFiles', checked === true)}
            />
            <Label htmlFor="task-accepts-files">{t('form.acceptsFiles')}</Label>
          </div>
          {errors.acceptsText && <p className="text-destructive text-xs">{t('form.answerChannelRequired')}</p>}
        </fieldset>

        {sessions.length > 0 && (
          <div className="space-y-2">
            <Label htmlFor="task-session">{t('form.sessionLabel')}</Label>
            <Select
              value={watch('tableSessionId') ?? 'none'}
              onValueChange={(value) => setValue('tableSessionId', value === 'none' ? '' : value)}
            >
              <SelectTrigger id="task-session">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">{t('form.sessionNone')}</SelectItem>
                {sessions.map((session) => (
                  <SelectItem key={session.id} value={session.id}>
                    {t('form.sessionOption', { number: session.sequenceNumber })}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <div className="space-y-2">
          <Label htmlFor="task-due">{t('form.dueLabel')}</Label>
          <Input id="task-due" type="datetime-local" {...register('dueAtLocal')} />
          <p className="text-fg-muted text-xs">{t('form.dueHint', { timeZone })}</p>
        </div>

        <div className="flex items-start gap-2">
          <Checkbox id="task-mandatory" checked={isMandatory} onCheckedChange={(checked) => setValue('isMandatory', checked === true)} />
          <div className="space-y-1">
            <Label htmlFor="task-mandatory">{t('form.mandatoryLabel')}</Label>
            {/* #70 said in the interface, where it matters: labelling it does not make the system act. */}
            <p className="text-fg-muted text-xs">{t('form.mandatoryHint')}</p>
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('form.cancel')}
          </Button>
          <Button type="submit" disabled={isBusy}>
            {task ? t('form.save') : t('form.publish')}
          </Button>
        </div>
      </form>
    </FormDialog>
  )
}

/** A blank form: text and files both open, which is what most requests want. */
function emptyForm(): TaskForm {
  return {
    title: '',
    description: '',
    audience: 'Players',
    targetUserId: '',
    tableSessionId: '',
    acceptsText: true,
    acceptsFiles: true,
    isMandatory: false,
    dueAtLocal: '',
  }
}

/** An existing task as form values, with its due date brought back into the reader's zone (#22). */
function formOf(task: TableTask, timeZone: string): TaskForm {
  return {
    title: task.title,
    description: task.description ?? '',
    audience: task.audience,
    targetUserId: task.targetUserId ?? '',
    tableSessionId: task.tableSessionId ?? '',
    acceptsText: task.acceptsText,
    acceptsFiles: task.acceptsFiles,
    isMandatory: task.isMandatory,
    dueAtLocal: utcIsoToLocalInput(task.dueAt, timeZone),
  }
}
