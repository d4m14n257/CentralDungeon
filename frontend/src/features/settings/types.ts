/**
 * Every setting the platform has, spelled the way the API spells them — a union of literals and not
 * a TS `enum` (arquitectura.md §3.2). Mirror of `SettingKey.wireName()`.
 *
 * The key is the contract: it addresses the setting in the URL, and it is what the screen builds the
 * label, the description and the unit from, in the reader's language (#197). A key the backend
 * publishes and this union does not have stops compiling, which is the point.
 */
export type SettingKey =
  'admin_queue.claim_timeout_minutes' | 'files.max_file_size_mb' | 'tables.max_players_cap' | 'profiles.visibility_window_days'

/**
 * How `/admin/settings` groups what it shows (#141). Mirror of `SettingCategory`.
 *
 * V1's column comment names a third group, `Texts`, and it deliberately does not exist: since #197
 * the backend writes no sentence a person reads, so a text setting would be a phrase stored in one
 * language and rendered raw — regla dura 18, broken by design.
 */
export type SettingCategory = 'Business' | 'Limits'

/** How a setting's stored value is read back. Mirror of `SettingValueType`. */
export type SettingValueType = 'Integer'

/**
 * One row of `/admin/settings`. Mirror of `SystemSettingResponse`.
 *
 * **The default travels with the current value**, which is what the screen was asked for (#141): an
 * admin looking at `15` has no way to tell whether somebody set it there or whether it has always
 * been that, and no way to put it back without finding the number in the source.
 *
 * **No label and no description here**, deliberately: the backend sends none (#197) and the screen
 * builds both from `key`, out of `src/locales/<idioma>/admin.json`.
 */
export interface SystemSetting {
  key: SettingKey
  category: SettingCategory
  valueType: SettingValueType
  /** What the platform is using right now — the override if there is one, the default otherwise. */
  value: number
  /** What it ships as, so "put it back" never needs the source code. */
  defaultValue: number
  minValue: number
  maxValue: number
  /**
   * Whether a human set this, as opposed to it still being the shipped default.
   *
   * Not `value !== defaultValue`: somebody may set a setting to exactly its default, and that is a
   * decision somebody made and signed.
   */
  overridden: boolean
  /**
   * Whether changing it re-answers questions the platform already answered (#44, #141).
   *
   * The screen turns it into a warning shown **before** the value is saved. The sentence is per key
   * and lives in i18n; this flag is what makes it appear.
   */
  retroactive: boolean
  updatedByName: string | null
  updatedAt: string | null
}

/** What changing a setting sends: the new value and why. Mirror of `UpdateSettingRequest`. */
export interface UpdateSettingInput {
  value: SystemSetting['value']
  justification: string
}

/**
 * One entry of a setting's history. Mirror of `SystemSettingChangeResponse`.
 *
 * `fromValue` is `null` on the first change, and that null is information: it says the platform was
 * still on the shipped default, which is a different fact from "it was already this number".
 */
export interface SystemSettingChange {
  id: string
  key: SettingKey
  fromValue: string | null
  toValue: string
  changedByName: string
  justification: string
  createdAt: string
}
