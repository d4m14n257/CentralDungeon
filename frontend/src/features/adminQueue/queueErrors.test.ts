import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'

import { ADMIN_QUEUE_ERROR_CODES, adminQueueErrorKey } from './queueErrors'

const t = i18n.getFixedT('es', 'admin')

function refusal(errorCode: string): ApiError {
  return new ApiError(409, { title: 'Conflict', status: 409, detail: 'in English, for a log', errorCode })
}

/**
 * The two refusals the reservation adds (#100). Both are races between two admins, which is the
 * normal case here and not the edge one — the shared tray exists **because** that happens — so both
 * have a sentence of their own rather than the generic one.
 */
describe('adminQueueErrorKey', () => {
  it.each(ADMIN_QUEUE_ERROR_CODES)('maps %s to a sentence of its own', (errorCode) => {
    expect(adminQueueErrorKey(refusal(errorCode))).toBe(`queue.errors.${errorCode}`)
  })

  /** Guessing at a message for a code nobody committed to is how an interface asserts what the
   *  server never said. Anything unknown falls back to one generic sentence. */
  it('falls back to one generic sentence for a code nobody committed to', () => {
    expect(adminQueueErrorKey(refusal('SOMETHING_ELSE'))).toBe('queue.errors.generic')
    expect(adminQueueErrorKey(new Error('the backend never answered'))).toBe('queue.errors.generic')
  })

  it('says nothing while nothing has failed', () => {
    expect(adminQueueErrorKey(null)).toBeNull()
    expect(adminQueueErrorKey(undefined)).toBeNull()
  })

  /**
   * #197: every key resolves to real text in the reader's language. A key that is not in the bundle
   * renders as itself, which is how an English log line ends up on screen by another route.
   */
  it.each([...ADMIN_QUEUE_ERROR_CODES, 'generic'])('has %s written in the admin bundle', (code) => {
    const message = t(`queue.errors.${code}`)

    expect(message).not.toBe(`queue.errors.${code}`)
    expect(message.length).toBeGreaterThan(10)
  })
})
