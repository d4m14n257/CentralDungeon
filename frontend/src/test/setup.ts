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

/**
 * jsdom implements no `ResizeObserver`, and Radix measures its triggers with one.
 *
 * Without this, rendering anything containing a `Select` throws on mount — which is a failure about
 * the environment and not about the component. A no-op is enough: nothing under test asserts on a
 * measured size, and the alternative is every test that happens to include a dropdown carrying its
 * own copy of this stub.
 */
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

/**
 * The rest of the DOM surface jsdom leaves out and Radix uses: pointer capture, which its dropdowns
 * call on the very first pointer event, and `scrollIntoView`, which they call to keep the highlighted
 * option in view.
 *
 * Same reasoning as above — these are gaps in the environment, not behaviour worth asserting on, and
 * leaving them out makes every test that happens to contain a dropdown fail for a reason that has
 * nothing to do with what it is testing.
 */
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.setPointerCapture ??= () => {}
Element.prototype.releasePointerCapture ??= () => {}
Element.prototype.scrollIntoView ??= () => {}
