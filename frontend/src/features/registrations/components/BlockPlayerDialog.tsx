import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useRequestPlayerBlock } from '../api/useRequestPlayerBlock'
import { useUnblockPlayer } from '../api/useUnblockPlayer'
import { blockRegistrationSchema, type BlockRegistrationForm } from '../schemas'
import { vetoErrorKey } from '../vetoErrors'
import { useConfirm } from '@/hooks/useConfirm'

/** Which of the two acts the dialog performs: asking the admins for a veto, or lifting one. */
export type VetoAction = 'request' | 'unblock'

interface BlockPlayerDialogProps {
  tableId: string
  registrationId: string
  playerName: string
  /** Asking for a veto or lifting one. */
  action: VetoAction
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of what this act does, shown above the note.
   *
   * A node rather than a `HelpLink` raised in here: `features/help` is another feature and a feature
   * never imports one. The screen composes the two.
   */
  help?: ReactNode
}

/**
 * The dialog behind the two acts of the veto: asking the admins for one, and lifting one.
 *
 * **One component for two acts because they are one shape** — name the person, read what is about
 * to happen, write why, send — and the reason is obligatory on both. Splitting them would be two
 * copies of the same form whose wording would drift apart. **Nobody vetoes from here**: asking opens
 * a request an admin decides, and nothing changes for the person meanwhile.
 *
 * **It is not `JustifiedTableActionDialog`**, which is the identical shape one feature over. A
 * feature never imports from another, and this form belongs to whoever owns the
 * registration. The shared component of `components/` is the move if a third feature ever needs it.
 *
 * **The refusal lands inline and not in a toast**: the form is still open, and the races this
 * cannot prevent — a colleague already asked, somebody lifted it first — are exactly what the reader
 * needs to read where they are already looking.
 *
 * @param props.tableId        the table the veto belongs to — it is per table, never platform-wide
 * @param props.registrationId the application being acted on
 * @param props.playerName     who, for the title
 * @param props.action         asking for a veto or lifting one
 * @param props.open           whether the dialog is showing
 * @param props.onOpenChange   called to open or close it
 * @param props.help           the help link the screen hands down
 */
export function BlockPlayerDialog({ tableId, registrationId, playerName, action, open, onOpenChange, help }: BlockPlayerDialogProps) {
  const { t } = useTranslation('registrations')
  const confirm = useConfirm()
  const requestBlock = useRequestPlayerBlock(tableId)
  const unblock = useUnblockPlayer(tableId)

  const form = useForm<BlockRegistrationForm>({
    resolver: zodResolver(blockRegistrationSchema),
    defaultValues: { justification: '' },
  })

  const variant = action
  const mutation = action === 'unblock' ? unblock : requestBlock

  function handleOpenChange(next: boolean) {
    if (!next) form.reset({ justification: '' })
    onOpenChange(next)
  }

  async function onSubmit(values: BlockRegistrationForm) {
    // A review before anything is written: what is about to change, and what follows.
    const confirmed = await confirm({
      title: t(`veto.${variant}ConfirmTitle`, { name: playerName }),
      description: t(`veto.${variant}ConfirmDescription`),
    })
    if (!confirmed) return
    mutation.mutate(
      { registrationId, justification: values.justification },
      {
        onSuccess: () => {
          toast.success(t(`veto.${variant}Success`, { name: playerName }))
          form.reset({ justification: '' })
          onOpenChange(false)
        },
      },
    )
  }

  const errorKey = vetoErrorKey(mutation.error)

  return (
    <FormDialog
      isDirty={form.formState.isDirty}
      open={open}
      onOpenChange={handleOpenChange}
      title={t(`veto.${variant}DialogTitle`, { name: playerName })}
      description={t(`veto.${variant}DialogDescription`)}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* What the act does, written out where the decision is made. For a request it is the
              sentence that says nothing changes yet — the second place it is said, the first being
              the button itself. */}
          <p className="border-border text-fg-muted rounded-lg border border-dashed px-4 py-3 text-sm">{t(`veto.${variant}Effect`)}</p>
          {help}
          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('veto.reasonLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {errorKey !== null && (
            <p role="alert" className="inline-error">
              {t(errorKey)}
            </p>
          )}
          <Button
            type="submit"
            variant={variant === 'unblock' ? 'default' : 'destructive'}
            disabled={mutation.isPending}
            className="w-full"
          >
            {t(`veto.${variant}Submit`)}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
