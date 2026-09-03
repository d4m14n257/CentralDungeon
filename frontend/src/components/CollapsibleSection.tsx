import { ChevronDown } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

interface CollapsibleSectionProps {
  /** El título de la sección, ya pasado por `t()`. */
  title: string
  /** La línea que resume el contenido plegado, para no tener que abrirlo solo para saber qué hay. */
  summary?: ReactNode
  /** Las acciones de la cabecera. No abren ni cierran: viven al lado del control, no dentro de él. */
  actions?: ReactNode
  /** Si arranca abierta. La sección recuerda su estado mientras esté montada, no más allá. */
  defaultOpen?: boolean
  children: ReactNode
}

/**
 * Un bloque plegable con título, resumen y acciones en la cabecera
 * (`frontend-diseno.md` §5) — el patrón que el legacy repetía en `CardComponent` y `ListComponent`.
 *
 * **El control de plegado es un botón y las acciones están fuera de él.** Anidar un botón dentro de
 * otro es HTML inválido y, peor, vuelve impredecible qué pasa al tocar: quien quiere cancelar una
 * sesión no quiere además cerrar la ficha.
 *
 * El resumen existe para que plegado siga informando. Una sección que al cerrarse no dice nada
 * obliga a abrirlas todas, que es exactamente lo que plegar venía a evitar.
 *
 * @param props.title       el título
 * @param props.summary     qué mostrar en la cabecera cuando está plegada
 * @param props.actions     las acciones de la cabecera
 * @param props.defaultOpen si arranca abierta
 * @param props.children    el contenido plegable
 */
export function CollapsibleSection({ title, summary, actions, defaultOpen = false, children }: CollapsibleSectionProps) {
  const [open, setOpen] = useState(defaultOpen)
  const contentId = useId()

  return (
    <section className="border-border rounded-lg border">
      <div className="flex items-center gap-2 px-3 py-2">
        <button
          type="button"
          onClick={() => setOpen((current) => !current)}
          aria-expanded={open}
          aria-controls={contentId}
          className="flex min-w-0 flex-1 items-center gap-2 text-left"
        >
          <ChevronDown aria-hidden="true" className={cn('size-4 shrink-0 transition-transform', open ? 'rotate-0' : '-rotate-90')} />
          <span className="truncate text-sm font-medium">{title}</span>
          {summary && <span className="text-fg-muted truncate text-xs">{summary}</span>}
        </button>
        {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
      </div>
      {open && (
        <div id={contentId} className="border-border border-t px-3 py-3">
          {children}
        </div>
      )}
    </section>
  )
}
