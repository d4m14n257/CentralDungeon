import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'

import { SubmitRequestSection } from './SubmitRequestSection'
import type { ApprovalRequestSummary } from '../types'

const mine = vi.hoisted(() => vi.fn())
const submit = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hooks: that keeps the query keys, the cache invalidation and the
// rule about what is offered running for real, which is where this component's behaviour lives.
vi.mock('../api/approvalsApi', () => ({ approvalsApi: { mine, submit } }))

function request(overrides: Partial<ApprovalRequestSummary> = {}): ApprovalRequestSummary {
  return {
    id: 'req-1',
    type: 'MasterGrant',
    status: 'Pending',
    requestedByName: 'dami',
    justification: 'Quiero dirigir',
    claimedByName: null,
    createdAt: '2026-03-04T12:00:00',
    ...overrides,
  }
}

function page(content: ApprovalRequestSummary[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }
}

function renderSection(type: 'MasterGrant' | 'TableOpen' | 'General' = 'MasterGrant') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<SubmitRequestSection type={type} />))
}

beforeEach(() => {
  mine.mockReset()
  submit.mockReset()
})

/**
 * The whole of principio 2 de `frontend-diseno.md` §1, in one component: a second request of the
 * same kind is refused with `409 REQUEST_ALREADY_PENDING`, so a button offered while one is waiting
 * is a button whose only possible outcome is a refusal.
 */
describe('SubmitRequestSection', () => {
  it('offers the button when nothing of that kind is waiting', async () => {
    mine.mockResolvedValue(page([]))

    renderSection()

    expect(await screen.findByRole('button', { name: 'Pedir el rol de master' })).toBeInTheDocument()
  })

  /**
   * **The server does the filtering, not a `find` over page one.** The listing carries resolved
   * requests too, so an old pending one can sit under twenty answered ones: reading a page of the
   * unfiltered list would conclude there was none and offer the button straight into the `409`.
   * With the filter the whole answer is at most three rows, one per kind.
   *
   * The query is asserted verbatim because it leaves the frontend and is parsed by
   * `SearchQueryParser.java`: it has to be the canonical string, with the value that travels and not
   * the label that is read.
   */
  it('asks the server for the pending ones, in the canonical spelling', async () => {
    mine.mockResolvedValue(page([]))

    renderSection()

    await waitFor(() => expect(mine).toHaveBeenCalledWith('/status Pending', 0))
  })

  /** Not a disabled button, not an error: the fact, and the date it happened on. */
  it('shows the pending request and its date instead of the button', async () => {
    mine.mockResolvedValue(page([request()]))

    renderSection()

    await waitFor(() => expect(screen.getByText(/Ya pediste el rol de master/)).toBeInTheDocument())
    expect(screen.getByText(/4 mar 2026/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pedir el rol de master' })).not.toBeInTheDocument()
  })

  /**
   * One kind at a time, and only that kind: somebody waiting on a master role can still ask for a
   * table. The rule is per kind and per person, and a section that read it as "any pending request"
   * would silence two screens out of three.
   */
  it('still offers a different kind while another one waits', async () => {
    mine.mockResolvedValue(page([request({ type: 'MasterGrant' })]))

    renderSection('TableOpen')

    expect(await screen.findByRole('button', { name: 'Pedir que se abra una mesa' })).toBeInTheDocument()
  })

  /**
   * A request that was already answered is not a reason to stop offering the next one — and the
   * screen checks the status itself rather than trusting the filter blindly. If the query ever
   * broadened, the worst outcome would be an extra request, never a button that quietly disappears
   * on somebody who is free to ask.
   */
  it('offers the button again once the pending one was resolved', async () => {
    mine.mockResolvedValue(page([request({ status: 'Rejected' })]))

    renderSection()

    expect(await screen.findByRole('button', { name: 'Pedir el rol de master' })).toBeInTheDocument()
  })

  /** Painting the button and taking it away a moment later is worse than a beat of silence. */
  it('draws nothing while it does not yet know', () => {
    mine.mockReturnValue(new Promise(() => {}))

    const { container } = renderSection()

    expect(container).toBeEmptyDOMElement()
  })

  /**
   * The reason is what an admin decides on, and the form refuses an empty one on this side too —
   * without a round trip, and without whitespace passing for an explanation.
   */
  it('refuses to send a request with no reason', async () => {
    mine.mockResolvedValue(page([]))

    renderSection()
    await userEvent.click(await screen.findByRole('button', { name: 'Pedir el rol de master' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Enviar el pedido' }))

    await waitFor(() => expect(submit).not.toHaveBeenCalled())
  })

  it('sends the kind of the screen it was raised from, with the reason', async () => {
    mine.mockResolvedValue(page([]))
    submit.mockResolvedValue(request())

    renderSection('TableOpen')
    await userEvent.click(await screen.findByRole('button', { name: 'Pedir que se abra una mesa' }))
    await userEvent.type(await screen.findByRole('textbox'), 'Quiero una mesa de terror los jueves')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar el pedido' }))

    await waitFor(() => expect(submit).toHaveBeenCalledWith({ type: 'TableOpen', justification: 'Quiero una mesa de terror los jueves' }))
  })
})
