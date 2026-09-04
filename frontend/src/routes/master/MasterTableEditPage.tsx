import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'

import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { RichTextEditor } from '@/components/RichTextEditor'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { helpPath, masterTableDetailPath } from '@/config/paths'
import { CatalogPicker } from '@/features/catalogs'
import { ScheduleEditor, createGameTableSchema, useManagedTable, useTableTypes, useUpdateTable } from '@/features/tables'
import type { CreateGameTableForm, GameTableStatus, TableScheduleEntry } from '@/features/tables'
import { useMe } from '@/features/users'
import { browserTimeZone, localInputToUtcIso, utcIsoToLocalInput } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'
import { ApiError } from '@/types/api'

/** The two states where the backend still accepts a rewrite of the table (#189). */
const EDITABLE_STATUSES: GameTableStatus[] = ['Preparation', 'ChangesRequested']

/**
 * `/master/tables/:id/edit` — rewriting a table that has not gone public yet.
 *
 * **One page with sections, not the wizard's four steps.** The steps exist so that somebody who has
 * decided nothing is not asked fifteen values at once; somebody correcting a draft already has all
 * fifteen and needs to see at a glance what changes. It is the same fields and the same components,
 * laid out for a different question.
 *
 * **The save replaces the table, it does not patch it** (#189), and the agenda and the catalogs are
 * replaced as whole sets (#190). That is why every field is sent on every save, including the ones
 * nobody touched: a field left out would empty itself.
 *
 * It is the only way to answer a `ChangesRequested` — the resubmit button on the status tab moves
 * the table, this is what makes the change the admin asked for.
 */
export function MasterTableEditPage() {
  const { t } = useTranslation('master')
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const tableId = id ?? ''
  // useManagedTable and not useGameTable: the backend checks membership before reading anything and
  // answers 403 when the actor does not run this table (#152).
  const { data: table, isPending, error, isLoadingError, refetch } = useManagedTable(tableId)
  const { data: me } = useMe()
  const { data: tableTypes } = useTableTypes()
  const updateTable = useUpdateTable(tableId)

  const [systems, setSystems] = useState<CatalogValue[]>([])
  const [tags, setTags] = useState<CatalogValue[]>([])
  const [platforms, setPlatforms] = useState<CatalogValue[]>([])
  const [schedule, setSchedule] = useState<TableScheduleEntry[]>([])

  // #22 took `users.timezone` out of the model, so the browser is the only source today; `lib/date.ts`
  // takes the zone as a parameter so a profile preference would change this line and no other (#111).
  const timeZone = useMemo(() => browserTimeZone(), [])

  const form = useForm<CreateGameTableForm>({
    resolver: zodResolver(createGameTableSchema),
    defaultValues: { name: '', description: '', permitted: '', requirements: '', duration: '' },
  })

  // The form is filled from the server's answer rather than rendered off it: react-hook-form owns
  // the values from here on, and re-seeding it on every refetch would throw away what is being typed.
  const { reset } = form
  useEffect(() => {
    if (!table) return
    reset({
      name: table.name,
      description: table.description ?? '',
      permitted: table.permitted ?? '',
      requirements: table.requirements ?? '',
      tableTypeId: tableTypes?.content.find((type) => type.name === table.tableTypeName)?.id ?? '',
      startDate: utcIsoToLocalInput(table.startDate, timeZone),
      duration: table.duration ? table.duration.slice(0, 5) : '',
      maxPlayers: table.maxPlayers === null ? '' : String(table.maxPlayers),
      totalSessions: table.totalSessions === null ? '' : String(table.totalSessions),
    })
    setSystems(table.systems)
    setTags(table.tags)
    setPlatforms(table.platforms)
    setSchedule(table.schedule)
  }, [table, tableTypes, timeZone, reset])

  if (isPending) {
    return <Skeleton className="h-96 w-full" />
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  if (isLoadingError || !table) {
    return <ErrorState onRetry={() => void refetch()} />
  }

  const isPrimary = table.masters.some((master) => master.userId === me?.id && master.masterType === 'Primary')

  // The backend refuses the rewrite past Preparation and ChangesRequested, and only from the table's
  // master. Painting a form that is guaranteed to fail would be worse than saying why it is closed.
  if (!isPrimary || !EDITABLE_STATUSES.includes(table.status)) {
    return <ForbiddenState description={t('edit.lockedDescription')} />
  }

  function onSubmit(values: CreateGameTableForm) {
    updateTable.mutate(
      {
        name: values.name,
        description: values.description ? values.description : null,
        permitted: values.permitted ? values.permitted : null,
        requirements: values.requirements ? values.requirements : null,
        tableTypeId: values.tableTypeId ? values.tableTypeId : null,
        systemIds: systems.map((value) => value.id),
        tagIds: tags.map((value) => value.id),
        platformIds: platforms.map((value) => value.id),
        startDate: values.startDate ? localInputToUtcIso(values.startDate, timeZone) : null,
        duration: values.duration ? values.duration : null,
        maxPlayers: values.maxPlayers ? Number(values.maxPlayers) : null,
        totalSessions: values.totalSessions ? Number(values.totalSessions) : null,
        schedule,
      },
      {
        onSuccess: () => {
          toast.success(t('edit.success'))
          void navigate(masterTableDetailPath(tableId))
        },
      },
    )
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div className="space-y-1">
        <h1 className="font-serif text-2xl font-semibold">{t('edit.title', { name: table.name })}</h1>
        <p className="text-fg-muted text-sm">{t('edit.description')}</p>
      </div>

      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-6">
          <section className="space-y-5" aria-label={t('edit.identitySection')}>
            <h2 className="font-serif text-lg font-semibold">{t('edit.identitySection')}</h2>
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
              name="tableTypeId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('create.tableTypeLabel')}</FormLabel>
                  <Select value={field.value ?? ''} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t('create.tableTypePlaceholder')} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(tableTypes?.content ?? []).map((type) => (
                        <SelectItem key={type.id} value={type.id}>
                          {type.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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
                    <RichTextEditor value={field.value ?? ''} onChange={field.onChange} ariaLabel={t('create.descriptionLabel')} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="permitted"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('create.permittedLabel')}</FormLabel>
                  <FormControl>
                    <RichTextEditor value={field.value ?? ''} onChange={field.onChange} ariaLabel={t('create.permittedLabel')} />
                  </FormControl>
                  <FormDescription>{t('create.permittedHint')}</FormDescription>
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
                    <RichTextEditor value={field.value ?? ''} onChange={field.onChange} ariaLabel={t('create.requirementsLabel')} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </section>

          <Separator />

          <section className="space-y-4" aria-label={t('edit.catalogsSection')}>
            <h2 className="font-serif text-lg font-semibold">{t('edit.catalogsSection')}</h2>
            <CatalogPicker kind="systems" label={t('create.systemsLabel')} selected={systems} onChange={setSystems} />
            <CatalogPicker kind="tags" label={t('create.tagsLabel')} selected={tags} onChange={setTags} />
            <CatalogPicker kind="platforms" label={t('create.platformsLabel')} selected={platforms} onChange={setPlatforms} />
          </section>

          <Separator />

          <section className="space-y-4" aria-label={t('edit.scheduleSection')}>
            <h2 className="font-serif text-lg font-semibold">{t('edit.scheduleSection')}</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="startDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('create.startDateLabel')}</FormLabel>
                    <FormControl>
                      <Input type="datetime-local" {...field} value={field.value ?? ''} />
                    </FormControl>
                    <FormDescription>{t('create.startDateHint')}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="duration"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('create.durationLabel')}</FormLabel>
                    <FormControl>
                      <Input type="time" {...field} value={field.value ?? ''} />
                    </FormControl>
                    <FormDescription>{t('create.durationHint')}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-baseline justify-between gap-3">
                <p className="text-sm font-medium">{t('create.scheduleLabel')}</p>
                {/* The help is linked from the screen that needs it, by its #ref (#167, #168). */}
                <Link to={helpPath('masters', 'schedule')} className="text-fg-muted hover:text-fg text-xs underline">
                  {t('create.scheduleHelp')}
                </Link>
              </div>
              <ScheduleEditor value={schedule} onChange={setSchedule} timeZone={timeZone} duration={form.watch('duration')} />
            </div>
          </section>

          <Separator />

          <section className="space-y-4" aria-label={t('edit.capacitySection')}>
            <h2 className="font-serif text-lg font-semibold">{t('edit.capacitySection')}</h2>
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
          </section>

          <p className="text-fg-subtle text-xs">
            {t('edit.replaceNotice')}{' '}
            <Link to={helpPath('masters', 'edit-table')} className="underline underline-offset-2">
              {t('edit.helpLink')}
            </Link>
          </p>

          <div className="flex items-center justify-between gap-3">
            <Button asChild type="button" variant="outline">
              <Link to={masterTableDetailPath(tableId)}>{t('edit.cancel')}</Link>
            </Button>
            <Button type="submit" disabled={updateTable.isPending}>
              {updateTable.isPending ? t('edit.saving') : t('edit.save')}
            </Button>
          </div>
        </form>
      </Form>
    </div>
  )
}

export { MasterTableEditPage as Component }
