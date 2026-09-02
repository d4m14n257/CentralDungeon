import type { ReactNode } from 'react'

import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { useScrollOnHash } from '@/hooks/useScrollOnHash'

interface HelpSectionProps {
  /** El `#ref` con el que se enlaza desde el resto de la aplicación (decisiones.md #168). */
  id: string
  title: string
  children: ReactNode
}

/**
 * Un bloque de la ayuda. El `id` es su dirección: `/help#search`, `/help/admins#assign-masters`.
 * Cambiarlo rompe los links que ya existen, así que se trata como parte del contrato de la
 * pantalla y no como un detalle de maquetado.
 *
 * El título va en un `h2` real y no en el `div` de `CardTitle`: es una página para leer.
 */
export function HelpSection({ id, title, children }: HelpSectionProps) {
  const ref = useScrollOnHash<HTMLElement>(id)

  return (
    <section id={id} ref={ref} className="scroll-mt-20">
      <Card>
        <CardHeader>
          <h2 className="font-serif text-lg leading-none font-semibold">{title}</h2>
        </CardHeader>
        <CardContent className="space-y-4">{children}</CardContent>
      </Card>
    </section>
  )
}

/** Lista de puntos de un bloque: la forma que tiene casi toda la ayuda. */
export function HelpList({ items }: { items: string[] }) {
  return (
    <ul className="space-y-2 text-sm">
      {items.map((item) => (
        <li key={item} className="flex gap-2">
          <span className="text-fg-subtle" aria-hidden>
            ·
          </span>
          <span>{item}</span>
        </li>
      ))}
    </ul>
  )
}

/** Pares término/explicación: estados, roles, cualquier cosa con nombre propio. */
export function HelpTerms({ terms, termWidth = 'w-40' }: { terms: { term: string; description: string }[]; termWidth?: string }) {
  return (
    <dl className="space-y-2 text-sm">
      {terms.map(({ term, description }) => (
        <div key={term} className="flex flex-col gap-0.5 sm:flex-row sm:gap-3">
          <dt className={`${termWidth} shrink-0 font-medium`}>{term}</dt>
          <dd className="text-fg-muted">{description}</dd>
        </div>
      ))}
    </dl>
  )
}
