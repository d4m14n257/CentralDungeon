import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useDisclosure } from './useDisclosure'

describe('useDisclosure', () => {
  it('starts closed with no item', () => {
    const { result } = renderHook(() => useDisclosure<string>())

    expect(result.current.isOpen).toBe(false)
    expect(result.current.item).toBeUndefined()
  })

  it('opens with the item that triggered it', () => {
    const { result } = renderHook(() => useDisclosure<{ id: string }>())

    act(() => result.current.open({ id: 'reg-1' }))

    expect(result.current.isOpen).toBe(true)
    expect(result.current.item).toEqual({ id: 'reg-1' })
  })

  it('opens with no item when called without one', () => {
    const { result } = renderHook(() => useDisclosure<string>())

    act(() => result.current.open())

    expect(result.current.isOpen).toBe(true)
    expect(result.current.item).toBeUndefined()
  })

  it('closing clears both the open flag and the stored item', () => {
    const { result } = renderHook(() => useDisclosure<string>())

    act(() => result.current.open('candidate-1'))
    act(() => result.current.close())

    expect(result.current.isOpen).toBe(false)
    expect(result.current.item).toBeUndefined()
  })
})
