import { beforeEach, describe, expect, it } from 'vitest'

import { clearDestination, isInAppPath, peekDestination, rememberDestination } from './postLoginDestination'

describe('postLoginDestination', () => {
  beforeEach(() => sessionStorage.clear())

  it('remembers an in-app path until it is cleared', () => {
    rememberDestination('/master/tables/t-1?tab=players#top')

    expect(peekDestination()).toBe('/master/tables/t-1?tab=players#top')
    expect(peekDestination()).toBe('/master/tables/t-1?tab=players#top')

    clearDestination()
    expect(peekDestination()).toBeNull()
  })

  it.each(['https://evil.example/x', '//evil.example/x', '/\\evil.example', 'javascript:alert(1)', 'player', ''])(
    'refuses %j, which is not an in-app path',
    (value) => {
      expect(isInAppPath(value)).toBe(false)
      rememberDestination(value)
      expect(peekDestination()).toBeNull()
    },
  )

  it('ignores a tampered value already in storage', () => {
    sessionStorage.setItem('postLoginDestination', '//evil.example')

    expect(peekDestination()).toBeNull()
  })
})
