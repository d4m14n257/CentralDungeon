import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { queryKeys } from '@/api/queryKeys'

import { approvalsApi } from './approvalsApi'
import { useApproveRequest } from './useApproveRequest'

vi.mock('./approvalsApi', () => ({ approvalsApi: { approve: vi.fn().mockResolvedValue({}) } }))

describe('useApproveRequest', () => {
  /** Approving a veto frees a seat and changes who sees the table: the master's detail and the admin listing go stale too. */
  it('marks the master detail and the admin listing of tables stale', async () => {
    const queryClient = new QueryClient()
    const keys = [
      queryKeys.tables.detail('t1'),
      queryKeys.tables.managedDetail('t1'),
      queryKeys.tables.managedDetail('t2'),
      queryKeys.tables.adminAll(),
    ]
    keys.forEach((key) => queryClient.setQueryData(key, {}))
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>

    const { result } = renderHook(() => useApproveRequest(), { wrapper })
    await act(async () => result.current.mutate({ requestId: 'req-1', input: { resolutionNote: 'Approved' } }))

    await waitFor(() => expect(approvalsApi.approve).toHaveBeenCalled())
    await waitFor(() => keys.forEach((key) => expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true)))
  })
})
