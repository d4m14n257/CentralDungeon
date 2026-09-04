import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { TaskFormDialog } from './TaskFormDialog'

const players = [
  { userId: 'player-1', userName: 'Ana' },
  { userId: 'player-2', userName: 'Bruno' },
]

function renderForm(onSubmit = vi.fn()) {
  render(<TaskFormDialog open onOpenChange={vi.fn()} players={players} sessions={[]} isBusy={false} onSubmit={onSubmit} />)
  return onSubmit
}

describe('TaskFormDialog', () => {
  it('says publishing will notify, because that is what it does (#77)', () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'Publicar y avisar' })).toBeInTheDocument()
    expect(screen.getByText('Al publicarla les llega un aviso a las personas a las que va dirigida.')).toBeInTheDocument()
  })

  /**
   * A task that takes neither text nor files asks for something nobody can hand in. The backend
   * refuses it too; this is so the person finds out while typing rather than after sending.
   */
  it('refuses a request that accepts neither text nor files', async () => {
    const onSubmit = renderForm()

    await userEvent.click(screen.getByLabelText('Con texto'))
    await userEvent.click(screen.getByLabelText('Con archivos'))
    await userEvent.type(screen.getByLabelText('Qué pedís'), 'Ficha')
    await userEvent.click(screen.getByRole('button', { name: 'Publicar y avisar' }))

    expect(await screen.findByText('Elegí al menos una forma de responder.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  /** An audience of one that names nobody addresses nobody, so the picker is required with it. */
  it('requires a recipient when the request is addressed to one person', async () => {
    const onSubmit = renderForm()

    await userEvent.type(screen.getByLabelText('Qué pedís'), 'Tu material')
    await userEvent.click(screen.getByLabelText('A quién se lo pedís'))
    await userEvent.click(screen.getByRole('option', { name: 'Para una persona' }))
    await userEvent.click(screen.getByRole('button', { name: 'Publicar y avisar' }))

    expect(await screen.findByText('Elegí a la persona a la que se lo pedís.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  /** Only the table's own players are offered: addressing somebody else lands in an unreachable inbox. */
  it('offers only the table players as recipients', async () => {
    renderForm()

    await userEvent.click(screen.getByLabelText('A quién se lo pedís'))
    await userEvent.click(screen.getByRole('option', { name: 'Para una persona' }))
    await userEvent.click(screen.getByLabelText('A quién'))

    expect(screen.getByRole('option', { name: 'Ana' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Bruno' })).toBeInTheDocument()
  })

  it('publishes a well-formed request with what was filled in', async () => {
    const onSubmit = renderForm()

    await userEvent.type(screen.getByLabelText('Qué pedís'), 'Ficha de personaje')
    await userEvent.click(screen.getByRole('button', { name: 'Publicar y avisar' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Ficha de personaje',
          audience: 'Players',
          targetUserId: null,
          acceptsText: true,
          acceptsFiles: true,
          isMandatory: false,
          dueAt: null,
        }),
      ),
    )
  })

  /** #70 stated where a master decides: the label is a label and the copy says so. */
  it('says that marking a request as important blocks nothing', () => {
    renderForm()

    expect(screen.getByText('Es solo una etiqueta. No bloquea nada ni saca a nadie de la mesa.')).toBeInTheDocument()
  })
})
