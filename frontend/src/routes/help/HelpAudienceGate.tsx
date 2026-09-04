import type { ReactNode } from 'react'

import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import type { HelpAudience } from '@/config/paths'
import { useMe } from '@/features/users'
import type { User } from '@/features/users'

/**
 * Who reads each role's help (decisiones.md #169, #170): **only somebody holding that role**, with
 * `Owner` as the one exception — it is an admin with more privileges and reads everything.
 *
 * The Master context also opens with a live row in `masters`, the same way `ContextSwitcher` does
 * (#135): somebody running a single table, assigned without the role, needs the master's help just
 * as much.
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
 * This is **not security**: the help is fixed text and there is nothing to protect (#103). It is
 * relevance — the help for a role you do not hold is of no use, and mixing it with yours is noise.
 */
export function HelpAudienceGate({ audience, children }: { audience: HelpAudience; children: ReactNode }) {
  const { data: me, isPending } = useMe()

  if (isPending) return <Skeleton className="h-40 w-full" />
  if (!me || !canReadHelp(audience, me)) return <ForbiddenState />
  return <>{children}</>
}
