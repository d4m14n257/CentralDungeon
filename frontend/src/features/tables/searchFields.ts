import type { TFunction } from 'i18next'

import type { SearchChoice, SearchField } from '@/lib/searchQuery'

import { ALL_TABLE_STATUSES } from './lifecycle'

/**
 * The commands the explorer's search box accepts (#164, #239, #240, #246).
 *
 * **This is the box #164 was designed for.** The search language was specified thinking of `/tag`,
 * and until F2.1 the command existed in no screen — the explorer was a paged list of everything
 * open, which stops being a listing somewhere around twenty tables.
 *
 * **All four are the free-text kind**, `examples` and not `values` (#246). `/file_type` declares its
 * choices because there are four MIME types and nobody types them; a catalog is the opposite in both
 * halves — its values are hundreds, they grow whenever a master proposes one (#55), and they are
 * exactly the words people already use. Offering a list would mean querying the catalog while
 * somebody types, which is a second search box inside the search box.
 *
 * **And spelling it differently still works**, which is what a list of options would have been for:
 * the backend resolves the whole synonym group, so a table tagged `DANDD` comes back for `D&D` and
 * the other way round (#54, #56).
 *
 * The examples are real values of `V3__catalog_seed.sql`, deliberately: the first thing somebody
 * tries out of the help has to return tables, or the command reads as broken.
 *
 * @param t the translator of the `tables` namespace
 * @returns the commands, in the order they are offered
 */
export function explorerSearchFields(t: TFunction): SearchField[] {
  return [
    { name: 'table_name', label: t('search.table_name'), examples: ['strahd', 'waterdeep'] },
    { name: 'table_system', label: t('search.table_system'), examples: ['D&D', 'Pathfinder 2e'] },
    { name: 'table_tag', label: t('search.table_tag'), examples: ['Principiantes', 'One-shot'] },
    { name: 'table_platform', label: t('search.table_platform'), examples: ['Roll20', 'Discord'] },
  ]
}

/**
 * What `/table_status` offers: every state a table can be in (#245).
 *
 * **A command with fixed choices and not free text**, the same distinction `/role`, `/file_type` and
 * `/status` make: there are ten of them, they are a closed set nobody grows by proposing one, and a
 * box that offers them is a box nobody has to guess the spelling of. That is the exact opposite of
 * `/table_system` and `/table_tag` next to it, whose values are catalogs — hundreds of them, growing
 * whenever a master proposes one (#55) — which is why those two declare `examples` instead (#246).
 *
 * The `value` is what the backend matches against `game_tables.status`; the `label` is what is typed
 * and read, so it is translated — and it is translated from the `tables` namespace, where the ten
 * labels already live, rather than copied into `admin` (#176: one place per thing).
 *
 * @param t the translator of the `tables` namespace
 * @returns the choices, in the order of a table's life
 */
export function tableStatusChoices(t: TFunction): SearchChoice[] {
  return ALL_TABLE_STATUSES.map((status) => ({ value: status, label: t(`status.${status}`) }))
}

/**
 * The commands `/admin/tables` accepts (#164, #240, #176).
 *
 * **Six and not four**: the explorer's four, which mean exactly the same thing here, plus the two
 * that only make sense once the screen shows every table there is rather than the handful waiting on
 * a review. A listing of everything with no way to narrow it is a listing nobody can use, which is
 * why the search arrived in the same slice as the "everything".
 *
 * `/table_master` is **by name, never by id**, like every other person command on the platform: an
 * admin looking for somebody's tables knows what they are called, not their UUID. It matches a
 * table's living masters, the primary and the co-masters alike — the question is "who runs this",
 * and the distinction between the two is not one the person asking is making.
 *
 * **A separate list from {@link explorerSearchFields}, which belongs to the player's explorer.** The
 * two share four commands and there the resemblance ends: the explorer only ever shows a table
 * somebody could still join, so `/table_status` there would offer ten values to narrow between two,
 * and it has `notMasteredBy` semantics this screen must not inherit. Folding them into one list would
 * put a command into a box where it can barely mean anything.
 *
 * An unknown value in `/table_status` is **not** an error on either side: it simply matches nothing,
 * the same way a mistyped `/command` stays literal text (arquitectura.md §2.5).
 *
 * @param t       the translator of the `admin` namespace, for the command labels
 * @param tTables the translator of the `tables` namespace, for the ten status labels it already owns
 * @returns the commands, in the order they are offered
 */
export function adminTableSearchFields(t: TFunction, tTables: TFunction): SearchField[] {
  return [
    { name: 'table_name', label: t('tables.search.table_name'), examples: ['strahd', 'waterdeep'] },
    { name: 'table_status', label: t('tables.search.table_status'), values: tableStatusChoices(tTables) },
    { name: 'table_master', label: t('tables.search.table_master'), examples: ['damian', 'carlos'] },
    { name: 'table_system', label: t('tables.search.table_system'), examples: ['D&D', 'Pathfinder 2e'] },
    { name: 'table_tag', label: t('tables.search.table_tag'), examples: ['Principiantes', 'One-shot'] },
    { name: 'table_platform', label: t('tables.search.table_platform'), examples: ['Roll20', 'Discord'] },
  ]
}
