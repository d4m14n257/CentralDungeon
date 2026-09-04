import { useEffect, useRef, type RefObject } from 'react'
import { useLocation } from 'react-router'

/**
 * Scrolls to this element when the URL's `#ref` names it (decisiones.md #168).
 *
 * Two reasons the element takes care of it rather than the screen containing it: React Router
 * navigates without a reload, so the browser's own jump to the anchor never happens; and pages load
 * with `lazy`, so an effect in the parent runs **before** the section exists in the DOM and finds
 * nothing to jump to.
 *
 * It depends on `key` as well as on the hash, so that coming back to the same anchor from another
 * screen jumps too.
 */
export function useScrollOnHash<T extends HTMLElement>(id: string): RefObject<T | null> {
  const ref = useRef<T>(null)
  const { hash, key } = useLocation()

  useEffect(() => {
    if (hash.slice(1) !== id) return
    ref.current?.scrollIntoView({ block: 'start' })
  }, [hash, key, id])

  return ref
}
