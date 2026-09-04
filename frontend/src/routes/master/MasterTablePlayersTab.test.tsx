import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { MasterSummary } from '@/features/tables'
import type { UserSummary } from '@/features/users'
import { MasterTablePlayersTab } from './MasterTablePlayersTab'

const MASTERS: MasterSummary[] = [
  { userId: 'user-1', name: 'Ana', karma: 8240, masterType: 'Primary' },
  { userId: 'user-2', name: 'Beto', karma: 8000, masterType: 'Secondary' },
]

const CANDIDATE: UserSummary = { id: 'user-3', discordUsername: 'carla', name: 'Carla' }

const addMaster = vi.fn()
const removeMaster = vi.fn()
const confirm = vi.fn().mockResolvedValue(true)
let outletContext = { tableId: 'table-1', isPrimary: true, masters: MASTERS }

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router')
  return { ...actual, useOutletContext: () => outletContext }
})

/** The real search box has its own test; what matters here is what the section does with a pick. */
vi.mock('@/features/users', () => ({
  UserPicker: ({ onSelect }: { onSelect: (user: UserSummary) => void }) => (
    <button type="button" onClick={() => onSelect(CANDIDATE)}>
      Elegir carla
    </button>
  ),
}))

vi.mock('@/features/tables', () => ({
  useAddMaster: () => ({ mutate: addMaster, isPending: false }),
  useRemoveMaster: () => ({ mutate: removeMaster, isPending: false }),
}))

vi.mock('@/features/registrations', () => ({
  useTablePlayers: () => ({ data: [{ userId: 'user-9', userName: 'Diego', userKarma: 6100 }], isPending: false, isLoadingError: false }),
}))

vi.mock('@/components/ConfirmDialog', () => ({ useConfirm: () => confirm }))

function renderTab() {
  render(
    <MemoryRouter>
      <MasterTablePlayersTab />
    </MemoryRouter>,
  )
}

describe('MasterTablePlayersTab', () => {
  beforeEach(() => {
    outletContext = { tableId: 'table-1', isPrimary: true, masters: MASTERS }
    vi.clearAllMocks()
    confirm.mockResolvedValue(true)
  })

  /** On screen these are "master" and "co-master" — the wire words never reach a reader (#166). */
  it('names the two roles in the words the interface uses, not the ones the API sends', () => {
    renderTab()

    expect(screen.getByText('Master')).toBeInTheDocument()
    expect(screen.getByText('Co-master')).toBeInTheDocument()
    expect(screen.queryByText('Primary')).not.toBeInTheDocument()
    expect(screen.queryByText('Secondary')).not.toBeInTheDocument()
  })

  /** The table's master cannot be removed — handing it over comes first, and the backend refuses. */
  it('offers no way to remove or promote the table’s own master', () => {
    renderTab()

    expect(screen.queryByRole('button', { name: 'Quitar a Ana' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitar a Beto' })).toBeInTheDocument()
  })

  /** Removing somebody takes their access away at once: it asks first (principio 3). */
  it('confirms before removing a co-master', async () => {
    const user = userEvent.setup()
    renderTab()

    await user.click(screen.getByRole('button', { name: 'Quitar a Beto' }))

    expect(confirm).toHaveBeenCalled()
    expect(removeMaster).toHaveBeenCalledWith('user-2', expect.anything())
  })

  it('does not remove anybody when the confirmation is declined', async () => {
    confirm.mockResolvedValue(false)
    const user = userEvent.setup()
    renderTab()

    await user.click(screen.getByRole('button', { name: 'Quitar a Beto' }))

    expect(removeMaster).not.toHaveBeenCalled()
  })

  it('adds the person picked as a co-master, never as the master', async () => {
    const user = userEvent.setup()
    renderTab()

    await user.click(screen.getByRole('button', { name: 'Elegir carla' }))

    expect(addMaster).toHaveBeenCalledWith({ userId: 'user-3', masterType: 'Secondary' }, expect.anything())
  })

  /** A co-master reads who runs the table but changes nothing: the backend only takes this from the Primary. */
  it('hides every control from somebody who is not the table’s master', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    renderTab()

    expect(screen.getByText('Beto')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar a Beto' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Elegir carla' })).not.toBeInTheDocument()
  })
})
