/**
 * The platform values a client falls back to until the server answers with the real ones.
 *
 * **They are the fallback, not the rule.** Both live in `system_settings`
 * now, editable from `/admin/settings` without a deploy, and `useClientLimits` is what fetches
 * them. These constants mirror `SettingKey`'s shipped defaults, so a screen rendered before the
 * answer arrives — or on a session where the request failed — still has a number to work with
 * instead of a blank or a `0`.
 *
 * If a constant and the server ever disagree, the server wins and the person sees its message.
 */

/** The per-file upload cap to assume until the server says otherwise, in bytes. */
export const DEFAULT_MAX_FILE_BYTES = 2 * 1024 * 1024

/**
 * How long a reservation of the shared admin tray lasts, in minutes.
 *
 * **The frontend never enforces it** — the backend's scheduled job is what releases a stale claim.
 * The number exists here so the tray screen and the help say the same thing out loud: an admin who
 * takes an item and walks away has to be told what happens next, and "fifteen minutes" written by
 * hand in four places in two languages is how that stops being true the first time it changes.
 *
 * It replaced `config/queue.ts`, which said in so many words that the day the backend stopped
 * treating this as a constant it would stop being one here too — and that day came with the editable settings.
 */
export const DEFAULT_CLAIM_TIMEOUT_MINUTES = 15

/** The seat cap to assume until the server says otherwise: the shipped default of `tables.max_players_cap`. */
export const DEFAULT_MAX_PLAYERS_CAP = 12

/**
 * The most values a table may link per catalog (systems, tags or platforms), mirroring the cap the
 * backend puts on each id list. It stops a form from sending a list the server would refuse, and the
 * server still checks it.
 */
export const MAX_CATALOG_VALUES_PER_KIND = 64
