import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeAll, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { WeeklyScheduleGrid } from './WeeklyScheduleGrid'
import type { WeeklyCommitment } from '../types'

const MINUTES_PER_DAY = 24 * 60

/** Minutes from Monday 00:00 UTC for a weekday index (Monday = 0) and an hour. */
function at(dayIndex: number, hour: number): number {
  return dayIndex * MINUTES_PER_DAY + hour * 60
}

const COMMITMENTS: WeeklyCommitment[] = [
  {
    tableId: 'late',
    tableName: 'La Cripta',
    role: 'Master',
    status: 'InProgress',
    blocks: [
      { startMinute: at(1, 20), durationMinutes: 180 },
      { startMinute: at(3, 21), durationMinutes: 90 },
    ],
  },
  {
    tableId: 'early',
    tableName: 'Faro Gris',
    role: 'Player',
    status: 'InProgress',
    blocks: [{ startMinute: at(1, 18), durationMinutes: 60 }],
  },
]

describe('WeeklyScheduleGrid on a phone', () => {
  // jsdom has no layout, so no element scrolls: the grid's scroll-to-first-busy-hour needs a stub.
  beforeAll(() => {
    Element.prototype.scrollTo = vi.fn()
  })

  it('lists the slots by day in order, each with its hour, length and table, and skips empty days', () => {
    render(
      <MemoryRouter>
        <WeeklyScheduleGrid commitments={COMMITMENTS} timeZone="UTC" linkFor={(commitment) => `/tables/${commitment.tableId}`} />
      </MemoryRouter>,
    )

    // Only the two days that have something on them get a heading.
    const days = screen.getAllByRole('heading', { level: 3 })
    expect(days.map((day) => day.textContent)).toEqual(['Martes', 'Jueves'])

    const tuesday = within(days[0]!.closest('section')!)
    const rows = tuesday.getAllByRole('listitem')
    expect(rows).toHaveLength(2)
    // The 18:00 slot comes first even though its table was listed second.
    expect(rows[0]).toHaveTextContent('18:00–19:00')
    expect(rows[0]).toHaveTextContent('1 h')
    expect(rows[0]).toHaveTextContent('Faro Gris')
    expect(rows[1]).toHaveTextContent('20:00–23:00')
    expect(rows[1]).toHaveTextContent('3 h')
    expect(within(rows[1]!).getByRole('link', { name: 'La Cripta' })).toHaveAttribute('href', '/tables/late')

    const thursday = within(days[1]!.closest('section')!).getAllByRole('listitem')
    expect(thursday).toHaveLength(1)
    expect(thursday[0]).toHaveTextContent('21:00–22:30')
    expect(thursday[0]).toHaveTextContent('1 h 30 min')
  })
})
