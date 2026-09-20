import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'

import { BanRequestsSection } from './BanRequestsSection'
import type { BanRequest } from '../types'

const list = vi.hoisted(() => vi.fn())
const approve = vi.hoisted(() => vi.fn())
const reject = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hooks: the query keys, the invalidation and the empty state all
// run for real.
vi.mock('../api/approvalsApi', () => ({
  approvalsApi: {},
  banRequestsApi: { list, approve, reject },
}))

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

function request(overrides: Partial<BanRequest> = {}): BanRequest {
  return {
    requestId: 'req-1',
    registrationId: 'reg-9',
    targetUserId: 'user-9',
    targetUserName: 'Diego',
    requestedByName: 'Beto',
    justification: 'Falta sin avisar desde diciembre',
    createdAt: '2026-03-04T12:00:00',
    ...overrides,
  }
}

function renderSection(isPrimary = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<BanRequestsSection tableId="table-1" isPrimary={isPrimary} />))
}

beforeEach(() => {
  list.mockReset()
  approve.mockReset()
  reject.mockReset()
})

describe('BanRequestsSection', () => {
  /**
   * **The `Primary` resolves it, not an admin** (#39 over #90): a veto between a co-master and a
   * player of *that* table is decided by whoever runs it. So the answer is here, on the table's own
   * screen, and never in the shared tray.
   */
  it('shows a pending request with its reason and who asked, and lets the Primary answer it', async () => {
    list.mockResolvedValue([request()])

    renderSection()

    expect(await screen.findByText(/Beto/)).toBeInTheDocument()
    expect(screen.getByText('Falta sin avisar desde diciembre')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Vetar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'No vetar' })).toBeInTheDocument()
  })

  /**
   * **The row names the person it is about**, which is the reason `BanRequestResponse` exists
   * instead of the shared approval summary: that one's entity is whoever asked, so two open requests
   * on one table were told apart only by the wording of their reasons — no way to decide about
   * somebody.
   */
  it('names who would be vetoed, so two open requests are told apart', async () => {
    list.mockResolvedValue([
      request(),
      request({ requestId: 'req-2', registrationId: 'reg-10', targetUserId: 'user-10', targetUserName: 'Elena' }),
    ])

    renderSection()

    expect(await screen.findByText('Vetar a Diego')).toBeInTheDocument()
    expect(screen.getByText('Vetar a Elena')).toBeInTheDocument()
  })

  /**
   * A co-master reads the same list and answers none of it. Seeing it is what stops them asking
   * twice; the buttons are absent rather than disabled, because they could not be used (principio 2).
   */
  it('lets a co-master see the wait but not resolve it', async () => {
    list.mockResolvedValue([request()])

    renderSection(false)

    expect(await screen.findByText(/esperando la respuesta del master/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Vetar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'No vetar' })).not.toBeInTheDocument()
  })

  /** Nothing waiting is not an empty state worth painting: the roster below is the everyday work. */
  it('renders nothing at all when nothing is waiting', async () => {
    list.mockResolvedValue([])

    const { container } = renderSection()

    await waitFor(() => expect(list).toHaveBeenCalled())
    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  /** #42: the note is required at both ends, and here it is what the co-master receives. */
  it('grants the request with the note that was written', async () => {
    list.mockResolvedValue([request()])
    approve.mockResolvedValue(undefined)
    const user = userEvent.setup()

    renderSection()
    await user.click(await screen.findByRole('button', { name: 'Vetar' }))

    const dialog = within(await screen.findByRole('dialog'))
    // Who it is about, and what was asked, on screen while the answer is written — not one dialog back.
    expect(dialog.getByText('Falta sin avisar desde diciembre')).toBeInTheDocument()
    expect(dialog.getByRole('heading', { name: 'Vetar a Diego' })).toBeInTheDocument()
    await user.type(dialog.getByRole('textbox'), 'De acuerdo, hace meses que no aparece')
    await user.click(dialog.getByRole('button', { name: 'Vetar' }))

    await waitFor(() =>
      expect(approve).toHaveBeenCalledWith('table-1', 'req-1', { resolutionNote: 'De acuerdo, hace meses que no aparece' }),
    )
  })

  /** Granting it is not marking it as read: the dialog says the veto lands at once. */
  it('says that granting the request applies the veto there and then', async () => {
    list.mockResolvedValue([request()])
    const user = userEvent.setup()

    renderSection()
    await user.click(await screen.findByRole('button', { name: 'Vetar' }))

    // And it names them while saying so: "queda vetado en el acto" about nobody in particular is
    // the sentence that lets a Primary grant the wrong one of two open requests.
    expect(within(await screen.findByRole('dialog')).getByText(/Diego queda vetado en el acto/)).toBeInTheDocument()
  })

  /** Turning it down changes nothing about the table, and still owes the co-master an explanation. */
  it('turns the request down with its own note', async () => {
    list.mockResolvedValue([request()])
    reject.mockResolvedValue({})
    const user = userEvent.setup()

    renderSection()
    await user.click(await screen.findByRole('button', { name: 'No vetar' }))

    const dialog = within(await screen.findByRole('dialog'))
    await user.type(dialog.getByRole('textbox'), 'Hablé con él, lo dejamos pasar')
    await user.click(dialog.getByRole('button', { name: 'No vetar' }))

    await waitFor(() => expect(reject).toHaveBeenCalledWith('table-1', 'req-1', { resolutionNote: 'Hablé con él, lo dejamos pasar' }))
    expect(approve).not.toHaveBeenCalled()
  })
})
