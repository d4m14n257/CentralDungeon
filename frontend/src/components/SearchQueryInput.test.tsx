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
function Harness({ onQuery }: { onQuery?: (query: string) => void } = {}) {
  const [value, setValue] = useState<SearchQueryValue>(emptySearchQuery)
  return (
    <>
      <SearchQueryInput
        fields={FIELDS}
        value={value}
        onChange={(next) => {
          setValue(next)
          onQuery?.(buildSearchQuery(next))
        }}
        label="Buscar personas"
      />
      <output>{buildSearchQuery(value)}</output>
    </>
  )
}

function searchBox() {
  return screen.getByRole('textbox', { name: 'Buscar personas' })
}

describe('SearchQueryInput', () => {
  it('manda lo que se escribe sin necesidad de cerrar un chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan')

    expect(screen.getByRole('status')).toHaveTextContent('juan')
  })

  it('convierte un /campo en chip al cerrarlo con Enter', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/discord_name juan{Enter}')

    expect(screen.getByText('Discord:')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('/discord_name juan')
    expect(searchBox()).toHaveValue('')
  })

  it('cierra el chip anterior al escribir un conector', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/user_name juan or /discord_name pablo{Enter}')

    expect(screen.getByRole('status')).toHaveTextContent('/user_name juan or /discord_name pablo')
  })

  it('cambia el conector entre dos chips al tocarlo', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), 'juan or pablo{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'o' }))

    expect(screen.getByRole('status')).toHaveTextContent('juan and pablo')
  })

  it('quita un criterio con la X del chip', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/user_name juan{Enter}')
    await userEvent.click(screen.getByRole('button', { name: 'Quitar criterio: juan' }))

    expect(screen.getByRole('status')).toHaveTextContent('')
  })

  it('devuelve el último chip al input con Backspace, para editarlo', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/user_name juan{Enter}')
    await userEvent.type(searchBox(), '{Backspace}')

    expect(searchBox()).toHaveValue('/user_name juan')
    expect(screen.queryByText('Nombre:')).not.toBeInTheDocument()
  })

  it('sugiere los campos disponibles al escribir una barra', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/us')

    const suggestion = screen.getByRole('button', { name: /Nombre/ })
    await userEvent.click(suggestion)

    expect(searchBox()).toHaveValue('/user_name ')
  })

  it('un prefijo que no es un campo queda como texto, sin romper la búsqueda', async () => {
    render(<Harness />)

    await userEvent.type(searchBox(), '/nickname juan{Enter}')

    expect(screen.getByRole('status')).toHaveTextContent('/nickname juan')
    expect(screen.queryByText('Discord:')).not.toBeInTheDocument()
  })
})
