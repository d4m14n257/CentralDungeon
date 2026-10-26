import { describe, expect, it } from 'vitest'

import { createGameTableSchema } from './schemas'

/** The seat field is validated against the cap the platform states, not a number written into the form. */
describe('createGameTableSchema seat cap', () => {
  const parse = (maxPlayers: string | undefined, cap = 12) =>
    createGameTableSchema(cap).safeParse({ name: 'A table', ...(maxPlayers !== undefined && { maxPlayers }) })

  it('accepts a number up to the cap, and no number at all', () => {
    expect(parse('12').success).toBe(true)
    expect(parse('').success).toBe(true)
    expect(parse(undefined).success).toBe(true)
  })

  it('refuses a number above the cap and says the range', () => {
    const result = parse('13')

    expect(result.success).toBe(false)
    expect(result.error?.issues[0]?.message).toBe('Tiene que estar entre 1 y 12')
  })

  it('follows the cap it was built with', () => {
    expect(parse('13', 20).success).toBe(true)
    expect(parse('21', 20).success).toBe(false)
  })

  it('refuses zero and anything that is not a whole number', () => {
    expect(parse('0').success).toBe(false)
    expect(parse('2.5').success).toBe(false)
  })
})
