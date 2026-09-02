import { useEffect, useRef, type RefObject } from 'react'
import { useLocation } from 'react-router'

/**
 * Lleva la pantalla a este elemento cuando el `#ref` de la URL lo nombra (decisiones.md #168).
 *
 * Dos razones para que sea el propio elemento el que se ocupe, y no la pantalla que lo contiene:
 * React Router navega sin recargar, así que el salto nativo del navegador al ancla nunca ocurre; y
 * las páginas cargan con `lazy`, así que un efecto en el padre corre **antes** de que la sección
 * exista en el DOM y no encuentra nada a dónde saltar.
 *
 * Depende de `key` además del hash para que volver al mismo ancla desde otra pantalla también salte.
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
