import { QueryClient } from '@tanstack/react-query'

/** Per-data staleTime policy (arquitectura.md 3.3) - the default of 0 is what causes request goteo. */
export const staleTime = {
  catalogs: 60 * 60 * 1000,
  tableList: 30_000,
  tableDetail: 60_000,
  notifications: Infinity,
  profile: 5 * 60 * 1000,
} as const

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: staleTime.tableList,
      retry: 1,
    },
  },
})
