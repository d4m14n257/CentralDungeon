import type { TFunction } from 'i18next'

import type { SearchField } from '@/lib/searchQuery'

/**
 * The commands the people search accepts (#164, #240).
 *
 * **Written here and not in the component that renders the box**, the same way files declare theirs
 * in `features/files/searchFields.ts`: the list is what the box offers, what the help documents and
 * what a `?q=` is read against, and three copies of it free to disagree is exactly the drift #240
 * went to remove.
 *
 * Both are free text: a person's name is not something anybody could offer a list of. The examples
 * are what the help shows — plausible values, and enough of them to demonstrate the commas.
 *
 * The basic criterion, with no command in front of it, is **the two at once**: whoever is searching
 * knows one of the two names, not which of them the system keeps where (#164).
 *
 * @param t the translator of the `users` namespace
 * @returns the commands, in the order they are offered
 */
export function userSearchFields(t: TFunction): SearchField[] {
  return [
    { name: 'discord_name', label: t('search.discordName'), examples: ['dami', 'pablo'] },
    { name: 'user_name', label: t('search.userName'), examples: ['damian', 'carlos', 'daniel'] },
  ]
}
