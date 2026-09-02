import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { SearchQueryInput } from './SearchQueryInput'
import { buildSearchQuery, emptySearchQuery, type SearchQueryValue } from '@/lib/searchQuery'

const FIELDS = [
  { name: 'discord_name', label: 'Discord' },
  { name: 'user_name', label: 'Nombre' },
]

/** Envoltorio controlado: el componente no guarda estado, así que el test hace de dueño. */
function Harness() {
  const [value, setValue] = useState<SearchQueryValue>(emptySearchQuery)
  return (
    <>
      <SearchQueryInput fields={FIELDS} value={value} onChange={setValue} label="Buscar personas" />
      <output>{buildSearchQuery(value)}</output>
    </>
  )
}

function searchBox() {
  return screen.getByRole('combobox', { name: 'Buscar personas' })
}

function query() {
  return screen.getByRole('status')
}

describe('SearchQueryInput', () => {
  it('manda lo que se escribe sin necesidad de cerrar un chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan')

    expect(query()).toHaveTextContent('juan')
  })

  it('la barra abre la lista de campos y elegir uno deja su chip fijo', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us')
    await userEvent.click(screen.getByRole('option', { name: /Nombre/ }))

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
    expect(searchBox()).toHaveValue('')
  })

  it('con el campo abierto, todo lo que se escribe es su valor, espacios incluidos', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan pablo')

    expect(query()).toHaveTextContent('/user_name juan pablo')
  })

  it('las flechas eligen entre los campos sugeridos, y Enter confirma', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/')
    await userEvent.keyboard('{ArrowDown}{Enter}')

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('las flechas dan la vuelta al llegar al final de la lista', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/')
    await userEvent.keyboard('{ArrowUp}{Enter}')

    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('otra barra cierra el criterio abierto y empieza el siguiente', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan /dis{Enter}pablo')

    expect(query()).toHaveTextContent('/user_name juan and /discord_name pablo')
  })

  it('Enter cierra el criterio abierto en un chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/dis{Enter}juan{Enter}')

    expect(screen.getByText('Discord:')).toBeInTheDocument()
    expect(screen.getByText('juan')).toBeInTheDocument()
    expect(searchBox()).toHaveValue('')
  })

  it('escribir un conector cierra el criterio anterior', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan or pablo')

    expect(query()).toHaveTextContent('juan or pablo')
  })

  it('cambia el conector entre dos chips al tocarlo', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan or pablo{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'o' }))

    expect(query()).toHaveTextContent('juan and pablo')
  })

  it('quita un criterio con la X del chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar criterio: juan' }))

    expect(query()).toHaveTextContent('')
  })

  it('Backspace con el texto vacío suelta primero el campo abierto', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(screen.queryByText('Nombre:')).not.toBeInTheDocument()
  })

  it('Backspace con el texto vacío y sin campo abierto devuelve el último chip al input', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us{Enter}juan{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(searchBox()).toHaveValue('juan')
    expect(screen.getByText('Nombre:')).toBeInTheDocument()
  })

  it('Escape cierra la lista de campos sin tocar lo escrito', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan /us')
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('option')).not.toBeInTheDocument()
    expect(searchBox()).toHaveValue('juan')
  })

  it('un /campo escrito de una sola vez llega al mismo chip que elegirlo de la lista', async () => {
    render(<Harness />)

    await userEvent.click(searchBox())
    await userEvent.paste('/discord_name juan')
    await userEvent.type(searchBox(), '{Enter}')

    expect(screen.getByText('Discord:')).toBeInTheDocument()
    expect(query()).toHaveTextContent('/discord_name juan')
  })

  it('un prefijo que no es un campo queda como texto, sin romper la búsqueda', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/nickname juan{Enter}')

    expect(query()).toHaveTextContent('/nickname juan')
    expect(screen.queryByText('Discord:')).not.toBeInTheDocument()
  })
})
