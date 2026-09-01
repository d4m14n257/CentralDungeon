import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormDescription, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/FormDialog'

import { useAssignMasters } from '../api/useAssignMasters'
import { assignMastersSchema, type AssignMastersForm } from '../schemas'

interface AssignMastersDialogProps {
  tableId: string
  tableName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * userId a mano, sin buscador: todavía no existe un endpoint de búsqueda de usuarios
 * (E2 sub-rebanada 5, /admin/users). Se documenta como límite conocido en el artifact de E2.
 */
export function AssignMastersDialog({ tableId, tableName, open, onOpenChange }: AssignMastersDialogProps) {
  const { t } = useTranslation('admin')
  const assignMasters = useAssignMasters()
  const form = useForm<AssignMastersForm>({
    resolver: zodResolver(assignMastersSchema),
    defaultValues: { primaryUserId: '', secondaryUserIds: '' },
  })

  function onSubmit(values: AssignMastersForm) {
    const secondaryUserIds = values.secondaryUserIds
      ? values.secondaryUserIds
          .split(',')
          .map((id) => id.trim())
          .filter((id) => id.length > 0)
      : []
    assignMasters.mutate(
      { tableId, request: { primaryUserId: values.primaryUserId.trim(), secondaryUserIds } },
      {
        onSuccess: () => {
          toast.success(t('tables.assignMastersSuccess'))
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
      title={t('tables.assignMastersDialogTitle', { name: tableName })}
      description={t('tables.assignMastersDialogDescription')}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="primaryUserId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('tables.primaryUserIdLabel')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="secondaryUserIds"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('tables.secondaryUserIdsLabel')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormDescription>{t('tables.secondaryUserIdsDescription')}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" disabled={assignMasters.isPending} className="w-full">
            {t('tables.assignMasters')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
