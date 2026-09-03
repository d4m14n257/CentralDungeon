import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/components/FormDialog'

import { useApplyToTable } from '../api/useApplyToTable'
import { createRegistrationSchema, type CreateRegistrationForm } from '../schemas'

interface ApplyToTableDialogProps {
  tableId: string
  tableName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * The dialog that wraps the application form. It owns the mutation, closes on success and reports
 * the outcome; the form inside it only collects and validates (#106, #110).
 *
 * @param props.tableId the table being applied to
 * @param props.open    whether the dialog is showing
 * @param props.onOpenChange called to open or close it
 */
export function ApplyToTableDialog({ tableId, tableName, open, onOpenChange }: ApplyToTableDialogProps) {
  const { t } = useTranslation('tables')
  const applyToTable = useApplyToTable(tableId)
  const form = useForm<CreateRegistrationForm>({
    resolver: zodResolver(createRegistrationSchema),
    defaultValues: { description: '' },
  })

  function onSubmit(values: CreateRegistrationForm) {
    applyToTable.mutate(
      { description: values.description ? values.description : null },
      {
        onSuccess: () => {
          toast.success(t('detail.applySuccess'))
          form.reset()
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('detail.applyDialogTitle', { name: tableName })}
      description={t('detail.applyDialogDescription')}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
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
          <Button type="submit" disabled={applyToTable.isPending} className="w-full">
            {applyToTable.isPending ? t('detail.applySubmitting') : t('detail.apply')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
