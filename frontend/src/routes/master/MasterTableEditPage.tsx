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
import { masterTableDetailPath } from '@/config/paths'
import { HelpLink } from '@/features/help'
import { CatalogPicker } from '@/features/catalogs'
import { useUnsavedChangesGuard } from '@/hooks/useUnsavedChanges'
import {
  DEFAULT_SLOT_DURATION,
  ScheduleEditor,
  WeeklyScheduleGrid,
  createGameTableSchema,
  tableTypeLabel,
  useManagedTable,
  useMySchedule,
  useTableTypes,
  useUpdateTable,
  MASTER_EDITABLE_STATUSES,
} from '@/features/tables'
import type { CreateGameTableForm, TableScheduleEntry } from '@/features/tables'
import { useMe } from '@/features/users'
import { WEEKDAYS, browserTimeZone, formatMinutes, minutesOfDay } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'
import { ApiError } from '@/types/api'

/** The two states where the backend still accepts a rewrite of the table (#189). */

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
  // The type's and the week's words belong to the tables domain (regla dura 18).
  const { t: tTables } = useTranslation('tables')
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
  // The reader's other commitments, so rewriting an agenda is looking and not guessing (#227). This
  // table is filtered out: it cannot clash with itself, and drawing it twice would say it did.
  const myWeek = useMySchedule()
  const pendingBlocks = schedule.map((entry) => ({
    startMinute: WEEKDAYS.indexOf(entry.weekday) * 24 * 60 + minutesOfDay(entry.hourtime),
    durationMinutes: minutesOfDay(entry.duration ?? DEFAULT_SLOT_DURATION),
  }))

  /** Adds the hour the master clicked, in UTC, unless this table already claimed it (#228). */
  function claimHour(utcStartMinute: number) {
    const weekday = WEEKDAYS[Math.floor(utcStartMinute / (24 * 60))]
    const hourtime = formatMinutes(utcStartMinute % (24 * 60))
    if (!weekday || schedule.some((entry) => entry.weekday === weekday && entry.hourtime.slice(0, 5) === hourtime)) {
      return
    }
    setSchedule([...schedule, { weekday, hourtime, duration: DEFAULT_SLOT_DURATION }])
  }

  /**
   * The table as it came from the server, in the shape the form takes.
   *
   * Recomputed rather than stored: it is what the form is seeded with **and** what "unchanged"
   * means, and two copies of that would drift.
   */
  const loadedValues = useMemo<CreateGameTableForm | undefined>(() => {
    if (!table) {
      return undefined
    }
    return {
      name: table.name,
      description: table.description ?? '',
      permitted: table.permitted ?? '',
      requirements: table.requirements ?? '',
      // By code when the application shipped the type, by name when a person created it (#225):
      // matching on the label alone broke as soon as the label started being translated.
      tableTypeId:
        tableTypes?.content.find((type) => (table.tableTypeCode ? type.code === table.tableTypeCode : type.name === table.tableTypeName))
          ?.id ?? '',
      // A plain day, straight into the control: nothing to convert (#230).
      startDate: table.startDate ?? '',
      maxPlayers: table.maxPlayers === null ? '' : String(table.maxPlayers),
      totalSessions: table.totalSessions === null ? '' : String(table.totalSessions),
    }
  }, [table, tableTypes])

  /**
   * Seeded with `values` and not with a `reset` in an effect.
   *
   * **That effect was silently dropping the table type.** It ran the moment the table arrived, which
   * is before a single field has registered - and the `Select` behind `tableTypeId` is a
   * `Controller`, so when it did mount it overwrote what the reset had put there. The screen then
   * showed the placeholder for a table that had a type, and saving wrote that emptiness back.
   *
   * `keepDirtyValues` is what the old effect was really after: a refetch refreshes the fields nobody
   * is editing and leaves the ones somebody is.
   */
  const form = useForm<CreateGameTableForm>({
    resolver: zodResolver(createGameTableSchema),
    // **Every field, not only the text ones.** A field missing from here registers as `''`
    // against a default of `undefined`, which react-hook-form reads as dirty - and on the edit
    // page `keepDirtyValues` then defended that emptiness against the value the table actually
    // had, which is how the table type was being silently dropped (#231).
    defaultValues: {
      name: '',
      description: '',
      permitted: '',
      requirements: '',
      tableTypeId: '',
      startDate: '',
      maxPlayers: '',
      totalSessions: '',
    },
    // Spread rather than passed as `undefined`: `exactOptionalPropertyTypes` draws a line between
    // "no values yet" and "values are undefined", and react-hook-form only accepts the first.
    ...(loadedValues ? { values: loadedValues } : {}),
    // A refetch refreshes the fields nobody is editing and leaves the ones somebody is - which is
    // what the old `reset` in an effect was trying to protect by hand.
    resetOptions: { keepDirtyValues: true },
  })

  // "Changed" means changed from what the table said, which `formState.isDirty` cannot answer here:
  // the fields left out of `defaultValues` read as dirty the moment they register, and a page nobody
  // had touched refused to be left. `watch` and not `getValues` so it is recomputed on every
  // keystroke - the blocker only ever sees what the last render put in its closure (#231).
  const written = form.watch()
  const formChanged =
    loadedValues !== undefined &&
    (Object.keys(loadedValues) as (keyof CreateGameTableForm)[]).some((key) => (written[key] ?? '') !== (loadedValues[key] ?? ''))

  // The catalogs and the agenda are compared against the table too, and for the same reason the
  // form is: a flag set from their `onChange` looked simpler and was wrong, because the pickers emit
  // once while they settle and a page nobody had touched refused to be left (#231).
  const catalogsOrAgendaChanged =
    table !== undefined &&
    (!sameCatalog(systems, table.systems) ||
      !sameCatalog(tags, table.tags) ||
      !sameCatalog(platforms, table.platforms) ||
      !sameAgenda(schedule, table.schedule))

  const { allowNextNavigation } = useUnsavedChangesGuard(formChanged || catalogsOrAgendaChanged)

  // Only what lives outside the form still needs seeding by hand.
  useEffect(() => {
    if (!table) return
    setSystems(table.systems)
    setTags(table.tags)
    setPlatforms(table.platforms)
    setSchedule(table.schedule)
  }, [table])

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
  if (!isPrimary || !MASTER_EDITABLE_STATUSES.includes(table.status)) {
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
        startDate: values.startDate ? values.startDate : null,
        maxPlayers: values.maxPlayers ? Number(values.maxPlayers) : null,
        totalSessions: values.totalSessions ? Number(values.totalSessions) : null,
        schedule,
      },
      {
        onSuccess: () => {
          // Saving and then leaving is not walking out on the work.
          allowNextNavigation()
          toast.success(t('edit.success'))
          void navigate(masterTableDetailPath(tableId))
        },
      },
    )
  }

  return (
    // Centred rather than hugging the left: these two are forms, and a form read against one
    // edge of a wide screen is a column of text with a desert next to it (#228).
    <div className="mx-auto max-w-2xl space-y-6">
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
                  {/*
                    **The empty emission is dropped on purpose.** Radix's Select reports a change
                    when its controlled value goes from empty to something after mount, and wiring
                    that straight into `field.onChange` wrote `''` back over the id the table
                    actually had - the screen then showed the placeholder for a classified table and
                    saving stored that emptiness. A person can never select nothing here: every
                    option carries an id, so an empty value is only ever the control talking to
                    itself (#231).
                  */}
                  <Select value={field.value ?? ''} onValueChange={(value) => value && field.onChange(value)}>
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t('create.tableTypePlaceholder')} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {(tableTypes?.content ?? []).map((type) => (
                        <SelectItem key={type.id} value={type.id}>
                          {tableTypeLabel(tTables, type.code, type.name)}
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
                      <Input type="date" {...field} value={field.value ?? ''} />
                    </FormControl>
                    <FormDescription>{t('create.startDateHint')}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-baseline justify-between gap-3">
                <p className="text-sm font-medium">{t('create.scheduleLabel')}</p>
                {/* The help is linked from the screen that needs it, by its #ref (#167, #168). */}
                <HelpLink section="masters.schedule" className="text-xs">
                  {t('create.scheduleHelp')}
                </HelpLink>
              </div>
              <ScheduleEditor value={schedule} onChange={setSchedule} timeZone={timeZone} />

              {/* The same week the wizard offers (#227, #228): the master rewriting an agenda needs
                  to see what else they gave away just as much as the one building it does. */}
              <div className="space-y-2 pt-2">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h3 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{tTables('schedule.inWizardTitle')}</h3>
                  <p className="text-fg-muted text-xs">{tTables('schedule.inWizardDescription')}</p>
                </div>
                {myWeek.isPending ? (
                  <Skeleton className="h-64 w-full" />
                ) : (
                  <WeeklyScheduleGrid
                    commitments={(myWeek.data ?? []).filter((commitment) => commitment.tableId !== tableId)}
                    timeZone={timeZone}
                    pending={pendingBlocks}
                    onPickHour={claimHour}
                  />
                )}
              </div>
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
            {t('edit.replaceNotice')} <HelpLink section="masters.edit-table">{t('edit.helpLink')}</HelpLink>
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

/** Two sets of catalog values are the same when they hold the same ids, in the same order. */
function sameCatalog(a: CatalogValue[], b: CatalogValue[]): boolean {
  return a.length === b.length && a.every((value, index) => value.id === b[index]?.id)
}

/** Two agendas are the same when they hold the same slots, in the same order. */
function sameAgenda(a: TableScheduleEntry[], b: TableScheduleEntry[]): boolean {
  return (
    a.length === b.length &&
    a.every((slot, index) => {
      const other = b[index]
      return (
        other !== undefined &&
        slot.weekday === other.weekday &&
        slot.hourtime.slice(0, 5) === other.hourtime.slice(0, 5) &&
        (slot.duration ?? '').slice(0, 5) === (other.duration ?? '').slice(0, 5)
      )
    })
  )
}

export { MasterTableEditPage as Component }
