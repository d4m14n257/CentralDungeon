import type { ComponentProps, ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

interface IconActionProps extends Omit<ComponentProps<typeof Button>, 'children' | 'size'> {
  /** Qué hace la acción, en palabras. Es el tooltip **y** el nombre accesible: un icono solo no dice nada. */
  label: string
  /** El icono. Se marca `aria-hidden` acá mismo, así el lector de pantalla anuncia `label` y no dos cosas. */
  icon: ReactNode
}

/**
 * Un botón de icono con su tooltip, para las acciones de una fila o una ficha
 * (`frontend-diseno.md` §5) — el reemplazo del `ActionButtonDefault` del legacy.
 *
 * **El texto no es decoración**: viaja como `aria-label` además de como tooltip, porque un icono sin
 * nombre accesible es un botón que no existe para quien no lo ve. Y el tooltip no es la única forma
 * de saber qué hace: en táctil no hay hover, así que el nombre tiene que estar en el DOM igual.
 *
 * El `TooltipProvider` va acá adentro y no en un layout: con `delayDuration` en 0 no hay demora
 * compartida que ganar subiéndolo, y así el componente funciona en cualquier árbol —incluido el de
 * un test— sin pedirle a quien lo usa que recuerde montar nada.
 *
 * @param props.label qué hace la acción, ya pasado por `t()`
 * @param props.icon  el icono a mostrar
 */
export function IconAction({ label, icon, ...props }: IconActionProps) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button type="button" variant="ghost" size="icon" aria-label={label} {...props}>
            <span aria-hidden="true" className="inline-flex">
              {icon}
            </span>
          </Button>
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
