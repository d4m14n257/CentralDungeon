import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { MasterSummary } from '@/features/tables'
import type { TablePlayer } from '@/features/registrations'
import type { UserSummary } from '@/features/users'
import { MasterTablePlayersTab } from './MasterTablePlayersTab'

const MASTERS: MasterSummary[] = [
  { userId: 'user-1', name: 'Ana', karma: 8240, masterType: 'Primary' },
  { userId: 'user-2', name: 'Beto', karma: 8000, masterType: 'Secondary' },
]

const CANDIDATE: UserSummary = { id: 'user-3', discordUsername: 'carla', name: 'Carla' }

function player(overrides: Partial<TablePlayer> = {}): TablePlayer {
  return {
    registrationId: 'reg-9',
    userId: 'user-9',
    userName: 'Diego',
    userKarma: 6100,
    status: 'Player',
    blockedByName: null,
    blockedAt: null,
    blockJustification: null,
    ...overrides,
  }
}

const addMaster = vi.fn()
const removeMaster = vi.fn()
const confirm = vi.fn().mockResolvedValue(true)
let outletContext = { tableId: 'table-1', isPrimary: true, masters: MASTERS }
let players: TablePlayer[] = [player()]
/** What the veto dialog was opened with, captured from the stub that stands in for it. */
let openedDialog: { action: string; isPrimary: boolean; playerName: string } | null = null

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

/**
 * The dialog has its own test, so here it is a stub that records what it was handed.
 *
 * **What this tab decides is not what the dialog does with it** — it is *which* act the row offers
 * and to whom, which is exactly the pair of props captured below.
 */
vi.mock('@/features/registrations', () => ({
  useTablePlayers: () => ({ data: players, isPending: false, isLoadingError: false }),
  RegistrationStatusBadge: ({ status }: { status: string }) => <span>{status === 'Blocked' ? 'Vetado' : status}</span>,
  BlockPlayerDialog: (props: { action: string; isPrimary: boolean; playerName: string; open: boolean }) => {
    if (props.open) openedDialog = { action: props.action, isPrimary: props.isPrimary, playerName: props.playerName }
    return null
  },
}))

/** The requests block is `features/approvals`' and has its own query; it is not what this tab decides. */
vi.mock('@/features/approvals', () => ({
  BanRequestsSection: () => null,
}))

vi.mock('@/hooks/useConfirm', () => ({ useConfirm: () => confirm }))

function renderTab() {
  render(
    <MemoryRouter>
      <MasterTablePlayersTab />
    </MemoryRouter>,
  )
}

/** The roster row, scoped: the masters list above it carries names too. */
function rowFor(name: string) {
  const item = screen.getByText(name).closest('li')
  if (!item) throw new Error(`no row for ${name}`)
  return within(item)
}

describe('MasterTablePlayersTab', () => {
  beforeEach(() => {
    outletContext = { tableId: 'table-1', isPrimary: true, masters: MASTERS }
    players = [player()]
    openedDialog = null
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

  /** The roster was a list with no action at all until F3.4. The veto is what it gained (#29, #39). */
  it('offers the veto on each player of the roster', () => {
    renderTab()

    expect(rowFor('Diego').getByRole('button', { name: 'Vetar' })).toBeInTheDocument()
  })

  /**
   * `fase-3-admin-owner.md:169`, the half that is easy to get wrong: a co-master sees the action in
   * the same place, and **the button itself says it is a request**. Learning that inside the dialog
   * would be learning it after deciding — "antes de apretar, no después".
   */
  it('tells a co-master that what they send is a request, on the button and before it is pressed', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    renderTab()

    expect(rowFor('Diego').getByRole('button', { name: 'Pedir que se lo vete' })).toBeInTheDocument()
    expect(rowFor('Diego').queryByRole('button', { name: 'Vetar' })).not.toBeInTheDocument()
    expect(screen.getByText(/lo que mandás es un pedido/)).toBeInTheDocument()
  })

  /** And the act that travels is chosen by who the reader is, not by what the dialog is told to send. */
  it('opens the veto dialog knowing whether it will veto or ask', async () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    const user = userEvent.setup()
    renderTab()

    await user.click(rowFor('Diego').getByRole('button', { name: 'Pedir que se lo vete' }))

    expect(openedDialog).toEqual({ action: 'block', isPrimary: false, playerName: 'Diego' })
  })

  /**
   * **A veto that disappears from the screen is not reversible in practice** — which is the whole of
   * why #39 asks for a reason. The row stays, marked, saying whose decision it was and when, with the
   * act that undoes it attached to it.
   */
  it('keeps a vetoed row in the roster, with who vetoed it and when', () => {
    players = [
      player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00', blockJustification: 'Falta sin avisar' }),
    ]
    renderTab()

    const row = rowFor('Diego')
    expect(row.getByText('Vetado')).toBeInTheDocument()
    expect(row.getByText(/Ana/)).toBeInTheDocument()
    // The reason is on the row: it is what whoever weighs lifting the veto decides on (#39).
    expect(row.getByText(/Falta sin avisar/)).toBeInTheDocument()
    expect(row.getByRole('button', { name: 'Levantar el veto' })).toBeInTheDocument()
    // And not the veto again: it is already applied (principio 2).
    expect(row.queryByRole('button', { name: 'Vetar' })).not.toBeInTheDocument()
  })

  /** Lifting is the Primary's alone: a co-master asking for one is not a mechanism that exists. */
  it('offers no way for a co-master to lift a veto', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    players = [player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00' })]
    renderTab()

    expect(rowFor('Diego').queryByRole('button', { name: 'Levantar el veto' })).not.toBeInTheDocument()
  })
})
