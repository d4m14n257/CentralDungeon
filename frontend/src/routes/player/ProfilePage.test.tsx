import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
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

// The approvals API is mocked and its hooks are not: the rule about when the request is offered is
// exactly what this screen has to get right, so it runs for real (F3.2).
const mine = vi.hoisted(() => vi.fn())
vi.mock('@/features/approvals/api/approvalsApi', () => ({ approvalsApi: { mine } }))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function wrap(children: ReactNode) {
    // The request dialog composes `FormDialog`, which guards a dirty form on the way out (#231).
    return (
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>{children}</ConfirmDialogProvider>
      </QueryClientProvider>
    )
  }
  return render(wrap(<ProfilePage />))
}

function noRequests() {
  mine.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 })
}

beforeEach(() => {
  mine.mockReset()
  noRequests()
})

describe('ProfilePage', () => {
  it('shows the reader’s own profile once it loads', () => {
    queryResult = { data: PROFILE, isPending: false, isLoadingError: false, refetch: vi.fn() }
    renderPage()

    expect(screen.getByText('Ana Valdez')).toBeInTheDocument()
  })

  /**
   * F3.2: the master role is asked for from here, because this is where somebody sees which roles
   * they have and therefore where the absence of one is noticed (fase-3-admin-owner.md:126).
   */
  it('offers the master role request to somebody who is not a master', async () => {
    queryResult = { data: { ...PROFILE, roles: ['Player'] }, isPending: false, isLoadingError: false, refetch: vi.fn() }
    renderPage()

    expect(await screen.findByRole('button', { name: 'Pedir el rol de master' })).toBeInTheDocument()
  })

  /**
   * And never to somebody who already runs tables: the backend refuses it with
   * `MASTER_ROLE_ALREADY_HELD`, and a button whose only possible outcome is a `409` is a button that
   * should not be there (principio 2 de frontend-diseno.md §1).
   */
  it('offers nothing of the sort to somebody who already is one', async () => {
    queryResult = { data: PROFILE, isPending: false, isLoadingError: false, refetch: vi.fn() }
    renderPage()

    await waitFor(() => expect(screen.getByText('Ana Valdez')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Pedir el rol de master' })).not.toBeInTheDocument()
    // Not the pitch either: there is nothing to explain to somebody the offer does not apply to.
    expect(screen.queryByText('¿Querés dirigir mesas?')).not.toBeInTheDocument()
  })

  /** One pending request at a time: what is shown instead is the fact, and the date it happened on. */
  it('shows the pending request instead of the button when one is already waiting', async () => {
    queryResult = { data: { ...PROFILE, roles: ['Player'] }, isPending: false, isLoadingError: false, refetch: vi.fn() }
    mine.mockResolvedValue({
      content: [
        {
          id: 'req-1',
          type: 'MasterGrant',
          status: 'Pending',
          requestedByName: 'Ana Valdez',
          justification: 'Quiero dirigir',
          claimedByName: null,
          createdAt: '2026-03-04T12:00:00',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderPage()

    await waitFor(() => expect(screen.getByText(/Ya pediste el rol de master/)).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Pedir el rol de master' })).not.toBeInTheDocument()
  })

  it('shows a skeleton while loading', () => {
    queryResult = { data: undefined, isPending: true, isLoadingError: false, refetch: vi.fn() }
    const { container } = renderPage()

    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })

  /** Nobody can be locked out of their own profile: there is no forbidden state to cover here. */
  it('offers a retry when the load fails, with no forbidden state involved', () => {
    queryResult = { data: undefined, isPending: false, isLoadingError: true, refetch: vi.fn() }
    renderPage()

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
