import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { queryClient as appQueryClient } from '@/config/query'
import { ADMIN_QUEUE_ERROR_CODES, type AdminQueueItem } from '@/features/adminQueue'
import { ApiError } from '@/types/api'

import { AdminQueuePage } from './AdminQueuePage'

const list = vi.hoisted(() => vi.fn())
const claim = vi.hoisted(() => vi.fn())
const release = vi.hoisted(() => vi.fn())
const approveRequest = vi.hoisted(() => vi.fn())
const rejectRequest = vi.hoisted(() => vi.fn())
const approveTable = vi.hoisted(() => vi.fn())
const requestChanges = vi.hoisted(() => vi.fn())
const toastError = vi.hoisted(() => vi.fn())

// The API modules are mocked, not the hooks: that keeps the query keys, the composition of three
// features and the four states running for real, which is where this screen's behaviour lives.
vi.mock('@/features/adminQueue/api/adminQueueApi', () => ({ adminQueueApi: { list, claim, release } }))
// `sonner` renders outside this tree, so the only way to count the messages one failure produces is
// to hold the calls. Counting them is the point: #197 is as much about *one* message as about which.
vi.mock('sonner', () => ({ toast: { error: toastError, success: vi.fn() } }))
vi.mock('@/features/approvals/api/approvalsApi', () => ({
  approvalsApi: { approve: approveRequest, reject: rejectRequest, list: vi.fn(), byId: vi.fn(), mine: vi.fn(), submit: vi.fn() },
}))
vi.mock('@/features/tables/api/gameTablesApi', () => ({
  gameTablesApi: { approve: approveTable, requestChanges },
  tableTypesApi: { list: vi.fn() },
}))

function item(overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return {
    type: 'approval_request',
    id: 'req-1',
    kind: 'ApprovalRequest',
    // The wire name of the request's kind, which is what the backend actually sends: the tray
    // normalizes four sources into one shape and cannot translate for a vocabulary it does not own.
    // Every `rowFor('Rol de master')` below is therefore an assertion that the screen translates it.
    title: 'MasterGrant',
    requestedByName: 'dami',
    detail: 'Quiero dirigir una mesa de terror',
    waitingSince: '2026-03-04T12:00:00',
    claimedByName: null,
    claimedAt: null,
    ...overrides,
  }
}

function table(overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return item({
    type: 'game_table',
    id: 'table-1',
    kind: 'TableWaitingReview',
    title: 'La maldición de Strahd',
    requestedByName: 'ana',
    detail: null,
    ...overrides,
  })
}

function page(content: AdminQueueItem[]) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }
}

function wrap(children: ReactNode, url: string, queryClient: QueryClient) {
  return (
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

function renderPage(url = '/admin/queue') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(wrap(<AdminQueuePage />, url, queryClient))
}

/**
 * The same screen on **the application's own `QueryClient`**, the one from `config/query.ts`.
 *
 * Only for the two tests about how many messages a failure produces. The isolated client above has
 * no `MutationCache`, so the global "no pudimos completar la acción" of `reportMutationError` cannot
 * fire in it at all — which means a test written against it would pass whether or not the mutations
 * opt out of it, and the double message the reviewer found would be invisible. Here the real handler
 * is in the loop, so `showsItsOwnError` is actually being exercised.
 */
function renderPageOnAppClient(url = '/admin/queue') {
  appQueryClient.clear()
  return render(wrap(<AdminQueuePage />, url, appQueryClient))
}

/** The row of a table is a `<tr>` on a wide screen; jsdom renders both layouts, so scope to the table. */
async function rowFor(text: string) {
  const grid = await screen.findByRole('table', { name: 'Bandeja' })
  const cell = await within(grid).findByText(text)
  const row = cell.closest('tr')
  if (!row) throw new Error(`no row for ${text}`)
  return within(row)
}

beforeEach(() => {
  list.mockReset()
  claim.mockReset()
  release.mockReset()
  approveRequest.mockReset()
  rejectRequest.mockReset()
  approveTable.mockReset()
  requestChanges.mockReset()
  toastError.mockReset()
})

describe('AdminQueuePage', () => {
  /**
   * #136, the same reading as `/master` and `/admin/requests`: an empty tray is a fact about the
   * platform and it is good news. There is no second empty state to tell it apart from, because the
   * tray has no search — which is itself the decision of §2 of the contract.
   */
  it('reads an empty tray as good news and not as a broken screen', async () => {
    list.mockResolvedValue(page([]))

    renderPage()

    expect(await screen.findByText('No hay nada esperando una acción')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  /** #103: no guard in the router, so the 403 has to land somewhere that explains itself. */
  it('paints the forbidden state when the backend refuses the tray', async () => {
    list.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Access denied', errorCode: 'FORBIDDEN' }))

    renderPage()

    await waitFor(() => expect(screen.getByText('No tenés permiso')).toBeInTheDocument())
  })

  /**
   * #197: what a person reads is written on this side from the code the backend sent, never taken
   * from its text. A request's `title` arrives as `MasterGrant` — the canonical wire name (#253) —
   * and the row has to show the same sentence `/admin/requests` shows, from the very same key. A
   * table's title is its own name and is shown verbatim, because that one *is* user text.
   */
  it('writes a request’s kind in the reader’s language and leaves a table’s name alone', async () => {
    list.mockResolvedValue(page([item(), table()]))

    renderPage()
    const grid = await screen.findByRole('table', { name: 'Bandeja' })

    expect(within(grid).getByText('Rol de master')).toBeInTheDocument()
    expect(within(grid).queryByText('MasterGrant')).not.toBeInTheDocument()
    expect(within(grid).getByText('La maldición de Strahd')).toBeInTheDocument()
  })

  /** #185: which page is in the URL, like every other admin screen. */
  it('asks for the page the URL names', async () => {
    list.mockResolvedValue(page([]))

    renderPage('/admin/queue?page=2')

    await waitFor(() => expect(list).toHaveBeenCalledWith(2))
  })

  /**
   * The reservation has to be visible or nobody uses it: its whole value is telling colleagues "I am
   * on this one", and a mark nobody can see tells nobody anything.
   */
  it('says whether a row is free or already taken, and since when', async () => {
    list.mockResolvedValue(page([item(), table({ claimedByName: 'dami', claimedAt: '2026-03-04T12:00:00' })]))

    renderPage()
    const free = await rowFor('Rol de master')
    const mine = await rowFor('La maldición de Strahd')

    expect(free.getByText('Libre')).toBeInTheDocument()
    expect(mine.getByText(/Lo tomó dami/)).toBeInTheDocument()
  })

  /**
   * **Principio 2 the right way round.** The rule as it was first written said resolving required
   * holding the item, so this screen hid the resolutions on a free row — and `/admin/requests`, which
   * cannot reserve anything at all, refused every resolution it offered. The corrected rule is that
   * you cannot work on what **somebody else** took; a free item is resolved on the spot and taking it
   * is implicit.
   *
   * So hiding the buttons here would hide an action that works, which is the same sin as showing one
   * that cannot. Every row this screen can show is free or already the reader's — a colleague's is
   * filtered out of the listing — so every row offers its resolutions.
   */
  it('offers the resolutions on a free row, not only on one the reader took', async () => {
    list.mockResolvedValue(page([item(), table()]))

    renderPage()
    const freeRequest = await rowFor('Rol de master')
    const freeTable = await rowFor('La maldición de Strahd')

    expect(freeRequest.getByRole('button', { name: 'Aprobar' })).toBeInTheDocument()
    expect(freeRequest.getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    expect(freeTable.getByRole('button', { name: 'Pedir cambios' })).toBeInTheDocument()
    // Taking it is still offered - it is what drops the row out of everybody else's tray - but it is
    // an option, not a gate, so nothing tells the reader to press it first.
    expect(freeRequest.getByRole('button', { name: 'Tomar' })).toBeInTheDocument()
    expect(screen.queryByText(/para poder resolverlo/i)).not.toBeInTheDocument()
  })

  it('takes an item with the source and the id of its own table', async () => {
    list.mockResolvedValue(page([item()]))
    claim.mockResolvedValue(item({ claimedByName: 'dami', claimedAt: '2026-03-04T12:05:00' }))

    renderPage()
    const row = await rowFor('Rol de master')
    await userEvent.click(row.getByRole('button', { name: 'Tomar' }))

    // `(type, id)` and not an id of the tray: there is no `admin_queue` table (#11).
    await waitFor(() => expect(claim).toHaveBeenCalledWith('approval_request', 'req-1'))
  })

  /** Releasing is what makes the reservation bearable: the way out is one press, not a wait. */
  it('hands a claimed item back', async () => {
    list.mockResolvedValue(page([item({ claimedByName: 'dami', claimedAt: '2026-03-04T12:05:00' })]))
    release.mockResolvedValue(undefined)

    renderPage()
    const row = await rowFor('Rol de master')
    await userEvent.click(row.getByRole('button', { name: 'Soltar' }))

    await waitFor(() => expect(release).toHaveBeenCalledWith('approval_request', 'req-1'))
  })

  /**
   * The two kinds are resolved by different acts, and the row offers only the ones its own kind has.
   * A "Pedir cambios" on a request would be a button with nothing behind it.
   */
  it('offers each kind the acts that belong to it', async () => {
    list.mockResolvedValue(page([item({ claimedByName: 'dami' }), table({ claimedByName: 'dami' })]))

    renderPage()
    const request = await rowFor('Rol de master')
    const waiting = await rowFor('La maldición de Strahd')

    expect(request.getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    expect(request.queryByRole('button', { name: 'Pedir cambios' })).not.toBeInTheDocument()
    expect(waiting.getByRole('button', { name: 'Pedir cambios' })).toBeInTheDocument()
    expect(waiting.queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument()
  })

  /** #42: the note is required on both acts, and the form refuses an empty one without a round trip. */
  it('refuses to resolve a request without a note', async () => {
    list.mockResolvedValue(page([item()]))

    renderPage()
    const row = await rowFor('Rol de master')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))

    await waitFor(() => expect(approveRequest).not.toHaveBeenCalled())
  })

  /** What is being decided on stays in view while the answer is written, not one dialog back. */
  it('sends the note with the resolution, with the reason on screen while it is written', async () => {
    list.mockResolvedValue(page([item()]))
    approveRequest.mockResolvedValue(item())

    renderPage()
    const row = await rowFor('Rol de master')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('Quiero dirigir una mesa de terror')).toBeInTheDocument()
    await userEvent.type(dialog.getByRole('textbox'), 'Dirigiste en otra comunidad, adelante')
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))

    await waitFor(() => expect(approveRequest).toHaveBeenCalledWith('req-1', { resolutionNote: 'Dirigiste en otra comunidad, adelante' }))
  })

  /**
   * The mudanza of #176 seen from this end: requesting changes on a table is performed **here**,
   * with the justification the master reads on the status tab.
   */
  it('requests changes on a table from the tray', async () => {
    list.mockResolvedValue(page([table()]))
    requestChanges.mockResolvedValue({})

    renderPage()
    const row = await rowFor('La maldición de Strahd')
    await userEvent.click(row.getByRole('button', { name: 'Pedir cambios' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByRole('textbox'), 'Falta el horario')
    await userEvent.click(dialog.getByRole('button', { name: 'Pedir cambios' }))

    await waitFor(() => expect(requestChanges).toHaveBeenCalledWith('table-1', { justification: 'Falta el horario' }))
  })

  /**
   * #197: the reservation's refusal gets a sentence of its own, **inside the dialog that was
   * refused** and in the reader's language. Answering `ITEM_ALREADY_CLAIMED` with "no pudimos
   * completar la acción" would leave the reader pressing the button again on a row that is about to
   * vanish from their tray, instead of telling them a colleague got there first.
   *
   * Driven off `ADMIN_QUEUE_ERROR_CODES`, so a code added to that list without a sentence in the
   * bundle fails here rather than reaching somebody as a bare key.
   */
  it.each(ADMIN_QUEUE_ERROR_CODES)('renders %s inline, over the form that was refused', async (errorCode) => {
    list.mockResolvedValue(page([item()]))
    approveRequest.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'in English, for a log', errorCode }))

    renderPage()
    const row = await rowFor('Rol de master')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByRole('textbox'), 'Va')
    await userEvent.click(dialog.getByRole('button', { name: 'Aprobar' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).not.toContain('in English, for a log')
    expect(alert.textContent?.length ?? 0).toBeGreaterThan(10)
  })

  /**
   * #197 on the one act with no form to put the answer in.
   *
   * Approving a table is a confirmation, so by the time it is refused there is no dialog left open —
   * the sentence goes to a toast, and it still has to be **the** sentence. The refusal worth naming
   * is a colleague having taken the table inside the fifteen seconds between two polls; "no pudimos
   * completar la acción" would leave the reader pressing again on a row that is about to disappear.
   *
   * **One message, not two.** The mutation carries `showsItsOwnError`, so the global handler of
   * `config/query.ts` stays out of the way instead of adding the generic line underneath.
   */
  it('answers a refused table approval with its own sentence, once', async () => {
    // A free row, which is the realistic setup now: the reader never took it, pressed Aprobar, and
    // a colleague had claimed it in the meantime.
    list.mockResolvedValue(page([table()]))
    approveTable.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', status: 409, detail: 'in English, for a log', errorCode: 'ITEM_ALREADY_CLAIMED' }),
    )

    renderPageOnAppClient()
    const row = await rowFor('La maldición de Strahd')
    await userEvent.click(row.getByRole('button', { name: 'Aprobar' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1))
    expect(toastError).toHaveBeenCalledWith(i18n.t('admin:queue.errors.ITEM_ALREADY_CLAIMED'))
    // Never the backend's `detail`, which is English and written for a log.
    expect(toastError).not.toHaveBeenCalledWith(expect.stringContaining('in English, for a log'))
  })

  /**
   * The same rule from the other side: a refusal that **does** have a form open writes into the form
   * and nowhere else. Before `showsItsOwnError` was on this mutation, one failure produced two
   * messages — the good sentence inline and the generic toast on top of it, which is precisely the
   * line `queueErrors.ts` exists to replace.
   */
  it('answers a refused request for changes inline and raises no toast at all', async () => {
    list.mockResolvedValue(page([table()]))
    requestChanges.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', status: 409, detail: 'in English, for a log', errorCode: 'ITEM_ALREADY_CLAIMED' }),
    )

    renderPageOnAppClient()
    const row = await rowFor('La maldición de Strahd')
    await userEvent.click(row.getByRole('button', { name: 'Pedir cambios' }))
    const dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByRole('textbox'), 'Falta el horario')
    await userEvent.click(dialog.getByRole('button', { name: 'Pedir cambios' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(i18n.t('admin:queue.errors.ITEM_ALREADY_CLAIMED'))
    expect(toastError).not.toHaveBeenCalled()
  })

  /** Punto 8 de la definición de terminado (#231): the help is raised from the screen that prompts it. */
  it('explains the reservation from the screen itself', async () => {
    list.mockResolvedValue(page([item()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo funciona la bandeja' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/Se libera solo a los 15 minutos/)).toBeInTheDocument()
  })
})
