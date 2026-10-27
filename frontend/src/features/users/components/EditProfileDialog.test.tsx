import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { queryClient } from '@/config/query'
import { acceptReview } from '@/test/review'
import { ApiError } from '@/types/api'

import { EditProfileDialog } from './EditProfileDialog'

const updateProfile = vi.hoisted(() => vi.fn())
const toastError = vi.hoisted(() => vi.fn())

vi.mock('../api/usersApi', () => ({ usersApi: { updateProfile } }))
vi.mock('sonner', () => ({ toast: { error: toastError, success: vi.fn() } }))

const USER = { id: 'user-1', name: 'Ana', country: 'AR', needsOnboarding: false }

/** The application's own client, so the real global error handler is in the loop. */
function renderDialog(onOpenChange = vi.fn()) {
  queryClient.clear()
  render(
    <QueryClientProvider client={queryClient}>
      <ConfirmDialogProvider>
        <EditProfileDialog profile={{ name: 'Ana', country: 'AR' }} open onOpenChange={onOpenChange} />
      </ConfirmDialogProvider>
    </QueryClientProvider>,
  )
  return onOpenChange
}

beforeEach(() => {
  updateProfile.mockReset()
  toastError.mockReset()
})

describe('EditProfileDialog', () => {
  it('starts from the current name and sends the edit to the profile endpoint after the review', async () => {
    updateProfile.mockResolvedValue({ ...USER, name: 'Ana Valdez' })
    const onOpenChange = renderDialog()

    const name = screen.getByLabelText('Nombre a mostrar')
    expect(name).toHaveValue('Ana')
    await userEvent.clear(name)
    await userEvent.type(name, 'Ana Valdez')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await acceptReview()

    await waitFor(() => expect(updateProfile).toHaveBeenCalled())
    expect(updateProfile.mock.calls[0]?.[0]).toEqual({ name: 'Ana Valdez', country: 'AR' })
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it('refuses an empty name without calling the server', async () => {
    renderDialog()

    await userEvent.clear(screen.getByLabelText('Nombre a mostrar'))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(screen.getByText('Esto es obligatorio')).toBeInTheDocument())
    expect(updateProfile).not.toHaveBeenCalled()
  })

  /** ADR-10 §4: the two doors stay distinct, and the refusal says which one was the wrong door. */
  it('tells the reader what the server refused with, from its code', async () => {
    updateProfile.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', status: 409, detail: 'Onboarding still pending', errorCode: 'ONBOARDING_PENDING' }),
    )
    const onOpenChange = renderDialog()

    await userEvent.type(screen.getByLabelText('Nombre a mostrar'), ' V')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await acceptReview()

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Primero completá el paso inicial para poder editar tu perfil.'))
    expect(onOpenChange).not.toHaveBeenCalledWith(false)
  })
})
