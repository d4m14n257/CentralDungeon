import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { Profile } from '@/features/users'
import { ProfilePage } from './ProfilePage'

const PROFILE: Profile = {
  id: 'user-1',
  name: 'Ana Valdez',
  country: 'AR',
  roles: ['Player', 'Master'],
  attendance: { present: 8, excused: 2, absent: 1, registered: 11 },
}

let queryResult: { data: Profile | undefined; isPending: boolean; isLoadingError: boolean; refetch: () => void } = {
  data: PROFILE,
  isPending: false,
  isLoadingError: false,
  refetch: vi.fn(),
}

vi.mock('@/features/users', () => ({
  useMyProfile: () => queryResult,
  ProfileCard: ({ profile }: { profile: Profile }) => <div>{profile.name}</div>,
}))

describe('ProfilePage', () => {
  it('shows the reader’s own profile once it loads', () => {
    queryResult = { data: PROFILE, isPending: false, isLoadingError: false, refetch: vi.fn() }
    render(<ProfilePage />)

    expect(screen.getByText('Ana Valdez')).toBeInTheDocument()
  })

  it('shows a skeleton while loading', () => {
    queryResult = { data: undefined, isPending: true, isLoadingError: false, refetch: vi.fn() }
    const { container } = render(<ProfilePage />)

    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })

  /** Nobody can be locked out of their own profile: there is no forbidden state to cover here. */
  it('offers a retry when the load fails, with no forbidden state involved', () => {
    queryResult = { data: undefined, isPending: false, isLoadingError: true, refetch: vi.fn() }
    render(<ProfilePage />)

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
