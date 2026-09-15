import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { AdminUserRolesCell } from './AdminUserRolesCell'

describe('AdminUserRolesCell', () => {
  /**
   * #165's order, and the reason the component sorts instead of trusting what arrived: a column
   * where one row reads "Master · Jugador" and the next "Jugador · Master" makes an admin compare
   * two rows character by character.
   */
  it('draws the roles in the order of #165, whatever order they arrived in', () => {
    render(<AdminUserRolesCell roles={['Owner', 'Player', 'Master']} />)

    const chips = screen.getAllByRole('listitem').map((chip) => chip.textContent)
    expect(chips).toEqual(['Jugador', 'Master', 'Owner'])
  })

  /** Every account is *created* with Player (#38), which is not the same as always having it. */
  it('says so when an account has no roles left', () => {
    render(<AdminUserRolesCell roles={[]} />)

    expect(screen.getByText('Sin roles')).toBeInTheDocument()
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
  })

  /** The colour is never the only carrier: the rank is named, not just tinted (frontend-diseno.md §3). */
  it('names Admin and Owner rather than only colouring them', () => {
    render(<AdminUserRolesCell roles={['Admin']} />)

    expect(screen.getByText('Admin')).toBeInTheDocument()
  })
})
