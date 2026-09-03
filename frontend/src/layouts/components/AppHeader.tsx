import { Link } from 'react-router'

import { adminTablesPath, masterTablesPath } from '@/config/paths'
import { NotificationBell } from '@/features/notifications'
import { useMe } from '@/features/users'
import { useContextStore, type AppContext } from '@/stores/contextStore'

import { BrandMark } from './BrandMark'
import { ContextSwitcher } from './ContextSwitcher'
import { UserMenu } from './UserMenu'

/**
 * La barra del shell (frontend-diseno.md 2): wordmark, contexto, notificaciones y avatar.
 * Va sobre `surface`, no sobre el canvas — es lo que la separa del contenido sin una sombra.
 */
export function AppHeader() {
  const { data: me } = useMe()
  const { activeContext } = useContextStore()

  // El contexto Master aparece con el rol de plataforma o con al menos una fila viva en `masters`
  // (decisiones.md #135) - un master de una sola mesa, asignado sin el rol, sigue siendo master.
  const availableContexts: AppContext[] = []
  if (me?.roles.includes('Player')) availableContexts.push('player')
  if (me?.roles.includes('Master') || me?.hasManagedTables) availableContexts.push('master')
  if (me?.roles.includes('Admin') || me?.roles.includes('Owner')) availableContexts.push('admin')

  // El logo vuelve al "home" del contexto activo, igual que ContextSwitcher al cambiar de
  // contexto - #master todavía no existe (dashboard de Master, F1 #136), así que el destino en
  // ese contexto es /master/tables, el punto de entrada real de E1 (decisiones.md #151). Se
  // recalcula contra availableContexts de la cuenta actual, no se confía crudo en el valor
  // persistido: activeContext es un store con `persist` en localStorage, ajeno a qué cuenta está
  // logueada - cambiar de cuenta en el mismo navegador puede dejar pisado el contexto de la
  // sesión anterior (decisiones.md #156, hallazgo sin explotación real: solo desvía el link del
  // logo, ningún endpoint de /master confía en este valor).
  const effectiveContext: AppContext = availableContexts.includes(activeContext) ? activeContext : 'player'
  const homePath = effectiveContext === 'master' ? masterTablesPath() : effectiveContext === 'admin' ? adminTablesPath() : '/'

  return (
    <header className="border-border bg-surface sticky top-0 z-10 border-b">
      <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-3">
        <Link to={homePath}>
          <BrandMark />
        </Link>
        <ContextSwitcher availableContexts={availableContexts} />
        <span className="flex-1" />
        <NotificationBell />
        {me && <UserMenu displayName={me.name} />}
      </div>
    </header>
  )
}
