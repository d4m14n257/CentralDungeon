/**
 * Public surface of the settings feature (#114, #141): the values the community changes without a
 * deploy, and the screen that changes them. Anything not listed here is private to it.
 *
 * **The client-side mirror of the file cap is deliberately not here.** `features/files` is what
 * reads it and a feature never imports another one (regla dura 16), so it lives one layer up, in
 * `hooks/useClientLimits` over `types/api.ts` — platform configuration rather than this domain.
 */

export { SettingHistory } from './components/SettingHistory'
export { SettingValueDialog } from './components/SettingValueDialog'
export { useSettingHistory } from './api/useSettingHistory'
export { useSystemSettings } from './api/useSystemSettings'
export { useUpdateSetting } from './api/useUpdateSetting'
export { SETTING_ERROR_CODES, settingErrorKey } from './settingErrors'
export { updateSettingSchema, type UpdateSettingForm } from './schemas'
/** The feature's domain types. Each is written once in `types.ts` and derived from there (§3.2). */
export type { SettingCategory, SettingKey, SettingValueType, SystemSetting, SystemSettingChange, UpdateSettingInput } from './types'
