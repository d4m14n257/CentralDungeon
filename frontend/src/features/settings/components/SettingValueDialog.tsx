import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

import { useUpdateSetting } from '../api/useUpdateSetting'
import { settingErrorKey } from '../settingErrors'
import { updateSettingSchema, type UpdateSettingForm } from '../schemas'
import type { SystemSetting } from '../types'

interface SettingValueDialogProps {
  /** The setting being edited, or null when the dialog is closed. */
  setting: SystemSetting | null
  open: boolean
  onOpenChange: (open: boolean) => void
  /**
   * The explanation of what settings are, rendered under the field.
   *
   * **A node instead of the dialog raising it itself**, because `HelpLink` lives in `features/help`
   * and a feature never imports another one (arquitectura.md §3.1.5). The screen owns the
   * composition, so the link still sits where the question is born.
   */
  help?: ReactNode
}

/**
 * Changes one setting's value, with a reason (#141).
 *
 * **The three things it says before the value is saved**, which is the difference between a
 * configuration screen and a box of numbers:
 *
 * - **The range**, as a hint and as validation built from that setting's own bounds. A cap somebody
 *   only discovers by hitting it reads as a bug rather than a rule (principio 2 de
 *   frontend-diseno.md §1), and every setting here has a different range — so one shared "must be a
 *   number" would be exactly that bug.
 * - **The default**, beside the current value, so putting something back never means finding the
 *   number in the source (#141 asks for both on screen).
 * - **The warning, for a setting whose change is retroactive** (#44). Lowering the visibility window
 *   takes visibility away from people who have it at this instant; raising it hands it back to people
 *   who had already lost it. #141 says in so many words that «ninguno de los dos es un ajuste
 *   cosmético y la pantalla tiene que decirlo», and after the fact is not saying it.
 *
 * It owns the mutation, as every `…Dialog` does (arquitectura.md §3.3, #110), and renders its
 * refusal inline above the button rather than as a toast that disappears while the form is still
 * open.
 *
 * @param props.setting      the setting being edited
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 * @param props.help         the explanation of what settings are, shown under the field
 */
export function SettingValueDialog({ setting, open, onOpenChange, help }: SettingValueDialogProps) {
  const { t } = useTranslation('admin')
  const update = useUpdateSetting()

  // Built from this setting's own bounds, so the form refuses exactly what the API would refuse.
  const form = useForm<UpdateSettingForm>({
    resolver: zodResolver(updateSettingSchema(setting?.minValue ?? 0, setting?.maxValue ?? 0)),
    defaultValues: { value: '', justification: '' },
  })

  // The field opens on the value in force, which is what makes this an edit rather than a blank
  // form: most changes are a nudge from the current number, not a number typed from nothing.
  useEffect(() => {
    if (open && setting) {
      form.reset({ value: String(setting.value), justification: '' })
    }
  }, [open, setting, form])

  const failure = settingErrorKey(update.error)

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset({ value: '', justification: '' })
      update.reset()
    }
    onOpenChange(next)
  }

  function onSubmit(values: UpdateSettingForm) {
    if (!setting) return
    update.mutate(
      { key: setting.key, input: { value: Number(values.value.trim()), justification: values.justification } },
      {
        onSuccess: () => {
          toast.success(t('settings.updateSuccess', { name: t(`settings.keys.${setting.key}.label`) }))
          handleOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={setting ? t(`settings.keys.${setting.key}.label`) : ''}
      description={setting ? t(`settings.keys.${setting.key}.description`) : ''}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {/* Said before the value is saved, never after: this is the one setting whose change
              re-answers questions the platform already answered (#44, #141). */}
          {setting?.retroactive && (
            <p role="note" className="bg-state-paused-bg text-state-paused-fg rounded-md px-3 py-2 text-sm">
              {t(`settings.keys.${setting.key}.retroactiveWarning`)}
            </p>
          )}

          <FormField
            control={form.control}
            name="value"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.valueLabel', { unit: setting ? t(`settings.keys.${setting.key}.unit`) : '' })}</FormLabel>
                <FormControl>
                  {/* `inputMode` and not `type="number"`: the spinner invites dragging a
                      platform-wide limit by accident, and a stray scroll over a focused number
                      input changes it silently. The rule is the schema's either way. */}
                  <Input inputMode="numeric" autoComplete="off" {...field} />
                </FormControl>
                <FormDescription>
                  {t('settings.rangeHint', { min: setting?.minValue ?? 0, max: setting?.maxValue ?? 0 })}
                  {' · '}
                  {t('settings.defaultHint', { value: setting?.defaultValue ?? 0 })}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          {help}

          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.justificationLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} placeholder={t('settings.justificationPlaceholder')} {...field} />
                </FormControl>
                {/* Why it is required, said where it is asked for: a platform-wide change leaves no
                    other trace, so the reason is the whole audit (#141). */}
                <FormDescription>{t('settings.justificationHint')}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          {failure && (
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
              {t(failure.key, failure.params)}
            </p>
          )}

          <Button type="submit" disabled={update.isPending} className="w-full">
            {t('settings.save')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
