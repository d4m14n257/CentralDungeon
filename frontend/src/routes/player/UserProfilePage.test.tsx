import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { Profile } from '@/features/users'
import { ApiError } from '@/types/api'
import { UserProfilePage } from './UserProfilePage'

const PROFILE: Profile = {
  id: 'user-9',
  name: 'Diego',
  country: 'AR',
  roles: ['Player'],
  attendance: { present: 3, excused: 0, absent: 0, registered: 3 },
}

let queryResult: {
  data: Profile | undefined
  isPending: boolean
  isLoadingError: boolean
  error: unknown
  refetch: () => void
} = { data: PROFILE, isPending: false, isLoadingError: false, error: null, refetch: vi.fn() }

// `vi.mock` is hoisted above these imports by Vitest, so `queryResult` above is only read, never
// captured stale, each time a test reassigns it before rendering.
vi.mock('@/features/users', () => ({
  useUserProfile: () => queryResult,
  ProfileCard: ({ profile }: { profile: Profile }) => <div>{profile.name}</div>,
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/player/users/user-9']}>
      <Routes>
        <Route path="/player/users/:id" element={<UserProfilePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('UserProfilePage', () => {
  it('shows the profile once it loads', () => {
    queryResult = { data: PROFILE, isPending: false, isLoadingError: false, error: null, refetch: vi.fn() }
    renderPage()

    expect(screen.getByText('Diego')).toBeInTheDocument()
  })

  it('shows a skeleton while loading', () => {
    queryResult = { data: undefined, isPending: true, isLoadingError: false, error: null, refetch: vi.fn() }
    const { container } = renderPage()

    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })

  /**
   * #249: a 404 here never means "no existe" the way the veto's 404 does (#29) — it can also mean
   * the two-week window of #44 closed. The screen has to say the truth that is safe for both cases.
   */
  it('explains that the profile is no longer visible on a 404, not that it does not exist', () => {
    queryResult = {
      data: undefined,
      isPending: false,
      isLoadingError: false,
      error: new ApiError(404, { title: 'Not Found', status: 404, detail: 'not found', errorCode: 'NOT_FOUND' }),
      refetch: vi.fn(),
    }
    renderPage()

    expect(screen.getByText('Ya no podés ver este perfil.')).toBeInTheDocument()
    expect(screen.queryByText(/no existe/i)).not.toBeInTheDocument()
  })

  it('offers a retry on a plain load failure', () => {
    queryResult = {
      data: undefined,
      isPending: false,
      isLoadingError: true,
      error: new ApiError(500, { title: 'Error', status: 500, detail: 'boom', errorCode: 'INTERNAL' }),
      refetch: vi.fn(),
    }
    renderPage()

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
