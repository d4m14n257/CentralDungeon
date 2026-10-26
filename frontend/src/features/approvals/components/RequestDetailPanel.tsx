import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { ErrorState } from '@/components/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { adminTableDetailPath, adminUserDetailPath } from '@/config/paths'
import { browserTimeZone, formatDateTime } from '@/lib/date'

import { useAdminRequest } from '../api/useAdminRequest'
import { isResolvableByAdmin } from '../requestTypes'
import { RequestStatusBadge } from './RequestStatusBadge'
import { RequestTypeBadge } from './RequestTypeBadge'

/**
 * One request, read in full: what was asked, and how it was answered.
 *
 * **It exists for the half the tray's row does not carry** — who resolved it, when, and the note
 * they wrote. A tray filtered by `Rejected` shows rows whose entire point is the reason behind them,
 * and the summary has no field for it, so without this the answer would be written and then
 * unreadable.
 *
 * It takes an **id and not the row** (§3.1.5): it asks for its own data, so what it shows is the
 * request as it is now rather than as the listing last saw it — which matters exactly here, since
 * the other admin's resolution is the thing most likely to have landed in between.
 *
 * @param props.requestId the request to read
 */
export function RequestDetailPanel({ requestId }: { requestId: string }) {
  const { t, i18n } = useTranslation('admin')
  const timeZone = browserTimeZone()
  const { data, isPending, isLoadingError, refetch } = useAdminRequest(requestId)

  if (isPending) return <Skeleton className="h-40 w-full" />
  if (isLoadingError || !data) return <ErrorState onRetry={() => void refetch()} />

  return (
    <div className="space-y-4 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <RequestTypeBadge type={data.type} />
        <RequestStatusBadge status={data.status} />
      </div>

      <div className="space-y-1">
        <p className="text-fg-muted text-xs">
          {t('requests.askedBy', { name: data.requestedByName })} · {formatDateTime(data.createdAt, i18n.language, timeZone)}
        </p>
        <p className="whitespace-pre-wrap">{data.justification}</p>
      </div>

      {/* What the request points at, when it points at something with its own screen. A veto points
          at an application, which has none, so it offers no link. */}
      {data.entityType === 'user' && (
        <Link to={adminUserDetailPath(data.entityId)} className="block text-xs underline-offset-2 hover:underline">
          {t('requests.viewPerson')}
        </Link>
      )}
      {data.entityType === 'game_table' && (
        <Link to={adminTableDetailPath(data.entityId)} className="block text-xs underline-offset-2 hover:underline">
          {t('requests.viewTable')}
        </Link>
      )}

      {/* The resolution, when there is one. A pending request is not missing anything - it simply
          has not been answered yet, and saying so is an answer of its own. */}
      {data.status === 'Pending' ? (
        <p className="text-fg-muted">
          {isResolvableByAdmin(data.type) ? t('requests.detailStillPending') : t('requests.detailPrimaryOnly')}
        </p>
      ) : (
        <div className="border-border space-y-1 rounded-lg border border-dashed px-4 py-3">
          <p className="text-fg-muted text-xs">
            {t('requests.resolvedBy', { name: data.resolvedByName ?? '' })}
            {data.resolvedAt && ` · ${formatDateTime(data.resolvedAt, i18n.language, timeZone)}`}
          </p>
          <p className="whitespace-pre-wrap">{data.resolutionNote}</p>
        </div>
      )}
    </div>
  )
}
