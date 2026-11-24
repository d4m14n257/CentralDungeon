import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { OAuthCallbackPage } from './OAuthCallbackPage'

// The screen under test only reads the query string; the session is not part of it.
vi.mock('@/providers/AuthProvider', () => ({ useAuth: () => ({ isBootstrapping: false, isAuthenticated: false }) }))
vi.mock('@/features/users', () => ({ useMe: () => ({ isPending: false }) }))

function renderCallback(search: string) {
  return render(
    <MemoryRouter initialEntries={[`/auth/callback${search}`]}>
      <OAuthCallbackPage />
    </MemoryRouter>,
  )
}

describe('OAuthCallbackPage', () => {
  /** The login is a browser navigation, so the backend sends the wait in the query string. */
  it('tells a rate-limited login how many seconds are left', () => {
    renderCallback('?error=RATE_LIMITED&retryAfterSeconds=42')

    expect(screen.getByText(/42 segundos/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al login' })).toBeInTheDocument()
  })

  it('keeps the generic message for an error code it does not know', () => {
    renderCallback('?error=something_else')

    expect(screen.getByText('No pudimos iniciar tu sesión')).toBeInTheDocument()
  })
})
