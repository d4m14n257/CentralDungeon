import { useTranslation } from 'react-i18next'

import { AttendanceSummaryView } from '@/components/AttendanceSummaryView'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'

import type { Profile } from '../types'

interface ProfileCardProps {
  /** The profile to show. */
  profile: Profile
}

/**
 * A profile's upper half (frontend-diseno.md §4): initials, name, country, roles and aggregate
 * attendance. `/player/profile` and `/player/users/:id` both mount this and nothing else yet.
 *
 * **The "Comentarios recibidos" heading of the wireframe is never drawn** (decisiones.md #248): it
 * belongs to F5, and a section titled that way with nothing underneath would read as a broken page
 * rather than as "nobody has commented on you yet" (frontend-diseno.md §5) — the same trap the
 * master dashboard's empty state already sidesteps. There is no placeholder, no "coming soon": the
 * card simply ends after attendance.
 *
 * **Attendance is three numbers, never a percentage** (#137, #98): it delegates to
 * {@link AttendanceSummaryView}, the one place that renders that shape, so a profile and a table's
 * own session tab can never drift into showing it two different ways.
 *
 * Every field but `id` can come back empty — no country set, no roles, nothing recorded — and none
 * of that is an error: onboarding guarantees a name, but a profile the reader is only now allowed to
 * see can still legitimately have nothing else to say about itself yet.
 *
 * @param props.profile the profile to show
 */
export function ProfileCard({ profile }: ProfileCardProps) {
  const { t } = useTranslation('users')
  const label = profile.name ?? t('profile.noName')
  const initials = label
    .split(' ')
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="border-border-strong bg-surface rounded-xl border p-6">
      <div className="flex items-start gap-4">
        <Avatar size="lg">
          <AvatarFallback className="text-lg font-semibold">{initials}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1 space-y-1.5">
          <h1 className="font-serif text-2xl font-semibold">{label}</h1>
          <div className="text-fg-muted flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            {/* No fallback text for a missing country: a person who never set one is not an error,
                and "no country" is not information anybody asked for (frontend-diseno.md §5). */}
            {profile.country && <span>{profile.country}</span>}
            {profile.roles.length > 0 && (
              <span className="flex flex-wrap gap-1">
                {profile.roles.map((role) => (
                  <Badge key={role} variant="outline">
                    {t(`profile.roles.${role}`, { defaultValue: role })}
                  </Badge>
                ))}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="border-border mt-4 border-t pt-4">
        <h2 className="text-fg-subtle text-xs font-medium tracking-wide uppercase">{t('profile.attendanceTitle')}</h2>
        <div className="mt-1.5">
          <AttendanceSummaryView summary={profile.attendance} />
        </div>
      </div>
    </div>
  )
}
