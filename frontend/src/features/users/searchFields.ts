import type { TFunction } from 'i18next'

import type { SearchChoice, SearchField } from '@/lib/searchQuery'

import { ACCOUNT_STATUSES, PLATFORM_ROLES } from './roles'

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

/**
 * What `/role` offers: the four platform roles, in the order of #165.
 *
 * **A command with fixed choices and not free text**, the same distinction `/file_type` makes: there
 * are four of them, they are a closed set, and a box that offers them is a box nobody has to guess
 * at. The `value` is what the backend matches against `roles.name`; the `label` is what is typed and
 * read, so it is translated.
 *
 * @param t the translator of the `admin` namespace
 * @returns the choices, in the order they are offered
 */
export function roleChoices(t: TFunction): SearchChoice[] {
  return PLATFORM_ROLES.map((role) => ({ value: role, label: t(`users.roles.${role}`) }))
}

/**
 * What `/status` offers: the three states an account can be in.
 *
 * `Deleted` is offered even though F3.1 neither produces it nor undoes it — `/admin/users` is the one
 * screen that can see such an account, and a search that cannot name what it shows is a search with
 * a hole in it.
 *
 * @param t the translator of the `admin` namespace
 * @returns the choices, in the order they are offered
 */
export function accountStatusChoices(t: TFunction): SearchChoice[] {
  return ACCOUNT_STATUSES.map((status) => ({ value: status, label: t(`users.status.${status}`) }))
}

/**
 * The commands `/admin/users` accepts (#164, #240, F3.1).
 *
 * **A separate list from {@link userSearchFields}, which belongs to the picker.** The two search the
 * same two names, and there the resemblance ends: the picker answers "who did you mean" over allowed
 * accounts, while this screen administers every account there is, so it needs the two commands that
 * only make sense once blocked accounts and roles are visible. Folding them into one list would put
 * `/status` in a dialog where every result is `Allowed` by construction.
 *
 * An unknown value in `/role` or `/status` is **not** an error on either side: it simply matches
 * nothing, the same way a mistyped `/command` stays literal text (§2.5).
 *
 * @param t the translator of the `admin` namespace
 * @returns the commands, in the order they are offered
 */
export function adminUserSearchFields(t: TFunction): SearchField[] {
  return [
    { name: 'discord_name', label: t('users.search.discordName'), examples: ['dami', 'pablo'] },
    { name: 'user_name', label: t('users.search.userName'), examples: ['damian', 'carlos', 'daniel'] },
    { name: 'role', label: t('users.search.role'), values: roleChoices(t) },
    { name: 'status', label: t('users.search.status'), values: accountStatusChoices(t) },
  ]
}
