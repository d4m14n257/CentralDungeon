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

/**
 * A command with fixed choices (#164), kept out of the shared harness on purpose: the tests about
 * moving through the list count what is in it, and a third field would change what they mean.
 */
const CHOICE_FIELDS = [
  {
    name: 'file_type',
    label: 'Tipo',
    values: [
      { value: 'application/pdf', label: 'PDF' },
      { value: 'image/png', label: 'PNG' },
    ],
  },
]

/** The same controlled wrapper, for the box that offers fixed choices. */
function ChoiceHarness() {
  const [value, setValue] = useState<SearchQueryValue>(emptySearchQuery)
  return (
    <MemoryRouter>
      <SearchQueryInput fields={CHOICE_FIELDS} value={value} onChange={setValue} label="Buscar archivos" />
      <output>{buildSearchQuery(value, CHOICE_FIELDS)}</output>
    </MemoryRouter>
  )
}

function choiceBox() {
  return screen.getByRole('combobox', { name: 'Buscar archivos' })
}

/** A controlled wrapper: the component keeps no state, so the test plays the owner. */
function Harness() {
  const [value, setValue] = useState<SearchQueryValue>(emptySearchQuery)
  return (
    <MemoryRouter>
      <SearchQueryInput fields={FIELDS} value={value} onChange={setValue} label="Buscar personas" />
      <output>{buildSearchQuery(value, FIELDS)}</output>
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

  /**
   * The standardisation of #240: picking writes text, and text is all there is until Enter.
   *
   * It used to pin the chip on the spot, so the very same command reached two different states
   * depending on whether it was picked from the list or typed out — and which of the two somebody did
   * depended on how long the command was, so the two search boxes of the app felt like two languages.
   */
  it('picking a command writes it into the text and closes nothing', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us')
    await userEvent.click(screen.getByRole('option', { name: /Nombre/ }))

    expect(searchBox()).toHaveValue('/user_name ')
    expect(screen.queryByText('Nombre:')).not.toBeInTheDocument()
  })

  it('picking a command and typing it out by hand leave the box in the same state', async () => {
    const { unmount } = render(<Harness />)
    await userEvent.type(searchBox(), '/us{Enter}damian{Enter}')
    const picked = searchBox().parentElement!.textContent

    unmount()
    render(<Harness />)
    await userEvent.type(searchBox(), '/user_name damian{Enter}')

    expect(searchBox().parentElement!.textContent).toBe(picked)
    expect(query()).toHaveTextContent('/user_name damian')
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

    expect(searchBox()).toHaveValue('/user_name ')
  })

  it('the arrows wrap around at the end of the list', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/')
    await userEvent.keyboard('{ArrowUp}{Enter}')

    expect(searchBox()).toHaveValue('/user_name ')
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
    // The first «y» is the one joining the two criteria; the second is the one waiting for whatever
    // comes next, which is a different connector and has its own chip.
    await userEvent.click(screen.getAllByRole('button', { name: 'y' })[0]!)

    expect(query()).toHaveTextContent('juan /or pablo')
  })

  /**
   * Choosing a connector shows its chip straight away.
   *
   * It used to wait until somebody typed again, so picking `/and` looked like it had done nothing
   * and the chip appeared later out of nowhere — and `or` showed immediately while `and` did not,
   * for no reason a reader could see.
   */
  it('shows the chosen connector as a chip before anything else is typed', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan{Enter}')
    await userEvent.type(searchBox(), '/or{Enter}')

    expect(screen.getByRole('button', { name: 'o' })).toBeInTheDocument()
  })

  /** And the one waiting is «y» by default, so the next criterion says how it will be joined. */
  it('shows the waiting connector as soon as there is a criterion to join', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan{Enter}')

    expect(screen.getByRole('button', { name: 'y' })).toBeInTheDocument()
  })

  it('removes a criterion through the chip X button', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar criterio: juan' }))

    expect(query()).toHaveTextContent('')
  })

  /** Nothing is pinned any more, so Backspace has one job: undo the last chip (#240). */
  it('Backspace on an empty box returns the last chip to the input, as the text that made it', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(searchBox()).toHaveValue('/user_name juan')
    expect(screen.queryByText('Nombre:')).not.toBeInTheDocument()
  })

  it('a chip handed back to the input rebuilds the same query when it is closed again', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.type(searchBox(), '{Backspace}{Enter}')

    expect(query()).toHaveTextContent('/user_name juan')
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

  /**
   * The second kind of command (#164): choosing it offers its values instead of waiting for somebody
   * to spell one. Nobody is going to type `application/vnd.openxmlformats-…`, and nobody should have
   * to know it exists.
   */
  it('offers the values of a command that has fixed choices', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type{Enter}')

    expect(screen.getByRole('option', { name: 'PDF' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'PNG' })).toBeInTheDocument()
  })

  /** The chip reads the label; the value that travels is the MIME type nobody wants to look at. */
  it('sends the chosen value and shows its label on the chip', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type{Enter}')
    await userEvent.click(screen.getByRole('option', { name: 'PDF' }))
    await userEvent.type(choiceBox(), '{Enter}')

    expect(screen.getByText('Tipo:')).toBeInTheDocument()
    expect(screen.getByText('PDF')).toBeInTheDocument()
    expect(query()).toHaveTextContent('/file_type application/pdf')
  })

  /** A chosen value is text like any other until Enter, and it is the label that is written (#240). */
  it('picking a value writes its label into the text and closes nothing', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type{Enter}')
    await userEvent.click(screen.getByRole('option', { name: 'PDF' }))

    expect(choiceBox()).toHaveValue('/file_type PDF')
    expect(screen.queryByText('Tipo:')).not.toBeInTheDocument()
  })

  /** Typed by hand it offers the very same list: it is the text that says a command is open, not a chip. */
  it('offers the values of a command typed out by hand', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type ')

    expect(screen.getByRole('option', { name: 'PDF' })).toBeInTheDocument()
  })

  it('the label typed by hand travels as the value it stands for', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type PNG{Enter}')

    expect(query()).toHaveTextContent('/file_type image/png')
  })

  it('offers the rest after a comma, and keeps the alternative already picked', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type PDF,')
    await userEvent.click(screen.getByRole('option', { name: 'PNG' }))
    await userEvent.type(choiceBox(), '{Enter}')

    expect(query()).toHaveTextContent('/file_type application/pdf,image/png')
  })

  /** With the box already saying it, a list repeating it is noise — whoever typed it or picked it. */
  it('offers nothing once what is typed is one of the values', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type PDF')

    expect(screen.queryByRole('option')).not.toBeInTheDocument()
  })

  /** Typing narrows the choices, so a long list stays usable. */
  it('narrows the choices by what has been typed', async () => {
    render(<ChoiceHarness />)

    await userEvent.type(choiceBox(), '/file_type{Enter}')
    await userEvent.type(choiceBox(), 'pn')

    expect(screen.getByRole('option', { name: 'PNG' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'PDF' })).not.toBeInTheDocument()
  })
})
