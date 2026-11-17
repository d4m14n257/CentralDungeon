import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { BlockPlayerDialog } from './BlockPlayerDialog'
import { acceptReview } from '@/test/review'

const unblock = vi.hoisted(() => vi.fn())
const requestBlock = vi.hoisted(() => vi.fn())

// The API module is mocked and not the hooks: the query keys, the invalidation and the form all run
// for real, which is where the behaviour under test lives.
vi.mock('../api/registrationsApi', () => ({
  registrationsApi: { unblock, requestBlock },
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
        action="request"
        open
        onOpenChange={() => {}}
        {...props}
      />,
    ),
  )
}

beforeEach(() => {
  unblock.mockReset()
  requestBlock.mockReset()
})

describe('BlockPlayerDialog · asking for a veto', () => {
  /** Nobody vetoes from here: the reason travels to the admins as a request, with the table in the path. */
  it('sends a request, never a veto, with the reason that was written', async () => {
    requestBlock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar hace un mes')
    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))
    await acceptReview()

    await waitFor(() => expect(requestBlock).toHaveBeenCalledWith('table-1', 'reg-9', 'Falta sin avisar hace un mes'))
    expect(unblock).not.toHaveBeenCalled()
  })

  /** What this sends is said where the decision is made: nothing changes until an admin answers. */
  it('says that nothing changes until an admin answers', () => {
    renderDialog()

    expect(screen.getByText(/Todavía no cambia nada/)).toBeInTheDocument()
    expect(screen.getByText(/hasta que un admin responda/)).toBeInTheDocument()
  })

  /** A reason is what the admin decides on: a round trip is a bad way to learn a textarea was blank. */
  it('refuses to send anything without a reason', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))

    await waitFor(() => expect(requestBlock).not.toHaveBeenCalled())
  })

  /** Whitespace is not a reason — the same thing `@NotBlank` says on the other side. */
  it('does not accept whitespace as a reason', async () => {
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), '   ')
    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))

    await waitFor(() => expect(requestBlock).not.toHaveBeenCalled())
  })

  /**
   * The race a screen cannot prevent: a colleague asked for the same person a moment ago. The answer is
   * inline, because the form is still open, and it is written from the code, never from the backend's
   * English `detail`.
   */
  it('shows the refusal inline, in the reader’s language', async () => {
    requestBlock.mockRejectedValue(
      new ApiError(409, {
        title: 'Conflict',
        status: 409,
        detail: 'A request is already pending',
        errorCode: 'REQUEST_ALREADY_PENDING',
      }),
    )
    const user = userEvent.setup()
    renderDialog()

    await user.type(screen.getByRole('textbox'), 'Falta sin avisar')
    await user.click(screen.getByRole('button', { name: 'Enviar el pedido' }))
    await acceptReview()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Ya hay un pedido de veto esperando respuesta para esa persona.')
    expect(alert).not.toHaveTextContent('already pending')
  })
})

describe('BlockPlayerDialog · lifting a veto', () => {
  /** The half that makes a veto acceptable, and it asks for a reason too. */
  it('lifts the veto with its own reason', async () => {
    unblock.mockResolvedValue({})
    const user = userEvent.setup()
    renderDialog({ action: 'unblock' })

    await user.type(screen.getByRole('textbox'), 'Hablamos y lo arreglamos')
    await user.click(screen.getByRole('button', { name: 'Levantar el veto' }))
    await acceptReview()

    await waitFor(() => expect(unblock).toHaveBeenCalledWith('table-1', 'reg-9', 'Hablamos y lo arreglamos'))
    expect(requestBlock).not.toHaveBeenCalled()
  })

  /** A full table is not an error: it leaves the person rejected for lack of seats, and the dialog says so. */
  it('warns that a full table leaves the person rejected for lack of seats', () => {
    renderDialog({ action: 'unblock' })

    expect(screen.getByText(/rechazada por falta de cupo/)).toBeInTheDocument()
  })

  /** A co-master who reaches the lifting is told who decides, not just refused. */
  it('tells a co-master who lifts a veto when the platform refuses them', async () => {
    unblock.mockRejectedValue(
      new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Not the primary master', errorCode: 'NOT_PRIMARY_MASTER' }),
    )
    const user = userEvent.setup()
    renderDialog({ action: 'unblock' })

    await user.type(screen.getByRole('textbox'), 'Hablamos')
    await user.click(screen.getByRole('button', { name: 'Levantar el veto' }))
    await acceptReview()

    expect(await screen.findByRole('alert')).toHaveTextContent('Levantar el veto lo decide el master de la mesa o un admin.')
  })
})
