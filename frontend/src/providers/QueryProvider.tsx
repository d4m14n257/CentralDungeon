import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

import { queryClient } from '@/config/query'

/**
 * Mounts the single QueryClient of `config/query.ts` over the app. Server data goes through TanStack
 * Query and nowhere else - no `useEffect` + `fetch`, no API response in Context or Zustand
 * (regla dura 11).
 *
 * @param props.children the application tree
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
