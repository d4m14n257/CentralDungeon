import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { APPROVAL_ERROR_CODES, type ApprovalRequestSummary } from '@/features/approvals'
import { ApiError } from '@/types/api'

import { AdminRequestsPage } from './AdminRequestsPage'
import { acceptReview } from '@/test/review'

const list = vi.hoisted(() => vi.fn())
const byId = vi.hoisted(() => vi.fn())
const approve = vi.hoisted(() => vi.fn())
const reject = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hooks: that keeps the query keys, the `?q=` round trip and the
// four states running for real, which is where this screen's behaviour actually lives.
vi.mock('@/features/approvals/api/approvalsApi', () => ({ approvalsApi: { list, byId, approve, reject } }))

function request(overrides: Partial<ApprovalRequestSummary> = {}): ApprovalRequestSummary {
  return {
    id: 'req-1',
    type: 'MasterGrant',
    status: 'Pending',
    requestedById: 'user-1',
    requestedByName: 'dami',
    justification: 'Quiero dirigir una mesa de terror',
    claimedByName: null,
    gameTableId: null,
    createdAt: '2026-03-04T12:00:00',
    ...overrides,
  }
}

function page(content: ApprovalRequestSummary[]) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }
}

/** Prints the router's search string, so a test can read what the screen wrote into the URL. */
function LocationSearch() {
  return <output data-testid="search">{decodeURIComponent(useLocation().search.replaceAll('+', ' '))}</output>
}

function renderPage(url = '/admin/requests') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <MemoryRouter initialEntries={[url]}>
        <QueryClientProvider client={queryClient}>
          <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
          <LocationSearch />
        </QueryClientProvider>
      </MemoryRouter>
    )
  }
  return render(wrap(<AdminRequestsPage />))
}

/** The row of a table is a `<tr>` on a wide screen; jsdom renders both layouts, so scope to the table. */
async function rowFor(name: string) {
  const table = await screen.findByRole('table', { name: 'Pedidos' })
  const cell = await within(table).findByText(name)
  const row = cell.closest('tr')
  if (!row) throw new Error(`no row for ${name}`)
  return within(row)
}

beforeEach(() => {
  list.mockReset()
  byId.mockReset()
  approve.mockReset()
  reject.mockReset()
})

describe('AdminRequestsPage', () => {
  /**
   * The decision of §2 of the contract: a tray that opens showing what is already resolved is a log,
   * not something anybody can work from. The filter is the screen's and not the endpoint's — `GET
   * /admin/requests` with no `q` answers with everything, like every other listing.
   */
  it('opens filtered by what is still waiting', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()

    await waitFor(() => expect(list).toHaveBeenCalledWith('/status Pending', 0, 25))
  })

  /** ADR-22 §6: the default is in the URL, so the link carries what the screen shows. */
  it('redirects an entry without a query to the default one', async () => {
    list.mockResolvedValue(page([]))

    renderPage()

    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('?q=/status Pending'))
  })

  /** And the filter is visible and removable, not hidden in the hook: it is a chip like any other. */
  it('shows that filter as a chip somebody can take off', async () => {
    list.mockResolvedValue(page([]))

    renderPage()

    // With no rows on screen there is exactly one «Pendiente» in the document, and it is the chip:
    // the status badge only ever appears inside a row.
    await screen.findByRole('combobox', { name: 'Buscar pedidos' })
    expect(screen.getByText('Pendiente')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Quitar/ })).toBeInTheDocument()
  })

  /** A `?q=` in the URL wins over the default: a filtered tray is something one admin sends another (ADR-12 §7). */
  it('respects a query restored from the URL instead of forcing its own', async () => {
    list.mockResolvedValue(page([]))

    renderPage('/admin/requests?q=%2Fstatus%20Rejected')

    await waitFor(() => expect(list).toHaveBeenCalledWith('/status Rejected', 0, 25))
  })

  /**
   * ADR-20 §4: the empty tray is good news. "Nothing is waiting for an answer" is a fact about the
   * platform; "nothing matched" is a fact about what was typed, and telling somebody who just
   * filtered by `Rejected` that everything is caught up would answer a question they did not ask.
   */
  it('reads an empty tray as good news and not as a broken screen', async () => {
    list.mockResolvedValue(page([]))

    renderPage()

    await waitFor(() => expect(screen.getByText('No hay nada esperando una respuesta')).toBeInTheDocument())
    expect(screen.queryByText('Ningún pedido coincide con esa búsqueda')).not.toBeInTheDocument()
  })

  it('says the search found nothing, differently, once the reader narrowed it themselves', async () => {
    list.mockResolvedValue(page([]))

    renderPage('/admin/requests?q=%2Fstatus%20Rejected')

    await waitFor(() => expect(screen.getByText('Ningún pedido coincide con esa búsqueda')).toBeInTheDocument())
  })

  /** ADR-20 §1: no guard in the router, so the 403 has to land somewhere that explains itself. */
  it('paints the forbidden state when the backend refuses the listing', async () => {
    list.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Access denied', errorCode: 'FORBIDDEN' }))

    renderPage()

    await waitFor(() => expect(screen.getByText('No tenés permiso')).toBeInTheDocument())
  })

  /**
   * Principio 2 again: a resolution is not re-resolved, so on a row that is already answered the two
   * buttons are **absent** rather than disabled — pressing one could only ever produce
   * `REQUEST_ALREADY_RESOLVED`.
   */
  it('offers approving and rejecting only on what is still pending', async () => {
    list.mockResolvedValue(page([request(), request({ id: 'req-2', requestedByName: 'ana', status: 'Approved' })]))

    renderPage()
    const pending = await rowFor('dami')
    const resolved = await rowFor('ana')

    expect(pending.getByRole('button', { name: 'Aprobar' })).toBeInTheDocument()
    expect(pending.getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    expect(resolved.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument()
    expect(resolved.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument()
    // Reading is not an action on the request, so it survives on a row nobody can resolve any more -
    // and there it is the only place the reason was written.
    expect(resolved.getByRole('button', { name: 'Ver detalle' })).toBeInTheDocument()
  })

  /** A player veto is the table Primary's to resolve: the backend answers 403, so an admin is not offered the acts. */
  it('offers no approve or reject on a player veto, only the detail', async () => {
    list.mockResolvedValue(page([request({ type: 'PlayerBan', requestedByName: 'beto' })]))
    byId.mockResolvedValue({
      ...request({ type: 'PlayerBan', requestedByName: 'beto' }),
      entityType: 'table_registration',
      entityId: 'reg-1',
      resolvedByName: null,
      resolutionNote: null,
      resolvedAt: null,
    })

    renderPage()
    const row = await rowFor('beto')

    expect(row.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument()
    await userEvent.click(row.getByRole('button', { name: 'Ver detalle' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(await dialog.findByText('Lo resuelve el master de la mesa, no un admin.')).toBeInTheDocument()
    expect(dialog.queryByRole('link')).not.toBeInTheDocument()
  })

  /** The detail points at what the request is about, with the screens an admin already has. */
  it('links the person or the table a request points at from its detail', async () => {
    list.mockResolvedValue(page([request({ type: 'TablePause' })]))
    byId.mockResolvedValue({
      ...request({ type: 'TablePause' }),
      entityType: 'game_table',
      entityId: 'table-7',
      resolvedByName: null,
      resolutionNote: null,
      resolvedAt: null,
    })

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Ver detalle' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(await dialog.findByRole('link', { name: 'Ver la mesa' })).toHaveAttribute('href', '/admin/tables/table-7')
  })

  /**
   * The row leads to the person who asked and, when the request is about a table (a pause, or a veto
   * through its application), to the table — in the table and in the phone card alike.
   */
  it('links the requester and the table from the row', async () => {
    list.mockResolvedValue(
      page([
        request({ type: 'PlayerBan', gameTableId: 'table-9' }),
        request({ id: 'req-2', requestedById: 'user-2', requestedByName: 'ana' }),
      ]),
    )

    renderPage()

    const row = await rowFor('dami')
    expect(row.getByRole('link', { name: 'dami' })).toHaveAttribute('href', '/admin/users/user-1')
    expect(row.getByRole('link', { name: 'Ver la mesa' })).toHaveAttribute('href', '/admin/tables/table-9')
    // Both layouts render: a veto's table is linked in the card too, and a request about a person has none.
    const tableLinks = screen.getAllByRole('link', { name: 'Ver la mesa' })
    expect(tableLinks).toHaveLength(2)
    tableLinks.forEach((link) => expect(link).toHaveAttribute('href', '/admin/tables/table-9'))
    const other = await rowFor('ana')
    expect(other.getByRole('link', { name: 'ana' })).toHaveAttribute('href', '/admin/users/user-2')
    expect(other.queryByRole('link', { name: 'Ver la mesa' })).not.toBeInTheDocument()
  })

  /** The reason is the row: an admin decides on it and on nothing else. */
  it('shows what was asked and by whom', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()
    const row = await rowFor('dami')

    expect(row.getByText('Quiero dirigir una mesa de terror')).toBeInTheDocument()
    expect(row.getByText('Rol de master')).toBeInTheDocument()
    expect(row.getByText('Pendiente')).toBeInTheDocument()
  })

  /** ADR-16 §1: the note is required on both acts, and the form refuses an empty one without a round trip. */
  it('refuses to resolve without a note', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))

    await waitFor(() => expect(approve).not.toHaveBeenCalled())
  })

  /** What was asked is on screen while the answer is being written, not one dialog back. */
  it('keeps the reason in view while the answer is written', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Rechazar' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('Quiero dirigir una mesa de terror')).toBeInTheDocument()
    expect(dialog.getByText(/Pedido por dami/)).toBeInTheDocument()
  })

  /**
   * The two approvals look identical on screen and do completely different things: one hands out a
   * role, the other creates nothing at all. An admin who assumes the second builds the table will
   * approve it, tell nobody, and leave whoever asked waiting for a table that is never coming — so
   * the dialog says which act it is about to perform.
   */
  it('says what approving this particular kind will do', async () => {
    list.mockResolvedValue(page([request({ type: 'TableOpen' })]))

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/La mesa no se crea sola/)).toBeInTheDocument()
  })

  it('sends the note with the resolution', async () => {
    list.mockResolvedValue(page([request()]))
    approve.mockResolvedValue({ ...request(), status: 'Approved' })

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByRole('textbox'), 'Dirigiste en otra comunidad, adelante')
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))
    await acceptReview()

    await waitFor(() => expect(approve).toHaveBeenCalledWith('req-1', { resolutionNote: 'Dirigiste en otra comunidad, adelante' }))
  })

  /**
   * ADR-23 §3: each of the four refusals is answered with a sentence of its own, rendered **over the
   * button that was just pressed** and not in a toast that disappears in four seconds — the form is
   * still open and the answer belongs where the reader is looking.
   *
   * The race is the one this cannot be prevented for: two admins with the same row on screen.
   */
  it.each(APPROVAL_ERROR_CODES)('renders %s inline, over the form that was refused', async (errorCode) => {
    list.mockResolvedValue(page([request()]))
    approve.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'in English, for a log', errorCode }))

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByRole('textbox'), 'Va')
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))
    await acceptReview()

    const alert = await screen.findByRole('alert')
    // The reader's language, never the backend's `detail`.
    expect(alert.textContent).not.toContain('in English, for a log')
    expect(alert.textContent?.length ?? 0).toBeGreaterThan(10)
  })

  /** Point 8 of plan-desarrollo.md §6 (ADR-20 §5): the help is raised from the screen that prompts it. */
  it('offers the help of the tray from the screen itself', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo funciona' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/no crea la mesa/i)).toBeInTheDocument()
  })

  /** ADR-22 §3: the search help documents **this** box's commands rather than a fixed list. */
  it('documents its own search commands', async () => {
    list.mockResolvedValue(page([request()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo buscar' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('/request_type')).toBeInTheDocument()
    expect(dialog.getByText('/status')).toBeInTheDocument()
    expect(dialog.getByText('/requested_by')).toBeInTheDocument()
    // And nothing from a box this screen is not.
    expect(dialog.queryByText(/discord_name/)).not.toBeInTheDocument()
  })

  /** The resolution is only readable in the detail: the listing's row has no field for it. */
  it('reads the resolution of an answered request from its detail', async () => {
    list.mockResolvedValue(page([request({ status: 'Rejected' })]))
    byId.mockResolvedValue({
      ...request({ status: 'Rejected' }),
      entityType: 'user',
      entityId: 'user-1',
      resolvedByName: 'ana',
      resolutionNote: 'Todavía no tenés mesas jugadas acá',
      resolvedAt: '2026-03-06T12:00:00',
    })

    renderPage()
    const row = await rowFor('dami')
    await userEvent.click(row.getByRole('button', { name: 'Ver detalle' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(await dialog.findByText('Todavía no tenés mesas jugadas acá')).toBeInTheDocument()
    expect(dialog.getByText(/Resuelto por ana/)).toBeInTheDocument()
    expect(dialog.getByRole('link', { name: 'Ver a quien lo pidió' })).toHaveAttribute('href', '/admin/users/user-1')
  })
})

describe('AdminRequestsPage default filter', () => {
  async function searchBox() {
    return screen.findByRole('combobox', { name: 'Buscar pedidos' })
  }

  /** Removing the chip and searching leaves an empty `?q=`: «all», not a reason to redirect back. */
  it('leaves an empty q, which shows everything, when the chip is removed', async () => {
    list.mockResolvedValue(page([request({ status: 'Approved' })]))
    renderPage()
    await searchBox()

    await userEvent.click(screen.getByRole('button', { name: /Quitar/ }))
    await userEvent.type(await searchBox(), '{Enter}')

    await waitFor(() => expect(list).toHaveBeenCalledWith(undefined, 0, 25))
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?q=$/)
    expect(await screen.findByRole('table', { name: 'Pedidos' })).toBeInTheDocument()
    expect(screen.queryByText('No hay nada esperando una respuesta')).not.toBeInTheDocument()
  })

  /** What is typed joins the default chip like in any search. */
  it('composes a typed criterion with the default chip', async () => {
    list.mockResolvedValue(page([]))
    renderPage()

    await userEvent.type(await searchBox(), '/requested_by ana{Enter}{Enter}')

    await waitFor(() => expect(list).toHaveBeenCalledWith('/status Pending /and /requested_by ana', 0, 25))
    expect(screen.getByTestId('search')).toHaveTextContent('?q=/status Pending /and /requested_by ana')
  })

  /** «Limpiar filtros» goes back to the default, not to the empty search. */
  it('returns to the default query when the filters are cleared', async () => {
    list.mockResolvedValue(page([]))
    renderPage('/admin/requests?q=')
    await waitFor(() => expect(list).toHaveBeenCalledWith(undefined, 0, 25))

    await userEvent.click(await screen.findByRole('button', { name: /Limpiar filtros/ }))

    await waitFor(() => expect(list).toHaveBeenLastCalledWith('/status Pending', 0, 25))
    expect(screen.getByTestId('search')).toHaveTextContent('?q=/status Pending')
  })
})
