import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/types/api'

import { useAvailableContexts } from './useAvailableContexts'

const me = vi.hoisted(() => vi.fn())

vi.mock('@/features/users/api/usersApi', () => ({ usersApi: { me } }))

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

describe('useAvailableContexts', () => {
  /** An empty `contexts` after a failed read is not an account without sections. */
  it('reports a failed read of the roles instead of an account with no contexts', async () => {
    me.mockRejectedValue(new ApiError(500, { title: 'Error', status: 500, detail: 'boom', errorCode: 'INTERNAL' }))

    const { result } = renderHook(() => useAvailableContexts(), { wrapper })

    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.isError).toBe(true)
    expect(result.current.contexts).toEqual([])
  })
})
