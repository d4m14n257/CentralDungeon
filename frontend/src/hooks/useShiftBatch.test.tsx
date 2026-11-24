import { act, fireEvent, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { ConfirmContext, type ConfirmFn } from './useConfirm'
import { useShiftBatch } from './useShiftBatch'

const QUESTION = { title: '¿Aplicar?', description: 'A todas' }

/** The hook with a confirmation that answers whatever the test decides, so no dialog is needed. */
function setup(answer = true) {
  const confirm = vi.fn<ConfirmFn>().mockResolvedValue(answer)
  const wrapper = ({ children }: { children: ReactNode }) => <ConfirmContext value={confirm}>{children}</ConfirmContext>
  return { confirm, ...renderHook(() => useShiftBatch(), { wrapper }) }
}

const shiftDown = () => fireEvent.keyDown(window, { key: 'Shift' })
const shiftUp = () => fireEvent.keyUp(window, { key: 'Shift' })

describe('useShiftBatch', () => {
  it('runs the action as usual when Shift is not held', () => {
    const { result } = setup()
    const run = vi.fn()

    act(() => result.current.press('delete', 'a', run))

    expect(run).toHaveBeenCalledOnce()
    expect(result.current.batch).toBeNull()
  })

  it('marks and unmarks a row instead of running when Shift is held', () => {
    const { result } = setup()
    const run = vi.fn()
    shiftDown()

    act(() => result.current.press('delete', 'a', run))
    act(() => result.current.press('delete', 'b', run))
    expect(result.current.batch).toEqual({ action: 'delete', ids: ['a', 'b'] })
    expect(result.current.isMarked('delete', 'a')).toBe(true)

    act(() => result.current.press('delete', 'a', run))
    expect(result.current.batch).toEqual({ action: 'delete', ids: ['b'] })
    act(() => result.current.press('delete', 'b', run))
    expect(result.current.batch).toBeNull()
    expect(run).not.toHaveBeenCalled()
  })

  it('replaces the batch when Shift is held on a different action', () => {
    const { result } = setup()
    shiftDown()

    act(() => result.current.press('delete', 'a', vi.fn()))
    act(() => result.current.press('detach', 'b', vi.fn()))

    expect(result.current.batch).toEqual({ action: 'detach', ids: ['b'] })
    expect(result.current.isMarked('delete', 'a')).toBe(false)
  })

  it('stops treating clicks as marks once Shift is released', () => {
    const { result } = setup()
    const run = vi.fn()
    shiftDown()
    shiftUp()

    act(() => result.current.press('delete', 'a', run))

    expect(run).toHaveBeenCalledOnce()
  })

  it('forgets Shift when the window loses focus, because the keyup would never arrive', () => {
    const { result } = setup()
    const run = vi.fn()
    shiftDown()
    fireEvent.blur(window)

    act(() => result.current.press('delete', 'a', run))

    expect(run).toHaveBeenCalledOnce()
  })

  it('drops the batch on Escape, but not on an Escape something else already consumed', () => {
    const { result } = setup()
    shiftDown()
    act(() => result.current.press('delete', 'a', vi.fn()))

    const consumed = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true })
    consumed.preventDefault()
    act(() => void window.dispatchEvent(consumed))
    expect(result.current.batch).not.toBeNull()

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(result.current.batch).toBeNull()
  })

  it('asks once, runs every marked row and empties the batch', async () => {
    const { result, confirm } = setup()
    const run = vi.fn().mockResolvedValue(undefined)
    shiftDown()
    act(() => result.current.press('delete', 'a', vi.fn()))
    act(() => result.current.press('delete', 'b', vi.fn()))

    let failed: string[] | null = null
    await act(async () => {
      failed = await result.current.apply({ confirm: QUESTION, run })
    })

    expect(confirm).toHaveBeenCalledOnce()
    expect(confirm).toHaveBeenCalledWith(QUESTION)
    expect(run.mock.calls).toEqual([['a'], ['b']])
    expect(failed).toEqual([])
    expect(result.current.batch).toBeNull()
  })

  it('keeps going past a failure and leaves only the failed rows marked', async () => {
    const { result } = setup()
    const run = vi.fn((id: string) => (id === 'b' ? Promise.reject(new Error('boom')) : Promise.resolve()))
    shiftDown()
    for (const id of ['a', 'b', 'c']) {
      act(() => result.current.press('delete', id, vi.fn()))
    }

    let failed: string[] | null = null
    await act(async () => {
      failed = await result.current.apply({ confirm: QUESTION, run })
    })

    expect(run).toHaveBeenCalledTimes(3)
    expect(failed).toEqual(['b'])
    expect(result.current.batch).toEqual({ action: 'delete', ids: ['b'] })
  })

  it('runs nothing and keeps the batch when the confirmation is declined', async () => {
    const { result } = setup(false)
    const run = vi.fn()
    shiftDown()
    act(() => result.current.press('delete', 'a', vi.fn()))

    let failed: string[] | null = []
    await act(async () => {
      failed = await result.current.apply({ confirm: QUESTION, run })
    })

    expect(failed).toBeNull()
    expect(run).not.toHaveBeenCalled()
    expect(result.current.batch).toEqual({ action: 'delete', ids: ['a'] })
  })
})
