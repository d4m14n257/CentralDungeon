import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { AttendanceEditor } from './AttendanceEditor'

const roster = [
  { userId: 'user-1', userName: 'Ana', attendance: 'Present' as const },
  { userId: 'user-2', userName: 'Beto', attendance: 'Unknown' as const },
]

describe('AttendanceEditor', () => {
  it('opens the card of the player whose row asked for it', async () => {
    const onOpenPerson = vi.fn()
    render(<AttendanceEditor roster={roster} isSaving={false} onSave={vi.fn()} onOpenPerson={onOpenPerson} />)

    await userEvent.click(screen.getByRole('button', { name: 'Ver la ficha de Beto' }))

    expect(onOpenPerson).toHaveBeenCalledWith('user-2')
  })

  it('offers the card on a read-only roster too, since reading is what the admin does there', () => {
    render(<AttendanceEditor roster={roster} isSaving={false} onSave={vi.fn()} readOnly onOpenPerson={vi.fn()} />)

    expect(screen.getAllByRole('button', { name: /Ver la ficha de/ })).toHaveLength(2)
  })

  it('draws no action when the screen gave no way to open a person', () => {
    render(<AttendanceEditor roster={roster} isSaving={false} onSave={vi.fn()} />)

    expect(screen.queryByRole('button', { name: /Ver la ficha de/ })).toBeNull()
  })
})
