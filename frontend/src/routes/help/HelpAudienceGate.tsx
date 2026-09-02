import type { ReactNode } from 'react'

import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import type { HelpAudience } from '@/config/paths'
import { useMe } from '@/features/users'
import type { User } from '@/features/users'

/**
 * Quién ve la ayuda de cada rol (decisiones.md #169, #170): **solo quien tiene ese rol**, con
 * `Owner` como única excepción — es un admin con más privilegios y ve todo.
 *
 * El contexto Master se abre también con una fila viva en `masters`, igual que el `ContextSwitcher`
 * (#135): a un master de una sola mesa, asignado sin el rol, la ayuda de master le sirve igual.
 */
export function canReadHelp(audience: HelpAudience, me: User): boolean {
  if (me.roles.includes('Owner')) return true
  switch (audience) {
    case 'players':
      return me.roles.includes('Player')
    case 'masters':
      return me.roles.includes('Master') || me.hasManagedTables
    case 'admins':
      return me.roles.includes('Admin')
  }
}

/**
 * Esto **no es seguridad**: la ayuda es texto fijo y no hay nada que proteger (#103). Es
 * relevancia — la ayuda de un rol que no tenés no te sirve, y mezclarla con la tuya es ruido.
 */
export function HelpAudienceGate({ audience, children }: { audience: HelpAudience; children: ReactNode }) {
  const { data: me, isPending } = useMe()

  if (isPending) return <Skeleton className="h-40 w-full" />
  if (!me || !canReadHelp(audience, me)) return <ForbiddenState />
  return <>{children}</>
}
