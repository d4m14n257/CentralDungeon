import { useCallback, useEffect, useRef, useState } from 'react'

import { useConfirm, type ConfirmOptions } from './useConfirm'

/** The rows picked so far, all for the same action. */
export interface ShiftBatch {
  /** Which row action the rows were picked for. */
  action: string
  /** The identities of the marked rows, in the order they were marked. */
  ids: string[]
}

/** What applying a batch needs from the screen: the question to ask once, and how to do one row. */
export interface ApplyBatchOptions {
  /** The single confirmation: it names the action and how many rows it reaches. */
  confirm: ConfirmOptions
  /** Performs the action on one row. A rejection counts that row as failed and moves on to the next. */
  run: (id: string) => Promise<unknown>
}

/**
 * Doing the same row action on several rows with one confirmation at the end.
 *
 * A click on a row action behaves as always — {@link press} just runs it — **unless Shift is held**:
 * then the click runs nothing and marks that row for that action instead. Clicking another marked
 * row's action unmarks it; clicking a different action starts a new batch for it, dropping the old
 * one. When the person is done marking, {@link apply} asks once, naming the action and the count, and
 * runs the rows one by one.
 *
 * Shift is tracked with `keydown` and `keyup` on `window`, and a lost window focus clears it, because
 * the `keyup` of a Shift released while another window had focus never arrives. Escape drops the
 * batch, unless something already consumed that Escape — a confirmation closing itself must not also
 * throw away what it was about to apply.
 *
 * It knows nothing about files or any other domain: an action is a name and a row is an id.
 *
 * @returns the batch (null when nothing is marked), `press`, `isMarked`, `clear` and `apply`
 */
export function useShiftBatch() {
  const confirm = useConfirm()
  const [batch, setBatch] = useState<ShiftBatch | null>(null)
  const shiftHeld = useRef(false)

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Shift') {
        shiftHeld.current = event.type === 'keydown'
      } else if (event.key === 'Escape' && event.type === 'keydown' && !event.defaultPrevented) {
        setBatch(null)
      }
    }
    function onBlur() {
      shiftHeld.current = false
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('keyup', onKey)
    window.addEventListener('blur', onBlur)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('keyup', onKey)
      window.removeEventListener('blur', onBlur)
    }
  }, [])

  /**
   * The click handler of a row action.
   *
   * @param action which row action this is
   * @param id     the row it was clicked on
   * @param run    what the action does on its own, called when Shift is not held
   */
  const press = useCallback((action: string, id: string, run: () => void) => {
    if (!shiftHeld.current) {
      run()
      return
    }
    setBatch((current) => {
      if (current?.action !== action) {
        return { action, ids: [id] }
      }
      const ids = current.ids.includes(id) ? current.ids.filter((marked) => marked !== id) : [...current.ids, id]
      return ids.length > 0 ? { action, ids } : null
    })
  }, [])

  /**
   * Whether a row is marked for an action.
   *
   * @param action which row action
   * @param id     the row
   */
  const isMarked = useCallback((action: string, id: string) => batch?.action === action && batch.ids.includes(id), [batch])

  /** Drops the batch without running anything. */
  const clear = useCallback(() => setBatch(null), [])

  /**
   * Asks once and then runs the marked rows, one after another.
   *
   * What went through stays done. Rows that failed stay marked, so the person can apply again to
   * retry just those; with no failures the batch is emptied.
   *
   * @param options the confirmation to show and how to run one row
   * @returns the ids that failed, or null when there was nothing to apply or the person declined
   */
  const apply = useCallback(
    async ({ confirm: question, run }: ApplyBatchOptions): Promise<string[] | null> => {
      if (!batch || !(await confirm(question))) {
        return null
      }
      const failed: string[] = []
      for (const id of batch.ids) {
        try {
          await run(id)
        } catch {
          failed.push(id)
        }
      }
      setBatch(failed.length > 0 ? { action: batch.action, ids: failed } : null)
      return failed
    },
    [batch, confirm],
  )

  return { batch, press, isMarked, clear, apply }
}
