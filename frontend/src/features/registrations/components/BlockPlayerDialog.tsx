import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useBlockPlayer } from '../api/useBlockPlayer'
import { useRequestPlayerBlock } from '../api/useRequestPlayerBlock'
import { useUnblockPlayer } from '../api/useUnblockPlayer'
import { blockRegistrationSchema, type BlockRegistrationForm } from '../schemas'
import { vetoErrorKey } from '../vetoErrors'

/** Which of the two directions the veto is being moved in. Who the reader is decides the rest. */
export type VetoAction = 'block' | 'unblock'

interface BlockPlayerDialogProps {
  tableId: string
  registrationId: string
  playerName: string
  /** Vetoing or lifting a veto. */
  action: VetoAction
  /**
   * Whether the reader runs the table or co-runs it (#71).
   *
   * **It changes what pressing the button does, not whether it is offered**: a `Primary` vetoes, a
   * `Secondary` files a request the `Primary` answers (#39). The dialog says which of the two is
   * about to happen — before the press, which is what `fase-3-admin-owner.md:169` asks for.
   */
  isPrimary: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of what this act does, shown above the note.
   *
   * A node rather than a `HelpLink` raised in here: `features/help` is another feature and a feature
   * never imports one (arquitectura.md §3.1.5). The screen composes the two.
   */
  help?: ReactNode
}

/**
 * The dialog behind the three acts of the veto: applying one, asking for one, and lifting one.
 *
 * **One component for three acts because they are one shape** — name the person, read what is about
 * to happen, write why, send — and the note is obligatory on all three (#39). Splitting them would
 * be three copies of the same form whose wording would drift apart.
 *
 * **It is not `JustifiedTableActionDialog`**, which is the identical shape one feature over. A
 * feature never imports from another (regla dura 16), and this form belongs to whoever owns the
 * registration. The shared component of `components/` is the move if a third feature ever needs it
 * (§3.1.2); two is not yet that.
 *
 * **The refusal lands inline and not in a toast** (#197): the form is still open, and the races this
 * cannot prevent — a colleague vetoed the same person, somebody lifted it first — are exactly what
 * the reader needs to read where they are already looking.
 *
 * @param props.tableId        the table the veto belongs to — it is per table, never platform-wide (#29)
 * @param props.registrationId the application being acted on
 * @param props.playerName     who, for the title
 * @param props.action         vetoing or lifting
 * @param props.isPrimary      whether this ends in a veto or in a request for one
 * @param props.open           whether the dialog is showing
 * @param props.onOpenChange   called to open or close it
 * @param props.help           the help link the screen hands down
 */
export function BlockPlayerDialog({
  tableId,
  registrationId,
  playerName,
  action,
  isPrimary,
  open,
  onOpenChange,
  help,
}: BlockPlayerDialogProps) {
  const { t } = useTranslation('registrations')
  const block = useBlockPlayer(tableId)
  const requestBlock = useRequestPlayerBlock(tableId)
  const unblock = useUnblockPlayer(tableId)

  const form = useForm<BlockRegistrationForm>({
    resolver: zodResolver(blockRegistrationSchema),
    defaultValues: { justification: '' },
  })

  // Lifting is a Primary-only act and the screen never offers it to anybody else, so the three cases
  // collapse to the two the reader can actually reach.
  const isRequest = action === 'block' && !isPrimary
  const mutation = action === 'unblock' ? unblock : isRequest ? requestBlock : block
  const variant = action === 'unblock' ? 'unblock' : isRequest ? 'request' : 'block'

  function handleOpenChange(next: boolean) {
    if (!next) form.reset({ justification: '' })
    onOpenChange(next)
  }

  function onSubmit(values: BlockRegistrationForm) {
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
          {/* What the act actually costs the other person, written out where the decision is made.
              For a co-master it is the sentence that says this is a request and not the veto — the
              second place it is said, the first being the button itself. */}
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
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
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
