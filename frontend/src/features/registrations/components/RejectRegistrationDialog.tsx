import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

import { useRejectRegistration } from '../api/useRejectRegistration'
import { rejectRegistrationSchema, type RejectRegistrationForm } from '../schemas'
import { useConfirm } from '@/hooks/useConfirm'

interface RejectRegistrationDialogProps {
  tableId: string
  registrationId: string
  candidateName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * The dialog a master turns down a candidate from. The justification is required, and it reaches the
 * applicant verbatim in their notification.
 *
 * @param props.registrationId the application being rejected
 * @param props.tableId        the table, for the cache invalidation
 * @param props.open           whether the dialog is showing
 * @param props.onOpenChange   called to open or close it
 */
export function RejectRegistrationDialog({ tableId, registrationId, candidateName, open, onOpenChange }: RejectRegistrationDialogProps) {
  const { t } = useTranslation('registrations')
  const confirm = useConfirm()
  const rejectRegistration = useRejectRegistration(tableId)
  const form = useForm<RejectRegistrationForm>({
    resolver: zodResolver(rejectRegistrationSchema),
    defaultValues: { justification: '' },
  })

  async function onSubmit(values: RejectRegistrationForm) {
    // A review before anything is written (#283): what is about to change, and what follows.
    const confirmed = await confirm({
      title: t('candidates.rejectConfirmTitle', { name: candidateName }),
      description: t('candidates.rejectConfirmDescription'),
    })
    if (!confirmed) return
    rejectRegistration.mutate(
      { registrationId, justification: values.justification },
      {
        onSuccess: () => {
          toast.success(t('candidates.rejectSuccess'))
          form.reset()
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      isDirty={form.formState.isDirty}
      open={open}
      onOpenChange={onOpenChange}
      title={t('candidates.rejectDialogTitle', { name: candidateName })}
      description={t('candidates.rejectDialogDescription')}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('candidates.rejectReasonLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" variant="destructive" disabled={rejectRegistration.isPending} className="w-full">
            {t('candidates.reject')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
