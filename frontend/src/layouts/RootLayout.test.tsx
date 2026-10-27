import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { RootLayout } from './RootLayout'

let auth = { isBootstrapping: false, isAuthenticated: true }
let me: { data?: { needsOnboarding: boolean }; isPending: boolean } = { isPending: false }

vi.mock('@/providers/AuthProvider', () => ({ useAuth: () => auth }))
vi.mock('@/features/users', () => ({ useMe: () => me }))
vi.mock('@/components/BackendStatusIndicator', () => ({ BackendStatusIndicator: () => null }))
vi.mock('@/components/dev/DevPanel', () => ({ DevPanel: () => null }))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<RootLayout />}>
          <Route path="/" element={<p>root</p>} />
          <Route path="/player" element={<p>player</p>} />
          <Route path="/onboarding" element={<p>onboarding</p>} />
          <Route path="/login" element={<p>login</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

/** ADR-10 §4: the first step blocks wherever the session enters, and does not repeat. */
describe('RootLayout onboarding guard', () => {
  beforeEach(() => {
    auth = { isBootstrapping: false, isAuthenticated: true }
    me = { isPending: false }
  })

  it('sends an account that owes the onboarding to it from any path', () => {
    me = { data: { needsOnboarding: true }, isPending: false }
    renderAt('/player')

    expect(screen.getByText('onboarding')).toBeInTheDocument()
  })

  it('lets that account stay on the onboarding', () => {
    me = { data: { needsOnboarding: true }, isPending: false }
    renderAt('/onboarding')

    expect(screen.getByText('onboarding')).toBeInTheDocument()
  })

  it('takes an account that already completed it out of the onboarding', () => {
    me = { data: { needsOnboarding: false }, isPending: false }
    renderAt('/onboarding')

    expect(screen.getByText('root')).toBeInTheDocument()
  })

  it('lets an account that completed it through everywhere else', () => {
    me = { data: { needsOnboarding: false }, isPending: false }
    renderAt('/player')

    expect(screen.getByText('player')).toBeInTheDocument()
  })

  it('waits for the profile before deciding', () => {
    me = { isPending: true }
    renderAt('/player')

    expect(screen.queryByText('player')).not.toBeInTheDocument()
    expect(screen.queryByText('onboarding')).not.toBeInTheDocument()
  })

  it('lets the request through when the profile could not be read', () => {
    me = { isPending: false }
    renderAt('/player')

    expect(screen.getByText('player')).toBeInTheDocument()
  })
})
