import { useMutation, useQueryClient } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { settingsApi } from './settingsApi'
import type { SettingKey, UpdateSettingInput } from '../types'

/**
 * Changes one setting (#141).
 *
 * **It invalidates three branches, and the third is the one that is easy to forget.** The listing and
 * that setting's history are obvious. `system.limits()` is the copy of the file cap every other
 * screen refuses uploads against: leaving it cached would give this session a dropzone that still
 * enforces the old number, which is exactly the disagreement F3.5 built the endpoint to stop.
 *
 * It does not matter that only one of the four settings appears there — a mutation never knows which
 * key it just moved by the time the cache is being cleaned, and re-reading one small answer is
 * cheaper than a rule that has to be kept in step with the settings catalogue.
 *
 * `meta.showsItsOwnError`: the dialog renders the refusal inline, beside the field (#197).
 *
 * @returns the mutation, taking the setting, the new value and the reason
 */
export function useUpdateSetting() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ key, input }: { key: SettingKey; input: UpdateSettingInput }) => settingsApi.update(key, input),
    meta: { showsItsOwnError: true },
    onSuccess: (_result, { key }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.list() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.history(key) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.system.limits() })
    },
  })
}
