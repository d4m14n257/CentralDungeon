import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { queryKeys } from '@/api/queryKeys'

import { registrationsApi } from './registrationsApi'
import { useAcceptRegistration } from './useAcceptRegistration'

vi.mock('./registrationsApi', () => ({ registrationsApi: { accept: vi.fn().mockResolvedValue({}) } }))

describe('useAcceptRegistration', () => {
  /** Taking the last seat turns the call for players off, and the master's view and the admin's listing must show it. */
  it("marks the master's detail and the admin listing stale along with the player's detail", async () => {
    const queryClient = new QueryClient()
    const keys = [
      queryKeys.tables.detail('t1'),
      queryKeys.tables.managedDetail('t1'),
      queryKeys.tables.adminAll(),
      queryKeys.registrations.candidates('t1'),
    ]
    keys.forEach((key) => queryClient.setQueryData(key, {}))
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>

    const { result } = renderHook(() => useAcceptRegistration('t1'), { wrapper })
    await act(async () => result.current.mutate('r1'))

    await waitFor(() => expect(registrationsApi.accept).toHaveBeenCalledWith('r1'))
    await waitFor(() => keys.forEach((key) => expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true)))
  })
})
