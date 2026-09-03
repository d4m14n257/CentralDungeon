import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ForbiddenState } from '@/components/ForbiddenState'
import { masterTableDetailPath } from '@/config/paths'
import { createGameTableSchema, useCreateTable, type CreateGameTableForm } from '@/features/tables'
import { useMe } from '@/features/users'

/**
 * Wizard de creación (frontend-diseno.md sitemap, /master/tables/new) - un formulario en un paso,
 * no un stepper multi-pantalla: el sitemap no detalla pasos y no hay otro wizard en el código para
 * seguirle la forma (arquitectura.md 2.4). Fecha/agenda quedan fuera: table_schedules llega con el wizard completo de F1.
 */
export function MasterTableCreatePage() {
  const { t } = useTranslation('master')
  const navigate = useNavigate()
  const createTable = useCreateTable()
  const { data: me } = useMe()
  const form = useForm<CreateGameTableForm>({
    resolver: zodResolver(createGameTableSchema),
    defaultValues: { name: '', description: '', requirements: '' },
  })

  // Crear exige el rol de plataforma Master, no solo pertenencia (#135) - el backend ya lo rechaza
  // con 403, pero mostrar un formulario que siempre va a fallar sería peor que no mostrar nada.
  if (me && !me.roles.includes('Master')) {
    return <ForbiddenState />
  }

  function onSubmit(values: CreateGameTableForm) {
    createTable.mutate(
      {
        name: values.name,
        description: values.description ? values.description : null,
        requirements: values.requirements ? values.requirements : null,
        maxPlayers: values.maxPlayers ? Number(values.maxPlayers) : null,
        totalSessions: values.totalSessions ? Number(values.totalSessions) : null,
      },
      {
        onSuccess: (table) => {
          toast.success(t('create.success'))
          void navigate(masterTableDetailPath(table.id))
        },
      },
    )
  }

  return (
    <div className="max-w-xl space-y-6">
      <div className="space-y-1">
        <h1 className="font-serif text-2xl font-semibold">{t('create.title')}</h1>
        <p className="text-fg-muted text-sm">{t('create.description')}</p>
      </div>
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('create.nameLabel')}</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('create.descriptionLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={4} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="requirements"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('create.requirementsLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid grid-cols-2 gap-4">
            <FormField
              control={form.control}
              name="maxPlayers"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('create.maxPlayersLabel')}</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} {...field} value={field.value ?? ''} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="totalSessions"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('create.totalSessionsLabel')}</FormLabel>
                  <FormControl>
                    <Input type="number" min={1} {...field} value={field.value ?? ''} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <p className="text-fg-subtle text-xs">{t('create.reviewNotice')}</p>
          <Button type="submit" disabled={createTable.isPending} className="w-full">
            {createTable.isPending ? t('create.submitting') : t('create.submit')}
          </Button>
        </form>
      </Form>
    </div>
  )
}

export { MasterTableCreatePage as Component }
