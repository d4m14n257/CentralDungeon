import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { endSession } from '@/api/client'
import { toast } from 'sonner'
import { SignOutButton } from './SignOutButton'

const signOut = vi.fn()

vi.mock('@/api/client', () => ({ endSession: vi.fn() }))
vi.mock('sonner', () => ({ toast: { error: vi.fn() } }))
vi.mock('@/providers/AuthProvider', () => ({ useAuth: () => ({ signOut }) }))

function renderButton() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <SignOutButton />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SignOutButton', () => {
  beforeEach(() => {
    vi.mocked(endSession).mockReset()
    vi.mocked(toast.error).mockReset()
    signOut.mockReset()
  })

  it('signs out without a notice when the server ends the session', async () => {
    vi.mocked(endSession).mockResolvedValue(undefined)
    renderButton()

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(signOut).toHaveBeenCalled())
    expect(endSession).toHaveBeenCalledTimes(1)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('retries once and stays quiet when the second call works', async () => {
    vi.mocked(endSession).mockRejectedValueOnce(new Error('down')).mockResolvedValue(undefined)
    renderButton()

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(signOut).toHaveBeenCalled())
    expect(endSession).toHaveBeenCalledTimes(2)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('clears the local session and warns when both calls fail', async () => {
    vi.mocked(endSession).mockRejectedValue(new Error('down'))
    renderButton()

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(signOut).toHaveBeenCalled())
    expect(endSession).toHaveBeenCalledTimes(2)
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('No pudimos cerrar tu sesión'))
  })
})
