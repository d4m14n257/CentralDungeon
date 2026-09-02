import { useEffect, useState } from 'react'

/** Filtros y buscadores: retrasa el valor para no pedirle al servidor una consulta por tecla (arquitectura.md 3.3). */
export function useDebounce<T>(value: T, delay = 400): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
