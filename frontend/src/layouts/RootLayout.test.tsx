import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { peekDestination } from '@/lib/postLoginDestination'
import { RootLayout } from './RootLayout'

let auth = { isBootstrapping: false, isAuthenticated: true }
let me: { data?: { needsOnboarding: boolean }; isPending: boolean } = { isPending: false }

vi.mock('@/providers/AuthProvider', () => ({ useAuth: () => auth }))
vi.mock('@/features/users', () => ({ useMe: () => me }))
vi.mock('@/components/BackendStatusIndicator', () => ({ BackendStatusIndicator: () => null }))
vi.mock('@/components/dev/DevPanel', () => ({ DevPanel: () => null }))

function tree(path: string) {
  return (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<RootLayout />}>
          <Route path="/" element={<p>root</p>} />
          <Route path="/player" element={<p>player</p>} />
          <Route path="/master/tables/:id" element={<p>master table</p>} />
          <Route path="/onboarding" element={<p>onboarding</p>} />
          <Route path="/login" element={<p>login</p>} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

function renderAt(path: string) {
  return render(tree(path))
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

/** The session guard of the entry points: /login for a session, and the destination of a deep link. */
describe('RootLayout login guard', () => {
  beforeEach(() => {
    sessionStorage.clear()
    auth = { isBootstrapping: false, isAuthenticated: true }
    me = { data: { needsOnboarding: false }, isPending: false }
  })

  it('sends an account that already has a session away from /login', () => {
    renderAt('/login')

    expect(screen.getByText('root')).toBeInTheDocument()
    expect(screen.queryByText('login')).not.toBeInTheDocument()
  })

  it('lets a visitor without a session see /login', () => {
    auth = { isBootstrapping: false, isAuthenticated: false }
    me = { isPending: false }
    renderAt('/login')

    expect(screen.getByText('login')).toBeInTheDocument()
  })

  it('remembers the deep link of a visitor it sends to /login', () => {
    auth = { isBootstrapping: false, isAuthenticated: false }
    me = { isPending: false }
    renderAt('/master/tables/t-1?tab=x')

    expect(screen.getByText('login')).toBeInTheDocument()
    expect(peekDestination()).toBe('/master/tables/t-1?tab=x')
  })

  it('does not remember the root, which is not a destination', () => {
    auth = { isBootstrapping: false, isAuthenticated: false }
    me = { isPending: false }
    renderAt('/')

    expect(peekDestination()).toBeNull()
  })

  it('does not remember the page of somebody who just signed out', () => {
    const { rerender } = renderAt('/player')
    expect(screen.getByText('player')).toBeInTheDocument()

    auth = { isBootstrapping: false, isAuthenticated: false }
    me = { isPending: false }
    rerender(tree('/player'))

    expect(peekDestination()).toBeNull()
  })

  it('keeps the remembered destination while the onboarding blocks, for the dispatcher to honour after it', () => {
    sessionStorage.setItem('postLoginDestination', '/player')
    me = { data: { needsOnboarding: true }, isPending: false }
    renderAt('/player')

    expect(screen.getByText('onboarding')).toBeInTheDocument()
    expect(peekDestination()).toBe('/player')
  })
})
