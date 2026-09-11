/**
 * The one place UTC becomes local time and back again (#22, #111).
 *
 * The backend stores and sends everything in UTC — a table plays "Wednesday 01:00 UTC", and the
 * fact that its players call that Tuesday night is theirs, not the server's. Converting in one
 * module is what keeps that from being re-decided in every component.
 *
 * **No date library.** There is no calendar arithmetic in this application: a schedule is a weekday
 * plus a time, and `Intl.DateTimeFormat` already knows every rule involved. The legacy `useDate`
 * did this with `Intl` too — what it got wrong was hard-coding `'es-ES'` inside the hook, which is
 * why **locale and time zone are parameters of every function here and never constants** (#111).
 */

/** A day of the week as the API spells it. The same seven names the backend enum carries. */
export type Weekday = 'Monday' | 'Tuesday' | 'Wednesday' | 'Thursday' | 'Friday' | 'Saturday' | 'Sunday'

/** The seven days in reading order, Monday first — the order an agenda is shown and sorted in. */
export const WEEKDAYS: readonly Weekday[] = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'] as const

/** One slot of a weekly agenda: a day and a `HH:mm` time, in whichever zone the caller is holding. */
export interface WeeklySlot {
  weekday: Weekday
  /** `HH:mm`, or `HH:mm:ss` as the API sends it — the seconds are ignored everywhere. */
  hourtime: string
}

/** Minutes in a day. */
const MINUTES_PER_DAY = 24 * 60

/** Minutes in a week. Weekly times wrap on this, which is the whole subtlety of the conversion. */
const MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

/**
 * A Monday chosen for being unambiguous in every zone on Earth: it is mid-month, far from any
 * daylight-saving boundary in the zones this community lives in, and its UTC weekday is Monday.
 * A weekly slot has no date of its own, so converting one needs a stand-in date, and picking a
 * fixed sane one beats using "today" — which would make the same agenda render differently
 * depending on when you looked at it.
 */
const REFERENCE_MONDAY_UTC = Date.UTC(2026, 5, 15)

/**
 * The reader's own time zone, from the browser.
 *
 * It is the **fallback only**: when a profile states a zone, that is the one to pass, because
 * somebody travelling has not moved their table (#111). Callers pass the zone explicitly; this is
 * what they use when there is nothing to pass.
 *
 * @returns an IANA zone name such as `America/Argentina/Buenos_Aires`
 */
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/**
 * Parses a `HH:mm` or `HH:mm:ss` time into minutes since midnight.
 *
 * @param hourtime the time as the API writes it
 * @returns minutes since 00:00, seconds dropped
 */
export function minutesOfDay(hourtime: string): number {
  const [hours = '0', minutes = '0'] = hourtime.split(':')
  return Number(hours) * 60 + Number(minutes)
}

/**
 * Formats minutes since midnight back into `HH:mm`.
 *
 * @param minutes minutes since 00:00; values outside the day wrap
 * @returns the time as `HH:mm`
 */
export function formatMinutes(minutes: number): string {
  const normalized = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
  const hours = Math.floor(normalized / 60)
  return `${String(hours).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`
}

/**
 * Turns a weekly slot stored in UTC into the same instant as the reader sees it.
 *
 * The day can move: a table at Wednesday 01:00 UTC is Tuesday 22:00 in Buenos Aires, and showing
 * "Wednesday" to somebody who plays on Tuesday night is the bug this function exists to prevent
 * (#22).
 *
 * @param slot     the slot as the API sent it, in UTC
 * @param timeZone the IANA zone to show it in — from the profile, or `browserTimeZone()` when there
 *                 is none (#111)
 * @returns the same slot expressed in that zone
 */
export function utcSlotToLocal(slot: WeeklySlot, timeZone: string): WeeklySlot {
  return shiftSlot(slot, offsetMinutes(slot, timeZone))
}

/**
 * Turns a weekly slot the reader typed in their own zone into the UTC the API expects.
 *
 * The inverse of {@link utcSlotToLocal}, and the direction that actually travels: what is written
 * to `table_schedules` is always UTC (#22).
 *
 * @param slot     the slot as the person entered it, in their own zone
 * @param timeZone the IANA zone they entered it in
 * @returns the same slot expressed in UTC
 */
export function localSlotToUtc(slot: WeeklySlot, timeZone: string): WeeklySlot {
  return shiftSlot(slot, -offsetMinutes(slot, timeZone))
}

/** How many minutes a week has. What a week-minute is reduced modulo, and what a block can run past. */
export const MINUTES_PER_WEEK_TOTAL = MINUTES_PER_WEEK

/**
 * A stretch of the week as the API sends it: minutes from Monday 00:00 **UTC**, and a length (#227).
 *
 * A single offset rather than a weekday and a time, because the reader's week does not start where
 * UTC's does: a Tuesday-night table in America is Wednesday in UTC (#22), and sending the pair would
 * make this side undo a conversion the other side had already half-made.
 */
export interface WeekBlock {
  /** When it starts, in minutes from Monday 00:00 UTC. */
  startMinute: number
  /** How long it lasts, in minutes. Always positive. */
  durationMinutes: number
}

/**
 * The same week-minute, expressed in the reader's zone (#22, #111).
 *
 * It wraps: a Sunday-night slot in UTC can be Monday morning locally, and the other way round.
 *
 * @param startMinute the minute, from Monday 00:00 UTC
 * @param timeZone    the IANA zone to read it in
 * @returns the minute from Monday 00:00 in that zone, inside the week
 */
export function utcWeekMinuteToLocal(startMinute: number, timeZone: string): number {
  const instant = new Date(REFERENCE_MONDAY_UTC + startMinute * 60_000)
  return mod(startMinute + zoneOffsetMinutes(instant, timeZone), MINUTES_PER_WEEK)
}

/**
 * The inverse: a minute the reader picked on their own grid, as the UTC that travels (#22).
 *
 * @param startMinute the minute, from Monday 00:00 in the reader's zone
 * @param timeZone    the zone they picked it in
 * @returns the minute from Monday 00:00 UTC
 */
export function localWeekMinuteToUtc(startMinute: number, timeZone: string): number {
  // The offset is measured at the instant the local minute names, which is that minute read as if
  // it were UTC — close enough that only a slot within an hour of a DST change could pick the
  // neighbouring offset, and this community's slots are evenings.
  const instant = new Date(REFERENCE_MONDAY_UTC + startMinute * 60_000)
  return mod(startMinute - zoneOffsetMinutes(instant, timeZone), MINUTES_PER_WEEK)
}

/**
 * Splits a block into the day-sized pieces a grid can draw.
 *
 * A block that runs past midnight belongs to two columns, and one that runs past Sunday night comes
 * back round to Monday — the same wrap {@code WeeklyInterval} does on the other side (#178).
 *
 * @param block the stretch, already in the reader's zone
 * @returns one piece per day it touches, each with the day's index from Monday and the minutes of
 *          that day it covers
 */
export function splitBlockByDay(block: WeekBlock): { dayIndex: number; startMinuteOfDay: number; durationMinutes: number }[] {
  const pieces: { dayIndex: number; startMinuteOfDay: number; durationMinutes: number }[] = []
  let remaining = block.durationMinutes
  let cursor = mod(block.startMinute, MINUTES_PER_WEEK)

  while (remaining > 0) {
    const dayIndex = Math.floor(cursor / MINUTES_PER_DAY)
    const startMinuteOfDay = cursor % MINUTES_PER_DAY
    const untilMidnight = MINUTES_PER_DAY - startMinuteOfDay
    const slice = Math.min(remaining, untilMidnight)
    pieces.push({ dayIndex, startMinuteOfDay, durationMinutes: slice })
    remaining -= slice
    cursor = mod(cursor + slice, MINUTES_PER_WEEK)
  }
  return pieces
}

/**
 * A week block written the way a person reads it: "Martes 20:00–23:00" (#227).
 *
 * The same phrasing {@link formatSlot} produces, for a block that travels as minutes rather than as
 * a weekday and a time. It names the day the block **starts** on, in the reader's zone, and closes
 * the range with where it ends — even when that is the small hours of the next day.
 *
 * @param block    the stretch, in UTC minutes from Monday 00:00
 * @param locale   the BCP-47 locale (#111)
 * @param timeZone the IANA zone to read it in (#22)
 * @returns the block as a phrase
 */
export function formatWeekBlock(block: WeekBlock, locale: string, timeZone: string): string {
  const localStart = utcWeekMinuteToLocal(block.startMinute, timeZone)
  const day = WEEKDAYS[Math.floor(localStart / MINUTES_PER_DAY)]
  const startOfDay = localStart % MINUTES_PER_DAY
  const start = formatMinutes(startOfDay)
  const end = formatMinutes((startOfDay + block.durationMinutes) % MINUTES_PER_DAY)
  return `${weekdayName(day ?? 'Monday', locale, 'short')} ${start}–${end}`
}

/** Positive modulo: `%` keeps the sign in JavaScript, and a wrapped week must not go negative. */
function mod(value: number, modulus: number): number {
  return ((value % modulus) + modulus) % modulus
}

/**
 * The name of a weekday in the reader's language, always starting with a capital.
 *
 * `Intl` writes each language by its own orthography, and Spanish spells weekdays in lowercase
 * ("lunes") while English capitalizes them ("Monday"). Here they are always capitalized: a day is a
 * label in a selector and a heading in an agenda, never a word inside a sentence, and mixing the two
 * shapes made the same list read differently depending on the language.
 *
 * The uppercasing goes through the locale, not through `toUpperCase()`: mapping a character to its
 * capital is itself language-dependent.
 *
 * @param weekday the day to name
 * @param locale  the BCP-47 locale — a parameter, never a constant (#111)
 * @param width   how long the name should be; `'short'` for a card, `'long'` for a form
 * @returns the localized name, capitalized
 */
export function weekdayName(weekday: Weekday, locale: string, width: 'long' | 'short' = 'long'): string {
  const date = new Date(REFERENCE_MONDAY_UTC + WEEKDAYS.indexOf(weekday) * MINUTES_PER_DAY * 60_000)
  const name = new Intl.DateTimeFormat(locale, { weekday: width, timeZone: 'UTC' }).format(date)
  return name.charAt(0).toLocaleUpperCase(locale) + name.slice(1)
}

/**
 * A slot written out the way a person reads it: "martes 20:00".
 *
 * @param slot     the slot, **already in the reader's zone** — convert with {@link utcSlotToLocal}
 *                 first, so the caller decides once whether it is showing local or UTC
 * @param locale   the BCP-47 locale (#111)
 * @param duration how long a session lasts, as `HH:mm`, to close the range. Omit for the start only
 * @returns the slot as a phrase, with its end time when a duration was given
 */
export function formatSlot(slot: WeeklySlot, locale: string, duration?: string | null): string {
  const start = formatMinutes(minutesOfDay(slot.hourtime))
  if (!duration) {
    return `${weekdayName(slot.weekday, locale)} ${start}`
  }
  const end = formatMinutes(minutesOfDay(slot.hourtime) + minutesOfDay(duration))
  return `${weekdayName(slot.weekday, locale)} ${start}–${end}`
}

/**
 * An ISO-8601 UTC instant as a date and time in the reader's zone.
 *
 * @param iso      the instant, as the API sends it
 * @param locale   the BCP-47 locale (#111)
 * @param timeZone the IANA zone to show it in (#111)
 * @returns the formatted date and time
 */
export function formatDateTime(iso: string, locale: string, timeZone: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(utcInstant(iso))
}

/**
 * An ISO-8601 UTC instant as a plain date in the reader's zone.
 *
 * @param iso      the instant, as the API sends it
 * @param locale   the BCP-47 locale (#111)
 * @param timeZone the IANA zone to show it in (#111)
 * @returns the formatted date
 */
export function formatDate(iso: string, locale: string, timeZone: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone }).format(utcInstant(iso))
}

/** The thresholds `formatRelativeDate` steps through, coarsest last. */
const RELATIVE_UNITS: { unit: Intl.RelativeTimeFormatUnit; seconds: number }[] = [
  { unit: 'year', seconds: 365 * 24 * 60 * 60 },
  { unit: 'month', seconds: 30 * 24 * 60 * 60 },
  { unit: 'week', seconds: 7 * 24 * 60 * 60 },
  { unit: 'day', seconds: 24 * 60 * 60 },
  { unit: 'hour', seconds: 60 * 60 },
  { unit: 'minute', seconds: 60 },
]

/**
 * An instant as "hace 3 días" — how long ago, rather than when exactly.
 *
 * **For the values whose meaning is the distance and not the date.** `lastUsedAt` is the case this
 * was written for: the question a person asks of it is "is this file still in use", and a formatted
 * date makes them do the subtraction themselves. It is also what warns them, without a word about
 * retention, that a file nobody has touched in months is the one the purge will reach first (#75).
 *
 * **No time zone parameter, and that is not an omission.** A distance between two instants is the
 * same distance everywhere; there is nothing to convert. Use `formatDate` when the calendar day is
 * what matters.
 *
 * The locale is a parameter like everywhere else in this module (#111, #192).
 *
 * @param iso    the instant, as the API sends it
 * @param locale the BCP-47 locale (#111)
 * @param now    the instant to measure against. A parameter so the result is testable without
 *               freezing the clock, and never something a caller needs to pass
 * @returns the distance in words, e.g. `hace 3 días`
 */
export function formatRelativeDate(iso: string, locale: string, now: Date = new Date()): string {
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const elapsedSeconds = (utcInstant(iso).getTime() - now.getTime()) / 1000
  for (const { unit, seconds } of RELATIVE_UNITS) {
    if (Math.abs(elapsedSeconds) >= seconds) {
      return formatter.format(Math.round(elapsedSeconds / seconds), unit)
    }
  }
  // Under a minute reads as "ahora" rather than "hace 4 segundos", which nobody needs to know.
  return formatter.format(0, 'second')
}

/**
 * A plain `YYYY-MM-DD` the API sent, formatted as it is.
 *
 * **No zone, on purpose** (#230). A date is not an instant: `game_tables.start_date` says which day
 * the table starts and means the same day to everybody reading it, so converting it would be the
 * bug and not the fix - a reader west of UTC would be shown the day before. The formatter is pinned
 * to UTC precisely so that nothing shifts.
 *
 * @param value  the date, as the API sends it, `YYYY-MM-DD`
 * @param locale the BCP-47 locale (#111)
 * @returns the formatted date
 */
export function formatPlainDate(value: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(`${value.slice(0, 10)}T00:00:00Z`))
}

/**
 * Reads an instant the API sent, which is **always UTC** even when it does not say so (#22).
 *
 * The backend serializes `LocalDateTime` as `2026-09-09T01:00:00`, with no offset, and JavaScript
 * reads a bare date-time like that as **local** time — so a session at 01:00 UTC rendered as 01:00
 * to a reader three hours behind, which is the exact bug the conversion exists to prevent. Appending
 * the `Z` is what makes the string mean what the server meant by it.
 */
function utcInstant(iso: string): Date {
  // An instant that already carries an offset (`Z`, `+03:00`) is left alone: it said its zone.
  return /(?:Z|[+-]\d{2}:?\d{2})$/.test(iso) ? new Date(iso) : new Date(`${iso}Z`)
}

/**
 * Converts what an `<input type="datetime-local">` holds into the ISO-8601 UTC the API takes.
 *
 * The control gives back wall-clock text with no zone attached, so the zone has to be supplied
 * rather than inferred — which is the same reason it is a parameter everywhere else here (#111).
 *
 * @param value    the control's value, `YYYY-MM-DDTHH:mm`
 * @param timeZone the IANA zone the person typed it in
 * @returns the instant as `YYYY-MM-DDTHH:mm:ss` in UTC, or null when the field is empty
 */
export function localInputToUtcIso(value: string, timeZone: string): string | null {
  if (!value) {
    return null
  }
  const naive = new Date(`${value}:00Z`)
  const shifted = new Date(naive.getTime() - zoneOffsetMinutes(naive, timeZone) * 60_000)
  return shifted.toISOString().slice(0, 19)
}

/**
 * The inverse: an ISO-8601 UTC instant as the text an `<input type="datetime-local">` wants.
 *
 * @param iso      the instant, as the API sends it, or null
 * @param timeZone the IANA zone to show it in
 * @returns the control's value, `YYYY-MM-DDTHH:mm`, or an empty string when there is no instant
 */
export function utcIsoToLocalInput(iso: string | null | undefined, timeZone: string): string {
  if (!iso) {
    return ''
  }
  const instant = new Date(iso.endsWith('Z') ? iso : `${iso}Z`)
  const shifted = new Date(instant.getTime() + zoneOffsetMinutes(instant, timeZone) * 60_000)
  return shifted.toISOString().slice(0, 16)
}

/**
 * How far ahead of UTC a zone is at the instant the given slot falls on.
 *
 * Resolved against a real instant rather than assumed constant, because half the world changes it
 * twice a year and the community this serves spans several such zones.
 */
function offsetMinutes(slot: WeeklySlot, timeZone: string): number {
  const instant = new Date(REFERENCE_MONDAY_UTC + weeklyMinutes(slot) * 60_000)
  return zoneOffsetMinutes(instant, timeZone)
}

/** The signed offset of a zone from UTC, in minutes, at one instant. */
function zoneOffsetMinutes(instant: Date, timeZone: string): number {
  // `sv-SE` because its date format is ISO-8601 already, which makes the round trip a parse and
  // not a hand-written reassembly of the parts.
  const asZoned = new Date(
    `${new Intl.DateTimeFormat('sv-SE', { dateStyle: 'short', timeStyle: 'medium', timeZone }).format(instant).replace(' ', 'T')}Z`,
  )
  return Math.round((asZoned.getTime() - instant.getTime()) / 60_000)
}

/** Where in the week a slot falls, in minutes from Monday 00:00. */
function weeklyMinutes(slot: WeeklySlot): number {
  return WEEKDAYS.indexOf(slot.weekday) * MINUTES_PER_DAY + minutesOfDay(slot.hourtime)
}

/** Moves a slot by a number of minutes, wrapping around the end of the week. */
function shiftSlot(slot: WeeklySlot, deltaMinutes: number): WeeklySlot {
  const shifted = (((weeklyMinutes(slot) + deltaMinutes) % MINUTES_PER_WEEK) + MINUTES_PER_WEEK) % MINUTES_PER_WEEK
  const weekday = WEEKDAYS[Math.floor(shifted / MINUTES_PER_DAY)]
  return { weekday: weekday ?? 'Monday', hourtime: formatMinutes(shifted % MINUTES_PER_DAY) }
}
