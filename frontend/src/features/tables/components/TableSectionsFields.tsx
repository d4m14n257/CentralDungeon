import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { RichTextEditor } from '@/components/RichTextEditor'
import { Button } from '@/components/ui/button'
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'

import { TABLE_SECTION_KEYS } from '../lib/tableSections'
import type { CreateGameTableForm } from '../schemas'
import type { TableSectionKey } from '../types'

/**
 * The four optional sections of a table, inside the create or edit form: one outline button per
 * section that is not written yet, and an editor for each one that is.
 *
 * **A section is shown when it has content or was just opened**, so a table loaded for editing shows
 * what it already has and nothing else. Removing one empties it, which is what the request then sends
 * as a section not written.
 *
 * It reads the form from its context, so it has to be rendered inside the page's `<Form>`.
 */
export function TableSectionsFields() {
  const { t } = useTranslation('tables')
  const form = useFormContext<CreateGameTableForm>()
  const values = useWatch({ control: form.control, name: TABLE_SECTION_KEYS })
  const [opened, setOpened] = useState<ReadonlySet<TableSectionKey>>(new Set())

  const isShown = (key: TableSectionKey, index: number) => opened.has(key) || Boolean(values[index])
  const hidden = TABLE_SECTION_KEYS.filter((key, index) => !isShown(key, index))

  function open(key: TableSectionKey) {
    setOpened((current) => new Set(current).add(key))
  }

  function remove(key: TableSectionKey) {
    form.setValue(key, '', { shouldDirty: true })
    setOpened((current) => {
      const next = new Set(current)
      next.delete(key)
      return next
    })
  }

  return (
    <div className="space-y-4">
      {TABLE_SECTION_KEYS.map((key, index) =>
        isShown(key, index) ? (
          <FormField
            key={key}
            control={form.control}
            name={key}
            render={({ field }) => (
              <FormItem>
                <div className="flex items-center justify-between gap-2">
                  <FormLabel>{t(`sections.${key}.label`)}</FormLabel>
                  <Button type="button" size="sm" variant="ghost" onClick={() => remove(key)}>
                    {t('sections.remove')}
                  </Button>
                </div>
                <FormControl>
                  <RichTextEditor value={field.value ?? ''} onChange={field.onChange} ariaLabel={t(`sections.${key}.label`)} />
                </FormControl>
                <FormDescription>{t(`sections.${key}.hint`)}</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        ) : null,
      )}
      {hidden.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {hidden.map((key) => (
            <Button key={key} type="button" size="sm" variant="outline" onClick={() => open(key)}>
              <Plus aria-hidden="true" />
              {t(`sections.${key}.label`)}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}
