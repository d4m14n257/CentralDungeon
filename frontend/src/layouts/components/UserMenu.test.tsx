import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { UserMenu } from './UserMenu'

const setTheme = vi.fn()
let resolvedTheme: string | undefined = 'dark'

vi.mock('next-themes', () => ({
  useTheme: () => ({ resolvedTheme, setTheme }),
}))

vi.mock('@/features/auth', () => ({
  useLogout: () => ({ mutate: vi.fn() }),
}))

vi.mock('@/providers/AuthProvider', () => ({
  useAuth: () => ({ signOut: vi.fn() }),
}))

function renderMenu(displayName: string | null = 'Ana Valdez') {
  return render(
    <MemoryRouter>
      <UserMenu displayName={displayName} />
    </MemoryRouter>,
  )
}

describe('UserMenu', () => {
  beforeEach(() => {
    setTheme.mockClear()
    resolvedTheme = 'dark'
  })

  it('offers the light theme while the dark one is active', async () => {
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Tema claro' }))

    expect(setTheme).toHaveBeenCalledWith('light')
  })

  it('offers the dark theme while the light one is active', async () => {
    resolvedTheme = 'light'
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'Tema oscuro' }))

    expect(setTheme).toHaveBeenCalledWith('dark')
  })

  it('falls back to a generic label before onboarding sets a display name', async () => {
    renderMenu(null)

    // El avatar no puede quedar vacío ni mostrar un signo de pregunta como nombre.
    expect(screen.getByRole('button', { name: 'Mi cuenta' })).toBeInTheDocument()
  })

  it('builds the avatar initials from the display name', () => {
    renderMenu()

    expect(screen.getByText('AV')).toBeInTheDocument()
  })
})
