import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/providers/i18n'
import { ApiError } from '@/types/api'

import { queryClient } from './query'

const toastError = vi.hoisted(() => vi.fn())
vi.mock('sonner', () => ({ toast: { error: toastError } }))

/** Fails a mutation through the client's global handler, as a rejected request would. */
function fail(errorCode: string, errorParams?: Record<string, string>) {
  const error = new ApiError(400, {
    title: 'Bad request',
    status: 400,
    detail: 'for the log',
    errorCode,
    ...(errorParams && { errorParams }),
  })
  queryClient.getMutationCache().config.onError?.(error, undefined, undefined, { meta: undefined } as never, undefined as never)
}

describe('the toast of a refused mutation', () => {
  beforeEach(() => {
    toastError.mockClear()
    void i18n.changeLanguage('es')
  })

  /** The seat cap travels with the refusal, so the sentence can say which number would have worked. */
  it('names the seat cap when a table asks for more seats than the platform allows', () => {
    fail('MAX_PLAYERS_ABOVE_CAP', { maxPlayersCap: '12' })

    expect(toastError).toHaveBeenCalledWith(expect.stringContaining('12'))
    expect(toastError).not.toHaveBeenCalledWith(i18n.t('errors.mutationFailed'))
  })

  it.each(['FILE_CATEGORY_NOT_YOURS', 'FILE_CATEGORY_NOT_PUBLISHABLE', 'FILE_CANNOT_SELF_PUBLISH', 'REQUEST_TYPE_NOT_ACCEPTED_HERE'])(
    'has a sentence of its own for %s, in both languages',
    async (code) => {
      fail(code)
      expect(toastError).toHaveBeenLastCalledWith(i18n.t(`errors.codes.${code}`))
      expect(toastError).not.toHaveBeenLastCalledWith(i18n.t('errors.mutationFailed'))

      await i18n.changeLanguage('en')
      expect(i18n.exists(`errors.codes.${code}`)).toBe(true)
      fail(code)
      expect(toastError).toHaveBeenLastCalledWith(i18n.t(`errors.codes.${code}`))
      expect(toastError).not.toHaveBeenLastCalledWith(i18n.t('errors.mutationFailed'))
    },
  )

  /** The limit is the server's, not a number written into the sentence. */
  it('states the file cap the server sent instead of a fixed one', () => {
    fail('FILE_TOO_LARGE', { maxBytes: String(7 * 1024 * 1024), sizeBytes: String(9 * 1024 * 1024) })

    const sentence = toastError.mock.calls[0]?.[0] as string
    expect(sentence).toContain('7 MB')
    expect(sentence).not.toContain('2 MB')
  })

  it('keeps the generic codes on the single toast', () => {
    fail('NOT_FOUND')

    expect(toastError).toHaveBeenCalledWith(i18n.t('errors.mutationFailed'))
  })
})
