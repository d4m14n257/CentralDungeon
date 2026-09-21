import { api } from '@/api/client'

import type { SettingKey, SystemSetting, SystemSettingChange, UpdateSettingInput } from '../types'

/**
 * The three calls of `/admin/settings` (F3.5, #141). Under their own prefix like every other
 * administrative surface, because all three answer to `hasAnyRole('ADMIN','OWNER')`.
 *
 * **None of them is paginated**, and that is the backend's shape rather than an omission here: there
 * are four settings and adding one is a line of an enum, so a page of four rows would be ceremony
 * around a list nobody scrolls.
 *
 * **The actor is never a parameter.** Who is changing a setting comes from the token, and an id in
 * the body would be a claim the caller makes about themselves (arquitectura.md §2.6) — which would
 * make the audit row worth nothing.
 */
export const settingsApi = {
  /**
   * Every setting the platform has, with what it is worth now and what it ships as.
   *
   * A setting nobody ever changed appears too, showing its default: the table holds overrides only,
   * so "no row" is the normal state and not a gap.
   */
  list: () => api.get<SystemSetting[]>('/api/v1/admin/settings'),

  /**
   * Changes one setting. A `PUT` and not a `POST` with a verb, unlike the rest of F3: this really is
   * an assignment — the same call twice leaves the platform in the same state.
   *
   * Refused with `SETTING_OUT_OF_RANGE`, carrying `minValue` and `maxValue`, when the number is
   * outside what that key allows — which the form already prevents, so it surfaces for somebody
   * calling the API another way.
   *
   * @param key   the setting to change, exactly as the listing spelled it
   * @param input the new value and the reason
   */
  update: (key: SettingKey, input: UpdateSettingInput) =>
    api.put<SystemSetting, UpdateSettingInput>(`/api/v1/admin/settings/${key}`, input),

  /**
   * What was done to one setting, oldest first — who moved it, from what to what, and why.
   *
   * Never paginated: it is read as a sequence inside the panel that opens on one row.
   *
   * @param key the setting whose history to read
   */
  history: (key: SettingKey) => api.get<SystemSettingChange[]>(`/api/v1/admin/settings/${key}/history`),
}
