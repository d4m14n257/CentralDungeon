import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { MasterTableHistoryTab } from './MasterTableHistoryTab'

const onOpenPerson = vi.fn()

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router')
  return { ...actual, useOutletContext: () => ({ tableId: 'table-1', masters: [{ userId: 'user-5' }], onOpenPerson }) }
})

vi.mock('@/features/tables', () => ({
  useTableStatusHistory: () => ({
    data: [
      {
        id: 'c-1',
        fromStatus: 'Opened',
        toStatus: 'Pause',
        changedById: 'user-5',
        changedByName: 'Ana',
        justification: null,
        createdAt: '2026-10-01T10:00:00',
      },
      {
        id: 'c-2',
        fromStatus: 'Pause',
        toStatus: 'InProgress',
        changedById: 'admin-9',
        changedByName: 'Bruno',
        justification: null,
        createdAt: '2026-10-02T10:00:00',
      },
    ],
    isPending: false,
    isLoadingError: false,
  }),
}))

describe('MasterTableHistoryTab', () => {
  it('opens the card of whoever made a change', async () => {
    render(<MasterTableHistoryTab />)

    await userEvent.click(screen.getByRole('button', { name: 'Ver la ficha de Ana' }))

    expect(onOpenPerson).toHaveBeenCalledWith('user-5')
  })

  it('leaves an admin who does not run the table as plain text, since the master cannot read that card', () => {
    render(<MasterTableHistoryTab />)

    expect(screen.getByText(/Bruno/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ver la ficha de Bruno' })).not.toBeInTheDocument()
  })
})
