/**
 * Which language the application speaks, and how it gets decided (#198).
 *
 * The rule is the same shape as the theme's: a stored choice wins, and when there is none the
 * browser is asked. The difference is the fallback — the theme falls back to a design decision
 * (dark, #131) and this one falls back to Spanish, because the community writes and plays in
 * Spanish and an unrecognized browser is far more likely to belong to somebody who does.
 */

/** The languages the application ships. Adding one means adding its folder under `src/locales/`. */
export const LANGUAGES = ['es', 'en'] as const

/** A language the application can actually render. Narrower than any BCP-47 tag. */
export type Language = (typeof LANGUAGES)[number]

/** What the reader gets when nothing else can be worked out. */
export const DEFAULT_LANGUAGE: Language = 'es'

/** Where the choice is kept. `localStorage`, like the theme: it is a preference, not account data. */
export const LANGUAGE_STORAGE_KEY = 'centraldungeon.language'

/**
 * Whether a string is one of the languages the application ships.
 *
 * @param value anything that claims to be a language
 * @returns true when it is one this application can render
 */
export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value)
}

/**
 * The language to start in.
 *
 * <p>The stored choice first — somebody who picked English meant it, whatever their browser says.
 * Then the browser's own preferences, in the order it lists them, matched on the primary subtag so
 * `en-GB` and `en-US` both mean English. Anything else falls back to Spanish: "cannot be worked out"
 * covers both a missing setting and a language this application does not speak.
 *
 * @param storage    where a previous choice would have been kept; omitted in environments without one
 * @param preferred  the reader's languages, most wanted first — `navigator.languages` in a browser
 * @returns the language to render in
 */
export function resolveInitialLanguage(
  storage: Pick<Storage, 'getItem'> | undefined = typeof localStorage === 'undefined' ? undefined : localStorage,
  preferred: readonly string[] = typeof navigator === 'undefined' ? [] : navigator.languages,
): Language {
  const stored = storage?.getItem(LANGUAGE_STORAGE_KEY)
  if (isLanguage(stored)) {
    return stored
  }
  for (const tag of preferred) {
    // The primary subtag is what carries the language; the region only says which flavour of it.
    const primary = tag.toLowerCase().split('-')[0]
    if (isLanguage(primary)) {
      return primary
    }
  }
  return DEFAULT_LANGUAGE
}
