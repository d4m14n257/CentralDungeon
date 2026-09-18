import { describe, expect, it } from 'vitest'

import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'

import { APPROVAL_ERROR_CODES, approvalErrorKey } from './approvalErrors'

function refusal(errorCode: string, status = 409) {
  return new ApiError(status, { title: 'Conflict', status, detail: 'written for a log, in English', errorCode })
}

/**
 * #197: what a person reads is built here from the code, never from the `ProblemDetail`'s `detail` —
 * that field is English and written for a log.
 */
describe('approvalErrorKey', () => {
  it('maps each of the feature’s own refusals to a key of its own', () => {
    for (const code of APPROVAL_ERROR_CODES.filter((code) => code !== 'ITEM_ALREADY_CLAIMED')) {
      expect(approvalErrorKey(refusal(code))).toBe(`requests.errors.${code}`)
    }
  })

  /**
   * **The one refusal whose sentence is not this feature's**, and deliberately so (F3.3).
   *
   * The reservation belongs to the shared tray: the very same fact — a colleague is working on this —
   * reaches `/admin/queue` and `/admin/requests`, and writing it twice, once per screen, is how two
   * sentences that mean one thing start to disagree (#176). So this points at the tray's key instead
   * of copying its words. It is a key in the same `admin` namespace both screens translate against,
   * not an import: `features/approvals` still imports nothing from `features/adminQueue` (§3.1.5).
   */
  it('borrows the tray’s sentence for the reservation rather than writing a second one', () => {
    expect(approvalErrorKey(refusal('ITEM_ALREADY_CLAIMED'))).toBe('queue.errors.ITEM_ALREADY_CLAIMED')
  })

  /**
   * And every one of those keys says something, in both languages. A code mapped to a key nobody
   * wrote would render as the key itself — which is the one failure mode this indirection exists to
   * prevent, and the one a mapping test alone would not catch.
   */
  it.each(['es', 'en'])('has a real sentence for every refusal in %s', (language) => {
    const t = i18n.getFixedT(language, 'admin')

    for (const code of APPROVAL_ERROR_CODES) {
      const key = approvalErrorKey(refusal(code)) as string
      expect(t(key), `${code} in ${language}`).not.toBe(key)
      expect(t(key).length).toBeGreaterThan(10)
    }
  })

  /**
   * Everything else falls back to one generic sentence. Guessing at a message for a code nobody
   * committed to is how an interface ends up asserting something the server never said.
   */
  it('falls back to the generic sentence for anything else', () => {
    expect(approvalErrorKey(refusal('SOMETHING_NEW', 500))).toBe('requests.errors.generic')
    expect(approvalErrorKey(new Error('the backend never answered'))).toBe('requests.errors.generic')
  })

  it('says nothing at all while nothing has failed', () => {
    expect(approvalErrorKey(null)).toBeNull()
    expect(approvalErrorKey(undefined)).toBeNull()
  })
})
