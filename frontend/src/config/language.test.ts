import { describe, expect, it } from 'vitest'

import { LANGUAGE_STORAGE_KEY, isLanguage, resolveInitialLanguage } from './language'

/** A stand-in for `localStorage` holding one value, so the tests never touch the real one. */
function storageHolding(value: string | null) {
  return { getItem: (key: string) => (key === LANGUAGE_STORAGE_KEY ? value : null) }
}

describe('resolveInitialLanguage', () => {
  it('honours a stored choice over anything the browser says', () => {
    expect(resolveInitialLanguage(storageHolding('en'), ['es-AR'])).toBe('en')
    expect(resolveInitialLanguage(storageHolding('es'), ['en-US'])).toBe('es')
  })

  /** The region only says which flavour of a language it is; the primary subtag is the language. */
  it('matches on the primary subtag, so en-GB and en-US are both English', () => {
    expect(resolveInitialLanguage(storageHolding(null), ['en-GB'])).toBe('en')
    expect(resolveInitialLanguage(storageHolding(null), ['es-419'])).toBe('es')
  })

  it('takes the first browser language it can actually render', () => {
    expect(resolveInitialLanguage(storageHolding(null), ['fr-FR', 'en-US', 'es-AR'])).toBe('en')
  })

  /** "Cannot be worked out" covers both no setting at all and a language this app does not speak. */
  it('falls back to Spanish when nothing matches', () => {
    expect(resolveInitialLanguage(storageHolding(null), ['fr-FR', 'de-DE'])).toBe('es')
    expect(resolveInitialLanguage(storageHolding(null), [])).toBe('es')
  })

  /** A value left over from an older build, or edited by hand, must not decide the language. */
  it('ignores a stored value that is not a language it ships', () => {
    expect(resolveInitialLanguage(storageHolding('klingon'), ['en-US'])).toBe('en')
  })

  it('survives an environment with no storage at all', () => {
    expect(resolveInitialLanguage(undefined, ['en-US'])).toBe('en')
  })
})

describe('isLanguage', () => {
  it('accepts the ones that ship and nothing else', () => {
    expect(isLanguage('es')).toBe(true)
    expect(isLanguage('en')).toBe(true)
    expect(isLanguage('en-US')).toBe(false)
    expect(isLanguage(null)).toBe(false)
    expect(isLanguage(42)).toBe(false)
  })
})
