import { useTranslation } from 'react-i18next'

import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'

import { PUBLISHABLE_CATEGORIES } from '../categories'
import type { FileCategory } from '../types'

interface PublishCategoriesFieldProps {
  /** The cajones chosen so far. Empty until the admin picks one — nothing is preselected. */
  value: FileCategory[]
  /** Called with the whole new selection whenever a cajón is ticked or unticked. */
  onChange: (value: FileCategory[]) => void
  /** Disables every checkbox while the upload is in flight. */
  disabled?: boolean
}

/**
 * Choosing which cajones a file is published into, **before** it is uploaded to the platform's
 * library (#233, #278).
 *
 * **Before and not after**, because in the library uploading is publishing: a file never sits there
 * without saying which flow it is for. It used to be a dialog opened on a row once the file was
 * already up, and in between the file was nobody's business — the admin's own `Private`, with no
 * cajón at all.
 *
 * **Checkboxes and not chips or a select, because a file can be offered in several flows at once** —
 * the case the whole redesign turns on. The community's blank sheet is asked for while a table
 * recruits *and* again once it is running, so it goes into `TableMaterial` and `MasterRequest`
 * together: one file, one blob, two rows. Each option carries the line that says where it shows up,
 * because the name alone does not tell an admin which screen a master will see it on.
 *
 * **Only three of the five are offered.** The two player-side cajones hold what individual people
 * answered with, and a blank offered to everybody is not an answer. The backend refuses them too.
 *
 * Nothing is preselected, which is M24.1's fix carried across from the audience it replaced: a
 * default would publish documents into a flow nobody chose, and the legacy showed what that looks
 * like — a document written for masters in front of a player.
 *
 * @param props.value    the cajones chosen so far
 * @param props.onChange called with the whole new selection
 * @param props.disabled disables the field while the upload is in flight
 */
export function PublishCategoriesField({ value, onChange, disabled }: PublishCategoriesFieldProps) {
  const { t } = useTranslation('files')

  function toggle(category: FileCategory) {
    onChange(value.includes(category) ? value.filter((chosen) => chosen !== category) : [...value, category])
  }

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{t('publish.categoriesLabel')}</legend>
      {PUBLISHABLE_CATEGORIES.map((category) => (
        <div key={category} className="flex items-start gap-2">
          <Checkbox
            id={`publish-${category}`}
            checked={value.includes(category)}
            disabled={disabled ?? false}
            onCheckedChange={() => toggle(category)}
          />
          <div className="space-y-0.5">
            <Label htmlFor={`publish-${category}`}>{t(`category.${category}`)}</Label>
            <p className="text-fg-muted text-xs">{t(`publish.hint.${category}`)}</p>
          </div>
        </div>
      ))}
      <p className="text-fg-muted text-xs">{t('publish.categoriesHint')}</p>
    </fieldset>
  )
}
