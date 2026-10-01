import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'

import { AttendanceSummaryView } from '@/components/AttendanceSummaryView'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PageHeader } from '@/components/PageHeader'
import { PaginationControls } from '@/components/PaginationControls'
import { SectionHeader } from '@/components/SectionHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { adminTableDetailPath, adminUsersPath } from '@/config/paths'
import { HelpLink } from '@/features/help'
import { ALL_TABLE_STATUSES, TableStatusBadge, type GameTableStatus } from '@/features/tables'
import {
  AdminUserRolesCell,
  BlockUserDialog,
  RoleChangeDialog,
  UserAdminHistory,
  UserStatusBadge,
  useAdminUser,
  useUserAdminCapabilities,
  useUserProfile,
  useUserTables,
} from '@/features/users'
import { useDisclosure } from '@/hooks/useDisclosure'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

/** Narrows the status the backend sent to one the tables feature knows how to badge. */
function isTableStatus(value: string): value is GameTableStatus {
  return (ALL_TABLE_STATUSES as readonly string[]).includes(value)
}

/**
 * The person's tables, one page at a time, each opening its admin view. Its own component because it
 * owns the page number and its own query (§3.1.5).
 */
function UserTablesSection({ userId }: { userId: string }) {
  const { t } = useTranslation('admin')
  const [page, setPage] = useState(0)
  const { data, isPending, isLoadingError, refetch } = useUserTables(userId, page)

  return (
    <section className="space-y-3">
      <SectionHeader title={t('users.detail.tablesTitle')} />
      {isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : isLoadingError ? (
        <ErrorState onRetry={() => void refetch()} />
      ) : data.content.length === 0 ? (
        <EmptyState title={t('users.detail.tablesEmptyTitle')} description={t('users.detail.tablesEmptyDescription')} />
      ) : (
        <>
          <ul className="list-divided">
            {data.content.map((table) => (
              <li key={table.tableId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                <Link to={adminTableDetailPath(table.tableId)} className="min-w-0 truncate text-sm hover:underline">
                  {table.tableName}
                </Link>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="text-fg-muted text-xs">{t(`users.detail.relation.${table.relation}`)}</span>
                  {isTableStatus(table.tableStatus) && <TableStatusBadge status={table.tableStatus} />}
                </span>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <PaginationControls page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={setPage} />
          )}
        </>
      )}
    </section>
  )
}

/**
 * `/admin/users/:id` — one account, whole, as an admin reads it (#284).
 *
 * **Everything about the person, and the actions on them, in one place.** The list of `/admin/users`
 * answers "who is there"; this answers "who is this": their profile (Discord handle, country,
 * attendance, tables played to the end), their standing (status and roles, with the two actions that
 * change them), every table they run, play at or applied to - each opening the admin's view of it -
 * and what admins did to the account and why. It replaced the history dialog of the list, which showed
 * the last of those and nothing else.
 *
 * Reached from the list and from any person on a table an admin is reading (#284). **No karma and no
 * comments yet** (#248): both are F5, and when they arrive they belong here.
 *
 * Each block reads its own data from the id (§3.1.5): the account from the admin read, the profile
 * from the profile read - which an admin always passes (#45) - the tables and the history from theirs.
 */
export function AdminUserDetailPage() {
  const { t, i18n } = useTranslation('admin')
  const { id } = useParams<{ id: string }>()
  const userId = id ?? ''
  const account = useAdminUser(userId, userId.length > 0)
  const { data: profile } = useUserProfile(userId)
  const { grantableRoles, canChangeStatus } = useUserAdminCapabilities()
  const roleDialog = useDisclosure()
  const statusDialog = useDisclosure()

  if (account.isPending) {
    return <Skeleton className="h-48 w-full" />
  }
  if (account.error instanceof ApiError && account.error.status === 403) {
    return <ForbiddenState />
  }
  if (account.isLoadingError || !account.data) {
    return <ErrorState onRetry={() => void account.refetch()} />
  }

  const user = account.data
  const timeZone = browserTimeZone()
  const facts = [
    { label: t('users.detail.discord'), value: user.discordUsername },
    { label: t('users.detail.country'), value: user.country ?? t('users.detail.notSet') },
    { label: t('users.detail.memberSince'), value: formatDate(user.createdAt, i18n.language, timeZone) },
    {
      label: t('users.detail.finishedTables'),
      value: profile === undefined ? '…' : String(profile.finishedTables),
    },
  ]

  return (
    <div className="space-y-8">
      <PageHeader
        title={user.name ?? user.discordUsername}
        badge={<UserStatusBadge status={user.status} />}
        back={{ to: adminUsersPath(), label: t('users.detail.back') }}
        help="admins.roles"
      />

      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {facts.map((fact) => (
          <div key={fact.label}>
            <dt className="section-label">{fact.label}</dt>
            <dd className="mt-1 text-sm">{fact.value}</dd>
          </div>
        ))}
      </dl>

      <section className="space-y-3">
        <SectionHeader
          title={t('users.detail.standingTitle')}
          actions={
            <>
              {/* Absent, never greyed out: the same two checks the list's row applies (principio 2). */}
              {grantableRoles.length > 0 && (
                <Button type="button" size="sm" variant="outline" onClick={() => roleDialog.open()}>
                  {t('users.changeRoles')}
                </Button>
              )}
              {canChangeStatus(user) && (
                <Button
                  type="button"
                  size="sm"
                  variant={user.status === 'Blocked' ? 'outline' : 'destructive'}
                  onClick={() => statusDialog.open()}
                >
                  {user.status === 'Blocked' ? t('users.unblock') : t('users.block')}
                </Button>
              )}
            </>
          }
        />
        <AdminUserRolesCell roles={user.roles} />
      </section>

      <section className="space-y-3">
        <SectionHeader title={t('users.detail.attendanceTitle')} />
        {profile ? <AttendanceSummaryView summary={profile.attendance} /> : <Skeleton className="h-6 w-48" />}
      </section>

      <UserTablesSection userId={userId} />

      <section className="space-y-3">
        <SectionHeader title={t('users.detail.historyTitle')} />
        <UserAdminHistory userId={userId} />
      </section>

      <RoleChangeDialog
        user={roleDialog.isOpen ? user : null}
        grantableRoles={grantableRoles}
        open={roleDialog.isOpen}
        onOpenChange={(open) => !open && roleDialog.close()}
        help={
          <p className="text-fg-subtle text-xs">
            {t('users.roleHelpHint')} <HelpLink section="admins.roles">{t('users.roleHelpLink')}</HelpLink>
          </p>
        }
      />
      <BlockUserDialog
        user={statusDialog.isOpen ? user : null}
        open={statusDialog.isOpen}
        onOpenChange={(open) => !open && statusDialog.close()}
        help={
          <p className="text-fg-subtle text-xs">
            {t('users.blockHelpHint')} <HelpLink section="admins.blocking">{t('users.blockHelpLink')}</HelpLink>
          </p>
        }
      />
    </div>
  )
}

export { AdminUserDetailPage as Component }
