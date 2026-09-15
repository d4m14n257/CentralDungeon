import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'

import { UserAdminHistory } from './UserAdminHistory'
import type { AdminUserDetail, UserAdminChange } from '../types'

const byId = vi.hoisted(() => vi.fn())
const history = vi.hoisted(() => vi.fn())

vi.mock('../api/adminUsersApi', () => ({ adminUsersApi: { byId, history } }))

const DETAIL: AdminUserDetail = {
  id: 'user-1',
  discordUsername: 'dami',
  name: 'Damián',
  country: 'AR',
  status: 'Blocked',
  roles: ['Player', 'Master'],
  createdAt: '2026-01-01T12:00:00',
  updatedAt: '2026-02-01T12:00:00',
}

function change(overrides: Partial<UserAdminChange>): UserAdminChange {
  return {
    id: 'change-1',
    type: 'RoleGranted',
    role: 'Master',
    fromStatus: null,
    toStatus: null,
    changedByName: 'Ana Valdez',
    justification: 'Va a dirigir los martes',
    createdAt: '2026-02-01T12:00:00',
    ...overrides,
  }
}

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function wrap(children: ReactNode) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
  return render(wrap(<UserAdminHistory userId="user-1" />))
}

beforeEach(() => {
  byId.mockReset()
  history.mockReset()
})

describe('UserAdminHistory', () => {
  /**
   * The reason the two audit tables are worth writing: a role change nobody can read back is a row
   * that was born orphaned, which is exactly what §7 of the phase document warns about.
   */
  it('reads back each kind of change with who made it and why', async () => {
    byId.mockResolvedValue(DETAIL)
    history.mockResolvedValue([
      change({ id: 'c1', type: 'RoleGranted', role: 'Master' }),
      change({ id: 'c2', type: 'RoleRevoked', role: 'Player', justification: 'Lo pidió la persona' }),
      change({
        id: 'c3',
        type: 'StatusChanged',
        role: null,
        fromStatus: 'Allowed',
        toStatus: 'Blocked',
        justification: 'Acoso reiterado',
      }),
    ])
    renderPanel()

    expect(await screen.findByText('Se dio el rol Master')).toBeInTheDocument()
    expect(screen.getByText('Se quitó el rol Jugador')).toBeInTheDocument()
    expect(screen.getByText('Activa → Bloqueada')).toBeInTheDocument()
    expect(screen.getAllByText('Por Ana Valdez')).toHaveLength(3)
    // Never conditional: the justification is mandatory on both tables (#84, #169).
    expect(screen.getByText('"Acoso reiterado"')).toBeInTheDocument()
  })

  /** The header is the account as it is *now*, which is why the panel takes an id and asks. */
  it('shows where the account stands today, above how it got there', async () => {
    byId.mockResolvedValue(DETAIL)
    history.mockResolvedValue([])
    renderPanel()

    expect(await screen.findByText('Bloqueada')).toBeInTheDocument()
    expect(screen.getByText('Sin cambios registrados')).toBeInTheDocument()
  })

  /** Its own four states (#150): the listing around it loaded, this is a second question. */
  it('explains a 403 rather than blanking', async () => {
    byId.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Denied', errorCode: 'FORBIDDEN' }))
    history.mockRejectedValue(new ApiError(403, { title: 'Forbidden', status: 403, detail: 'Denied', errorCode: 'FORBIDDEN' }))
    renderPanel()

    expect(await screen.findByText('No tenés permiso')).toBeInTheDocument()
  })

  it('offers a retry when the record cannot be loaded', async () => {
    byId.mockResolvedValue(DETAIL)
    history.mockRejectedValue(new ApiError(500, { title: 'Error', status: 500, detail: 'Boom', errorCode: 'INTERNAL_ERROR' }))
    renderPanel()

    expect(await screen.findByRole('button', { name: /Reintentar/i })).toBeInTheDocument()
  })
})
