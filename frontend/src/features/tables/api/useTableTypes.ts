import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { tableTypesApi } from './gameTablesApi'

/**
 * The table types the wizard's selector offers.
 *
 * Read from the API rather than written into the code: `V2__seed.sql` ships two of them and admins
 * add the rest from the application (modelo-datos.md 6), so a hard-coded list would go stale the
 * first time somebody used the feature it belongs to.
 *
 * @returns the query, holding one page of every type
 */
export function useTableTypes() {
  return useQuery({
    queryKey: queryKeys.tables.types(),
    queryFn: () => tableTypesApi.list(),
  })
}
