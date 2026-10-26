import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import { ApiError } from '@/types/api'

import { CatalogCombobox } from './CatalogCombobox'

const list = vi.hoisted(() => vi.fn())

vi.mock('../api/catalogsApi', () => ({ catalogsApi: { list } }))

function renderCombobox() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>
          <CatalogCombobox kind="systems" selected={[]} onSelect={vi.fn()} />
        </ConfirmDialogProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => list.mockReset())

describe('CatalogCombobox', () => {
  /** A failed read must not say «there is nothing with that name»: it says it failed and lets the person try again. */
  it('shows the failure with a retry instead of «no results» when the catalog could not be read', async () => {
    list.mockRejectedValueOnce(new ApiError(500, { title: 'Error', status: 500, detail: 'boom', errorCode: 'INTERNAL' }))
    list.mockResolvedValueOnce({ content: [{ id: 's1', name: 'D&D 5e' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })

    renderCombobox()
    await userEvent.click(screen.getByRole('combobox'))

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
    expect(screen.queryByText('No hay nada con ese nombre.')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('D&D 5e')).toBeInTheDocument()
  })
})
