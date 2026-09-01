export type RelativeTimeUnit = 'now' | 'minutes' | 'hours' | 'days' | 'months'

export interface RelativeTime {
  unit: RelativeTimeUnit
  count: number
}

/** Escala simple para notificaciones - minutos, horas, días o meses desde createdAt, sin librería. */
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
