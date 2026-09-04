import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router'

import '@/providers/i18n'
import { SearchQueryInput } from './SearchQueryInput'
import { buildSearchQuery, emptySearchQuery, type SearchQueryValue } from '@/lib/searchQuery'

const FIELDS = [
  { name: 'discord_name', label: 'Discord' },
  { name: 'user_name', label: 'Nombre' },
]

/** A controlled wrapper: the component keeps no state, so the test plays the owner. */
function Harness() {
  const [value, setValue] = useState<SearchQueryValue>(emptySearchQuery)
  return (
    <MemoryRouter>
      <SearchQueryInput fields={FIELDS} value={value} onChange={setValue} label="Buscar personas" />
      <output>{buildSearchQuery(value)}</output>
    </MemoryRouter>
  )
}

function searchBox() {
  return screen.getByRole('combobox', { name: 'Buscar personas' })
}

function query() {
  return screen.getByRole('status')
}

describe('SearchQueryInput', () => {
  it('searches what is typed without having to close a chip first', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan')

    expect(query()).toHaveTextContent('juan')
  })

  it('the slash opens the field list, and picking one leaves its chip fixed', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us')
    await userEvent.click(screen.getByRole('option', { name: /Nombre/ }))

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
    expect(searchBox()).toHaveValue('')
  })

  it('with a field open, everything typed is its value, spaces included', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan pablo')

    expect(query()).toHaveTextContent('/user_name juan pablo')
  })

  it('commas separate alternatives of the same criterion', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}damian,carlos,daniel')

    expect(query()).toHaveTextContent('/user_name damian,carlos,daniel')
  })

  it('the arrows move through the suggestions and Enter confirms', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/')
    await userEvent.keyboard('{ArrowDown}{Enter}')

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('the arrows wrap around at the end of the list', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/')
    await userEvent.keyboard('{ArrowUp}{Enter}')

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('another slash closes the open criterion and starts the next one', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan /dis{Enter}pablo')

    expect(query()).toHaveTextContent('/user_name juan /and /discord_name pablo')
  })

  it('/or shows up among the suggestions once there is something to join, and leaves its chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan{Enter}pablo /o')
    await userEvent.click(screen.getByRole('option', { name: /Unir/ }))
    await userEvent.type(searchBox(), 'pedro')

    expect(query()).toHaveTextContent('juan /and pablo /or pedro')
  })

  it('offers the connector even while the previous criterion is still open', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan /o')
    await userEvent.click(screen.getByRole('option', { name: /Unir/ }))
    await userEvent.type(searchBox(), 'pablo')

    expect(query()).toHaveTextContent('juan /or pablo')
  })

  it('offers no connector while there is nothing to join', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/o')

    expect(screen.queryByRole('option', { name: /Unir/ })).not.toBeInTheDocument()
  })

  /** Without this nobody could search for a value containing the word: the separator is the slash. */
  it('a bare or is part of the value, not a connector', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan or pablo')

    expect(query()).toHaveTextContent('/user_name juan or pablo')
  })

  it('Enter closes the open criterion into a chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/dis{Enter}juan{Enter}')

    expect(screen.getByText('Discord:')).toBeInTheDocument()
    expect(screen.getByText('juan')).toBeInTheDocument()
    expect(searchBox()).toHaveValue('')
  })

  it('toggles the connector between two chips when it is tapped', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan{Enter}pablo{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'y' }))

    expect(query()).toHaveTextContent('juan /or pablo')
  })

  it('removes a criterion through the chip X button', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar criterio: juan' }))

    expect(query()).toHaveTextContent('')
  })

  it('Backspace on an empty box releases the open field first', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(screen.queryByText('Nombre:')).not.toBeInTheDocument()
  })

  it('Backspace on an empty box with no open field returns the last chip to the input', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(searchBox()).toHaveValue('juan')
    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('Escape closes the field list without touching what was typed', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan /us')
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('option')).not.toBeInTheDocument()
    expect(searchBox()).toHaveValue('juan')
  })

  it('a /field typed in one go reaches the same chip as picking it from the list', async () => {
    render(<Harness />)

    await userEvent.click(searchBox())
    await userEvent.paste('/discord_name juan')
    await userEvent.type(searchBox(), '{Enter}')

    expect(screen.getByText('Discord:')).toBeInTheDocument()
    expect(query()).toHaveTextContent('/discord_name juan')
  })

  it('a prefix that is not a field stays as text, without breaking the search', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/nickname juan{Enter}')

    expect(query()).toHaveTextContent('/nickname juan')
    expect(screen.queryByText('Discord:')).not.toBeInTheDocument()
  })
})
