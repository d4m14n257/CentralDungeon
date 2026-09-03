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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { helpPath, masterTableDetailPath } from '@/config/paths'
import { CatalogChip, CatalogCombobox } from '@/features/catalogs'
import { ScheduleEditor, createGameTableSchema, useCreateTable, useTableTypes, WIZARD_STEPS } from '@/features/tables'
import type { CreateGameTableForm, TableScheduleEntry, WizardStep } from '@/features/tables'
import { useMe } from '@/features/users'
import { browserTimeZone, formatSlot, localInputToUtcIso, utcSlotToLocal } from '@/lib/date'
import type { CatalogValue } from '@/types/catalog'

/**
 * El wizard de creación, `/master/tables/new` (frontend-diseno.md sitemap).
 *
 * **Cuatro pasos, una decisión cada uno**: identidad → catálogos → agenda y duración → cupo y
 * revisión. No es decoración: una mesa completa pide quince datos de cinco clases distintas, y un
 * formulario único obliga a leerlos todos antes de contestar el primero. El último paso es el
 * resumen, porque lo que se envía queda a revisión de un admin (#27) y conviene verlo entero antes.
 *
 * La pantalla compone: los catálogos los trae `features/catalogs` y la agenda `features/tables`,
 * y ninguna de las dos sabe de la otra (§3.1.5, regla dura 16).
 *
 * La hora se escribe en la zona del lector y viaja en UTC (#22): la conversión es de `lib/date.ts`
 * y ocurre una sola vez, al enviar.
 */
export function MasterTableCreatePage() {
  const { t, i18n } = useTranslation('master')
  const navigate = useNavigate()
  const createTable = useCreateTable()
  const { data: me } = useMe()
  const { data: tableTypes } = useTableTypes()
  const [step, setStep] = useState<WizardStep>('identity')
  const [systems, setSystems] = useState<CatalogValue[]>([])
  const [tags, setTags] = useState<CatalogValue[]>([])
  const [platforms, setPlatforms] = useState<CatalogValue[]>([])
  const [schedule, setSchedule] = useState<TableScheduleEntry[]>([])

  // #22 quitó `users.timezone` del modelo, así que hoy la única fuente es el navegador. `lib/date.ts`
  // recibe la zona como parámetro justamente para que el día que exista una preferencia de perfil
  // se cambie esta línea y nada más (#111).
  const timeZone = useMemo(() => browserTimeZone(), [])

  const form = useForm<CreateGameTableForm>({
    resolver: zodResolver(createGameTableSchema),
    defaultValues: { name: '', description: '', permitted: '', requirements: '', duration: '03:00' },
  })

  // Crear exige el rol de plataforma Master, no solo pertenencia (#135) - el backend ya lo rechaza
  // con 403, pero mostrar un formulario que siempre va a fallar sería peor que no mostrar nada.
  if (me && !me.roles.includes('Master')) {
    return <ForbiddenState />
  }

  const stepIndex = WIZARD_STEPS.indexOf(step)
  const values = form.getValues()

  async function goNext() {
    // Solo el primer paso tiene campos obligatorios; validar de más frenaría al que todavía no
    // decidió el cupo, que es justamente lo que el paso siguiente sirve para decidir.
    if (step === 'identity' && !(await form.trigger('name'))) {
      return
    }
    const next = WIZARD_STEPS[stepIndex + 1]
    if (next) {
      setStep(next)
    }
  }

  function goBack() {
    const previous = WIZARD_STEPS[stepIndex - 1]
    if (previous) {
      setStep(previous)
    }
  }

  function onSubmit(formValues: CreateGameTableForm) {
    // Un wizard no se manda desde cualquier paso: Enter en un campo de texto dispara el submit
    // nativo del formulario, y sin esta guarda crearía la mesa mientras la persona todavía está
    // escribiendo el nombre. El botón de enviar solo existe en el último paso; esto cubre el teclado.
    if (step !== 'capacity') {
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
                        {(tableTypes?.content ?? []).map((type) => (
                          <SelectItem key={type.id} value={type.id}>
                            {type.name}
                            {type.description && <span className="text-fg-subtle ml-2 text-xs">{type.description}</span>}
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
                  {/* La ayuda se enlaza desde la pantalla que la necesita, con su #ref (#167, #168). */}
                  <Link to={helpPath('masters', 'schedule')} className="text-fg-muted hover:text-fg text-xs underline">
                    {t('create.scheduleHelp')}
                  </Link>
                </div>
                <ScheduleEditor value={schedule} onChange={setSchedule} timeZone={timeZone} duration={values.duration} />
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
                <SummaryRow
                  label={t('create.tableTypeLabel')}
                  value={tableTypes?.content.find((type) => type.id === values.tableTypeId)?.name ?? t('create.reviewEmpty')}
                />
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

          <div className="flex items-center justify-between gap-3">
            <Button type="button" variant="outline" onClick={goBack} disabled={stepIndex === 0}>
              {t('create.back')}
            </Button>
            {/*
              Los dos botones llevan `key` distinta a propósito. Sin eso React reutiliza el mismo
              nodo y solo le cambia el `type`: al tocar «Siguiente» en el penúltimo paso, el
              atributo pasaba a `submit` **durante** el despacho del click, y el navegador ejecutaba
              la acción por defecto sobre el nodo ya convertido — es decir, creaba la mesa sin que
              nadie tocara «Crear mesa». Con la key son dos elementos, y el que recibió el click
              deja de existir antes de que haya acción por defecto que ejecutar.
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

/** What one catalog block of the wizard needs. Private: it is a piece of this screen, not of a feature. */
interface CatalogPickerProps {
  kind: 'systems' | 'tags' | 'platforms'
  label: string
  selected: CatalogValue[]
  onChange: (values: CatalogValue[]) => void
}

/**
 * One catalog on the wizard: the chips already chosen plus the combobox that adds another.
 *
 * A master who does not find their system proposes it here and keeps going; what comes back is
 * marked as pending, because the table publishes while the value waits for an admin (#55, #57).
 */
function CatalogPicker({ kind, label, selected, onChange }: CatalogPickerProps) {
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium">{label}</p>
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {selected.map((value) => (
            <CatalogChip key={value.id} value={value} onRemove={(id) => onChange(selected.filter((item) => item.id !== id))} />
          ))}
        </div>
      )}
      <CatalogCombobox
        kind={kind}
        selected={selected}
        canPropose
        onSelect={(value) => {
          if (!selected.some((item) => item.id === value.id)) {
            onChange([...selected, value])
          }
        }}
      />
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
