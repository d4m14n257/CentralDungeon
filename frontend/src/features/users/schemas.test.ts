import { describe, expect, it } from 'vitest'

import { completeOnboardingSchema } from './schemas'

describe('completeOnboardingSchema', () => {
  /** An unchosen country is a missing answer, like the name, and not a malformed code. */
  it('asks for the country as required when it is empty', () => {
    const result = completeOnboardingSchema.safeParse({ name: 'Ana', country: '' })

    expect(result.error?.issues[0]?.message).toBe('Esto es obligatorio')
  })

  it('accepts a two-letter code', () => {
    expect(completeOnboardingSchema.safeParse({ name: 'Ana', country: 'AR' }).success).toBe(true)
  })
})
