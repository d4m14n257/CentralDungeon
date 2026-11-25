import { describe, expect, it } from 'vitest'

import { hasPublishableCategory } from './libraryState'

describe('hasPublishableCategory', () => {
  it('is false for a file that says nothing about where it goes', () => {
    expect(hasPublishableCategory({ categories: [] })).toBe(false)
  })

  it('is false when its only categories are the player-side ones, which nothing is published into', () => {
    expect(hasPublishableCategory({ categories: ['PlayerApplication', 'PlayerSubmission'] })).toBe(false)
  })

  it('is true as soon as one of its categories can be published into', () => {
    expect(hasPublishableCategory({ categories: ['PlayerSubmission', 'Announcement'] })).toBe(true)
  })
})
