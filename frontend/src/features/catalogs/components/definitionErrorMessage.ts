import type { TFunction } from 'i18next'

import { ApiError } from '@/types/api'

/** The refusals of defining a tag that have a sentence of their own; anything else reads as a plain failure. */
const EXPLAINED_CODES = new Set(['CATALOG_VALUE_NOT_CANONICAL'])

/**
 * The sentence for a failed attempt to define a tag group, in the reader's language.
 *
 * @param error whatever the mutation failed with
 * @param t     the `catalogs` translator
 * @returns the sentence to show under the form
 */
export function definitionErrorMessage(error: unknown, t: TFunction<'catalogs'>): string {
  if (error instanceof ApiError && EXPLAINED_CODES.has(error.problem.errorCode)) {
    return t(`errors.${error.problem.errorCode}`)
  }
  return t('definition.failed')
}
