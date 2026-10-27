import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { AdminUserSummary, User } from '@/features/users'
import { ApiError } from '@/types/api'

import { AdminUsersPage } from './AdminUsersPage'

const list = vi.hoisted(() => vi.fn())
const me = vi.hoisted(() => vi.fn())

// The API modules are mocked, not the hooks: that keeps the capability rule, the query keys and the
// four states running for real, which is where this screen's behaviour actually lives.
vi.mock('@/features/users/api/adminUsersApi', () => ({ adminUsersApi: { list } }))
vi.mock('@/features/users/api/usersApi', () => ({ usersApi: { me } }))

function account(overrides: Partial<AdminUserSummary> = {}): AdminUserSummary {
  return {
    id: 'user-1',
    discordUsername: 'dami',
    name: 'Damián',
    country: 'AR',
    status: 'Allowed',
    roles: ['Player'],
    createdAt: '2026-01-15T12:00:00',
    ...overrides,
  }
}

function page(content: AdminUserSummary[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }
}

function signedInAs(...roles: string[]) {
  const profile: User = { id: 'me', name: 'Yo', country: 'AR', needsOnboarding: false, roles, hasManagedTables: false }
  me.mockResolvedValue(profile)
}

function renderPage(url = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <MemoryRouter initialEntries={[url]}>
        <QueryClientProvider client={queryClient}>
          <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
        </QueryClientProvider>
      </MemoryRouter>
    )
  }
  return render(wrap(<AdminUsersPage />))
}

/** The row of a table is a `<tr>` on a wide screen; jsdom renders both layouts, so scope to the table. */
async function rowFor(discordUsername: string) {
  const table = await screen.findByRole('table', { name: 'Usuarios' })
  const cell = await within(table).findByText(discordUsername)
  const row = cell.closest('tr')
  if (!row) throw new Error(`no row for ${discordUsername}`)
  return within(row)
}

beforeEach(() => {
  list.mockReset()
  me.mockReset()
})

describe('AdminUsersPage', () => {
  /**
   * The proof of §3's matrix on the screen, and of principle 2: **absent, not disabled**. An admin
   * opening this page has a way to make somebody a master and no way at all to make somebody an
   * admin — the dialog's role list is the whole of what is on offer, and it does not contain the rank.
   */
  it('an admin is offered Player and Master and no trace of Admin or Owner', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account()]))
    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Cambiar roles' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByRole('button', { name: /Jugador/ })).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: /Master/ })).toBeInTheDocument()
    expect(dialog.queryByRole('button', { name: /Admin/ })).not.toBeInTheDocument()
    expect(dialog.queryByRole('button', { name: /Owner/ })).not.toBeInTheDocument()
    // The hint speaks to the admin: only an owner moves Admin.
    expect(dialog.getByText(/el rol Admin lo mueve solo un owner/)).toBeInTheDocument()
  })

  /** Owner is not granted from the application (ADR-06 §1): the owner is offered three and told so. */
  it('the owner is offered Player, Master and Admin and never Owner to grant', async () => {
    signedInAs('Owner')
    list.mockResolvedValue(page([account()]))
    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Cambiar roles' }))

    const dialog = within(await screen.findByRole('dialog'))
    for (const label of ['Jugador', 'Master', 'Admin']) {
      expect(dialog.getByRole('button', { name: new RegExp(label) })).toBeInTheDocument()
    }
    expect(dialog.queryByRole('button', { name: /Owner/ })).not.toBeInTheDocument()
    expect(dialog.getByText(/El rol Owner no se da desde la aplicación/)).toBeInTheDocument()
    expect(dialog.queryByText(/solo un owner/)).not.toBeInTheDocument()
  })

  it('the owner can take Owner away from an account that holds it', async () => {
    signedInAs('Owner')
    list.mockResolvedValue(page([account({ roles: ['Owner'] })]))
    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Cambiar roles' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByRole('button', { name: /Owner/ })).toHaveTextContent('lo tiene')
  })

  /**
   * Between peers there is no authority — and not even the owner is offered it, because a block is
   * irreversible from the blocked side, who cannot sign in to ask for it to be undone.
   */
  it.each(['Admin', 'Owner'])('offers no block on an account holding %s', async (privileged) => {
    signedInAs('Owner')
    list.mockResolvedValue(page([account({ roles: ['Player', privileged as 'Admin'] }), account({ id: 'user-2', discordUsername: 'ana' })]))

    renderPage()
    const privilegedRow = await rowFor('dami')
    const ordinaryRow = await rowFor('ana')

    expect(privilegedRow.queryByRole('button', { name: 'Bloquear' })).not.toBeInTheDocument()
    expect(privilegedRow.queryByRole('button', { name: 'Desbloquear' })).not.toBeInTheDocument()
    // The comparison that makes the assertion mean something: the button does exist, one row down.
    expect(ordinaryRow.getByRole('button', { name: 'Bloquear' })).toBeInTheDocument()
  })

  /** It is the one screen that sees a closed account, so it is the one that offers the way back. */
  it('offers the way back on a blocked account', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account({ status: 'Blocked' })]))

    renderPage()
    const row = await rowFor('dami')

    expect(row.getByRole('button', { name: 'Desbloquear' })).toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Bloquear' })).not.toBeInTheDocument()
  })

  /**
   * Reading the record is not an action on the account, so it survives on a row nobody may touch.
   * Since ADR-06 §8 it opens the whole record, where the history now lives.
   */
  it('offers the record on every row, even the untouchable ones', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account({ roles: ['Owner'] })]))

    renderPage()
    const row = await rowFor('dami')

    expect(row.getByRole('button', { name: 'Ver ficha' })).toBeInTheDocument()
  })

  /** ADR-20 §1: no guard in the router, so the 403 has to land somewhere that explains itself. */
  it('paints the forbidden state when the backend refuses the listing', async () => {
    signedInAs('Player')
    list.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Access denied', errorCode: 'FORBIDDEN' }))

    renderPage()

    await waitFor(() => expect(screen.getByText('No tenés permiso')).toBeInTheDocument())
  })

  /** The four states of ADR-19 §4; this is the one that is easiest to leave out. */
  it('says the search found nobody, differently from having no accounts at all', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([]))

    renderPage()

    await waitFor(() => expect(screen.getByText('Todavía no hay cuentas')).toBeInTheDocument())
  })

  /**
   * Point 8 of plan-desarrollo.md §6 (ADR-20 §5): the help is raised from the screen that prompts
   * the question, and the search help documents **this** box's commands rather than a fixed list
   * (ADR-22 §3). `/role` and `/status` are written once, in `adminUserSearchFields`, and the box hands
   * them over - so this is what proves they reach the reader without being spelled out twice.
   */
  it('documents its own search commands, /role and /status among them', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo buscar' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('/role')).toBeInTheDocument()
    expect(dialog.getByText('/status')).toBeInTheDocument()
    expect(dialog.getByText('/discord_name')).toBeInTheDocument()
    expect(dialog.getByText('/user_name')).toBeInTheDocument()
    // The four choices of /role, offered rather than left to be guessed at.
    expect(dialog.getByText(/Jugador, Master, Admin, Owner/)).toBeInTheDocument()
    // And nothing from a box this screen is not.
    expect(dialog.queryByText(/file_name/)).not.toBeInTheDocument()
  })

  /** The explanation of who may grant what, raised from the header (ADR-20 §5). */
  it('offers the roles help from the screen itself', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo funciona' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/admin mueve Jugador y Master/i)).toBeInTheDocument()
  })

  it('shows an account`s roles as chips and its status as a badge', async () => {
    signedInAs('Admin')
    list.mockResolvedValue(page([account({ roles: ['Master', 'Player'], status: 'Blocked' })]))

    renderPage()
    const row = await rowFor('dami')

    // The order of ADR-07 §8, whatever order the server sent them in.
    expect(row.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual(['Jugador', 'Master'])
    expect(row.getByText('Bloqueada')).toBeInTheDocument()
  })

  /** A `?page=` past the end of a list that has rows is not «there are no accounts»: the way back must be on screen. */
  it('says a page past the end does not exist instead of claiming the list is empty', async () => {
    signedInAs('Admin')
    list.mockResolvedValue({ content: [], page: 7, size: 20, totalElements: 3, totalPages: 1 })

    renderPage('/?page=7')

    expect(await screen.findByText('Esa página no existe')).toBeInTheDocument()
    expect(screen.queryByText('Todavía no hay cuentas')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ir a la última página' })).toBeInTheDocument()
  })
})
