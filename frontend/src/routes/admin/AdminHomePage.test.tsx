import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'

import { AdminHomePage } from './AdminHomePage'

const list = vi.hoisted(() => vi.fn())

vi.mock('@/features/adminQueue/api/adminQueueApi', () => ({ adminQueueApi: { list } }))

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminHomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function page(totalElements: number) {
  return { content: [], page: 0, size: 1, totalElements, totalPages: totalElements }
}

describe('AdminHomePage', () => {
  beforeEach(() => list.mockReset())

  it('links the tray with how many items wait, asking for a page of one', async () => {
    list.mockResolvedValue(page(3))
    renderHome()

    expect(await screen.findByText('3 ítems esperan una acción')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Bandeja/ })).toHaveAttribute('href', '/admin/queue')
    expect(list).toHaveBeenCalledWith(0, 1)
  })

  it('says nothing is waiting when the tray is empty, and still links it', async () => {
    list.mockResolvedValue(page(0))
    renderHome()

    expect(await screen.findByText('No hay nada esperando')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Bandeja/ })).toHaveAttribute('href', '/admin/queue')
  })
})
