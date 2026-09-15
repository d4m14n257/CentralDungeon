import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'

import { DataTable, type DataTableColumn } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { PaginationControls } from '@/components/PaginationControls'
import { SearchQueryInput } from '@/components/SearchQueryInput'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import {
  AdminUserRolesCell,
  BlockUserDialog,
  RoleChangeDialog,
  UserAdminHistory,
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
 * One of the wide tables of frontend-diseno.md §5.b: below `md` it stops being a table and each row
 * becomes a card, built from the same column definitions — never horizontal scroll.
 *
 * **No role guard in front of it** (#103): somebody who forces the route without the role gets a
 * `403` from the backend and lands on `ForbiddenState`, which is an explanation rather than a blank
 * page.
 */
export function AdminUsersPage() {
  const { t, i18n } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const timeZone = browserTimeZone()

  const page = Number(searchParams.get('page') ?? '0')

  // The box holds a structured value; what travels - to the URL and to the API - is the raw string
  // of #164. Hydrating from `?q=` on mount is what makes a filtered view linkable (#185). One list
  // of commands, given to the box, used to read the URL and to build the help (#240).
  const fields = useMemo(() => adminUserSearchFields(t), [t])
  const search = useSearchQuery({ fields, initialQuery: searchParams.get('q') ?? '', onQueryChange: (query) => updateParams({ q: query }) })

  const { data, isPending, isLoadingError, error, refetch } = useAdminUsers(search.debouncedQuery, page)
  const { grantableRoles, canChangeStatus } = useUserAdminCapabilities()

  const roleDialog = useDisclosure<AdminUserSummary>()
  const statusDialog = useDisclosure<AdminUserSummary>()
  const historyDialog = useDisclosure<AdminUserSummary>()

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
    { id: 'discordUsername', header: t('users.columns.discordUsername'), role: 'title', cell: (user) => user.discordUsername },
    { id: 'status', header: t('users.columns.status'), role: 'badge', cell: (user) => <UserStatusBadge status={user.status} /> },
    // No fallback text for a missing name or country: somebody who never set one is not an error,
    // and "no country" is not information anybody asked for (frontend-diseno.md §5).
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
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('users.title')}</h1>
        <HelpLink section="admins.roles" className="text-sm">
          {t('users.helpLink')}
        </HelpLink>
      </div>
      <p className="text-fg-muted text-sm">{t('users.description')}</p>

      <SearchQueryInput
        fields={search.fields}
        value={search.value}
        onChange={search.onChange}
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
              <div className="flex flex-wrap justify-end gap-2">
                {/* Absent, never greyed out: an admin has no role to hand out that this account can
                    take, so there is nothing to press. */}
                {grantableRoles.length > 0 && (
                  <Button size="sm" variant="outline" onClick={() => roleDialog.open(user)}>
                    {t('users.changeRoles')}
                  </Button>
                )}
                {canChangeStatus(user) && (
                  <Button size="sm" variant={user.status === 'Blocked' ? 'outline' : 'destructive'} onClick={() => statusDialog.open(user)}>
                    {user.status === 'Blocked' ? t('users.unblock') : t('users.block')}
                  </Button>
                )}
                {/* Reading the record is not an action on the account, so it is offered on every row
                    - including the ones nobody may touch. */}
                <Button size="sm" variant="ghost" onClick={() => historyDialog.open(user)}>
                  {t('users.history')}
                </Button>
              </div>
            )}
          />
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onPageChange={(next) => updateParams({ page: String(next) })}
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

      <Dialog open={historyDialog.isOpen} onOpenChange={(open) => !open && historyDialog.close()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('users.historyDialogTitle', { name: historyDialog.item?.discordUsername ?? '' })}</DialogTitle>
            <DialogDescription>{t('users.historyDialogDescription')}</DialogDescription>
          </DialogHeader>
          {/* An id and not the row (§3.1.5): the panel asks for its own data, so the header it draws
              is the account as it is now rather than as the page last saw it. */}
          {historyDialog.item && <UserAdminHistory userId={historyDialog.item.id} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

export { AdminUsersPage as Component }
