import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { AttendanceSummaryView } from './AttendanceSummaryView'

describe('AttendanceSummaryView', () => {
  /** #137: the three numbers, without collapsing "excused" into "absent". */
  it('shows the three counts separately', () => {
    render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(screen.getByText('Presente').nextElementSibling).toHaveTextContent('8')
    expect(screen.getByText('Con aviso').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Ausente').nextElementSibling).toHaveTextContent('1')
  })

  /** The denominator is the sessions with something recorded: `Unknown` stays out (#137). */
  it('uses the recorded sessions as the denominator, not the whole run', () => {
    render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(screen.getByText('Sesiones registradas').nextElementSibling).toHaveTextContent('11')
  })

  /** Never a ratio and never a percentage: that would decide for the reader (#98, #137). */
  it('never renders a ratio or a percentage', () => {
    const { container } = render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(container.textContent).not.toMatch(/%|8\s*(de|\/)\s*11/)
  })

  /** A table that just started has nothing recorded, and that is not the same as being absent (#137). */
  it('says nothing is recorded yet instead of showing three zeros', () => {
    render(<AttendanceSummaryView summary={{ present: 0, excused: 0, absent: 0, registered: 0 }} />)

    expect(screen.getByText('Todavía no hay asistencia registrada.')).toBeInTheDocument()
    expect(screen.queryByText('Presente')).not.toBeInTheDocument()
  })
})
