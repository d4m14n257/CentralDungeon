import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { browserTimeZone, formatDateTime } from '@/lib/date'
import { ApiError } from '@/types/api'

import { useAdminUserHistory } from '../api/useAdminUserHistory'
import type { UserAdminChange } from '../types'

/**
 * One account's administration record: every step that got it where it stands, and why.
 *
 * **It is what keeps the two audit tables from being write-only.** `user_role_changes` and
 * `user_status_changes` exist because `audit_logs` is F6 and a role change with no record is a
 * change nobody can review; a record nothing reads back would have been the same gap one layer down.
 *
 * It takes an **id** and asks for its own data (arquitectura §3.1.5). It used to open in a dialog from
 * the list with the account's current status and roles as its header; since #284 it is a section of
 * the account's record on /admin/users/:id, which shows both right above it, so the header went with
 * the dialog.
 *
 * The four states are its own (#150): the listing around it has loaded by definition, and this is a
 * second question to the server that can fail, come back empty, or be refused on its own.
 *
 * @param props.userId the account whose record to show
 */
export function UserAdminHistory({ userId }: { userId: string }) {
  const { t, i18n } = useTranslation('admin')
  const timeZone = browserTimeZone()
  const history = useAdminUserHistory(userId)

  if (history.error instanceof ApiError && history.error.status === 403) {
    return <ForbiddenState />
  }

  // isLoadingError, not isError: a background refetch that fails must not blank a panel that is
  // already showing something (#150).
  if (history.isLoadingError) {
    return <ErrorState onRetry={() => void history.refetch()} />
  }

  return (
    <div className="space-y-4">
      {history.isPending && <Skeleton className="h-32 w-full" />}
      {history.data?.length === 0 && <EmptyState title={t('users.historyEmptyTitle')} description={t('users.historyEmptyDescription')} />}
      {history.data && history.data.length > 0 && (
        <ol className="list-divided">
          {history.data.map((change) => (
            <li key={change.id} className="space-y-1 px-4 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium">{summarize(change, t)}</span>
                <span className="text-fg-subtle text-xs">{formatDateTime(change.createdAt, i18n.language, timeZone)}</span>
              </div>
              <p className="text-fg-muted text-xs">{t('users.historyChangedBy', { name: change.changedByName })}</p>
              {/* Never conditional: the justification is mandatory on both tables, so a row without
                  one would be a bug worth seeing rather than a field to hide. */}
              <p className="text-fg text-xs italic">{`"${change.justification}"`}</p>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/**
 * One line saying what a change was.
 *
 * The three kinds are spelled out rather than rendered from a shared template, because they are not
 * the same sentence with a different noun: two are about a role and one is about a transition
 * between two statuses.
 *
 * @param change the entry to describe
 * @param t      the translator of the `admin` namespace
 * @returns the sentence to show
 */
function summarize(change: UserAdminChange, t: TFunction): string {
  if (change.type === 'StatusChanged') {
    return t('users.historyStatusChanged', {
      from: change.fromStatus ? t(`users.status.${change.fromStatus}`) : '',
      to: change.toStatus ? t(`users.status.${change.toStatus}`) : '',
    })
  }
  const role = change.role ? t(`users.roles.${change.role}`) : ''
  return t(change.type === 'RoleGranted' ? 'users.historyRoleGranted' : 'users.historyRoleRevoked', { role })
}
