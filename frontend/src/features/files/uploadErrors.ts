import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'
import { allowedTypesLabel } from './limits'

/** The refusals of an upload that have their sentence in the `files` namespace, under `errors`. */
const FILES_NAMESPACE_CODES: ReadonlySet<string> = new Set(['FILE_TOO_LARGE', 'FILE_TYPE_NOT_ALLOWED', 'FILE_EMPTY'])

/** The other refusals an upload can raise, whose sentence is shared with the global one under `errors.codes`. */
const SHARED_CODES: ReadonlySet<string> = new Set(['FILE_CATEGORY_NOT_YOURS', 'FILE_CATEGORY_NOT_PUBLISHABLE', 'FILE_CANNOT_SELF_PUBLISH'])

/**
 * The sentence that says why one file of a multiple upload was refused, in the reader's language.
 *
 * Built from the error code and its parameters, never from the `ProblemDetail`'s `detail`, which is
 * English and written for a log. A code with no sentence of its own, a server error or a request that
 * never got an answer falls back to the generic one: guessing at a reason is how an interface ends up
 * asserting something the server never said.
 *
 * The size limit and the accepted types are filled in from what the interface already knows when the
 * server's refusal did not carry them, so the sentence never renders with a blank where a number goes.
 *
 * @param error what the upload of that file rejected with
 * @param maxFileSizeBytes the per-file cap in force, used when the refusal does not name one
 * @returns the translated reason
 */
export function uploadFailureReason(error: unknown, maxFileSizeBytes: number): string {
  if (error instanceof ApiError) {
    const { errorCode, errorParams } = error.problem
    if (FILES_NAMESPACE_CODES.has(errorCode)) {
      return i18n.t(`errors.${errorCode}`, {
        ns: 'files',
        maxBytes: maxFileSizeBytes,
        types: allowedTypesLabel(i18n.language),
        ...errorParams,
      })
    }
    if (SHARED_CODES.has(errorCode)) {
      return i18n.t(`errors.codes.${errorCode}`, { ...errorParams })
    }
    // Without its wait the sentence would say "try again in  min", which is worse than the generic one.
    if (errorCode === 'RATE_LIMITED' && errorParams?.['retryAfterSeconds'] !== undefined) {
      return i18n.t('errors.codes.RATE_LIMITED', { ...errorParams })
    }
  }
  return i18n.t('errors.uploadFailed', { ns: 'files' })
}
