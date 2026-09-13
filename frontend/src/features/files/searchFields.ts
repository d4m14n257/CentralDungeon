import type { TFunction } from 'i18next'

import type { FileCategory } from './types'

/**
 * What `/file_type` offers, and what each choice sends (#164).
 *
 * **A command with fixed choices rather than free text**, because the values it searches are MIME
 * types: nobody is going to type
 * `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, and nobody should have
 * to know it exists. The label is the word people actually use for the format; the value is what the
 * backend matches against `files.mime_type`.
 *
 * Word is two entries collapsed into one choice would be wrong — `.doc` and `.docx` are different
 * MIME types and a criterion takes alternatives, so they are offered separately and can both be
 * picked into the same chip.
 *
 * @param t the translator of the `files` namespace
 * @returns the choices, in the order they are offered
 */
export function FILE_TYPE_CHOICES(t: TFunction): { value: string; label: string }[] {
  return [
    { value: 'application/pdf', label: t('search.types.pdf') },
    { value: 'image/png', label: t('search.types.png') },
    { value: 'image/jpeg', label: t('search.types.jpeg') },
    { value: 'application/msword', label: t('search.types.doc') },
    { value: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', label: t('search.types.docx') },
  ]
}

/**
 * What `/file_categories` offers: the cajones a file can belong to (#233, #239).
 *
 * **A different question from `/file_type`, and both are commands with fixed choices.** The type is
 * the format — PDF, PNG — and the cajón is the flow the file came out of: material de mesa, una
 * entrega, un anuncio. Somebody looking for "the sheet I handed in" is asking the second, and no
 * amount of searching filenames answers it.
 *
 * The caller passes which cajones to offer, because on somebody's own library that is only theirs
 * (#237) while an admin sees all five.
 *
 * @param t          the translator of the `files` namespace
 * @param categories the cajones to offer
 * @returns the choices, in the order they were given
 */
export function fileCategoryChoices(t: TFunction, categories: readonly FileCategory[]): { value: string; label: string }[] {
  return categories.map((category) => ({ value: category, label: t(`category.${category}`) }))
}
