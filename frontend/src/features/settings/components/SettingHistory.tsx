import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Skeleton } from '@/components/ui/skeleton'
import { browserTimeZone, formatDateTime } from '@/lib/date'
import { ApiError } from '@/types/api'

import { useSettingHistory } from '../api/useSettingHistory'
import type { SettingKey } from '../types'

/**
 * Everything that was ever done to one setting: from what to what, by whom, and why (#141).
 *
 * **It is what keeps `system_setting_changes` from being write-only.** A platform-wide change
 * produces no notification and no visible event, so the row is the only trace it leaves — and a
 * record nothing reads back is born orphaned, which is the failure `fase-3-admin-owner.md` §7 names.
 *
 * It takes a **key** and asks for its own data (arquitectura.md §3.1.5), so the row that opens it
 * passes an address rather than a whole setting.
 *
 * The four states are its own (#150): the listing around it has loaded by definition, and this is a
 * second question to the server that can fail, come back empty, or be refused on its own.
 *
 * @param props.settingKey the setting whose record to show
 */
export function SettingHistory({ settingKey }: { settingKey: SettingKey }) {
  const { t, i18n } = useTranslation('admin')
  const timeZone = browserTimeZone()
  const history = useSettingHistory(settingKey)

  if (history.error instanceof ApiError && history.error.status === 403) {
    return <ForbiddenState />
  }

  // isLoadingError, not isError: a failed background refetch must not blank a panel that is already
  // showing rows (#150).
  if (history.isLoadingError) {
    return <ErrorState onRetry={() => void history.refetch()} />
  }

  if (history.isPending) {
    return <Skeleton className="h-32 w-full" />
  }

  if (history.data.length === 0) {
    return <EmptyState title={t('settings.historyEmptyTitle')} description={t('settings.historyEmptyDescription')} />
  }

  return (
    <ol className="divide-border divide-y rounded-lg border">
      {history.data.map((change) => (
        <li key={change.id} className="space-y-1 px-4 py-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            {/* `fromValue` is null on the first change, and that null is information: the platform
                was still on the shipped default, which is a different fact from "it was already this
                number". Two sentences, not one with an empty hole in it. */}
            <span className="font-medium">
              {change.fromValue === null
                ? t('settings.historyFirstChange', { to: change.toValue })
                : t('settings.historyChanged', { from: change.fromValue, to: change.toValue })}
            </span>
            <span className="text-fg-subtle text-xs">{formatDateTime(change.createdAt, i18n.language, timeZone)}</span>
          </div>
          <p className="text-fg-muted text-xs">{t('settings.historyChangedBy', { name: change.changedByName })}</p>
          {/* Never conditional: the column is NOT NULL, so a row without a reason would be a bug
              worth seeing rather than a field to hide. */}
          <p className="text-fg text-xs italic">{`"${change.justification}"`}</p>
        </li>
      ))}
    </ol>
  )
}
