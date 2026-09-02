import type { ReactNode } from 'react'
import { useLocation } from 'react-router'

import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { useScrollOnHash } from '@/hooks/useScrollOnHash'
import { cn } from '@/lib/utils'

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
 * **La sección que la URL nombra queda resaltada** (#170): quien llega desde un link cae en medio
 * de una página larga, y sin una marca no sabe cuál de los bloques vino a leer. El resaltado dura
 * mientras el `#ref` la nombre — se apaga al navegar a otra, no con un temporizador que obligue a
 * leer rápido.
 *
 * El título va en un `h2` real y no en el `div` de `CardTitle`: es una página para leer.
 */
export function HelpSection({ id, title, children }: HelpSectionProps) {
  const ref = useScrollOnHash<HTMLElement>(id)
  const { hash } = useLocation()
  const isTargeted = hash.slice(1) === id

  return (
    <section id={id} ref={ref} className="scroll-mt-20" aria-current={isTargeted ? 'location' : undefined}>
      <Card className={cn(isTargeted && 'border-primary ring-primary/30 ring-2')}>
        <CardHeader>
          <h2 className="font-serif text-lg leading-none font-semibold">{title}</h2>
        </CardHeader>
        <CardContent className="space-y-4">{children}</CardContent>
      </Card>
    </section>
  )
}

/** Lista de puntos de un bloque: lo que hay que saber. */
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

/**
 * Los pasos de una tarea: cómo se hace, en orden. La ayuda no describe nada más que exista, enseña
 * a usarlo (#170), y para eso el paso numerado es la forma que se sigue con el sitio abierto al lado.
 */
export function HelpSteps({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <ol className="space-y-2 text-sm">
        {items.map((item, index) => (
          <li key={item} className="flex gap-3">
            <span className="bg-primary/15 text-fg flex size-5 shrink-0 items-center justify-center rounded-full text-xs font-medium">
              {index + 1}
            </span>
            <span className="pt-0.5">{item}</span>
          </li>
        ))}
      </ol>
    </div>
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
