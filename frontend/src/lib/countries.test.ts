import { describe, expect, it } from 'vitest'

import { countries } from './countries'

describe('countries', () => {
  it('builds the list without throwing (regression: Intl.supportedValuesOf has no "region" key)', () => {
    expect(countries.length).toBeGreaterThan(190)
  })

  it('has no duplicate codes', () => {
    const codes = countries.map((country) => country.code)
    expect(new Set(codes).size).toBe(codes.length)
  })

  it('resolves real Spanish names instead of falling back to the raw code', () => {
    const argentina = countries.find((country) => country.code === 'AR')
    expect(argentina?.name).toBe('Argentina')
  })

  it('is sorted alphabetically by name in Spanish', () => {
    const names = countries.map((country) => country.name)
    const sorted = [...names].sort((a, b) => a.localeCompare(b, 'es'))
    expect(names).toEqual(sorted)
  })
})
