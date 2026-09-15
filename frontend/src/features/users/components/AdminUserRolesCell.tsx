import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import { PLATFORM_ROLES } from '../roles'
import type { PlatformRole } from '../types'

/**
 * How each role looks. The classes are written out in full and statically on purpose — Tailwind 4
 * scans the source for literals and cannot see a class name built from a template string.
 *
 * A `Record` over `PlatformRole` rather than a lookup with a fallback, so a fifth role could not be
 * added without deciding how it looks (arquitectura.md §3.2, regla 9).
 *
 * `Admin` and `Owner` read differently from the other two because they are a different kind of
 * thing: they are the rank (#169), and an admin scanning this column is looking for exactly them.
 * The colour is never the only carrier — the label is always there (frontend-diseno.md §3).
 */
const ROLE_CLASSES: Record<PlatformRole, string> = {
  Player: 'bg-raised text-fg-muted border-border',
  Master: 'bg-raised text-fg-muted border-border',
  Admin: 'bg-raised text-brand-fg border-brand-400',
  Owner: 'bg-raised text-brand-fg border-brand-400',
}

/**
 * Somebody's active roles, as chips, **in the order of #165** — Player, Master, Admin, Owner.
 *
 * It sorts rather than trusting what arrived. The API sends them in this order already, and the
 * point of sorting anyway is that the column reads the same on every row whatever the server's
 * ordering happens to be: a listing where one row says "Master · Player" and the next says
 * "Player · Master" makes an admin compare two rows character by character.
 *
 * An account with no roles at all **says so in words**, and this is the one place on the screen that
 * fills an empty value in. It is a real state — every account is *created* with `Player` (#38),
 * which is a statement about signup and not an invariant, so a role can be revoked down to none —
 * and it is a state somebody *decided*, unlike the blank name or country next to it, which are
 * fields nobody got around to filling. On a phone, where the row is a card and the label sits beside
 * the value, an empty cell there reads as a component that failed rather than as an answer.
 *
 * @param props.roles the account's active roles, in any order
 */
export function AdminUserRolesCell({ roles }: { roles: readonly PlatformRole[] }) {
  const { t } = useTranslation('admin')
  const ordered = PLATFORM_ROLES.filter((role) => roles.includes(role))

  if (ordered.length === 0) {
    return <span className="text-fg-subtle text-xs">{t('users.noRoles')}</span>
  }

  return (
    <ul className="flex flex-wrap justify-end gap-1 md:justify-start">
      {ordered.map((role) => (
        <li key={role}>
          <span className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium', ROLE_CLASSES[role])}>
            {t(`users.roles.${role}`)}
          </span>
        </li>
      ))}
    </ul>
  )
}
