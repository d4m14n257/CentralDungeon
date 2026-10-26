import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { MasterTableCreatePage } from './MasterTableCreatePage'

const list = vi.hoisted(() => vi.fn())
const me = vi.hoisted(() => vi.fn())

vi.mock('@/features/tables/api/gameTablesApi', () => ({ gameTablesApi: {}, tableTypesApi: { list } }))
vi.mock('@/features/users/api/usersApi', () => ({ usersApi: { me } }))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // A data router: the wizard guards unsaved changes with `useBlocker`, which needs one.
  const router = createMemoryRouter([{ path: '/', element: <MasterTableCreatePage /> }])
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfirmDialogProvider>
        <RouterProvider router={router} />
      </ConfirmDialogProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  list.mockReset()
  me.mockReset()
  me.mockResolvedValue({ id: 'me', name: 'Yo', country: 'AR', needsOnboarding: false, roles: ['Master'], hasManagedTables: true })
})

describe('MasterTableCreatePage', () => {
  /** An empty select would read as «there are no table types»; a failed read has to say it failed. */
  it('says the table types could not be loaded, and retries', async () => {
    list.mockRejectedValueOnce(new ApiError(500, { title: 'Error', status: 500, detail: 'boom', errorCode: 'INTERNAL' }))
    list.mockResolvedValueOnce({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })

    renderPage()

    const retry = await screen.findByRole('button', { name: 'Reintentar' })
    await userEvent.click(retry)

    expect(list).toHaveBeenCalledTimes(2)
  })
})
