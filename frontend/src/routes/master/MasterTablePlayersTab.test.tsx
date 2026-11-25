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
  { userId: 'user-1', name: 'Ana', masterType: 'Primary' },
  { userId: 'user-2', name: 'Beto', masterType: 'Secondary' },
]

const CANDIDATE: UserSummary = { id: 'user-3', discordUsername: 'carla', name: 'Carla' }

function player(overrides: Partial<TablePlayer> = {}): TablePlayer {
  return {
    registrationId: 'reg-9',
    userId: 'user-9',
    userName: 'Diego',
    status: 'Player',
    blockedByName: null,
    blockedAt: null,
    blockJustification: null,
    banRequestedByName: null,
    banRequestJustification: null,
    ...overrides,
  }
}

const addMaster = vi.fn()
const removeMaster = vi.fn()
const confirm = vi.fn().mockResolvedValue(true)
let outletContext: {
  tableId: string
  isPrimary: boolean
  masters: MasterSummary[]
  readOnly?: boolean
  onOpenPerson?: (userId: string) => void
} = {
  tableId: 'table-1',
  isPrimary: true,
  masters: MASTERS,
}
let players: TablePlayer[] = [player()]
/** What the veto dialog was opened with, captured from the stub that stands in for it. */
let openedDialog: { action: string; playerName: string } | null = null

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
  BlockPlayerDialog: (props: { action: string; playerName: string; open: boolean }) => {
    if (props.open) openedDialog = { action: props.action, playerName: props.playerName }
    return null
  },
}))

/** The requests block is `features/approvals`' and has its own query; what matters here is who it is told can resolve. */
vi.mock('@/features/approvals', () => ({
  BanRequestsSection: ({ canResolve }: { canResolve: boolean }) => <p>{canResolve ? 'requests: admin' : 'requests: master'}</p>,
}))

vi.mock('@/hooks/useConfirm', () => ({ useConfirm: () => confirm, useConfirmOpen: () => false }))

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

  /**
   * An admin reads the same tab from /admin/tables/:id and changes nothing on it - neither the
   * masters nor the roster, and asks for no veto: the admin is who decides one.
   */
  it('read by an admin, shows the masters and the roster with no master’s button', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS, readOnly: true }
    players = [player(), player({ registrationId: 'reg-8', userId: 'user-8', userName: 'Eva', status: 'Blocked' })]
    renderTab()

    expect(screen.getByText('Ana')).toBeInTheDocument()
    expect(rowFor('Diego').queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar a Beto' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Elegir carla' })).not.toBeInTheDocument()
  })

  /** An admin decided the veto, so an admin lifts it from the table's own tab — and answers the requests. */
  it('lets an admin lift a veto, and tells the requests section the admin decides them', async () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS, readOnly: true }
    players = [player({ registrationId: 'reg-8', userId: 'user-8', userName: 'Eva', status: 'Blocked', blockedByName: 'Ana' })]
    const user = userEvent.setup()
    renderTab()

    expect(screen.getByText('requests: admin')).toBeInTheDocument()
    await user.click(rowFor('Eva').getByRole('button', { name: 'Levantar el veto' }))
    expect(openedDialog).toEqual({ action: 'unblock', playerName: 'Eva' })
  })

  /** The tab does not decide what a person's card is - the screen that mounts it does. */
  it('opens the card the screen handed down, for a master and for a player alike', async () => {
    const onOpenPerson = vi.fn()
    outletContext = { tableId: 'table-1', isPrimary: true, masters: MASTERS, onOpenPerson }
    const user = userEvent.setup()
    renderTab()

    await user.click(screen.getByRole('button', { name: 'Ver la ficha de Beto' }))
    await user.click(screen.getByRole('button', { name: 'Ver la ficha de Diego' }))

    expect(onOpenPerson).toHaveBeenNthCalledWith(1, 'user-2')
    expect(onOpenPerson).toHaveBeenNthCalledWith(2, 'user-9')
  })

  /** On screen these are "master" and "co-master" — the wire words never reach a reader. */
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

  /** Removing somebody takes their access away at once: it asks first. */
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

  /** Any master asks for a veto, from the same button: the roster is where the people are. */
  it('offers «Pedir veto» on each player of the roster', () => {
    renderTab()
    expect(rowFor('Diego').getByRole('button', { name: 'Pedir veto' })).toBeInTheDocument()
    expect(rowFor('Diego').queryByRole('button', { name: 'Vetar' })).not.toBeInTheDocument()
    expect(screen.getByText('requests: master')).toBeInTheDocument()
  })

  it('offers the same button to a co-master, and says before pressing it that an admin decides', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    renderTab()

    expect(rowFor('Diego').getByRole('button', { name: 'Pedir veto' })).toBeInTheDocument()
    expect(screen.getByText(/lo decide un admin/)).toBeInTheDocument()
  })

  it('opens the dialog to ask, never to veto', async () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    const user = userEvent.setup()
    renderTab()

    await user.click(rowFor('Diego').getByRole('button', { name: 'Pedir veto' }))

    expect(openedDialog).toEqual({ action: 'request', playerName: 'Diego' })
  })

  /**
   * **A veto that disappears from the screen is not reversible in practice**. The row stays, marked,
   * saying whose decision it was and when, with the act that undoes it attached to it.
   */
  it('keeps a vetoed row in the roster, with who vetoed it and when, and lets the Primary lift it', () => {
    players = [
      player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00', blockJustification: 'Falta sin avisar' }),
    ]
    renderTab()

    const row = rowFor('Diego')
    expect(row.getByText('Vetado')).toBeInTheDocument()
    expect(row.getByText(/Ana/)).toBeInTheDocument()
    expect(row.getByText(/Falta sin avisar/)).toBeInTheDocument()
    expect(row.getByRole('button', { name: 'Levantar el veto' })).toBeInTheDocument()
    // And not the request again: it is already applied.
    expect(row.queryByRole('button', { name: 'Pedir veto' })).not.toBeInTheDocument()
  })

  /** A veto is asked for by a master and decided by an admin, so the row carries both reasons. */
  it('shows the master who asked for the veto and why, then the admin who decided it and why', () => {
    players = [
      player({
        status: 'Blocked',
        blockedByName: 'Ana',
        blockedAt: '2026-03-04T12:00:00',
        blockJustification: 'Confirmado',
        banRequestedByName: 'Bruno',
        banRequestJustification: 'Falta sin avisar',
      }),
    ]
    renderTab()

    const row = rowFor('Diego')
    const requested = row.getByText('Lo pidió Bruno')
    const requestReason = row.getByText('«Falta sin avisar»')
    const decided = row.getByText(/Vetado por Ana/)
    const decisionReason = row.getByText('«Confirmado»')
    // In that order: the request, then the decision on it.
    expect(requested.compareDocumentPosition(requestReason) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(requestReason.compareDocumentPosition(decided) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(decided.compareDocumentPosition(decisionReason) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  /** A veto older than requests has nothing behind it: only the admin's side shows. */
  it('shows only the admin side of a veto with no request behind it', () => {
    players = [player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00', blockJustification: 'Confirmado' })]
    renderTab()

    const row = rowFor('Diego')
    expect(row.getByText(/Vetado por Ana/)).toBeInTheDocument()
    expect(row.getByText('«Confirmado»')).toBeInTheDocument()
    expect(row.queryByText(/Lo pidió/)).not.toBeInTheDocument()
  })

  it('opens the dialog to lift the veto', async () => {
    players = [player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00' })]
    const user = userEvent.setup()
    renderTab()

    await user.click(rowFor('Diego').getByRole('button', { name: 'Levantar el veto' }))

    expect(openedDialog).toEqual({ action: 'unblock', playerName: 'Diego' })
  })

  /** Lifting is the Primary's or an admin's: a co-master lifting is not a mechanism that exists. */
  it('offers no way for a co-master to lift a veto', () => {
    outletContext = { tableId: 'table-1', isPrimary: false, masters: MASTERS }
    players = [player({ status: 'Blocked', blockedByName: 'Ana', blockedAt: '2026-03-04T12:00:00' })]
    renderTab()

    expect(rowFor('Diego').queryByRole('button', { name: 'Levantar el veto' })).not.toBeInTheDocument()
  })
})
