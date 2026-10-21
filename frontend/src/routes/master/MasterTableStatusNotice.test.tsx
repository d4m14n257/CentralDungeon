import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { GameTableStatus } from '@/features/tables'
import { MasterTableStatusNotice } from './MasterTableStatusNotice'

const requestPause = vi.fn()

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router')
  return { ...actual, useNavigate: () => vi.fn() }
})

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

/**
 * The lifecycle mutations are stubbed; the real `JustifiedTableActionDialog` is not.
 *
 * What is being tested is the screen's own decision — which act is offered in which status, and what
 * the justification travels as — and the dialog is where that justification is written.
 */
vi.mock('@/features/tables', async () => {
  const actual = await vi.importActual<typeof import('@/features/tables')>('@/features/tables')
  const idle = () => ({ mutate: vi.fn(), isPending: false })
  return {
    JustifiedTableActionDialog: actual.JustifiedTableActionDialog,
    // The real one: it is a pure mapping from an error to a key, and stubbing it would test the stub.
    pauseRequestErrorKey: actual.pauseRequestErrorKey,
    useCancelTable: idle,
    useDeleteTable: idle,
    useFinishTable: idle,
    useResubmitTable: idle,
    useStartTable: idle,
    useSubmitTableForReview: idle,
    useRequestTablePause: () => ({ mutate: requestPause, isPending: false }),
  }
})

vi.mock('@/hooks/useConfirm', () => ({ useConfirm: () => vi.fn().mockResolvedValue(true) }))

function renderNotice(status: GameTableStatus, isPrimary = true) {
  render(
    <MemoryRouter>
      <MasterTableStatusNotice tableId="table-1" status={status} isPrimary={isPrimary} />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('MasterTableStatusNotice · requesting a pause', () => {
  /** ADR-07 §2: there is nothing to pause before the table is running, so the act is absent (principle 2). */
  it('offers the pause request only on a table that is running', () => {
    const { unmount } = render(<span />)
    unmount()

    renderNotice('InProgress')
    expect(screen.getByRole('button', { name: 'Pedir pausa' })).toBeInTheDocument()
  })

  it('offers nothing to ask on a table that has not started', () => {
    renderNotice('Opened')

    expect(screen.queryByRole('button', { name: 'Pedir pausa' })).not.toBeInTheDocument()
  })

  /**
   * **The button is gone and a sentence replaces it** once the request is in.
   *
   * The sentence matters as much as the absence: `PauseRequested` reads like a pause, and a master
   * who takes it for one stops turning up while the calendar is still promising dates to their
   * players. Only an admin's answer freezes the agenda.
   */
  it('replaces the button with the wait once a pause has been asked for', () => {
    renderNotice('PauseRequested')

    expect(screen.queryByRole('button', { name: 'Pedir pausa' })).not.toBeInTheDocument()
    expect(screen.getByText(/todavía no te respondieron/)).toBeInTheDocument()
    expect(screen.getByText(/la mesa sigue en curso/)).toBeInTheDocument()
  })

  /**
   * Asking for a pause is taken from **any** master of the table, unlike everything in
   * the other transitions: a co-master running the sessions is exactly the person who knows it has to stop.
   */
  it('lets a co-master ask, while the Primary-only transitions stay hidden from them', () => {
    renderNotice('InProgress', false)

    expect(screen.getByRole('button', { name: 'Pedir pausa' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finalizar mesa' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancelar mesa' })).not.toBeInTheDocument()
  })

  /** ADR-07 §2 asks for a reason on a pause, and it is what the admin reads to decide. */
  it('sends the reason that was written', async () => {
    const user = userEvent.setup()
    renderNotice('InProgress')

    await user.click(screen.getByRole('button', { name: 'Pedir pausa' }))
    const dialog = within(await screen.findByRole('dialog'))
    await user.type(dialog.getByRole('textbox'), 'Me operan la semana que viene')
    await user.click(dialog.getByRole('button', { name: 'Pedir pausa' }))

    await waitFor(() => expect(requestPause).toHaveBeenCalledWith({ justification: 'Me operan la semana que viene' }, expect.anything()))
  })

  /**
   * **A table waiting on an answer is still a table its master may end.**
   *
   * `PauseRequested` joined the cancelable statuses on both sides when the status got a producer:
   * without it, a master who asked for a pause and then decided to close the table instead would
   * find the button gone, with no way out except waiting for an admin to answer a request that no
   * longer matters.
   */
  it('still offers cancelling while a pause is waiting on an answer', () => {
    renderNotice('PauseRequested')

    expect(screen.getByRole('button', { name: 'Cancelar mesa' })).toBeInTheDocument()
  })

  /**
   * ADR-21 §10: the notice says where the table stands in every status, and cancelling sits apart at the
   * right when there are other decisions to keep it away from.
   */
  it('names the situation and keeps cancelling apart from the other decisions', () => {
    renderNotice('InProgress')

    expect(screen.getByText('Se está jugando')).toBeInTheDocument()
    const cancel = screen.getByRole('button', { name: 'Cancelar mesa' })
    expect(cancel.parentElement).toHaveClass('ml-auto')
    expect(screen.getByRole('button', { name: 'Finalizar mesa' }).parentElement).not.toHaveClass('ml-auto')
  })

  /** A status with nothing to decide still has its notice, so the reader learns where to look. */
  it('still says where a finished table stands, with no buttons', () => {
    renderNotice('Finished')

    expect(screen.getByText('Se jugó hasta el final')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  /** An empty reason never leaves the screen: the schema refuses it before the round trip does. */
  it('refuses to send a pause request with no reason', async () => {
    const user = userEvent.setup()
    renderNotice('InProgress')

    await user.click(screen.getByRole('button', { name: 'Pedir pausa' }))
    const dialog = within(await screen.findByRole('dialog'))
    await user.click(dialog.getByRole('button', { name: 'Pedir pausa' }))

    await waitFor(() => expect(requestPause).not.toHaveBeenCalled())
  })
})
