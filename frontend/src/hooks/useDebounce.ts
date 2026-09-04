import { useEffect, useState } from 'react'

/** Filters and search boxes: delays the value so the server is not asked one query per keystroke (arquitectura.md 3.3). */
export function useDebounce<T>(value: T, delay = 400): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
