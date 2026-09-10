import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ScheduleEditor } from './ScheduleEditor'
import type { TableScheduleEntry } from '../types'

const BUENOS_AIRES = 'America/Argentina/Buenos_Aires'

/** Wednesday 01:00 UTC is Tuesday 22:00 in Buenos Aires — the case where the day itself moves (#22). */
const CROSSES_MIDNIGHT: TableScheduleEntry = { weekday: 'Wednesday', hourtime: '01:00:00', duration: '03:00' }

describe('ScheduleEditor', () => {
  it('shows the slot in the reader zone, and says what is stored underneath (#22)', () => {
    render(<ScheduleEditor value={[CROSSES_MIDNIGHT]} onChange={vi.fn()} timeZone={BUENOS_AIRES} />)

    expect(screen.getByText('Martes 22:00–01:00')).toBeInTheDocument()
    expect(screen.getByText('En UTC: Miércoles 01:00')).toBeInTheDocument()
  })

  it('adds nothing on its own: slots come from the grid now (#228)', () => {
    render(<ScheduleEditor value={[]} onChange={vi.fn()} timeZone={BUENOS_AIRES} />)

    // No day picker, no hour field, no «Agregar» — one way to add a slot, and it is not here.
    expect(screen.queryByRole('button', { name: 'Agregar' })).not.toBeInTheDocument()
    expect(screen.getByText(/grilla/i)).toBeInTheDocument()
  })

  it('changes the length of one slot without touching the others (#228)', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const saturday: TableScheduleEntry = { weekday: 'Saturday', hourtime: '18:00:00', duration: '03:00' }
    render(<ScheduleEditor value={[CROSSES_MIDNIGHT, saturday]} onChange={onChange} timeZone={BUENOS_AIRES} />)

    await user.click(screen.getByRole('combobox', { name: 'Duración de Sábado 15:00' }))
    await user.click(screen.getByRole('option', { name: '6 h' }))

    expect(onChange).toHaveBeenCalledWith([CROSSES_MIDNIGHT, { ...saturday, duration: '06:00' }])
  })

  it('removes the slot the person asked to remove', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const saturday: TableScheduleEntry = { weekday: 'Saturday', hourtime: '18:00:00', duration: '03:00' }
    render(<ScheduleEditor value={[CROSSES_MIDNIGHT, saturday]} onChange={onChange} timeZone={BUENOS_AIRES} />)

    await user.click(screen.getByRole('button', { name: 'Quitar Martes 22:00' }))

    expect(onChange).toHaveBeenCalledWith([saturday])
  })
})
