import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ApiError } from '@/types/api'

import { MasterDashboardPage } from './MasterDashboardPage'

const get = vi.hoisted(() => vi.fn())

vi.mock('@/features/tables/api/gameTablesApi', () => ({
  masterDashboardApi: { get },
  gameTablesApi: {},
  tableTypesApi: { list: vi.fn() },
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <MasterDashboardPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('MasterDashboardPage', () => {
  /** The screen keeps its shape: the title is there while the tray loads and when it fails. */
  it('keeps its title while loading', () => {
    get.mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('keeps its title and offers a retry when the tray could not be read', async () => {
    get.mockRejectedValue(new ApiError(500, { title: 'Error', status: 500, detail: 'boom', errorCode: 'INTERNAL' }))

    renderPage()

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })
})
