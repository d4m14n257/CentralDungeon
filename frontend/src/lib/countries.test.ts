import { describe, expect, it } from 'vitest'

import { countriesIn } from './countries'

describe('countriesIn', () => {
  it('builds the list without throwing (regression: Intl.supportedValuesOf has no "region" key)', () => {
    expect(countriesIn('es').length).toBeGreaterThan(190)
  })

  it('has no duplicate codes', () => {
    const codes = countriesIn('es').map((country) => country.code)
    expect(new Set(codes).size).toBe(codes.length)
  })

  it('resolves real names instead of falling back to the raw code', () => {
    expect(countriesIn('es').find((country) => country.code === 'DE')?.name).toBe('Alemania')
  })

  /** #198: the same country has a different name per language, which is the point of the parameter. */
  it('names each country in the language it is asked for', () => {
    expect(countriesIn('en').find((country) => country.code === 'DE')?.name).toBe('Germany')
  })

  it('is sorted alphabetically in the language it was asked for', () => {
    const names = countriesIn('en').map((country) => country.name)
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'en')))
  })

  /** Sorting once and reusing it would leave one of the two languages with an alphabet that is not its own. */
  it('orders differently per language, because the names themselves differ', () => {
    expect(countriesIn('es').map((country) => country.code)).not.toEqual(countriesIn('en').map((country) => country.code))
  })
})
