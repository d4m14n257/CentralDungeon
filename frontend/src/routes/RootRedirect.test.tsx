import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AppContext } from '@/stores/contextStore'
import { RootRedirect } from './RootRedirect'

let contexts: AppContext[] = []

vi.mock('@/hooks/useAvailableContexts', () => ({
  useAvailableContexts: () => ({
    contexts,
    isPending: false,
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
  })

  /** #270: the admin context's home is `/admin` itself, not the tray. */
  it('sends an admin-only account to /admin', () => {
    contexts = ['admin']
    renderRoot()

    expect(screen.getByText('admin home')).toBeInTheDocument()
  })

  /** #269: with no context at all, navigating would loop between `/` and the player guard. */
  it('explains instead of redirecting when the account has no context', () => {
    renderRoot()

    expect(screen.queryByText('player home')).not.toBeInTheDocument()
    expect(screen.getByText('Tu cuenta no tiene ninguna sección')).toBeInTheDocument()
  })
})
