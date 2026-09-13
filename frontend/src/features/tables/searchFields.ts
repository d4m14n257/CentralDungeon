import type { TFunction } from 'i18next'

import type { SearchField } from '@/lib/searchQuery'

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
