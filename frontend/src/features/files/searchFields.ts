import type { TFunction } from 'i18next'

import type { SearchField } from '@/lib/searchQuery'

import { FILE_CATEGORIES } from './categories'
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

/**
 * The commands `/my/files` accepts (#164, #237, #240).
 *
 * **Only the cajones that are theirs**: offering one this person can never have a file in is offering
 * a search that always comes back empty. Which those are is the server's answer, so it arrives as an
 * argument.
 *
 * And with **none** of them — an account that is neither player nor master, or the moment before the
 * server has answered — the command is not offered at all. It is the same rule one step further: a
 * `/file_categories` with an empty list is a command that can match nothing, and its help would read
 * "elegís entre:" with nothing after the colon.
 *
 * @param t          the translator of the `files` namespace
 * @param categories the cajones this person may file under
 * @returns the commands, in the order they are offered
 */
export function myFileSearchFields(t: TFunction, categories: readonly FileCategory[]): SearchField[] {
  const fields: SearchField[] = [
    { name: 'file_name', label: t('search.file_name'), examples: ['ficha', 'mapa', 'inventario'] },
    { name: 'file_type', label: t('search.file_type'), values: FILE_TYPE_CHOICES(t) },
  ]
  if (categories.length > 0) {
    fields.push({ name: 'file_categories', label: t('search.file_categories'), values: fileCategoryChoices(t, categories) })
  }
  return fields
}

/**
 * The commands `/admin/files` accepts (#164, #237, #240).
 *
 * Two differences from a person's own library, and both come from what the screen is: it holds the
 * platform's files, so `/file_owner` exists — somebody else uploaded them — and **all five** cajones
 * are offered, announcements included.
 *
 * @param t the translator of the `files` namespace
 * @returns the commands, in the order they are offered
 */
export function adminFileSearchFields(t: TFunction): SearchField[] {
  return [
    { name: 'file_name', label: t('search.file_name'), examples: ['ficha', 'mapa', 'inventario'] },
    { name: 'file_owner', label: t('search.file_owner'), examples: ['damian', 'carlos'] },
    { name: 'file_type', label: t('search.file_type'), values: FILE_TYPE_CHOICES(t) },
    { name: 'file_categories', label: t('search.file_categories'), values: fileCategoryChoices(t, FILE_CATEGORIES) },
  ]
}
