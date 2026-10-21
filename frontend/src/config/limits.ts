/**
 * The platform values a client falls back to until the server answers with the real ones.
 *
 * **They stopped being the rule in F3.5 and became the fallback.** Both live in `system_settings`
 * now, editable from `/admin/settings` without a deploy (ADR-17 §3), and `useClientLimits` is what fetches
 * them. These constants mirror `SettingKey`'s shipped defaults, so a screen rendered before the
 * answer arrives — or on a session where the request failed — still has a number to work with
 * instead of a blank or a `0`.
 *
 * If a constant and the server ever disagree, the server wins and the person sees its message (ADR-23 §3).
 */

/** The per-file upload cap to assume until the server says otherwise, in bytes (ADR-13 §3, ADR-17 §3). */
export const DEFAULT_MAX_FILE_BYTES = 2 * 1024 * 1024

/**
 * How long a reservation of the shared admin tray lasts, in minutes (ADR-16 §5, ADR-17 §3).
 *
 * **The frontend never enforces it** — the backend's scheduled job is what releases a stale claim.
 * The number exists here so the tray screen and the help say the same thing out loud: an admin who
 * takes an item and walks away has to be told what happens next, and "fifteen minutes" written by
 * hand in four places in two languages is how that stops being true the first time it changes.
 *
 * It replaced `config/adminQueue.ts`, which said in so many words that the day the backend stopped
 * treating this as a constant it would stop being one here too — and F3.5 is that day.
 */
export const DEFAULT_CLAIM_TIMEOUT_MINUTES = 15
