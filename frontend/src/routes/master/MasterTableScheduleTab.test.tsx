import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { TableScheduleEntry } from '@/features/tables'
import { MasterTableScheduleTab } from './MasterTableScheduleTab'

let schedule: TableScheduleEntry[] = []

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router')
  return {
    ...actual,
    useOutletContext: () => ({
      tableId: 'table-1',
      status: 'Published',
      isPrimary: true,
      schedule,
      startDate: null,
      totalSessions: null,
    }),
  }
})

function renderTab() {
  return render(
    <MemoryRouter>
      <MasterTableScheduleTab />
    </MemoryRouter>,
  )
}

describe('MasterTableScheduleTab', () => {
  // The test zone is Buenos Aires (UTC-3), so a slot stored at 17:00 UTC reads 14:00 here.
  it('closes each slot with its own end time, one duration per slot', () => {
    schedule = [
      { weekday: 'Tuesday', hourtime: '17:00:00', duration: '03:00:00' },
      { weekday: 'Saturday', hourtime: '17:00:00', duration: '06:00:00' },
    ]
    renderTab()

    expect(screen.getByText('Martes 14:00–17:00')).toBeInTheDocument()
    expect(screen.getByText('Sábado 14:00–20:00')).toBeInTheDocument()
  })

  it('does not show a table-wide duration that no longer exists', () => {
    schedule = [{ weekday: 'Tuesday', hourtime: '17:00:00', duration: '03:00:00' }]
    renderTab()

    expect(screen.queryByText('Duración de una sesión')).not.toBeInTheDocument()
  })

  it('shows only the start of a slot that claims no interval', () => {
    schedule = [{ weekday: 'Tuesday', hourtime: '17:00:00', duration: null }]
    renderTab()

    expect(screen.getByText('Martes 14:00')).toBeInTheDocument()
  })
})
