import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AppContext } from '@/stores/contextStore'
import { RootRedirect } from './RootRedirect'

let contexts: AppContext[] = []
let isError = false
const retry = vi.fn()

// The sign-out button is not under test here; it only needs a session to close.
vi.mock('@/features/auth', () => ({ useLogout: () => ({ mutate: vi.fn(), isPending: false }) }))
vi.mock('@/providers/AuthProvider', () => ({ useAuth: () => ({ signOut: vi.fn() }) }))

vi.mock('@/hooks/useAvailableContexts', () => ({
  useAvailableContexts: () => ({
    contexts,
    isPending: false,
    isError,
    retry,
    activeContext: contexts[0] ?? 'player',
    preferredContext: contexts[0] ?? 'player',
  }),
}))

function renderRoot() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/player" element={<p>player home</p>} />
        <Route path="/admin" element={<p>admin home</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RootRedirect', () => {
  beforeEach(() => {
    contexts = []
    isError = false
    retry.mockClear()
  })

  /** ADR-20 §3: the admin context's home is `/admin` itself, not the tray. */
  it('sends an admin-only account to /admin', () => {
    contexts = ['admin']
    renderRoot()

    expect(screen.getByText('admin home')).toBeInTheDocument()
  })

  /** ADR-20 §2: with no context at all, navigating would loop between `/` and the player guard. */
  it('explains instead of redirecting when the account has no context', () => {
    renderRoot()

    expect(screen.queryByText('player home')).not.toBeInTheDocument()
    expect(screen.getByText('Tu cuenta no tiene ninguna sección')).toBeInTheDocument()
  })

  it('offers a way to sign out from the screen of an account with no context', () => {
    renderRoot()

    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
  })

  /** A failed read of the roles leaves `contexts` empty too, and that must not read as «no section». */
  it('shows an error with a retry, and not «no section», when the roles could not be read', async () => {
    isError = true
    renderRoot()

    expect(screen.getByText('Algo salió mal')).toBeInTheDocument()
    expect(screen.queryByText('Tu cuenta no tiene ninguna sección')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(retry).toHaveBeenCalledOnce()
  })
})
