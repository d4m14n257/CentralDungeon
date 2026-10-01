import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Ban, IdCard, LockOpen, UserCog } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router'

import { IconAction } from '@/components/IconAction'
import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/PageHeader'
import { adminPageSizeFrom, pageSize } from '@/config/pagination'
import { adminUserDetailPath } from '@/config/paths'
import { HelpLink } from '@/features/help'
import {
  AdminUserRolesCell,
  BlockUserDialog,
  RoleChangeDialog,
  UserStatusBadge,
  adminUserSearchFields,
  useAdminUsers,
  useUserAdminCapabilities,
  type AdminUserSummary,
} from '@/features/users'
import { useDisclosure } from '@/hooks/useDisclosure'
import { useSearchQuery } from '@/hooks/useSearchQuery'
import { browserTimeZone, formatDate } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * `/admin/users` — every account the platform has, and the two things an administrator can do to
 * one: move its roles and close it (F3.1).
 *
 * **It is the one screen that sees blocked accounts.** Everywhere else, `Allowed` is the only status
 * that can appear — the picker's endpoint filters the rest out and a blocked person cannot sign in —
 * so the `/status` command and the status column only mean something here.
 *
 * **Which buttons exist is a capability question, asked once** (`useUserAdminCapabilities`) and never
 * a loose `if` in this JSX. An admin finds no way at all to hand out `Admin` or `Owner`, and nobody
 * is offered a block on an account that holds either: not a greyed-out button, not one that fails
 * when pressed — the button is not there (principio 2 de frontend-diseno.md §1). The backend refuses
 * the same things independently, endpoint by endpoint (#103), which is what makes this a display
 * decision rather than the security.
 *
 * **What was searched and which page are in the URL** (#185), like `/admin/catalogs` and
 * `/admin/files`: a row is something one admin sends to another, and state that only lives in
 * `useState` cannot be linked to.
 *
 * One of the wide tables of skill `diseno` §5.b: below `md` it stops being a table and each row
 * becomes a card, built from the same column definitions — never horizontal scroll.
 *
 * **Behind the admin context's guard** (#269): an account without `Admin` or `Owner` is sent home by
 * `AdminLayout` before this paints. `ForbiddenState` stays for a `403` that still arrives - the
 * backend authorizes on its own (#103) and the page must not go blank if it refuses.
 */
export function AdminUsersPage() {
  const { t, i18n } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const timeZone = browserTimeZone()

  const page = Number(searchParams.get('page') ?? '0')
  // Rows per page (#271): in the URL like the page, so a link carries the view it was sent from.
  const size = adminPageSizeFrom(searchParams.get('size'))

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of #164. Hydrating from `?q=` on mount is what makes a filtered view linkable (#185). One list
  // of commands, given to the box, used to read the URL and to build the help (#240).
  const fields = useMemo(() => adminUserSearchFields(t), [t])
  const search = useSearchQuery({ fields, initialQuery: searchParams.get('q') ?? '', onQueryChange: (query) => updateParams({ q: query }) })

  const { data, isPending, isLoadingError, error, refetch } = useAdminUsers(search.query, page, size)
  const { grantableRoles, canChangeStatus } = useUserAdminCapabilities()

  const roleDialog = useDisclosure<AdminUserSummary>()
  const statusDialog = useDisclosure<AdminUserSummary>()
  const navigate = useNavigate()

  /** Writes the screen's state into the URL, resetting the page whenever the search changes. */
  function updateParams(changes: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '') next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  const columns: DataTableColumn<AdminUserSummary>[] = [
    // The handle opens the account (#284): the row is where an admin finds somebody, the record is where they read them.
    {
      id: 'discordUsername',
      header: t('users.columns.discordUsername'),
      role: 'title',
      cell: (user) => (
        <Link to={adminUserDetailPath(user.id)} className="hover:underline">
          {user.discordUsername}
        </Link>
      ),
    },
    { id: 'status', header: t('users.columns.status'), role: 'badge', cell: (user) => <UserStatusBadge status={user.status} /> },
    // No fallback text for a missing name or country: somebody who never set one is not an error,
    // and "no country" is not information anybody asked for (skill `diseno` §5).
    { id: 'name', header: t('users.columns.name'), cell: (user) => user.name ?? '' },
    { id: 'country', header: t('users.columns.country'), cell: (user) => user.country ?? '' },
    { id: 'roles', header: t('users.columns.roles'), cell: (user) => <AdminUserRolesCell roles={user.roles} /> },
    {
      id: 'createdAt',
      header: t('users.columns.createdAt'),
      // Earns its place in a wide table and nothing else: on a phone card it is the line nobody
      // came for.
      role: 'hidden',
      cell: (user) => formatDate(user.createdAt, i18n.language, timeZone),
    },
  ]

  return (
    <div className="space-y-4">
      <PageHeader title={t('users.title')} description={t('users.description')} help="admins.roles" />

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
        searchedQuery={search.query}
        onSearch={search.onSearch}
        placeholder={t('users.searchPlaceholder')}
        label={t('users.searchLabel')}
      />

      {isPending && <Skeleton className="h-64 w-full" />}
      {/* isLoadingError, not isError: a failed background refetch must not blank a table that is
          already showing rows (#150). */}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {data && data.content.length === 0 && (
        <EmptyState
          title={search.query ? t('users.noResultsTitle') : t('users.emptyTitle')}
          description={search.query ? t('users.noResultsDescription') : t('users.emptyDescription')}
        />
      )}
      {data && data.content.length > 0 && (
        <>
          <DataTable
            label={t('users.title')}
            columns={columns}
            rows={data.content}
            getRowId={(user) => user.id}
            renderActions={(user) => (
              <>
                {/* Absent, never greyed out: an admin has no role to hand out that this account can
                    take, so there is nothing to press. */}
                {grantableRoles.length > 0 && (
                  <IconAction icon={<UserCog className="size-4" />} label={t('users.changeRoles')} onClick={() => roleDialog.open(user)} />
                )}
                {canChangeStatus(user) && (
                  <IconAction
                    icon={user.status === 'Blocked' ? <LockOpen className="size-4" /> : <Ban className="size-4" />}
                    label={user.status === 'Blocked' ? t('users.unblock') : t('users.block')}
                    onClick={() => statusDialog.open(user)}
                    className={user.status === 'Blocked' ? undefined : 'text-destructive hover:text-destructive'}
                  />
                )}
                {/* Reading the record is not an action on the account, so it is offered on every row
                    - including the ones nobody may touch. It used to open the history alone; the
                    history now lives on the record with everything else (#284). */}
                <IconAction
                  icon={<IdCard className="size-4" />}
                  label={t('users.viewRecord')}
                  onClick={() => void navigate(adminUserDetailPath(user.id))}
                />
              </>
            )}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
            pageSize={size}
            // The default leaves the URL, like an empty search does; any change of size starts over
            // at the first page, which `updateParams` does for every change that is not the page.
            onPageSizeChange={(next) => updateParams({ size: next === pageSize.admin ? '' : String(next) })}
          />
        </>
      )}

      {/* The two dialogs get their help as a node rather than raising it themselves: `HelpLink`
          lives in `features/help`, and a feature never imports another one (§3.1.5). The screen is
          what may compose the two, so the explanation still sits inside the dialog that prompts it
          (#231) instead of in an index nobody opens. */}
      <RoleChangeDialog
        user={roleDialog.item ?? null}
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
        user={statusDialog.item ?? null}
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

export { AdminUsersPage as Component }
