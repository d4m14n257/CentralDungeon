import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useBlocker } from 'react-router'

import { useConfirm } from '@/hooks/useConfirm'

/**
 * Asks before throwing away work in progress (#231).
 *
 * **Two ways out, and both are covered.** Navigating inside the application is intercepted with
 * `useBlocker`, which holds the transition until the answer comes back; closing the tab or
 * reloading is `beforeunload`, whose wording belongs to the browser and cannot be ours. Neither can
 * substitute for the other: the router never sees a tab closing, and `beforeunload` never fires on
 * a client-side navigation.
 *
 * **It only speaks when there is something to lose.** A form nobody touched asks nothing on the way
 * out - a confirmation that always appears stops being read, and this one has to be read.
 *
 * @param isDirty whether there are unsaved changes right now
 * @returns `allowNextNavigation`, to be called immediately before redirecting after a successful
 *          save. **A ref and not a piece of state on purpose**: the router asks whether to block
 *          during the same tick as the `navigate` call, before React has re-rendered, so a state
 *          flag set beside it is still `false` when the question is asked - and the screen ends up
 *          asking whether to discard work it has just finished saving
 */
export function useUnsavedChangesGuard(isDirty: boolean): { allowNextNavigation: () => void } {
  const { t } = useTranslation('common')
  const confirm = useConfirm()
  const released = useRef(false)

  // Same-path navigations - a tab switching, a `?query` changing - are not leaving, and blocking
  // them would ask the question for something that loses nothing.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => isDirty && !released.current && currentLocation.pathname !== nextLocation.pathname,
  )

  // The blocker is a fresh object on every render, so the effect below runs again while the
  // question is still on screen. This makes sure it is asked once per blocked transition and not
  // once per render.
  const asked = useRef(false)

  useEffect(() => {
    if (blocker.state !== 'blocked') {
      asked.current = false
      return
    }
    if (asked.current) {
      return
    }
    asked.current = true
    void (async () => {
      const leave = await confirm({
        title: t('unsavedChanges.title'),
        description: t('unsavedChanges.description'),
        confirmLabel: t('unsavedChanges.leave'),
        cancelLabel: t('unsavedChanges.stay'),
      })
      if (leave) {
        blocker.proceed()
      } else {
        blocker.reset()
      }
    })()
  }, [blocker, confirm, t])

  useEffect(() => {
    if (!isDirty) {
      return
    }
    function warn(event: BeforeUnloadEvent) {
      if (!released.current) {
        event.preventDefault()
      }
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [isDirty])

  return {
    allowNextNavigation: () => {
      released.current = true
    },
  }
}
