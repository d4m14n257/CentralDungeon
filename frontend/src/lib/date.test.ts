import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatDateTime,
  formatPlainDate,
  formatRelativeDate,
  formatMinutes,
  formatSlot,
  localInputToUtcIso,
  localSlotToUtc,
  minutesOfDay,
  utcIsoToLocalInput,
  utcSlotToLocal,
  weekdayName,
  splitBlockByDay,
  utcWeekMinuteToLocal,
  localWeekMinuteToUtc,
} from './date'

/**
 * The community plays at night in America, which is the small hours of the next day in UTC (#22).
 * Every case here is that fact, from one angle or another — it is the most likely bug of F1.
 */
const BUENOS_AIRES = 'America/Argentina/Buenos_Aires'
const MADRID = 'Europe/Madrid'

describe('utcSlotToLocal', () => {
  it('moves the day back when the UTC slot is in the small hours', () => {
    expect(utcSlotToLocal({ weekday: 'Wednesday', hourtime: '01:00' }, BUENOS_AIRES)).toEqual({
      weekday: 'Tuesday',
      hourtime: '22:00',
    })
  })

  it('moves the day forward for a zone ahead of UTC', () => {
    expect(utcSlotToLocal({ weekday: 'Tuesday', hourtime: '23:30' }, MADRID)).toEqual({
      weekday: 'Wednesday',
      hourtime: '01:30',
    })
  })

  it('wraps around the end of the week', () => {
    expect(utcSlotToLocal({ weekday: 'Monday', hourtime: '01:00' }, BUENOS_AIRES)).toEqual({
      weekday: 'Sunday',
      hourtime: '22:00',
    })
  })

  it('leaves a slot alone in UTC itself', () => {
    expect(utcSlotToLocal({ weekday: 'Friday', hourtime: '20:00' }, 'UTC')).toEqual({ weekday: 'Friday', hourtime: '20:00' })
  })

  it('drops the seconds the API sends', () => {
    expect(utcSlotToLocal({ weekday: 'Friday', hourtime: '20:00:00' }, 'UTC').hourtime).toBe('20:00')
  })
})

describe('localSlotToUtc', () => {
  it('is the inverse of utcSlotToLocal', () => {
    const utc = { weekday: 'Wednesday', hourtime: '01:00' } as const

    expect(localSlotToUtc(utcSlotToLocal(utc, BUENOS_AIRES), BUENOS_AIRES)).toEqual(utc)
  })

  it('turns a Tuesday night in Buenos Aires into a Wednesday in UTC', () => {
    expect(localSlotToUtc({ weekday: 'Tuesday', hourtime: '22:00' }, BUENOS_AIRES)).toEqual({
      weekday: 'Wednesday',
      hourtime: '01:00',
    })
  })
})

describe('minutesOfDay and formatMinutes', () => {
  it('round-trips a time', () => {
    expect(formatMinutes(minutesOfDay('20:30'))).toBe('20:30')
  })

  it('wraps a time past midnight, which is what closes a session that runs into the next day', () => {
    expect(formatMinutes(minutesOfDay('23:00') + minutesOfDay('03:00'))).toBe('02:00')
  })
})

describe('weekdayName', () => {
  it('names the day in the given locale, never a hard-coded one (#111)', () => {
    expect(weekdayName('Tuesday', 'es-AR')).toBe('Martes')
    expect(weekdayName('Tuesday', 'en-US')).toBe('Tuesday')
  })

  it('capitalizes it even where the language would not, because it is a label and not a sentence', () => {
    // `Intl` gives "miércoles" in Spanish; the accent has to survive the capital.
    expect(weekdayName('Wednesday', 'es-AR')).toBe('Miércoles')
    expect(weekdayName('Saturday', 'es-AR')).toBe('Sábado')
  })
})

describe('splitBlockByDay', () => {
  it('leaves a block that fits in one day alone', () => {
    // Tuesday 20:00 = 1 * 1440 + 1200, three hours.
    expect(splitBlockByDay({ startMinute: 1440 + 1200, durationMinutes: 180 })).toEqual([
      { dayIndex: 1, startMinuteOfDay: 1200, durationMinutes: 180 },
    ])
  })

  it('cuts a block that runs past midnight into the two days it touches (#178)', () => {
    // Tuesday 23:00 plus three hours ends Wednesday 02:00.
    expect(splitBlockByDay({ startMinute: 1440 + 1380, durationMinutes: 180 })).toEqual([
      { dayIndex: 1, startMinuteOfDay: 1380, durationMinutes: 60 },
      { dayIndex: 2, startMinuteOfDay: 0, durationMinutes: 120 },
    ])
  })

  it('wraps a Sunday night block round to Monday, the way the week does', () => {
    // Sunday 23:00 = 6 * 1440 + 1380, two hours: one on Sunday, one back at the start of the week.
    expect(splitBlockByDay({ startMinute: 6 * 1440 + 1380, durationMinutes: 120 })).toEqual([
      { dayIndex: 6, startMinuteOfDay: 1380, durationMinutes: 60 },
      { dayIndex: 0, startMinuteOfDay: 0, durationMinutes: 60 },
    ])
  })
})

describe('week minutes', () => {
  it('round-trips a minute through a zone and back (#22)', () => {
    const tuesdayEvening = 1440 + 1200
    const local = utcWeekMinuteToLocal(tuesdayEvening, 'America/Argentina/Buenos_Aires')

    expect(local).not.toBe(tuesdayEvening)
    expect(localWeekMinuteToUtc(local, 'America/Argentina/Buenos_Aires')).toBe(tuesdayEvening)
  })

  it('wraps instead of going negative when a zone pushes a Monday slot back past the week', () => {
    // Monday 00:30 UTC is Sunday night in Buenos Aires, which is the end of the week and not -180.
    const local = utcWeekMinuteToLocal(30, 'America/Argentina/Buenos_Aires')

    expect(local).toBeGreaterThan(0)
    expect(local).toBeLessThan(7 * 24 * 60)
  })
})

describe('formatSlot', () => {
  it('writes the start alone when there is no duration', () => {
    expect(formatSlot({ weekday: 'Friday', hourtime: '20:00' }, 'es-AR')).toBe('Viernes 20:00')
  })

  it('closes the range with the duration, wrapping past midnight', () => {
    expect(formatSlot({ weekday: 'Tuesday', hourtime: '23:00' }, 'es-AR', '03:00')).toBe('Martes 23:00–02:00')
  })
})

describe('the datetime-local round trip', () => {
  it('reads a local wall clock as the UTC instant it names', () => {
    expect(localInputToUtcIso('2026-09-15T20:00', BUENOS_AIRES)).toBe('2026-09-15T23:00:00')
  })

  it('writes a UTC instant back as the local wall clock', () => {
    expect(utcIsoToLocalInput('2026-09-15T23:00:00', BUENOS_AIRES)).toBe('2026-09-15T20:00')
  })

  it('treats an empty field as no instant at all rather than as the epoch', () => {
    expect(localInputToUtcIso('', BUENOS_AIRES)).toBeNull()
    expect(utcIsoToLocalInput(null, BUENOS_AIRES)).toBe('')
  })
})

describe('formatDateTime and formatDate', () => {
  /**
   * The backend serializes `LocalDateTime` with no offset — `2026-09-09T01:00:00` — and JavaScript
   * reads a date like that as **local** time. Without normalizing it, a session at 01:00 UTC showed
   * as 01:00 to somebody three hours behind: exactly the bug the conversion exists to prevent (#22).
   */
  it('reads a bare instant from the API as UTC and not as the reader wall clock', () => {
    expect(formatDateTime('2026-09-09T01:00:00', 'es', BUENOS_AIRES)).toMatch(/8 sept/)
    expect(formatDateTime('2026-09-09T01:00:00', 'es', BUENOS_AIRES)).toMatch(/22:00/)
  })

  it('shows the same instant as the next day in Madrid, which is where it falls', () => {
    expect(formatDateTime('2026-09-09T01:00:00', 'es', MADRID)).toMatch(/9 sept/)
  })

  it('leaves an instant that already declares its offset alone', () => {
    expect(formatDateTime('2026-09-09T01:00:00Z', 'es', BUENOS_AIRES)).toMatch(/8 sept/)
  })

  it('applies the same reading to a plain date', () => {
    expect(formatDate('2026-09-09T01:00:00', 'es', BUENOS_AIRES)).toMatch(/8 sept/)
  })
})

/**
 * #230: `start_date` is a day, and a day does not move.
 *
 * The suite runs three hours behind UTC on purpose (#192), which is exactly the zone where treating
 * a date as an instant shows the day before — the bug this function exists to not have.
 */
describe('formatPlainDate', () => {
  it('shows the day the API sent, whatever zone the reader is in', () => {
    expect(formatPlainDate('2026-09-09', 'es')).toMatch(/9 sept/)
    expect(formatPlainDate('2026-01-01', 'es')).toMatch(/1 ene/)
  })

  it('follows the locale', () => {
    expect(formatPlainDate('2026-09-09', 'en')).toMatch(/Sep 9/)
  })
})

describe('formatRelativeDate', () => {
  const now = new Date('2026-09-10T12:00:00Z')

  it('reads as a distance, which is what the question about lastUsedAt actually is', () => {
    expect(formatRelativeDate('2026-09-07T12:00:00', 'es', now)).toBe('hace 3 días')
    expect(formatRelativeDate('2026-08-27T12:00:00', 'es', now)).toBe('hace 2 semanas')
    expect(formatRelativeDate('2026-01-10T12:00:00', 'es', now)).toBe('hace 8 meses')
  })

  it('speaks the reader’s language, because the locale is a parameter (#111, #192)', () => {
    expect(formatRelativeDate('2026-09-07T12:00:00', 'en', now)).toBe('3 days ago')
  })

  /**
   * The API sends `2026-09-10T11:00:00` with no offset and JavaScript reads a bare date-time as
   * local, which would make an hour ago read as hours off for anybody not on UTC (#22).
   */
  it('reads the instant as UTC even when it does not say so', () => {
    expect(formatRelativeDate('2026-09-10T11:00:00', 'es', now)).toBe('hace 1 hora')
    expect(formatRelativeDate('2026-09-10T11:00:00Z', 'es', now)).toBe('hace 1 hora')
  })

  /** Under a minute is "ahora": nobody needs to be told a file was touched four seconds ago. */
  it('collapses anything under a minute', () => {
    expect(formatRelativeDate('2026-09-10T11:59:56', 'es', now)).toBe('ahora')
  })
})
