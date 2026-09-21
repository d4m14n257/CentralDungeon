import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { settingsApi } from './settingsApi'
import type { SettingKey } from '../types'

/**
 * What was done to one setting, and why (#141).
 *
 * It is what keeps the audit table from being write-only. A platform-wide change produces no
 * notification and no visible event, so without somewhere to read it back the row would be born
 * orphaned — the failure `fase-3-admin-owner.md` §7 names.
 *
 * @param key the setting to read, or null while no panel is open — which is what `enabled` keys off,
 *            so opening the screen does not fetch four histories nobody asked for
 * @returns the query, oldest change first
 */
export function useSettingHistory(key: SettingKey | null) {
  return useQuery({
    queryKey: queryKeys.settings.history(key ?? ''),
    queryFn: () => settingsApi.history(key as SettingKey),
    enabled: key !== null,
  })
}
