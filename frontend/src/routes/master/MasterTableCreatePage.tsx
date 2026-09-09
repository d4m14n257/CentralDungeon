import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'

import { ForbiddenState } from '@/components/ForbiddenState'
import { RichTextEditor } from '@/components/RichTextEditor'
import { RichTextView } from '@/components/RichTextView'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { helpPath, masterTableDetailPath } from '@/config/paths'
import { CatalogChip, CatalogPicker } from '@/features/catalogs'
import {
  ScheduleEditor,
  WeeklyScheduleGrid,
  useMySchedule,
  createGameTableSchema,
  tableTypeDescription,
  tableTypeLabel,
  useCreateTable,
  useTableTypes,
  WIZARD_STEPS,
} from '@/features/tables'
import type { CreateGameTableForm, TableScheduleEntry, WizardStep } from '@/features/tables'
import { useMe } from '@/features/users'
import { WEEKDAYS, browserTimeZone, formatMinutes, formatSlot, localInputToUtcIso, minutesOfDay, utcSlotToLocal } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'

/**
 * The create-table wizard, `/master/tables/new` (frontend-diseno.md sitemap).
 *
 * **Four steps, one decision each**: identity → catalogs → agenda and duration → capacity and
 * review. It is not decoration: a complete table asks for fifteen values of five different kinds,
 * and a single form forces somebody to read all of them before answering the first. The last step is
 * the summary, because what is sent goes to an admin for review (#27) and is worth seeing whole.
 *
 * The screen composes: the catalogs come from `features/catalogs` and the agenda from
 * `features/tables`, and neither knows about the other (§3.1.5, regla dura 16).
 *
 * The time is typed in the reader's zone and travels as UTC (#22): the conversion belongs to
 * `lib/date.ts` and happens once, on submit.
 */
export function MasterTableCreatePage() {
  const { t, i18n } = useTranslation('master')
  // A second namespace rather than copying the labels into `master`: the words belong to the
  // tables domain and are the same ones the explorer's card shows (regla dura 18).
  const { t: tTables } = useTranslation('tables')
  const navigate = useNavigate()
  const createTable = useCreateTable()
  const { data: me } = useMe()
  const { data: tableTypes } = useTableTypes()
  const [step, setStep] = useState<WizardStep>('identity')
  const [systems, setSystems] = useState<CatalogValue[]>([])
  const [tags, setTags] = useState<CatalogValue[]>([])
  const [platforms, setPlatforms] = useState<CatalogValue[]>([])
  const [schedule, setSchedule] = useState<TableScheduleEntry[]>([])
  // What the current step is missing, as an i18n key. The backend refuses the same three things
  // (#226); saying it here means the master finds out on the step that can fix it, not after
  // filling in four steps and pressing «Crear mesa».
  const [stepError, setStepError] = useState<string | null>(null)
  // The week as it already is, so the grid can show what is taken before anything is taken again.
  const myWeek = useMySchedule()

  // #22 took `users.timezone` out of the model, so today the browser is the only source. `lib/date.ts`
  // takes the zone as a parameter precisely so that the day a profile preference exists, this line
  // changes and nothing else (#111).
  const timeZone = useMemo(() => browserTimeZone(), [])

  const form = useForm<CreateGameTableForm>({
    resolver: zodResolver(createGameTableSchema),
    defaultValues: { name: '', description: '', permitted: '', requirements: '', duration: '03:00' },
  })

  // Creating requires the Master platform role, not merely membership (#135) - the backend already
  // refuses with a 403, but showing a form that is always going to fail would be worse than showing
  // nothing.
  if (me && !me.roles.includes('Master')) {
    return <ForbiddenState />
  }

  const stepIndex = WIZARD_STEPS.indexOf(step)
  const values = form.getValues()
  const selectedType = tableTypes?.content.find((type) => type.id === values.tableTypeId)
  const selectedTableTypeLabel = selectedType ? tableTypeLabel(tTables, selectedType.code, selectedType.name) : null

  // What this table has claimed so far, drawn on the same grid as everything else. A slot with no
  // duration yet is given an hour so it is visible: the master is choosing when, and the length
  // comes from the field above (#178 measures the interval, so the real one arrives with it).
  const durationMinutes = values.duration ? minutesOfDay(values.duration) : 60
  const pendingBlocks = schedule.map((entry) => ({
    startMinute: WEEKDAYS.indexOf(entry.weekday) * 24 * 60 + minutesOfDay(entry.hourtime),
    durationMinutes,
  }))

  /**
   * Adds the hour the master clicked on the grid, in UTC, unless the table already claimed it.
   * The editor above stays: somebody who would rather type it still can.
   */
  function claimHour(utcStartMinute: number) {
    const weekday = WEEKDAYS[Math.floor(utcStartMinute / (24 * 60))]
    const hourtime = formatMinutes(utcStartMinute % (24 * 60))
    if (!weekday || schedule.some((entry) => entry.weekday === weekday && entry.hourtime.slice(0, 5) === hourtime)) {
      return
    }
    setSchedule([...schedule, { weekday, hourtime }])
    setStepError(null)
  }

  /**
   * What the step the master is on still needs, as an i18n key, or null when it is complete.
   *
   * The capacity step asks for nothing: how many people play and how many sessions are both
   * legitimately undecided while a table is in preparation.
   */
  function whatIsMissing(current: WizardStep): string | null {
    if (current === 'catalogs') {
      if (systems.length === 0) return 'create.missingSystem'
      if (platforms.length === 0) return 'create.missingPlatform'
    }
    if (current === 'schedule' && schedule.length === 0) {
      return 'create.missingSchedule'
    }
    return null
  }

  async function goNext() {
    if (step === 'identity' && !(await form.trigger('name'))) {
      return
    }
    const missing = whatIsMissing(step)
    if (missing) {
      setStepError(missing)
      return
    }
    setStepError(null)
    const next = WIZARD_STEPS[stepIndex + 1]
    if (next) {
      setStep(next)
    }
  }

  function goBack() {
    setStepError(null)
    const previous = WIZARD_STEPS[stepIndex - 1]
    if (previous) {
      setStep(previous)
    }
  }

  function onSubmit(formValues: CreateGameTableForm) {
    // A wizard is not submitted from any step: Enter in a text field fires the form's native submit,
    // and without this guard it would create the table while somebody is still typing the name. The
    // submit button only exists on the last step; this covers the keyboard.
    if (step !== 'capacity') {
      return
    }
    // Belt and braces for the path back: somebody can reach the last step, go back, empty the
    // catalogs or the agenda and come forward again. goNext catches that, and so does this.
    const missing = WIZARD_STEPS.map(whatIsMissing).find(Boolean)
    if (missing) {
      setStepError(missing)
      return
    }
    createTable.mutate(
      {
        name: formValues.name,
        description: formValues.description ? formValues.description : null,
        permitted: formValues.permitted ? formValues.permitted : null,
        requirements: formValues.requirements ? formValues.requirements : null,
        tableTypeId: formValues.tableTypeId ? formValues.tableTypeId : null,
        systemIds: systems.map((value) => value.id),
        tagIds: tags.map((value) => value.id),
        platformIds: platforms.map((value) => value.id),
        startDate: formValues.startDate ? localInputToUtcIso(formValues.startDate, timeZone) : null,
        duration: formValues.duration ? formValues.duration : null,
        maxPlayers: formValues.maxPlayers ? Number(formValues.maxPlayers) : null,
        totalSessions: formValues.totalSessions ? Number(formValues.totalSessions) : null,
        schedule,
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
    <div className="max-w-2xl space-y-6">
      <div className="space-y-1">
        <h1 className="font-serif text-2xl font-semibold">{t('create.title')}</h1>
        <p className="text-fg-muted text-sm">{t('create.description')}</p>
      </div>

      <ol className="flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label={t('create.stepsLabel')}>
        {WIZARD_STEPS.map((name, index) => (
          <li
            key={name}
            aria-current={name === step ? 'step' : undefined}
            className={index === stepIndex ? 'text-brand-fg font-medium' : 'text-fg-subtle'}
          >
            {index + 1}. {t(`create.steps.${name}`)}
          </li>
        ))}
      </ol>

      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-5">
          {step === 'identity' && (
            <>
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
                        {(tableTypes?.content ?? []).map((type) => {
                          const description = tableTypeDescription(tTables, type.code, type.description)
                          return (
                            <SelectItem key={type.id} value={type.id}>
                              {tableTypeLabel(tTables, type.code, type.name)}
                              {description && <span className="text-fg-subtle ml-2 text-xs">{description}</span>}
                            </SelectItem>
                          )
                        })}
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
            </>
          )}

          {step === 'catalogs' && (
            <>
              <CatalogPicker kind="systems" label={t('create.systemsLabel')} selected={systems} onChange={setSystems} />
              <CatalogPicker kind="tags" label={t('create.tagsLabel')} selected={tags} onChange={setTags} />
              <CatalogPicker kind="platforms" label={t('create.platformsLabel')} selected={platforms} onChange={setPlatforms} />
            </>
          )}

          {step === 'schedule' && (
            <>
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
                <ScheduleEditor value={schedule} onChange={setSchedule} timeZone={timeZone} duration={values.duration} />

                {/* The week the master already gave away, so finding room is looking and not guessing
                    (#227). Clicking a free hour claims it: the gap and taking it are one gesture. */}
                <div className="space-y-2">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h3 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{tTables('schedule.inWizardTitle')}</h3>
                    <p className="text-fg-muted text-xs">{tTables('schedule.inWizardDescription')}</p>
                  </div>
                  {myWeek.isPending ? (
                    <Skeleton className="h-64 w-full" />
                  ) : (
                    <WeeklyScheduleGrid
                      commitments={myWeek.data ?? []}
                      timeZone={timeZone}
                      pending={pendingBlocks}
                      onPickHour={claimHour}
                    />
                  )}
                </div>
              </div>
            </>
          )}

          {step === 'capacity' && (
            <>
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

              <Separator />

              <section className="space-y-3" aria-label={t('create.reviewTitle')}>
                <h2 className="font-serif text-lg font-semibold">{t('create.reviewTitle')}</h2>
                <SummaryRow label={t('create.nameLabel')} value={values.name} />
                <SummaryRow label={t('create.tableTypeLabel')} value={selectedTableTypeLabel ?? t('create.reviewEmpty')} />
                <SummaryChips label={t('create.systemsLabel')} values={systems} empty={t('create.reviewEmpty')} />
                <SummaryChips label={t('create.tagsLabel')} values={tags} empty={t('create.reviewEmpty')} />
                <SummaryChips label={t('create.platformsLabel')} values={platforms} empty={t('create.reviewEmpty')} />
                <SummaryRow
                  label={t('create.scheduleLabel')}
                  value={
                    schedule.length === 0
                      ? t('create.reviewEmpty')
                      : schedule.map((entry) => formatSlot(utcSlotToLocal(entry, timeZone), i18n.language, values.duration)).join(' · ')
                  }
                />
                {values.description && (
                  <div className="space-y-1">
                    <p className="text-fg-subtle text-xs tracking-wide uppercase">{t('create.descriptionLabel')}</p>
                    <RichTextView html={values.description} />
                  </div>
                )}
              </section>

              <p className="text-fg-subtle text-xs">{t('create.reviewNotice')}</p>
            </>
          )}

          {stepError && (
            <p role="alert" className="text-state-canceled-fg text-sm">
              {t(stepError)}
            </p>
          )}

          <div className="flex items-center justify-between gap-3">
            <Button type="button" variant="outline" onClick={goBack} disabled={stepIndex === 0}>
              {t('create.back')}
            </Button>
            {/*
              The two buttons carry different `key`s on purpose. Without them React reuses one node
              and only swaps its `type`: pressing "Next" on the second-to-last step flipped the
              attribute to `submit` **during** the click's dispatch, and the browser then ran the
              default action on the already-converted node — that is, it created the table without
              anybody pressing "Create table". With the keys they are two elements, and the one that
              received the click stops existing before there is any default action left to run.
            */}
            {step === 'capacity' ? (
              <Button key="submit" type="submit" disabled={createTable.isPending}>
                {createTable.isPending ? t('create.submitting') : t('create.submit')}
              </Button>
            ) : (
              <Button key="next" type="button" onClick={() => void goNext()}>
                {t('create.next')}
              </Button>
            )}
          </div>
        </form>
      </Form>
    </div>
  )
}

/** One line of the review step: a label and what was decided for it. */
function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap justify-between gap-2 text-sm">
      <span className="text-fg-subtle">{label}</span>
      <span className="text-right font-medium">{value}</span>
    </div>
  )
}

/** One line of the review step whose value is a set of catalog values. */
function SummaryChips({ label, values, empty }: { label: string; values: CatalogValue[]; empty: string }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
      <span className="text-fg-subtle">{label}</span>
      {values.length === 0 ? (
        <span className="font-medium">{empty}</span>
      ) : (
        <span className="flex flex-wrap justify-end gap-1.5">
          {values.map((value) => (
            <CatalogChip key={value.id} value={value} />
          ))}
        </span>
      )}
    </div>
  )
}

export { MasterTableCreatePage as Component }
