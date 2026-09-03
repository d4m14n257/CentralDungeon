import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatDateTime,
  formatMinutes,
  formatSlot,
  localInputToUtcIso,
  localSlotToUtc,
  minutesOfDay,
  utcIsoToLocalInput,
  utcSlotToLocal,
  weekdayName,
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
    expect(weekdayName('Tuesday', 'es-AR')).toBe('martes')
    expect(weekdayName('Tuesday', 'en-US')).toBe('Tuesday')
  })
})

describe('formatSlot', () => {
  it('writes the start alone when there is no duration', () => {
    expect(formatSlot({ weekday: 'Friday', hourtime: '20:00' }, 'es-AR')).toBe('viernes 20:00')
  })

  it('closes the range with the duration, wrapping past midnight', () => {
    expect(formatSlot({ weekday: 'Tuesday', hourtime: '23:00' }, 'es-AR', '03:00')).toBe('martes 23:00–02:00')
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
   * El backend serializa `LocalDateTime` sin offset — `2026-09-09T01:00:00` — y JavaScript lee una
   * fecha así como hora **local**. Sin normalizarla, una sesión de las 01:00 UTC se mostraba como
   * 01:00 a alguien tres horas atrás: justo el error que la conversión existe para evitar (#22).
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
