import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AppContext } from '@/stores/contextStore'
import { RequireContext } from './RequireContext'

/** The contexts the signed-in account has, and whether its roles have arrived yet. */
let contexts: AppContext[] = []
let isPending = false

// The mapping from roles to contexts has its own owner (`useAvailableContexts`); what is under test
// here is only what the guard does with the answer.
vi.mock('@/hooks/useAvailableContexts', () => ({
  useAvailableContexts: () => ({ contexts, isPending, activeContext: contexts[0] ?? 'player', preferredContext: contexts[0] ?? 'player' }),
}))

function renderAt(url: string, context: AppContext) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/" element={<p>root dispatcher</p>} />
        <Route
          path={`/${context}/*`}
          element={
            <RequireContext context={context}>
              <p>{context} shell</p>
            </RequireContext>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireContext', () => {
  beforeEach(() => {
    contexts = []
    isPending = false
  })

  /** #269: a player who types an admin URL never sees the admin shell - they go back to `/`. */
  it('sends a player who forces /admin back to the root dispatcher', () => {
    contexts = ['player']
    renderAt('/admin/users', 'admin')

    expect(screen.getByText('root dispatcher')).toBeInTheDocument()
    expect(screen.queryByText('admin shell')).not.toBeInTheDocument()
  })

  it('lets an admin or owner into the admin context', () => {
    contexts = ['admin']
    renderAt('/admin/users', 'admin')

    expect(screen.getByText('admin shell')).toBeInTheDocument()
  })

  /** Roles are cumulative but not implied: a master without `Player` has no player context. */
  it('sends a master without the Player role out of /player', () => {
    contexts = ['master']
    renderAt('/player', 'player')

    expect(screen.getByText('root dispatcher')).toBeInTheDocument()
  })

  /** Deciding before the roles arrive would bounce everybody, including the ones who belong. */
  it('decides nothing while the roles are still in flight', () => {
    isPending = true
    renderAt('/admin', 'admin')

    expect(screen.queryByText('root dispatcher')).not.toBeInTheDocument()
    expect(screen.queryByText('admin shell')).not.toBeInTheDocument()
    expect(screen.getByText('Cargando...')).toBeInTheDocument()
  })
})
