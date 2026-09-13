import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import type { Profile } from '../types'
import { ProfileCard } from './ProfileCard'

const BASE_PROFILE: Profile = {
  id: 'user-1',
  name: 'Ana Valdez',
  country: 'AR',
  roles: ['Player', 'Master'],
  attendance: { present: 8, excused: 2, absent: 1, registered: 11 },
}

describe('ProfileCard', () => {
  /** #137: the three counts, never collapsed into a ratio. */
  it('shows the three attendance counts separately', () => {
    render(<ProfileCard profile={BASE_PROFILE} />)

    expect(screen.getByText('Presente').nextElementSibling).toHaveTextContent('8')
    expect(screen.getByText('Con aviso').nextElementSibling).toHaveTextContent('2')
    expect(screen.getByText('Ausente').nextElementSibling).toHaveTextContent('1')
  })

  /**
   * #248: "Comentarios recibidos" is F5. Drawing the heading with nothing under it would read as a
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

  /** A profile with nothing set beyond the name still has to look finished, not broken (#248). */
  it('looks right when there is no country and no attendance recorded', () => {
    const sparse: Profile = {
      id: 'user-2',
      name: 'Beto',
      country: null,
      roles: [],
      attendance: { present: 0, excused: 0, absent: 0, registered: 0 },
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
      name: null,
      country: null,
      roles: [],
      attendance: { present: 0, excused: 0, absent: 0, registered: 0 },
    }
    render(<ProfileCard profile={noName} />)

    expect(screen.getByText('Sin nombre')).toBeInTheDocument()
  })
})
