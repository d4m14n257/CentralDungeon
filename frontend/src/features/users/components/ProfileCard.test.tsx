import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { Profile } from '../types'
import { ProfileCard } from './ProfileCard'

const BASE_PROFILE: Profile = {
  id: 'user-1',
  discordUsername: 'elara.discord',
  name: 'Ana Valdez',
  country: 'AR',
  roles: ['Player', 'Master'],
  attendance: { present: 8, excused: 2, absent: 1, registered: 11 },
  finishedTables: 2,
}

vi.mock('./FinishedTablesDialog', () => ({
  FinishedTablesDialog: ({ userId, canOpenTables }: { userId: string; canOpenTables: boolean }) => (
    <div>{`finished tables of ${userId}, links ${canOpenTables}`}</div>
  ),
}))

describe('ProfileCard', () => {
  /** ADR-08 §5: the three counts, never collapsed into a ratio. */
  it('shows the three attendance counts separately', () => {
    render(<ProfileCard profile={BASE_PROFILE} />)

    expect(screen.getByText('Presente').nextElementSibling).toHaveTextContent('8')
    expect(screen.getByText('Con aviso').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Ausente').nextElementSibling).toHaveTextContent('1')
  })

  /**
   * ADR-10 §5: "Comentarios recibidos" is F5. Drawing the heading with nothing under it would read as a
   * broken page rather than as an honest empty state, so this component never draws it at all.
   */
  it('never renders a comments section', () => {
    render(<ProfileCard profile={BASE_PROFILE} />)

    expect(screen.queryByText(/Comentarios recibidos/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/karma/i)).not.toBeInTheDocument()
  })

  it('shows the name and the roles', () => {
    render(<ProfileCard profile={BASE_PROFILE} />)

    expect(screen.getByText('Ana Valdez')).toBeInTheDocument()
    expect(screen.getByText('Jugador')).toBeInTheDocument()
    expect(screen.getByText('Master')).toBeInTheDocument()
  })

  /** A profile with nothing set beyond the name still has to look finished, not broken (ADR-10 §5). */
  it('looks right when there is no country and no attendance recorded', () => {
    const sparse: Profile = {
      id: 'user-2',
      discordUsername: 'elara.discord',
      name: 'Beto',
      country: null,
      roles: [],
      attendance: { present: 0, excused: 0, absent: 0, registered: 0 },
      finishedTables: 2,
    }
    const { container } = render(<ProfileCard profile={sparse} />)

    expect(screen.getByText('Beto')).toBeInTheDocument()
    expect(screen.getByText('Todavía no hay asistencia registrada.')).toBeInTheDocument()
    expect(container.textContent).not.toMatch(/undefined|null/i)
  })

  /** Onboarding normally guarantees a name, but the card still has to hold up if one is missing. */
  it('falls back to a generic label when there is no name', () => {
    const noName: Profile = {
      id: 'user-3',
      discordUsername: 'elara.discord',
      name: null,
      country: null,
      roles: [],
      attendance: { present: 0, excused: 0, absent: 0, registered: 0 },
      finishedTables: 2,
    }
    render(<ProfileCard profile={noName} />)

    expect(screen.getByText('Sin nombre')).toBeInTheDocument()
  })

  /** Own profile: the count leads to the history, but only where that screen is reachable. */
  it('links the own count to the player history for an account with Player', () => {
    render(
      <MemoryRouter>
        <ProfileCard profile={BASE_PROFILE} isOwn viewerIsPlayer />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: '2 mesas terminadas' })).toHaveAttribute('href', '/player/history')
  })

  it('keeps the own count a plain number without Player, since the history would bounce it', () => {
    render(
      <MemoryRouter>
        <ProfileCard profile={BASE_PROFILE} isOwn viewerIsPlayer={false} />
      </MemoryRouter>,
    )

    expect(screen.getByText('2 mesas terminadas')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '2 mesas terminadas' })).not.toBeInTheDocument()
  })

  /** Somebody else's profile: the count opens the list, whose names link only for a Player. */
  it("opens the list of finished tables from somebody else's count", async () => {
    render(<ProfileCard profile={BASE_PROFILE} viewerIsPlayer />)

    await userEvent.click(screen.getByRole('button', { name: '2 mesas terminadas' }))

    expect(screen.getByText('finished tables of user-1, links true')).toBeInTheDocument()
  })

  it('does not offer to open a count of zero', () => {
    render(<ProfileCard profile={{ ...BASE_PROFILE, finishedTables: 0 }} />)

    expect(screen.queryByRole('button', { name: /terminadas/ })).not.toBeInTheDocument()
  })
})
