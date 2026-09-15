import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { BlockUserDialog } from './BlockUserDialog'
import type { AdminUserSummary } from '../types'

const block = vi.hoisted(() => vi.fn())
const unblock = vi.hoisted(() => vi.fn())

vi.mock('../api/adminUsersApi', () => ({ adminUsersApi: { block, unblock } }))

const ALLOWED: AdminUserSummary = {
  id: 'user-1',
  discordUsername: 'dami',
  name: 'Damián',
  country: 'AR',
  status: 'Allowed',
  roles: ['Player'],
  createdAt: '2026-01-01T12:00:00',
}

const BLOCKED: AdminUserSummary = { ...ALLOWED, status: 'Blocked' }

function renderDialog(user: AdminUserSummary) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    // The confirm provider is real and not a stub: FormDialog asks it before discarding a dirty
    // form (#231), so a dialog rendered without it throws rather than rendering.
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<BlockUserDialog user={user} open onOpenChange={vi.fn()} />))
}

beforeEach(() => {
  block.mockReset()
  unblock.mockReset()
})

describe('BlockUserDialog', () => {
  /**
   * #84, and the reason this is a dialog and not a confirm: "block" is a word people read as
   * "delete", so the two consequences are on screen before the button is pressed.
   */
  it('says what a block means before it is pressed: no access, and nothing removed', () => {
    renderDialog(ALLOWED)

    expect(screen.getByText('La persona no va a poder volver a entrar a la plataforma.')).toBeInTheDocument()
    expect(screen.getByText(/Sus datos se conservan/)).toBeInTheDocument()
  })

  /** Until F6 brings audit_logs, this row is the only record of why an account was closed. */
  it('refuses to send without a reason', async () => {
    renderDialog(ALLOWED)

    await userEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await waitFor(() => expect(screen.getByText('Esto es obligatorio')).toBeInTheDocument())
    expect(block).not.toHaveBeenCalled()
  })

  it('sends the reason with the block', async () => {
    block.mockResolvedValue({ ...ALLOWED, status: 'Blocked', updatedAt: '2026-02-01T12:00:00' })
    renderDialog(ALLOWED)

    await userEvent.type(screen.getByLabelText('Motivo'), 'Acoso reiterado en tres mesas')
    await userEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await waitFor(() => expect(block).toHaveBeenCalledWith('user-1', { justification: 'Acoso reiterado en tres mesas' }))
  })

  /** Which of the two acts it is comes from the account's own status - there is no third choice. */
  it('turns into an unblock for an account that is already blocked', async () => {
    unblock.mockResolvedValue({ ...BLOCKED, status: 'Allowed', updatedAt: '2026-02-01T12:00:00' })
    renderDialog(BLOCKED)

    // The block's two consequences are not repeated: reopening an account has one, and everybody
    // already knows what it is.
    expect(screen.queryByText(/Sus datos se conservan/)).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Motivo'), 'Se resolvió el reporte')
    await userEvent.click(screen.getByRole('button', { name: 'Desbloquear' }))

    await waitFor(() => expect(unblock).toHaveBeenCalledWith('user-1', { justification: 'Se resolvió el reporte' }))
    expect(block).not.toHaveBeenCalled()
  })

  /**
   * #197: the sentence comes from `errorCode`, never from `detail` - which is English and written
   * for a log. The screen does not offer this button for a privileged account, so the code only
   * arrives when somebody reached the API another way; it still has to read as an explanation.
   */
  it('explains a refusal from its code and not from the backend`s English detail', async () => {
    block.mockRejectedValue(
      new ApiError(403, {
        title: 'Forbidden',
        status: 403,
        detail: 'Cannot block a user holding ADMIN or OWNER',
        errorCode: 'CANNOT_BLOCK_PRIVILEGED',
      }),
    )
    renderDialog(ALLOWED)

    await userEvent.type(screen.getByLabelText('Motivo'), 'Da igual')
    await userEvent.click(screen.getByRole('button', { name: 'Bloquear' }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('No se puede bloquear a una cuenta con rol de Admin o de Owner.'),
    )
    expect(screen.queryByText(/Cannot block a user holding/)).not.toBeInTheDocument()
  })

  /** A code nobody committed to gets one honest generic sentence, not an invented specific one. */
  it('falls back to a generic sentence for a code it does not know', async () => {
    unblock.mockRejectedValue(new ApiError(500, { title: 'Error', status: 500, detail: 'Boom', errorCode: 'INTERNAL_ERROR' }))
    renderDialog(BLOCKED)

    await userEvent.type(screen.getByLabelText('Motivo'), 'Se resolvió el reporte')
    await userEvent.click(screen.getByRole('button', { name: 'Desbloquear' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('No pudimos aplicar el cambio. Probá de nuevo.'))
  })
})
