import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider, useConfirm } from './ConfirmDialog'

function AskAndReport() {
  const confirm = useConfirm()
  return (
    <button
      onClick={() => {
        void confirm({ title: 'Aceptar a Jugador', description: '¿Confirmás?' }).then((confirmed) => {
          document.title = confirmed ? 'confirmed' : 'cancelled'
        })
      }}
    >
      Ask
    </button>
  )
}

describe('ConfirmDialogProvider / useConfirm', () => {
  it('shows the title and description passed to confirm()', async () => {
    render(
      <ConfirmDialogProvider>
        <AskAndReport />
      </ConfirmDialogProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))

    expect(screen.getByText('Aceptar a Jugador')).toBeInTheDocument()
    expect(screen.getByText('¿Confirmás?')).toBeInTheDocument()
  })

  it('resolves true when the confirm button is clicked', async () => {
    render(
      <ConfirmDialogProvider>
        <AskAndReport />
      </ConfirmDialogProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(document.title).toBe('confirmed'))
    expect(screen.queryByText('¿Confirmás?')).not.toBeInTheDocument()
  })

  it('resolves false when the cancel button is clicked', async () => {
    render(
      <ConfirmDialogProvider>
        <AskAndReport />
      </ConfirmDialogProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(document.title).toBe('cancelled'))
  })

  it('throws when useConfirm is called outside the provider', () => {
    function Unwrapped() {
      useConfirm()
      return null
    }

    expect(() => render(<Unwrapped />)).toThrow('useConfirm must be used within ConfirmDialogProvider')
  })
})
