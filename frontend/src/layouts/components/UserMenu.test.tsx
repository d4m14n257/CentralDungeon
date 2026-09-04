import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { LANGUAGE_STORAGE_KEY } from '@/config/language'
import i18n from '@/providers/i18n'
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

    // The avatar cannot be left empty, nor show a question mark as a name.
    expect(screen.getByRole('button', { name: 'Mi cuenta' })).toBeInTheDocument()
  })

  it('builds the avatar initials from the display name', () => {
    renderMenu()

    expect(screen.getByText('AV')).toBeInTheDocument()
  })

  /**
   * #198: each language names itself. Somebody hunting for their own language does not necessarily
   * read the one currently on screen, so "English" is never offered as "Inglés".
   */
  it('names every language in itself, not in the one currently active', async () => {
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))

    expect(screen.getByRole('menuitem', { name: 'Español' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'English' })).toBeInTheDocument()
  })

  it('switches the language and remembers the choice', async () => {
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))
    await userEvent.click(screen.getByRole('menuitem', { name: 'English' }))

    expect(i18n.language).toBe('en')
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
    // The labels follow immediately, without a reload.
    expect(screen.getByRole('button', { name: 'Ana Valdez' })).toBeInTheDocument()

    await i18n.changeLanguage('es')
    localStorage.removeItem(LANGUAGE_STORAGE_KEY)
  })
})
