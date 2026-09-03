import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { AttendanceSummaryView } from './AttendanceSummaryView'

describe('AttendanceSummaryView', () => {
  /** #137: los tres números, sin colapsar «con aviso» dentro de «ausente». */
  it('shows the three counts separately', () => {
    render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(screen.getByText('Presente').nextElementSibling).toHaveTextContent('8')
    expect(screen.getByText('Con aviso').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Ausente').nextElementSibling).toHaveTextContent('1')
  })

  /** El denominador son las sesiones con algo registrado: `Unknown` queda fuera (#137). */
  it('uses the recorded sessions as the denominator, not the whole run', () => {
    render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(screen.getByText('Sesiones registradas').nextElementSibling).toHaveTextContent('11')
  })

  /** Nunca una razón ni un porcentaje: eso decidiría por quien lee (#98, #137). */
  it('never renders a ratio or a percentage', () => {
    const { container } = render(<AttendanceSummaryView summary={{ present: 8, excused: 2, absent: 1, registered: 11 }} />)

    expect(container.textContent).not.toMatch(/%|8\s*(de|\/)\s*11/)
  })

  /** Una mesa recién arrancada no tiene nada registrado, y eso no es ser ausente (#137). */
  it('says nothing is recorded yet instead of showing three zeros', () => {
    render(<AttendanceSummaryView summary={{ present: 0, excused: 0, absent: 0, registered: 0 }} />)

    expect(screen.getByText('Todavía no hay asistencia registrada.')).toBeInTheDocument()
    expect(screen.queryByText('Presente')).not.toBeInTheDocument()
  })
})
