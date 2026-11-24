import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'
import { uploadFailureReason } from './uploadErrors'

const MAX_BYTES = 2 * 1024 * 1024

function refusal(errorCode: string, errorParams?: Record<string, string>) {
  return new ApiError(400, {
    title: 'Bad Request',
    status: 400,
    detail: 'English for a log',
    errorCode,
    ...(errorParams && { errorParams }),
  })
}

describe('uploadFailureReason', () => {
  it('says the limit the server named when a file is too large', () => {
    const reason = uploadFailureReason(refusal('FILE_TOO_LARGE', { maxBytes: String(5 * 1024 * 1024) }), MAX_BYTES)

    expect(reason).toContain('5 MB')
  })

  it('falls back to the limit the interface knows when the refusal does not carry one', () => {
    expect(uploadFailureReason(refusal('FILE_TOO_LARGE'), MAX_BYTES)).toContain('2 MB')
  })

  it('names the accepted types when the type is refused', () => {
    expect(uploadFailureReason(refusal('FILE_TYPE_NOT_ALLOWED'), MAX_BYTES)).toContain('PDF')
  })

  it('uses the shared sentence of a refusal that is not about the file itself', () => {
    expect(uploadFailureReason(refusal('FILE_CATEGORY_NOT_YOURS'), MAX_BYTES)).toContain('no es una de las tuyas')
  })

  it('says how long to wait when the uploads came too fast', () => {
    expect(uploadFailureReason(refusal('RATE_LIMITED', { retryAfterSeconds: '90' }), MAX_BYTES)).toContain('2 min')
  })

  it('never shows the backend detail: an unknown code and a missing wait read as the generic reason', () => {
    const generic = 'No se pudo subir el archivo'

    expect(uploadFailureReason(refusal('SOMETHING_NEW'), MAX_BYTES)).toContain(generic)
    expect(uploadFailureReason(refusal('RATE_LIMITED'), MAX_BYTES)).toContain(generic)
  })

  it('reads a failure that is not an answer from the server, such as a dropped connection, as the generic reason', () => {
    expect(uploadFailureReason(new TypeError('Failed to fetch'), MAX_BYTES)).toContain('No se pudo subir el archivo')
  })
})
