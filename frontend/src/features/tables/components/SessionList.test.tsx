import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { SessionList } from './SessionList'
import type { SessionListItem } from './SessionList'

const BUENOS_AIRES = 'America/Argentina/Buenos_Aires'

function session(overrides: Partial<SessionListItem> & Pick<SessionListItem, 'id' | 'sequenceNumber'>): SessionListItem {
  return { scheduledAt: '2026-09-09T01:00:00', status: 'Scheduled', ...overrides }
}

describe('SessionList', () => {
  /** What arrives is UTC and what is read is local time (#22): the day moves one back. */
  it('shows a UTC instant in the reader zone, not in UTC', () => {
    render(<SessionList sessions={[session({ id: 's1', sequenceNumber: 1 })]} timeZone={BUENOS_AIRES} />)

    // Wednesday 01:00 UTC is Tuesday 22:00 in Buenos Aires: the day has to be the 8th, not the 9th.
    expect(screen.getByText(/8 sept/)).toBeInTheDocument()
    expect(screen.getByText(/22:00/)).toBeInTheDocument()
  })

  it('names the zone it is showing, so a time is never ambiguous', () => {
    render(<SessionList sessions={[session({ id: 's1', sequenceNumber: 1 })]} timeZone={BUENOS_AIRES} />)

    expect(screen.getByText(`Se muestra en tu hora local (${BUENOS_AIRES})`)).toBeInTheDocument()
  })

  it('keeps the run numbering the caller gave it, cancelled sessions included', () => {
    render(
      <SessionList
        sessions={[
          session({ id: 's1', sequenceNumber: 1, status: 'Held' }),
          session({ id: 's2', sequenceNumber: 2, status: 'Cancelled' }),
          session({ id: 's3', sequenceNumber: 3 }),
        ]}
        timeZone={BUENOS_AIRES}
      />,
    )

    expect(screen.getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByText('Sesión 2')).toBeInTheDocument()
    expect(screen.getByText('Cancelada')).toBeInTheDocument()
    expect(screen.getByText('Jugada')).toBeInTheDocument()
  })

  /** `Unknown` is not an absence: it means nobody recorded anything, and it is not shown as data (#137). */
  it('shows my attendance when there is one, and stays silent when there is not', () => {
    render(
      <SessionList
        sessions={[
          session({ id: 's1', sequenceNumber: 1, status: 'Held', myAttendance: 'Excused' }),
          session({ id: 's2', sequenceNumber: 2, myAttendance: 'Unknown' }),
        ]}
        timeZone={BUENOS_AIRES}
      />,
    )

    expect(screen.getByText('Con aviso')).toBeInTheDocument()
    expect(screen.queryByText('Sin registrar')).not.toBeInTheDocument()
  })
})
