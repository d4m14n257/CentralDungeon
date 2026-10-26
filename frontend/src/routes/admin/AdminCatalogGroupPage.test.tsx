import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { AdminCatalogGroupPage } from './AdminCatalogGroupPage'

const group = vi.hoisted(() => vi.fn())

vi.mock('@/features/catalogs/api/catalogsApi', () => ({
  catalogsApi: {
    group,
    list: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    pending: vi.fn(),
  },
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={['/admin/catalogs/systems/missing']}>
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>
          <Routes>
            <Route path="/admin/catalogs/:kind/:id" element={<AdminCatalogGroupPage />} />
          </Routes>
        </ConfirmDialogProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('AdminCatalogGroupPage', () => {
  /** A group that cannot be read is the whole answer: no canvas, and no tray offering to open more groups. */
  it('says a group that does not exist does not exist, without a retry or a tray', async () => {
    group.mockRejectedValue(new ApiError(404, { title: 'Not found', status: 404, detail: 'nope', errorCode: 'NOT_FOUND' }))

    renderPage()

    expect(await screen.findByText('No existe')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    expect(screen.queryByText('Traer otro grupo')).not.toBeInTheDocument()
  })
})
