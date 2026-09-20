import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useSubmitRequest } from '../api/useSubmitRequest'
import { approvalErrorKey } from '../approvalErrors'
import { submitApprovalRequestSchema, type SubmitApprovalRequestForm } from '../schemas'
import type { SubmittableRequestType } from '../types'

interface SubmitRequestDialogProps {
  /**
   * What is being asked for. Fixed by the screen that raised the dialog, never chosen here.
   *
   * **The submittable three and not all five** (F3.4): `TablePause` and `PlayerBan` are raised from
   * their own entity's route, because `POST /requests` carries no `entityId` on purpose. Narrowing
   * it here means a screen that tried to raise one of those from this dialog would not compile.
   */
  type: SubmittableRequestType
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of how a request works, shown above the field.
   *
   * A node rather than a `HelpLink` raised here: `features/help` is another feature and a feature
   * never imports one (arquitectura.md §3.1.5). The screen composes the two, which is how F3.1
   * resolved the same problem.
   */
  help?: ReactNode
}

/**
 * Asking an admin for something (#42).
 *
 * **The kind of request is a prop and not a field.** The form lives on the screen that prompted the
 * question — the master role on your own profile, an open table in the explorer, anything else from
 * the help — so by the time this dialog is open, what is being asked for is already decided
 * (fase-3-admin-owner.md:126). A dropdown here would turn three specific acts into one generic
 * "make a request" screen, which is exactly what that decision refused.
 *
 * **The justification is required, not offered.** It is the whole of what an admin will read: the
 * request carries no other information about why it should be granted, and one with an empty reason
 * is one nobody can decide on. The server enforces it too, with `@NotBlank`.
 *
 * The dialog is never reached with a request of the same kind already waiting: the section that
 * raises it shows the pending one instead of the button. `REQUEST_ALREADY_PENDING` and
 * `MASTER_ROLE_ALREADY_HELD` are still rendered inline, for the race and for whoever arrives by
 * another route.
 *
 * @param props.type         what is being asked for
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.help         the explanation of how a request works
 */
export function SubmitRequestDialog({ type, open, onOpenChange, help }: SubmitRequestDialogProps) {
  const { t } = useTranslation('admin')
  const submit = useSubmitRequest()
  const errorKey = approvalErrorKey(submit.error)

  const form = useForm<SubmitApprovalRequestForm>({
    resolver: zodResolver(submitApprovalRequestSchema),
    defaultValues: { type, justification: '' },
  })

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ type, justification: '' })
      submit.reset()
    }
    onOpenChange(next)
  }

  function onSubmit(values: SubmitApprovalRequestForm) {
    submit.mutate(values, {
      onSuccess: () => {
        toast.success(t('requests.submit.success'))
        handleOpenChange(false)
      },
    })
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t(`requests.submit.${type}.title`)}
      description={t(`requests.submit.${type}.description`)}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* What happens next, said before the button is pressed: an admin reads it, answers with a
              reason of their own, and the answer arrives as a notification. Nobody should have to
              discover the shape of the mechanism by using it. */}
          <ul className="border-border text-fg-muted space-y-1 rounded-lg border border-dashed px-4 py-3 text-sm">
            <li>{t('requests.submit.flowReviewed')}</li>
            <li>{t(`requests.submit.${type}.effect`)}</li>
          </ul>

          {help}

          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('requests.submit.justificationLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={4} placeholder={t(`requests.submit.${type}.placeholder`)} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {errorKey && (
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
              {t(errorKey)}
            </p>
          )}

          <Button type="submit" disabled={submit.isPending} className="w-full">
            {t('requests.submit.action')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
