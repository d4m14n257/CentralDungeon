import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'

import { MasterTableDetailPage } from './MasterTableDetailPage'

const managedById = vi.hoisted(() => vi.fn())
const me = vi.hoisted(() => vi.fn())

vi.mock('@/features/tables/api/gameTablesApi', () => ({ gameTablesApi: { managedById }, tableTypesApi: { list: vi.fn() } }))
vi.mock('@/features/users/api/usersApi', () => ({ usersApi: { me } }))

function failure(status: number) {
  return new ApiError(status, { title: 'Error', status, detail: 'detail', errorCode: 'ERROR' })
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={['/master/tables/t1']}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/master/tables/:id" element={<MasterTableDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  managedById.mockReset()
  me.mockReset()
  me.mockResolvedValue({ id: 'me', name: 'Yo', country: 'AR', needsOnboarding: false, roles: ['Master'], hasManagedTables: true })
})

describe('MasterTableDetailPage', () => {
  it('says a table that does not exist does not exist, without a retry', async () => {
    managedById.mockRejectedValue(failure(404))

    renderPage()

    expect(await screen.findByText('No existe')).toBeInTheDocument()
    expect(screen.queryByText('Algo salió mal')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('offers a retry when the failure is not a verdict', async () => {
    managedById.mockRejectedValue(failure(500))

    renderPage()

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
