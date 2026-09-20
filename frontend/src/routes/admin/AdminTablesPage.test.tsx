import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { AdminTableSummary } from '@/features/tables'
import { ApiError } from '@/types/api'

import { AdminTablesPage } from './AdminTablesPage'

const admin = vi.hoisted(() => vi.fn())
const pause = vi.hoisted(() => vi.fn())
const resume = vi.hoisted(() => vi.fn())
const toastError = vi.hoisted(() => vi.fn())

// `sonner` renders outside this tree, so the only way to read what a failure said is to mock it.
vi.mock('sonner', () => ({ toast: { error: toastError, success: vi.fn() } }))

// The API module is mocked, not the hooks: that keeps the query keys, the `?q=` round trip and the
// four states running for real, which is where this screen's behaviour actually lives.
vi.mock('@/features/tables/api/gameTablesApi', () => ({
  gameTablesApi: { admin, delete: vi.fn(), assignMasters: vi.fn(), createUnassigned: vi.fn(), pause, resume },
  tableTypesApi: { list: vi.fn() },
}))

function table(overrides: Partial<AdminTableSummary> = {}): AdminTableSummary {
  return {
    id: 'table-1',
    name: 'La maldición de Strahd',
    status: 'Preparation',
    tableTypeName: null,
    tableTypeCode: null,
    maxPlayers: 5,
    playerCount: 2,
    primaryMasterName: 'ana',
    claimedByName: null,
    createdAt: '2026-03-04T12:00:00',
    ...overrides,
  }
}

function page(content: AdminTableSummary[]) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 }
}

function renderPage(url = '/admin/tables') {
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
  return render(wrap(<AdminTablesPage />))
}

/** The row of a table is a `<tr>` on a wide screen; jsdom renders both layouts, so scope to the table. */
async function rowFor(name: string) {
  const grid = await screen.findByRole('table', { name: 'Mesas' })
  const cell = await within(grid).findByText(name)
  const row = cell.closest('tr')
  if (!row) throw new Error(`no row for ${name}`)
  return within(row)
}

beforeEach(() => {
  admin.mockReset()
  pause.mockReset()
  resume.mockReset()
  toastError.mockReset()
})

describe('AdminTablesPage', () => {
  /**
   * #176: the screen stopped being a queue. It used to default to the review statuses, which made it
   * a second tray with rules of its own — so it opens asking for **everything**, with no `q` of its
   * own and no status narrowing, and the reader does the narrowing.
   */
  it('opens showing every table, with no filter of its own', async () => {
    admin.mockResolvedValue(page([table()]))

    renderPage()

    await waitFor(() => expect(admin).toHaveBeenCalledWith(undefined, undefined, 0))
  })

  /** #185: what was searched and which page are in the URL, not in `useState`. */
  it('restores the search and the page from the URL', async () => {
    admin.mockResolvedValue(page([]))

    renderPage('/admin/tables?q=%2Ftable_status%20Preparation&page=2')

    await waitFor(() => expect(admin).toHaveBeenCalledWith('/table_status Preparation', undefined, 2))
  })

  /**
   * The mudanza of #176, from the side that loses: reviewing is the tray's, so these two buttons are
   * **gone** from here rather than offered in both places under different rules. A `Preparation`
   * table is exactly the row that used to carry them.
   */
  it('no longer offers approving or requesting changes', async () => {
    admin.mockResolvedValue(page([table({ status: 'Preparation' })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Pedir cambios' })).not.toBeInTheDocument()
  })

  /** What it keeps: giving an unassigned table its masters, or admitting it will never have any. */
  it('keeps assigning masters and deleting on a table with no master', async () => {
    admin.mockResolvedValue(page([table({ status: 'Unassigned', primaryMasterName: null })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.getByRole('button', { name: 'Asignar masters' })).toBeInTheDocument()
    expect(row.getByRole('button', { name: 'Eliminar' })).toBeInTheDocument()
  })

  /** And offers neither on a table that already has one: principio 2, absent rather than disabled. */
  it('offers neither on a table that already has a master', async () => {
    admin.mockResolvedValue(page([table({ status: 'Opened' })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.queryByRole('button', { name: 'Asignar masters' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument()
  })

  /** Showing every table means showing what state each one is in, which is the point of the screen. */
  it('shows each table with its status and its master', async () => {
    admin.mockResolvedValue(page([table({ status: 'Opened' })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.getByText('Abierta')).toBeInTheDocument()
    expect(row.getByText('ana')).toBeInTheDocument()
  })

  /**
   * #100: a table somebody is reviewing right now says so, even though this screen offers no action
   * that needs the reservation. Without it an admin sees a `Preparation` table, goes to the tray to
   * review it, and finds it is not there.
   */
  it('says when a table is already being reviewed by somebody', async () => {
    admin.mockResolvedValue(page([table({ claimedByName: 'dami' })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.getByText(/dami/)).toBeInTheDocument()
  })

  /**
   * #136 read the other way round: "there are no tables" is a fact about the platform and "nothing
   * matched" is a fact about what was typed. Telling somebody who just filtered by `Canceled` that
   * the platform has no tables would answer a question they did not ask.
   */
  it('tells an empty platform apart from an empty search', async () => {
    admin.mockResolvedValue(page([]))

    const { unmount } = renderPage()
    expect(await screen.findByText('Todavía no hay mesas')).toBeInTheDocument()
    unmount()

    renderPage('/admin/tables?q=%2Ftable_status%20Canceled')
    expect(await screen.findByText('Ninguna mesa coincide con esa búsqueda')).toBeInTheDocument()
  })

  /** #103: no guard in the router, so the 403 has to land somewhere that explains itself. */
  it('paints the forbidden state when the backend refuses the listing', async () => {
    admin.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Access denied', errorCode: 'FORBIDDEN' }))

    renderPage()

    await waitFor(() => expect(screen.getByText('No tenés permiso')).toBeInTheDocument())
  })

  /**
   * The other half of the mudanza: an admin who learned the old screen comes here for "Aprobar",
   * finds a listing, and has no way to discover the buttons moved unless something says so. The
   * section whose text moved with them is the one this links, and its `listing` line is what answers
   * the question they actually have.
   */
  it('says where reviewing went, from the screen that used to do it', async () => {
    admin.mockResolvedValue(page([table()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Dónde se revisan las mesas' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/Las mesas se revisan desde «Bandeja»/)).toBeInTheDocument()
    expect(dialog.getByText(/«Mesas» es otra cosa/)).toBeInTheDocument()
  })

  /** #240: the search help documents **this** box's commands rather than a fixed list. */
  it('documents its own six search commands', async () => {
    admin.mockResolvedValue(page([table()]))

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Cómo buscar' }))

    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText('/table_status')).toBeInTheDocument()
    expect(dialog.getByText('/table_master')).toBeInTheDocument()
    expect(dialog.getByText('/table_name')).toBeInTheDocument()
    // And nothing from a box this screen is not.
    expect(dialog.queryByText(/discord_name/)).not.toBeInTheDocument()
  })

  /**
   * #163, from the side that had been missing: `pause()` and `resume()` have had an endpoint since
   * E2 and no button on any screen. Each is offered on exactly the status it applies to — pausing a
   * running table, resuming a paused one — because principio 2 shows an action only where it works.
   */
  it('offers pausing on a running table and resuming on a paused one', async () => {
    admin.mockResolvedValue(page([table({ status: 'InProgress' })]))

    const { unmount } = renderPage()
    const running = await rowFor('La maldición de Strahd')
    expect(running.getByRole('button', { name: 'Pausar' })).toBeInTheDocument()
    expect(running.queryByRole('button', { name: 'Reanudar' })).not.toBeInTheDocument()
    unmount()

    admin.mockResolvedValue(page([table({ status: 'Pause' })]))
    renderPage()
    const paused = await rowFor('La maldición de Strahd')
    expect(paused.getByRole('button', { name: 'Reanudar' })).toBeInTheDocument()
    expect(paused.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument()
  })

  /** And neither on a table that is not in either state — an `Opened` one has nothing to pause yet. */
  it('offers neither on a table that is not running or paused', async () => {
    admin.mockResolvedValue(page([table({ status: 'Opened' })]))

    renderPage()
    const row = await rowFor('La maldición de Strahd')

    expect(row.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Reanudar' })).not.toBeInTheDocument()
  })

  /** #32: `Pause` demands a justification, and it is the master who will read it. */
  it('sends the pause with the reason that was written', async () => {
    admin.mockResolvedValue(page([table({ status: 'InProgress' })]))
    pause.mockResolvedValue(table({ status: 'Pause' }))
    const user = userEvent.setup()

    renderPage()
    const row = await rowFor('La maldición de Strahd')
    await user.click(row.getByRole('button', { name: 'Pausar' }))

    const dialog = within(await screen.findByRole('dialog'))
    await user.type(dialog.getByRole('textbox'), 'El master se fue de viaje')
    await user.click(dialog.getByRole('button', { name: 'Pausar' }))

    await waitFor(() => expect(pause).toHaveBeenCalledWith('table-1', { justification: 'El master se fue de viaje' }))
  })

  /**
   * #193 and #197, which is the whole reason this refusal was worth building a message for.
   *
   * Resuming re-checks the master's agenda, because they may have taken another table while this one
   * was frozen. The backend answers naming that table in `errorParams`, and the screen has to say
   * **which** one: "no pudimos completar la acción" would throw away the single fact that makes the
   * problem solvable.
   */
  it('names the table the agenda clashes with when resuming is refused', async () => {
    admin.mockResolvedValue(page([table({ status: 'Pause' })]))
    resume.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: 'Cannot resume: agenda overlaps table Curse of Strahd',
        errorCode: 'SCHEDULE_CONFLICT',
        errorParams: { otherTableName: 'Las minas de Phandelver' },
      }),
    )
    const user = userEvent.setup()

    renderPage()
    const row = await rowFor('La maldición de Strahd')
    await user.click(row.getByRole('button', { name: 'Reanudar' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1))
    expect(toastError).toHaveBeenCalledWith(expect.stringContaining('Las minas de Phandelver'))
    // And never the backend's own English, which is written for a log (#197).
    expect(toastError).not.toHaveBeenCalledWith(expect.stringContaining('Cannot resume'))
  })

  /**
   * And the same refusal without its parameter falls back to the generic sentence rather than
   * rendering «se pisa con «»», which would say less than saying nothing specific at all.
   */
  it('falls back to the generic refusal when the clash carries no table name', async () => {
    admin.mockResolvedValue(page([table({ status: 'Pause' })]))
    resume.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'Cannot resume', errorCode: 'SCHEDULE_CONFLICT' }))
    const user = userEvent.setup()

    renderPage()
    const row = await rowFor('La maldición de Strahd')
    await user.click(row.getByRole('button', { name: 'Reanudar' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('No pudimos completar la acción. Probá de nuevo.'))
  })
})
