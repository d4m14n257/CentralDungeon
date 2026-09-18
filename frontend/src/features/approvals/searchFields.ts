import type { TFunction } from 'i18next'

import type { SearchChoice, SearchField } from '@/lib/searchQuery'

import { APPROVAL_REQUEST_TYPES, APPROVAL_STATUSES } from './requestTypes'

/**
 * What `/request_type` offers: the three kinds of request there are.
 *
 * **A command with fixed choices and not free text**, the same distinction `/role` and `/file_type`
 * make: there are three of them, they are a closed set, and a box that offers them is a box nobody
 * has to guess at. The `value` is what the backend matches against `request_type`; the `label` is
 * what is typed and read, so it is translated.
 *
 * @param t the translator of the `admin` namespace
 * @returns the choices, in the order they are offered
 */
export function requestTypeChoices(t: TFunction): SearchChoice[] {
  return APPROVAL_REQUEST_TYPES.map((type) => ({ value: type, label: t(`requests.types.${type}`) }))
}

/**
 * What `/status` offers: the three states a request can be in.
 *
 * @param t the translator of the `admin` namespace
 * @returns the choices, in the order they are offered
 */
export function requestStatusChoices(t: TFunction): SearchChoice[] {
  return APPROVAL_STATUSES.map((status) => ({ value: status, label: t(`requests.status.${status}`) }))
}

/**
 * The commands `/admin/requests` accepts (#164, #240).
 *
 * **Written here and not in the component that renders the box**, the same way users and files
 * declare theirs: the list is what the box offers, what the help documents and what a `?q=` is read
 * against, and three copies of it free to disagree is exactly the drift #240 went to remove.
 *
 * `/requested_by` is free text — a person's name is not something anybody could offer a list of —
 * and it searches the two names the platform keeps, the display one and the Discord one, because
 * whoever is looking knows one of them and not which of the two the system keeps where (#164).
 *
 * Without a command in front of it, what is typed searches the justification **and** the name of
 * whoever asked: the two things an admin reads a row for.
 *
 * An unknown value in `/request_type` or `/status` is **not** an error on either side: it simply
 * matches nothing, the same way a mistyped `/command` stays literal text (arquitectura.md §2.5).
 *
 * @param t the translator of the `admin` namespace
 * @returns the commands, in the order they are offered
 */
export function approvalRequestSearchFields(t: TFunction): SearchField[] {
  return [
    { name: 'request_type', label: t('requests.search.type'), values: requestTypeChoices(t) },
    { name: 'status', label: t('requests.search.status'), values: requestStatusChoices(t) },
    { name: 'requested_by', label: t('requests.search.requestedBy'), examples: ['dami', 'damian', 'carlos'] },
  ]
}
