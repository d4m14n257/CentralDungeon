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

function renderCombobox(kind: 'systems' | 'tags' = 'systems') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ConfirmDialogProvider>
          <CatalogCombobox kind={kind} selected={[]} onSelect={vi.fn()} />
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

  it('groups tags under their category, in a fixed order, with the unclassified ones last', async () => {
    list.mockResolvedValueOnce({
      content: [
        { id: 't1', name: 'Sin clasificar', status: 'Accepted', category: null, description: null },
        { id: 't2', name: 'Homebrew', status: 'Accepted', category: 'Format', description: null },
        { id: 't3', name: 'Horror', status: 'Accepted', category: 'Theme', description: 'Fear and dread.' },
        { id: 't4', name: 'One-shot', status: 'Accepted', category: 'Format', description: null },
      ],
      page: 0,
      size: 20,
      totalElements: 4,
      totalPages: 1,
    })

    renderCombobox('tags')
    await userEvent.click(screen.getByRole('combobox'))
    await screen.findByText('Horror')

    const headings = Array.from(document.querySelectorAll('[cmdk-group-heading]')).map((heading) => heading.textContent)
    expect(headings).toEqual(['Temas', 'Formato y duración', 'Sin categoría'])
    const names = screen.getAllByRole('option').map((option) => option.textContent)
    expect(names).toEqual(['HorrorFear and dread.', 'Homebrew', 'One-shot', 'Sin clasificar'])
  })
})
