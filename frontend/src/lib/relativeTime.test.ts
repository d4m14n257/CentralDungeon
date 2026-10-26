import { describe, expect, it } from 'vitest'

import { relativeTimeFrom } from './relativeTime'

describe('relativeTimeFrom', () => {
  it('reads a timestamp without an offset as UTC, not as the reader’s local time', () => {
    // The suite runs three hours behind UTC; read as local, this would be three hours in the future.
    expect(relativeTimeFrom('2026-10-25T02:00:00', new Date('2026-10-25T02:05:00Z'))).toEqual({ unit: 'minutes', count: 5 })
  })

  it('leaves a timestamp that states its offset alone', () => {
    expect(relativeTimeFrom('2026-10-24T23:00:00-03:00', new Date('2026-10-25T04:00:00Z'))).toEqual({ unit: 'hours', count: 2 })
  })
})
