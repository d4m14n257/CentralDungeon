import { useTranslation } from 'react-i18next'

import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'

import { MAX_TAG_DESCRIPTION_LENGTH, type TagDefinitionDraft } from '../tagDefinition'
import { TAG_CATEGORIES } from '../tagCategories'
import type { TagCategory } from '../types'

/** The Select item that stands for "no category": Radix refuses an empty string as an item value. */
const NO_CATEGORY = 'none'

/** What the fields need. */
export interface TagDefinitionFieldsProps {
  /** Prefix for the inputs' ids, so two forms on one screen do not share them. */
  idPrefix: string
  /** The draft being edited. */
  value: TagDefinitionDraft
  /** Called with the whole draft every time either field changes. */
  onChange: (value: TagDefinitionDraft) => void
  /** Whether the description failed validation, so the field can say so. */
  descriptionInvalid?: boolean
}

/**
 * A tag group's category and definition, as two fields: a select over the fixed categories, with
 * "no category" first, and a plain-text definition. Shared by the dialog that edits a group's
 * definition and the one that accepts a proposal as a group of its own.
 *
 * @param props.idPrefix           prefix for the inputs' ids
 * @param props.value              the draft being edited
 * @param props.onChange           called with the new draft
 * @param props.descriptionInvalid whether the description failed validation
 */
export function TagDefinitionFields({ idPrefix, value, onChange, descriptionInvalid = false }: TagDefinitionFieldsProps) {
  const { t } = useTranslation('catalogs')

  return (
    <>
      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-category`}>{t('definition.categoryLabel')}</Label>
        <Select
          value={value.category ?? NO_CATEGORY}
          onValueChange={(next) => onChange({ ...value, category: next === NO_CATEGORY ? null : (next as TagCategory) })}
        >
          <SelectTrigger id={`${idPrefix}-category`} className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NO_CATEGORY}>{t('tagCategory.none')}</SelectItem>
            {TAG_CATEGORIES.map((category) => (
              <SelectItem key={category} value={category}>
                {t(`tagCategory.${category}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-description`}>{t('definition.descriptionLabel')}</Label>
        <Textarea
          id={`${idPrefix}-description`}
          value={value.description}
          maxLength={MAX_TAG_DESCRIPTION_LENGTH}
          aria-invalid={descriptionInvalid}
          aria-describedby={`${idPrefix}-description-hint`}
          onChange={(event) => onChange({ ...value, description: event.target.value })}
        />
        <p id={`${idPrefix}-description-hint`} className="text-fg-muted text-xs">
          {descriptionInvalid
            ? t('definition.descriptionTooLong', { max: MAX_TAG_DESCRIPTION_LENGTH })
            : t('definition.descriptionHint', { count: value.description.length, max: MAX_TAG_DESCRIPTION_LENGTH })}
        </p>
      </div>
    </>
  )
}
