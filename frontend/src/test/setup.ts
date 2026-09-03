import '@testing-library/jest-dom/vitest'

import { DEFAULT_LANGUAGE } from '@/config/language'
import i18n from '@/providers/i18n'

/**
 * Every test renders in Spanish, whatever the machine says.
 *
 * Without this the language would come from `navigator.languages` (#198), which in jsdom is
 * `en-US` and on a developer's own machine is whatever they set — so a test asserting on a label
 * would pass or fail depending on who ran it. Pinning it makes the assertions about behaviour
 * rather than about the environment; the language switch itself is covered by its own tests.
 */
void i18n.changeLanguage(DEFAULT_LANGUAGE)
