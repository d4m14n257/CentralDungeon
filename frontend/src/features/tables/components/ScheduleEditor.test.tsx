import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ScheduleEditor } from './ScheduleEditor'
import type { TableScheduleEntry } from '../types'

const BUENOS_AIRES = 'America/Argentina/Buenos_Aires'

function renderEditor(value: TableScheduleEntry[], onChange = vi.fn()) {
  render(<ScheduleEditor value={value} onChange={onChange} timeZone={BUENOS_AIRES} duration="03:00" />)
  return onChange
}

describe('ScheduleEditor', () => {
  /** Lo que se guarda es UTC y lo que se lee es hora local (#22): el día se corre uno para atrás. */
  it('shows a UTC slot in the reader zone, and says what is stored underneath', () => {
    renderEditor([{ weekday: 'Wednesday', hourtime: '01:00:00' }])

    expect(screen.getByText('martes 22:00–01:00')).toBeInTheDocument()
    expect(screen.getByText('En UTC: miércoles 01:00')).toBeInTheDocument()
  })

  it('sends the new slot back in UTC, not in the zone it was typed in', async () => {
    const user = userEvent.setup()
    const onChange = renderEditor([])

    await user.clear(screen.getByLabelText('Hora'))
    await user.type(screen.getByLabelText('Hora'), '22:00')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    // Viernes 22:00 en Buenos Aires es sábado 01:00 en UTC.
    expect(onChange).toHaveBeenCalledWith([{ weekday: 'Saturday', hourtime: '01:00' }])
  })

  it('removes the slot the person asked to remove', async () => {
    const user = userEvent.setup()
    const onChange = renderEditor([
      { weekday: 'Wednesday', hourtime: '01:00:00' },
      { weekday: 'Saturday', hourtime: '23:00:00' },
    ])

    await user.click(screen.getByRole('button', { name: 'Quitar martes 22:00' }))

    expect(onChange).toHaveBeenCalledWith([{ weekday: 'Saturday', hourtime: '23:00:00' }])
  })

  /** La clave primaria es (mesa, día, hora): la misma franja dos veces es una sola franja. */
  it('refuses to add a slot the agenda already has', async () => {
    const user = userEvent.setup()
    const onChange = renderEditor([{ weekday: 'Saturday', hourtime: '01:00:00' }])

    await user.clear(screen.getByLabelText('Hora'))
    await user.type(screen.getByLabelText('Hora'), '22:00')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('names the zone the times are being written in', () => {
    renderEditor([])

    expect(screen.getByText(/America\/Argentina\/Buenos_Aires/)).toBeInTheDocument()
  })
})
