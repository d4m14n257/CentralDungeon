/** The coarse buckets a relative time falls into. `now` means less than a minute ago. */
export type RelativeTimeUnit = 'now' | 'minutes' | 'hours' | 'days' | 'months'

/**
 * A time difference already reduced to a unit and a count, ready for i18n's pluralization. The
 * formatting itself is the caller's, so the same value renders in any language.
 */
export interface RelativeTime {
  unit: RelativeTimeUnit
  count: number
}

/** A simple scale for notifications - minutes, hours, days or months since createdAt, with no library. */
export function relativeTimeFrom(iso: string, now: Date = new Date()): RelativeTime {
  const minutes = Math.floor((now.getTime() - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return { unit: 'now', count: 0 }
  if (minutes < 60) return { unit: 'minutes', count: minutes }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return { unit: 'hours', count: hours }
  const days = Math.floor(hours / 24)
  if (days < 30) return { unit: 'days', count: days }
  return { unit: 'months', count: Math.floor(days / 30) }
}
