import { useTranslation } from 'react-i18next'

import { DEFAULT_LANGUAGE, isLanguage, LANGUAGE_STORAGE_KEY, type Language } from '@/config/language'

/** What {@link useLanguage} hands back: the language in use and the way to change it. */
export interface LanguageControl {
  /** The language currently being rendered. */
  language: Language
  /** Switches language, remembers the choice, and updates the document for assistive technology. */
  setLanguage: (next: Language) => void
}

/**
 * Reading and changing the application's language (#198) — the counterpart of `next-themes`'
 * `useTheme`, which the theme switch already uses.
 *
 * It changes the language and remembers the choice. Keeping `<html lang>` in step is deliberately
 * *not* done here but in `providers/i18n.ts`, which owns it: that has to hold on the screens this
 * hook never reaches, the login page above all.
 *
 * @returns the current language and the setter
 */
export function useLanguage(): LanguageControl {
  const { i18n } = useTranslation()
  const language = isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE

  function setLanguage(next: Language) {
    void i18n.changeLanguage(next)
    localStorage.setItem(LANGUAGE_STORAGE_KEY, next)
  }

  return { language, setLanguage }
}
