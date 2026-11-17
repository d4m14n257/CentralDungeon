import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'
import { useUnblockPlayer } from './useUnblockPlayer'

vi.mock('./registrationsApi', () => ({ registrationsApi: { unblock: vi.fn().mockResolvedValue(undefined) } }))

describe('useUnblockPlayer', () => {
  /** Lifting a veto gives one reader the table back, in every flavour of its detail and in the admin listing. */
  it('marks every view of the table stale, including the master detail and the admin listing', async () => {
    const queryClient = new QueryClient()
    const keys = [
      queryKeys.tables.detail('t1'),
      queryKeys.tables.managedDetail('t1'),
      queryKeys.tables.adminAll(),
      queryKeys.tables.lists(),
      queryKeys.registrations.players('t1'),
    ]
    keys.forEach((key) => queryClient.setQueryData(key, {}))
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>

    const { result } = renderHook(() => useUnblockPlayer('t1'), { wrapper })
    await act(async () => result.current.mutate({ registrationId: 'r1', justification: 'Settled' }))

    await waitFor(() => expect(registrationsApi.unblock).toHaveBeenCalled())
    await waitFor(() => keys.forEach((key) => expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true)))
  })
})
