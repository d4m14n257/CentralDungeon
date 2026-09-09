import type { TFunction } from 'i18next'

/**
 * How a table type is read on screen (#225).
 *
 * The two types the application ships carry a `code`, so their label comes from the reader's
 * language file — the backend never writes the sentence (#197, regla dura 18). A type an admin
 * creates carries no code, and then its `name` is shown exactly as they typed it, the same way
 * `systems`, `tags` and `platforms` already work: what a person wrote is theirs.
 *
 * The API's `name` doubles as the fallback, so a code that arrives before its translation does
 * still reads as words instead of as `tableTypes.SOMETHING.name`.
 *
 * @param t the `tables` namespace's translator
 * @param code the type's code, or null when a person named it
 * @param name the label the API sent
 * @returns what to show, or null when the table has no type at all
 */
export function tableTypeLabel(t: TFunction<'tables'>, code: string | null, name: string | null): string | null {
  if (!code) {
    return name
  }
  return t(`tableTypes.${code}.name`, { defaultValue: name ?? code })
}

/**
 * What the type means, for the one place that explains it: the wizard's selector. "Public" alone
 * does not say what it implies.
 *
 * Follows the same rule as the label — translated when the application shipped the type, verbatim
 * when a person wrote it.
 *
 * @param t the `tables` namespace's translator
 * @param code the type's code, or null when a person named it
 * @param description the text the API sent, or null when the row never got one
 * @returns the description to show, or null when there is none
 */
export function tableTypeDescription(t: TFunction<'tables'>, code: string | null, description: string | null): string | null {
  if (!code) {
    return description
  }
  const translated = t(`tableTypes.${code}.description`, { defaultValue: description ?? '' })
  return translated === '' ? null : translated
}
