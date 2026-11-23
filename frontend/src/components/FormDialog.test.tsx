import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmContext, ConfirmOpenContext } from '@/hooks/useConfirm'
import { FormDialog } from './FormDialog'

function renderForm({ confirmOpen, isDirty = false }: { confirmOpen: boolean; isDirty?: boolean }) {
  const onOpenChange = vi.fn()
  const confirm = vi.fn().mockResolvedValue(true)
  render(
    <ConfirmContext value={confirm}>
      <ConfirmOpenContext value={confirmOpen}>
        <FormDialog open onOpenChange={onOpenChange} title="Editar perfil" isDirty={isDirty}>
          <input aria-label="Nombre" />
        </FormDialog>
      </ConfirmOpenContext>
    </ConfirmContext>,
  )
  return { onOpenChange, confirm }
}

describe('FormDialog', () => {
  it('closes on Escape when nothing is on top of it', async () => {
    const { onOpenChange } = renderForm({ confirmOpen: false })

    await userEvent.keyboard('{Escape}')

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  // The confirmation lives outside the form's tree, so a click on its overlay can reach the form as a
  // dismissal. While it is up, that dismissal must neither close the form nor ask a second question.
  it('ignores a dismissal while a confirmation is open on top', async () => {
    const { onOpenChange, confirm } = renderForm({ confirmOpen: true, isDirty: true })

    await userEvent.keyboard('{Escape}')

    expect(onOpenChange).not.toHaveBeenCalled()
    expect(confirm).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: 'Editar perfil' })).toBeInTheDocument()
  })

  it('asks before closing a dirty form, and closes when the answer is to discard', async () => {
    const { onOpenChange, confirm } = renderForm({ confirmOpen: false, isDirty: true })

    await userEvent.keyboard('{Escape}')

    expect(confirm).toHaveBeenCalledOnce()
    await vi.waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })
})
