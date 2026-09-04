import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/FormDialog'

import { useCreateUnassignedTable } from '../api/useCreateUnassignedTable'

const createUnassignedTableSchema = z.object({ name: z.string().min(1).max(128) })
type CreateUnassignedTableForm = z.infer<typeof createUnassignedTableSchema>

interface CreateUnassignedTableDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** An admin can create a table without running it (#72) - assignMasters is what opens it afterwards. */
export function CreateUnassignedTableDialog({ open, onOpenChange }: CreateUnassignedTableDialogProps) {
  const { t } = useTranslation('admin')
  const createUnassignedTable = useCreateUnassignedTable()
  const form = useForm<CreateUnassignedTableForm>({
    resolver: zodResolver(createUnassignedTableSchema),
    defaultValues: { name: '' },
  })

  function onSubmit(values: CreateUnassignedTableForm) {
    createUnassignedTable.mutate(
      { name: values.name },
      {
        onSuccess: () => {
          toast.success(t('tables.createUnassignedSuccess'))
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
      title={t('tables.createUnassignedDialogTitle')}
      description={t('tables.createUnassignedDialogDescription')}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('tables.nameLabel')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" disabled={createUnassignedTable.isPending} className="w-full">
            {t('tables.createUnassigned')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
