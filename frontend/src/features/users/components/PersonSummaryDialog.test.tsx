import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { Profile } from '../types'
import { PersonSummaryDialog } from './PersonSummaryDialog'

const PROFILE: Profile = {
  id: 'user-9',
  discordUsername: 'diego.discord',
  name: 'Diego',
  country: 'AR',
  roles: ['Player', 'Master'],
  attendance: { present: 3, excused: 1, absent: 0, registered: 4 },
  finishedTables: 2,
}

vi.mock('../api/useUserProfile', () => ({
  useUserProfile: () => ({ data: PROFILE, isPending: false, isLoadingError: false, refetch: vi.fn() }),
}))

describe('PersonSummaryDialog', () => {
  /**
   * #284: the master's card is the general picture - who they are on the server and how many tables
   * they saw through. Their standing and roles are the admin's record, not this.
   */
  it('shows the Discord user, the name and the finished tables, and nothing about the account', () => {
    render(<PersonSummaryDialog userId="user-9" open onOpenChange={vi.fn()} />)

    expect(screen.getByText('diego.discord')).toBeInTheDocument()
    expect(screen.getByText('2 mesas terminadas')).toBeInTheDocument()
    expect(screen.queryByText('AR')).not.toBeInTheDocument()
    expect(screen.queryByText('Master')).not.toBeInTheDocument()
  })
})
