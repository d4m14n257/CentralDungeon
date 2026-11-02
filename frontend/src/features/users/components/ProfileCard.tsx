import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { AttendanceSummaryView } from '@/components/AttendanceSummaryView'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { playerHistoryPath } from '@/config/paths'

import type { Profile } from '../types'
import { FinishedTablesDialog } from './FinishedTablesDialog'

interface ProfileCardProps {
  /** The profile to show. */
  profile: Profile
  /** What sits at the right end of the card's header — on one's own profile, its «Cómo funciona» (ADR-21 §5). */
  actions?: ReactNode
  /** Whether this is the reader's own profile: the finished-tables count then leads to their history. */
  isOwn?: boolean
  /** Whether the reader holds the Player role, which is what lets them open a table's public detail. */
  viewerIsPlayer?: boolean
}

/**
 * A profile's upper half (diseno §4): initials, name, Discord handle, country, roles,
 * aggregate attendance and how many tables they played to the end (ADR-06 §8). `/profile` and `/users/:id` both mount this and nothing else yet.
 *
 * **The "Comentarios recibidos" heading of the wireframe is never drawn** (ADR-10 §5): it
 * belongs to F5, and a section titled that way with nothing underneath would read as a broken page
 * rather than as "nobody has commented on you yet" (skill `diseno` §5) — the same trap the
 * master dashboard's empty state already sidesteps. There is no placeholder, no "coming soon": the
 * card simply ends after attendance.
 *
 * **Attendance is three numbers, never a percentage** (ADR-08 §5, ADR-11 §10): it delegates to
 * {@link AttendanceSummaryView}, the one place that renders that shape, so a profile and a table's
 * own session tab can never drift into showing it two different ways.
 *
 * Every field but `id` can come back empty — no country set, no roles, nothing recorded — and none
 * of that is an error: onboarding guarantees a name, but a profile the reader is only now allowed to
 * see can still legitimately have nothing else to say about itself yet.
 *
 * **The finished-tables count opens** what it counts. On one's own profile it links to the player history,
 * but only for an account with the Player role (without it that screen would bounce them, so it stays a
 * plain number). On somebody else's it opens a dialog listing their finished tables, whose names link to
 * the public detail under the same condition.
 *
 * @param props.profile        the profile to show
 * @param props.actions        what sits at the right end of the header
 * @param props.isOwn          whether it is the reader's own profile
 * @param props.viewerIsPlayer whether the reader holds the Player role
 */
export function ProfileCard({ profile, actions, isOwn = false, viewerIsPlayer = false }: ProfileCardProps) {
  const { t } = useTranslation('users')
  const [tablesOpen, setTablesOpen] = useState(false)
  const label = profile.name ?? t('profile.noName')
  const initials = label
    .split(' ')
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()

  const finishedTablesText = t('summary.finishedTablesValue', { count: profile.finishedTables })

  return (
    <div className="border-border-strong bg-surface rounded-xl border p-6">
      <div className="flex items-start gap-4">
        <Avatar size="lg">
          <AvatarFallback className="text-lg font-semibold">{initials}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1 space-y-1.5">
          <h1 className="page-title">{label}</h1>
          <div className="text-fg-muted flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            {/* How the community finds them on the server (ADR-06 §8). */}
            <span>{t('profile.discordHandle', { handle: profile.discordUsername })}</span>
            {/* No fallback text for a missing country: a person who never set one is not an error,
                and "no country" is not information anybody asked for (skill `diseno` §5). */}
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
        {actions && <div className="shrink-0">{actions}</div>}
      </div>

      <div className="border-border mt-4 border-t pt-4">
        <h2 className="section-label">{t('profile.attendanceTitle')}</h2>
        <div className="mt-1.5">
          <AttendanceSummaryView summary={profile.attendance} />
        </div>
        <p className="text-fg-muted mt-2 text-sm">
          {profile.finishedTables === 0 || (isOwn && !viewerIsPlayer) ? (
            finishedTablesText
          ) : isOwn ? (
            <Link to={playerHistoryPath()} className="hover:text-fg underline">
              {finishedTablesText}
            </Link>
          ) : (
            <button type="button" className="hover:text-fg cursor-pointer underline" onClick={() => setTablesOpen(true)}>
              {finishedTablesText}
            </button>
          )}
        </p>
        {!isOwn && tablesOpen && (
          <FinishedTablesDialog userId={profile.id} canOpenTables={viewerIsPlayer} open onOpenChange={setTablesOpen} />
        )}
      </div>
    </div>
  )
}
