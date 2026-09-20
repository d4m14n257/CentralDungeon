import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { BlockPlayerDialog } from './BlockPlayerDialog'

const block = vi.hoisted(() => vi.fn())
const unblock = vi.hoisted(() => vi.fn())
const requestBlock = vi.hoisted(() => vi.fn())

// The API module is mocked and not the hooks: the query keys, the invalidation and the form all run
// for real, which is where the behaviour under test lives.
vi.mock('../api/registrationsApi', () => ({
  registrationsApi: { block, unblock, requestBlock },
}))

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

function renderDialog(props: Partial<Parameters<typeof BlockPlayerDialog>[0]> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    // `FormDialog` asks before discarding a half-written reason, so it needs the provider.
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(
    wrap(
      <BlockPlayerDialog
        tableId="table-1"
        registrationId="reg-9"
        playerName="Diego"
        action="block"
        isPrimary
        open
        onOpenChange={() => {}}
        {...props}
      />,
    ),
  )
}

beforeEach(() => {
  block.mockReset()
  unblock.mockReset()
  requestBlock.mockReset()
})

describe('BlockPlayerDialog', () => {
  /** #39: the `Primary` vetoes, and the veto is per table — the table travels with the call (#29). */
  it('vetoes when the reader runs the table', async () => {
    block.mockResolvedValue({})
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar hace un mes')
    await user.click(screen.getByRole('button', { name: 'Vetar' }))

    await waitFor(() => expect(block).toHaveBeenCalledWith('table-1', 'reg-9', 'Falta sin avisar hace un mes'))
    expect(requestBlock).not.toHaveBeenCalled()
  })

  /**
   * And a co-master's identical press sends a **request** instead — two endpoints, not one that
   * behaves two ways (#39, #71).
   */
  it('sends a request when the reader co-runs the table', async () => {
    requestBlock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderDialog({ isPrimary: false })

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar hace un mes')
    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))

    await waitFor(() => expect(requestBlock).toHaveBeenCalledWith('table-1', 'reg-9', 'Falta sin avisar hace un mes'))
    expect(block).not.toHaveBeenCalled()
  })

  /** `fase-3-admin-owner.md:169` again, inside the dialog: what this sends is said, not implied. */
  it('says to a co-master that nothing changes until the master answers', () => {
    renderDialog({ isPrimary: false })

    expect(screen.getByText(/Todavía no cambia nada/)).toBeInTheDocument()
    expect(screen.getByText(/lo que mandás es un pedido/)).toBeInTheDocument()
  })

  /** What the veto actually costs the other person, written where the decision is taken. */
  it('says what a veto takes away, on the veto itself', () => {
    renderDialog()

    expect(screen.getByText(/no puede descargar sus archivos/)).toBeInTheDocument()
    expect(screen.getByText(/No pierde la cuenta/)).toBeInTheDocument()
  })

  /** The half that makes the other half acceptable (#39), and it asks for a reason too. */
  it('lifts the veto with its own reason', async () => {
    unblock.mockResolvedValue({})
    const user = userEvent.setup()
    renderDialog({ action: 'unblock' })

    await user.type(screen.getByRole('textbox'), 'Hablamos y lo arreglamos')
    await user.click(screen.getByRole('button', { name: 'Levantar el veto' }))

    await waitFor(() => expect(unblock).toHaveBeenCalledWith('table-1', 'reg-9', 'Hablamos y lo arreglamos'))
  })

  /**
   * **A veto with no reason is not reversible in practice**, which is why the reason is required on
   * this side as well as on the server: a round trip is a bad way to learn a textarea was blank.
   */
  it('refuses to send anything without a reason', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('button', { name: 'Vetar' }))

    await waitFor(() => expect(block).not.toHaveBeenCalled())
  })

  /** Whitespace is not a reason — the same thing `@NotBlank` says on the other side. */
  it('does not accept whitespace as a reason', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), '   ')
    await user.click(screen.getByRole('button', { name: 'Vetar' }))

    await waitFor(() => expect(block).not.toHaveBeenCalled())
  })

  /**
   * The race a screen cannot prevent: a colleague vetoed the same person a moment ago. The answer is
   * inline, because the form is still open and that is where the reader is looking (#197) — and it
   * is written from the code, never from the backend's English `detail`.
   */
  it('shows the refusal inline, in the reader’s language', async () => {
    block.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: 'Registration is already blocked',
        errorCode: 'REGISTRATION_ALREADY_BLOCKED',
      }),
    )
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar')
    await user.click(screen.getByRole('button', { name: 'Vetar' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Esa persona ya está vetada en esta mesa. Actualizá la pantalla para ver cómo quedó.')
    expect(alert).not.toHaveTextContent('already blocked')
  })

  /** A co-master who reaches the Primary-only act is told what they *can* do, not just refused. */
  it('tells a co-master what to do instead when the platform refuses them', async () => {
    requestBlock.mockRejectedValue(
      new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Not the primary master', errorCode: 'NOT_PRIMARY_MASTER' }),
    )
    const user = userEvent.setup()
    renderDialog({ isPrimary: false })

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar')
    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/lo que podés hacer es pedirlo/)
  })
})
