import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { RoleChangeDialog } from './RoleChangeDialog'
import { ADMIN_GRANTABLE_ROLES, PLATFORM_ROLES } from '../roles'
import type { AdminUserSummary, PlatformRole } from '../types'

const grantRole = vi.hoisted(() => vi.fn())
const revokeRole = vi.hoisted(() => vi.fn())

vi.mock('../api/adminUsersApi', () => ({ adminUsersApi: { grantRole, revokeRole } }))

const PLAYER: AdminUserSummary = {
  id: 'user-1',
  discordUsername: 'dami',
  name: 'Damián',
  country: 'AR',
  status: 'Allowed',
  roles: ['Player'],
  createdAt: '2026-01-01T12:00:00',
}

function renderDialog(grantableRoles: readonly PlatformRole[], user: AdminUserSummary = PLAYER) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  function wrap(children: ReactNode) {
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<RoleChangeDialog user={user} grantableRoles={grantableRoles} open onOpenChange={vi.fn()} />))
}

beforeEach(() => {
  grantRole.mockReset()
  revokeRole.mockReset()
})

describe('RoleChangeDialog', () => {
  /**
   * Principio 2 de frontend-diseno.md §1, taken literally: **absent, not disabled**. An admin does
   * not find a greyed-out "make this person an admin" and does not find one that fails when pressed.
   */
  it('an admin finds no way at all to hand out Admin or Owner', () => {
    renderDialog(ADMIN_GRANTABLE_ROLES)

    expect(screen.getByRole('button', { name: /Jugador/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Master/ })).toBeInTheDocument()
    // Not `toBeDisabled`: a disabled button that does not say why is worse than no button.
    expect(screen.queryByRole('button', { name: /Admin/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Owner/ })).not.toBeInTheDocument()
  })

  it('the owner sees the four', () => {
    renderDialog(PLATFORM_ROLES)

    for (const label of ['Jugador', 'Master', 'Admin', 'Owner']) {
      expect(screen.getByRole('button', { name: new RegExp(label) })).toBeInTheDocument()
    }
  })

  /** The direction is read off the account, never chosen twice: what they lack is given. */
  it('grants a role the account does not have, with its reason', async () => {
    grantRole.mockResolvedValue({ ...PLAYER, roles: ['Player', 'Master'], updatedAt: '2026-02-01T12:00:00' })
    renderDialog(ADMIN_GRANTABLE_ROLES)

    await userEvent.click(screen.getByRole('button', { name: /Master/ }))
    expect(screen.getByText('Se le va a dar el rol Master a dami.')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Motivo'), 'Va a dirigir la campaña de los martes')
    await userEvent.click(screen.getByRole('button', { name: 'Dar el rol' }))

    await waitFor(() =>
      expect(grantRole).toHaveBeenCalledWith('user-1', { role: 'Master', justification: 'Va a dirigir la campaña de los martes' }),
    )
    expect(revokeRole).not.toHaveBeenCalled()
  })

  /** And what they hold is taken away — the same press, the other endpoint. */
  it('revokes a role the account already holds', async () => {
    revokeRole.mockResolvedValue({ ...PLAYER, roles: [], updatedAt: '2026-02-01T12:00:00' })
    renderDialog(ADMIN_GRANTABLE_ROLES)

    await userEvent.click(screen.getByRole('button', { name: /Jugador/ }))
    expect(screen.getByText('Se le va a quitar el rol Jugador a dami.')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Motivo'), 'Lo pidió la persona')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar el rol' }))

    await waitFor(() => expect(revokeRole).toHaveBeenCalledWith('user-1', { role: 'Player', justification: 'Lo pidió la persona' }))
    expect(grantRole).not.toHaveBeenCalled()
  })

  /** Every audit row carries a reason (#169): the server enforces it, and so does the form. */
  it('refuses to send without a role or without a reason', async () => {
    renderDialog(ADMIN_GRANTABLE_ROLES)

    await userEvent.click(screen.getByRole('button', { name: 'Dar el rol' }))
    await waitFor(() => expect(screen.getAllByText('Esto es obligatorio').length).toBeGreaterThan(0))
    expect(grantRole).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: /Master/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Dar el rol' }))
    await waitFor(() => expect(screen.getByText('Esto es obligatorio')).toBeInTheDocument())
    expect(grantRole).not.toHaveBeenCalled()
  })

  /**
   * The invariante global of the phase (#3 of docs/fase-3-admin-owner.md §3), read back in the
   * reader's own language from the code and not from the backend's English `detail` (#197).
   */
  it.each([
    ['LAST_OWNER', 'La plataforma no puede quedarse sin ningún Owner. Dale el rol a otra persona antes de quitar este.'],
    ['CANNOT_REVOKE_OWN_OWNER', 'No podés quitarte tu propio rol de Owner. Tiene que hacerlo otro Owner.'],
    ['ROLE_GRANT_FORBIDDEN', 'Solo un Owner puede dar o quitar los roles de Admin y de Owner.'],
  ])('explains %s from its code', async (errorCode, sentence) => {
    revokeRole.mockRejectedValue(new ApiError(409, { title: 'Conflict', status: 409, detail: 'English, for the log', errorCode }))
    renderDialog(PLATFORM_ROLES, { ...PLAYER, roles: ['Player', 'Owner'] })

    await userEvent.click(screen.getByRole('button', { name: /Owner/ }))
    await userEvent.type(screen.getByLabelText('Motivo'), 'Se va del proyecto')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar el rol' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(sentence))
    expect(screen.queryByText('English, for the log')).not.toBeInTheDocument()
  })
})
