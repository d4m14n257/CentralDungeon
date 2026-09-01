import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/components/FormDialog'

import { changeTableStatusSchema, type ChangeTableStatusForm } from '../schemas'

interface JustifiedTableActionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  submitLabel: string
  destructive?: boolean
  isPending: boolean
  onConfirm: (justification: string) => void
}

/**
 * Toda transición que exige justificación (Pedir cambios, Cancelar) comparte esta forma - una
 * sola pantalla, sin abstraerla de más: dos usos reales ya (arquitectura.md 2.4).
 */
export function JustifiedTableActionDialog({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  destructive,
  isPending,
  onConfirm,
}: JustifiedTableActionDialogProps) {
  const { t } = useTranslation('master')
  const form = useForm<ChangeTableStatusForm>({
    resolver: zodResolver(changeTableStatusSchema),
    defaultValues: { justification: '' },
  })

  function onSubmit(values: ChangeTableStatusForm) {
    onConfirm(values.justification)
    form.reset()
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('status.justificationLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" variant={destructive ? 'destructive' : 'default'} disabled={isPending} className="w-full">
            {submitLabel}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
