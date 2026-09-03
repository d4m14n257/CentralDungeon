import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'

import { DEFAULT_LANGUAGE, LANGUAGE_STORAGE_KEY } from '@/config/language'
import i18n from '@/providers/i18n'

import { LanguageSwitch } from './LanguageSwitch'

describe('LanguageSwitch', () => {
  afterEach(async () => {
    await i18n.changeLanguage(DEFAULT_LANGUAGE)
    localStorage.removeItem(LANGUAGE_STORAGE_KEY)
  })

  /**
   * The whole reason this exists next to the login form: on a screen with no `UserMenu`, somebody
   * who cannot read what is on screen has to see that the language can be changed without opening
   * anything first (#198).
   */
  it('shows every language at once, without a menu to open', () => {
    render(<LanguageSwitch />)

    expect(screen.getByRole('button', { name: 'Español' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'English' })).toBeInTheDocument()
  })

  it('marks the active language as pressed, and only that one', () => {
    render(<LanguageSwitch />)

    expect(screen.getByRole('button', { name: 'Español' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'English' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('switches the language and remembers the choice', async () => {
    render(<LanguageSwitch />)

    await userEvent.click(screen.getByRole('button', { name: 'English' }))

    expect(i18n.language).toBe('en')
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
  })

  /** The group needs a name of its own: the globe is decorative and the buttons only say themselves. */
  it('names the group for assistive technology', () => {
    render(<LanguageSwitch />)

    expect(screen.getByRole('group', { name: 'Idioma' })).toBeInTheDocument()
  })
})
