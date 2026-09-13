import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { LANGUAGE_STORAGE_KEY } from '@/config/language'
import i18n from '@/providers/i18n'
import { UserMenu } from './UserMenu'

const setTheme = vi.fn()
let resolvedTheme: string | undefined = 'dark'
/** Whether this account holds `Player` or `Master`, which is what decides the library entry (#241). */
let hasPersonalLibrary = true

vi.mock('next-themes', () => ({
  useTheme: () => ({ resolvedTheme, setTheme }),
}))

vi.mock('@/features/auth', () => ({
  useLogout: () => ({ mutate: vi.fn() }),
}))

vi.mock('@/providers/AuthProvider', () => ({
  useAuth: () => ({ signOut: vi.fn() }),
}))

// Whose roles decide the library entry; the profile query behind it is not what is under test here.
vi.mock('@/hooks/useHasPersonalLibrary', () => ({
  useHasPersonalLibrary: () => ({ hasPersonalLibrary, isPending: false }),
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
    hasPersonalLibrary = true
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

  /**
   * #241: a personal library is filled by the player and master flows, so an account holding neither
   * role — a pure admin, the owner — has none, and an entry leading to a screen that can do nothing
   * is worse than no entry.
   */
  it('offers the personal library to a player or a master', async () => {
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))

    expect(screen.getByRole('menuitem', { name: 'Mis archivos' })).toBeInTheDocument()
  })

  it('does not offer it to an account that is neither', async () => {
    hasPersonalLibrary = false
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))

    expect(screen.queryByRole('menuitem', { name: 'Mis archivos' })).not.toBeInTheDocument()
  })

  /** The week is not the library: it is transversal to everybody, and stays where it was (#227). */
  it('keeps offering the reader their own week either way', async () => {
    hasPersonalLibrary = false
    renderMenu()

    await userEvent.click(screen.getByRole('button', { name: 'Ana Valdez' }))

    expect(screen.getByRole('menuitem', { name: 'Mi horario' })).toBeInTheDocument()
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
