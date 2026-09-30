import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { PublishCategoriesField } from './PublishCategoriesField'

describe('PublishCategoriesField', () => {
  /**
   * The library speaks of three cajones, not five (#233, #278): the player-side ones hold what each
   * person answered with, and nothing is ever published into them.
   */
  it('offers only the cajones a file can be published into', () => {
    render(<PublishCategoriesField value={[]} onChange={vi.fn()} />)

    expect(screen.getAllByRole('checkbox')).toHaveLength(3)
    expect(screen.getByRole('checkbox', { name: 'De mesa' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Petición del master' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Anuncios de la comunidad' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Solicitud de jugador' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Entrega del jugador' })).not.toBeInTheDocument()
  })

  /** Nothing is preselected: a default would publish into a flow nobody chose (M24.1). */
  it('starts with nothing chosen', () => {
    render(<PublishCategoriesField value={[]} onChange={vi.fn()} />)

    for (const checkbox of screen.getAllByRole('checkbox')) {
      expect(checkbox).not.toBeChecked()
    }
  })

  /** Several at once is the case the relation exists for: one blank, two flows (#233). */
  it('adds to the selection and takes out of it', async () => {
    const onChange = vi.fn()
    const { rerender } = render(<PublishCategoriesField value={['TableMaterial']} onChange={onChange} />)

    await userEvent.click(screen.getByRole('checkbox', { name: 'Petición del master' }))
    expect(onChange).toHaveBeenLastCalledWith(['TableMaterial', 'MasterRequest'])

    rerender(<PublishCategoriesField value={['TableMaterial', 'MasterRequest']} onChange={onChange} />)
    await userEvent.click(screen.getByRole('checkbox', { name: 'De mesa' }))
    expect(onChange).toHaveBeenLastCalledWith(['MasterRequest'])
  })
})
