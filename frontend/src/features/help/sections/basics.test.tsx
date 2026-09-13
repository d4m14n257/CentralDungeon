import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import type { SearchField } from '@/lib/searchQuery'
import { SearchHelp } from './basics'

const USER_FIELDS: SearchField[] = [
  { name: 'discord_name', label: 'Discord', examples: ['dami'] },
  { name: 'user_name', label: 'Nombre', examples: ['damian', 'carlos', 'daniel'] },
]

const FILE_FIELDS: SearchField[] = [
  { name: 'file_name', label: 'nombre', examples: ['ficha'] },
  {
    name: 'file_type',
    label: 'tipo de archivo',
    values: [
      { value: 'application/pdf', label: 'PDF' },
      { value: 'image/png', label: 'PNG' },
    ],
  },
]

/**
 * #240: the rules of the search are the same everywhere, but the examples are not.
 *
 * They used to be five fixed rows about `/user_name` and `/discord_name`, shown from every box — so
 * the help of `/my/files` taught two commands that screen does not have and none of the ones it does.
 */
describe('SearchHelp', () => {
  it('names every command of the box that opened it', () => {
    render(<SearchHelp searchFields={FILE_FIELDS} />)

    expect(screen.getByText('/file_name')).toBeInTheDocument()
    expect(screen.getByText('/file_type')).toBeInTheDocument()
  })

  it('teaches no command the box does not have', () => {
    render(<SearchHelp searchFields={FILE_FIELDS} />)

    expect(screen.queryByText(/user_name/)).not.toBeInTheDocument()
    expect(screen.queryByText(/discord_name/)).not.toBeInTheDocument()
  })

  it('works every command into an example of its own', () => {
    render(<SearchHelp searchFields={FILE_FIELDS} />)

    expect(screen.getByText('/file_name ficha')).toBeInTheDocument()
    expect(screen.getByText('/file_type PDF')).toBeInTheDocument()
  })

  /** A command with fixed choices says what they are: nobody can pick from a list they cannot see. */
  it('lists the choices of a command that takes a fixed set', () => {
    render(<SearchHelp searchFields={FILE_FIELDS} />)

    expect(screen.getByText(/PDF, PNG/)).toBeInTheDocument()
  })

  it('shows the connectors with the box own commands', () => {
    render(<SearchHelp searchFields={USER_FIELDS} />)

    expect(screen.getByText('/discord_name dami /or /user_name damian')).toBeInTheDocument()
    expect(screen.getByText('/discord_name dami /and /user_name damian')).toBeInTheDocument()
  })

  /** The commas need a command with more than one plausible value, whichever kind it is. */
  it('shows the commas with a command that has several values', () => {
    render(<SearchHelp searchFields={USER_FIELDS} />)

    expect(screen.getByText('/user_name damian,carlos,daniel')).toBeInTheDocument()
  })

  /**
   * A command declared with no choices behind it — a person with no cajón of their own (#237) — is
   * not the pick-from-a-list kind. Saying «elegís entre:» with nothing after the colon promises a
   * list that is not there.
   */
  it('does not present a command with an empty list as one you pick from', () => {
    render(<SearchHelp searchFields={[{ name: 'file_categories', label: 'cajón', values: [] }]} />)

    expect(screen.queryByText(/elegís entre/)).not.toBeInTheDocument()
  })

  it('shows the rules even with no box to draw examples from', () => {
    render(<SearchHelp />)

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getAllByRole('listitem').length).toBeGreaterThan(0)
  })
})
