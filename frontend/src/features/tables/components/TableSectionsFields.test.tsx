import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm } from 'react-hook-form'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { Form } from '@/components/ui/form'

import { TableSectionsFields } from './TableSectionsFields'
import type { CreateGameTableForm } from '../schemas'
import { sectionsToRequest } from '../lib/tableSections'

let latest: Partial<CreateGameTableForm> = {}

function Harness({ initial }: { initial: Partial<CreateGameTableForm> }) {
  const form = useForm<CreateGameTableForm>({
    defaultValues: { name: '', characterCreation: '', whatToExpect: '', equipment: '', safety: '', ...initial },
  })
  latest = form.watch()
  return (
    <Form {...form}>
      <form>
        <TableSectionsFields />
      </form>
    </Form>
  )
}

describe('TableSectionsFields', () => {
  /** Nothing is written yet: the four sections are offered as buttons and no editor is open. */
  it('offers the four sections as buttons when none is written', () => {
    render(<Harness initial={{}} />)

    for (const label of ['Creación de personaje', 'Qué esperar', 'Equipo', 'Seguridad y content warnings']) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument()
    }
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  /** Pressing a section's button opens its editor, with the hint, and takes the button away. */
  it('opens the editor of a section when its button is pressed', async () => {
    render(<Harness initial={{}} />)

    await userEvent.click(screen.getByRole('button', { name: 'Seguridad y content warnings' }))

    expect(screen.getByRole('textbox', { name: 'Seguridad y content warnings' })).toBeInTheDocument()
    expect(screen.getByText(/márcalos también con las etiquetas de seguridad/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Seguridad y content warnings' })).not.toBeInTheDocument()
  })

  /** A table loaded with a section shows it open, and removing it empties it so it is sent as not written. */
  it('shows a written section open, and removing it empties it', async () => {
    render(<Harness initial={{ equipment: '<p>Dados</p>' }} />)

    expect(screen.getByRole('textbox', { name: 'Equipo' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Quitar' }))

    expect(screen.queryByRole('textbox', { name: 'Equipo' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Equipo' })).toBeInTheDocument()
    expect(sectionsToRequest(latest).equipment).toBeNull()
  })
})

describe('sectionsToRequest', () => {
  /** An empty editor is a section not written: it travels as null, never as an empty string. */
  it('sends an empty section as null and a written one as it is', () => {
    expect(sectionsToRequest({ characterCreation: '<p>Nivel 3</p>', whatToExpect: '', safety: undefined })).toEqual({
      characterCreation: '<p>Nivel 3</p>',
      whatToExpect: null,
      equipment: null,
      safety: null,
    })
  })
})
