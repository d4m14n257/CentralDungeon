import { useTranslation } from 'react-i18next'

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { PUBLISHABLE_CATEGORIES } from '../categories'
import type { FileCategory } from '../types'

interface PublishCategorySelectProps {
  /** The cajón chosen for this file, or null while nothing has been chosen yet. */
  value: FileCategory | null
  /** Called with the cajón that was picked. */
  onChange: (value: FileCategory) => void
  /** The file's name, so the accessible label says which file the select is about. */
  fileName: string
  /** True once the admin tried to upload without choosing, to mark the select as the thing missing. */
  invalid?: boolean
  /** Disables the select while the upload is in flight. */
  disabled?: boolean
}

/**
 * What one file is, chosen on its own row of the upload list of /admin/files/upload (#233, #278).
 *
 * **One per file**, because each file dropped in is its own document: the community's blank sheet is
 * table material, the rules are an announcement, and they can go up together.
 *
 * **Only the three a file can be published into.** The two player-side cajones hold what each person
 * answered with, and nothing is ever published there; the backend refuses them too.
 *
 * Nothing is preselected, which is M24.1's fix carried across from the audience it replaced: a default
 * would publish a document into a flow nobody chose.
 *
 * @param props.value    the cajón chosen, or null
 * @param props.onChange called with the cajón picked
 * @param props.fileName the file the select is about, for its accessible label
 * @param props.invalid  marks the select as what is missing
 * @param props.disabled disables it while the upload is in flight
 */
export function PublishCategorySelect({ value, onChange, fileName, invalid, disabled }: PublishCategorySelectProps) {
  const { t } = useTranslation('files')

  return (
    <Select value={value ?? ''} onValueChange={(next) => onChange(next as FileCategory)} disabled={disabled ?? false}>
      <SelectTrigger
        className="flex-1 sm:w-48 sm:flex-none"
        aria-label={t('upload.categoryLabel', { name: fileName })}
        aria-invalid={invalid ?? false}
      >
        <SelectValue placeholder={t('upload.categoryPlaceholder')} />
      </SelectTrigger>
      <SelectContent>
        {PUBLISHABLE_CATEGORIES.map((category) => (
          <SelectItem key={category} value={category}>
            {t(`category.${category}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
