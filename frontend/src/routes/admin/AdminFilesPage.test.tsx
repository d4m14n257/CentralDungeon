import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { AdminFile } from '@/features/files'

import { AdminFilesPage } from './AdminFilesPage'

const listForAdmin = vi.hoisted(() => vi.fn())

// The API module is mocked, not the hook, so the query and the table run for real.
vi.mock('@/features/files/api/filesApi', () => ({ filesApi: { listForAdmin } }))

const FILE: AdminFile = {
  id: 'file-1',
  name: 'ficha.pdf',
  mimeType: 'application/pdf',
  sizeBytes: 2048,
  fileType: 'Library',
  categories: ['TableMaterial'],
  usages: [],
  ownerId: 'user-5',
  ownerName: 'ana',
  tables: [
    { id: 'table-1', name: 'La Cripta' },
    { id: 'table-2', name: 'El Faro' },
  ],
  status: 'Current',
  lastUsedAt: null,
  createdAt: '2026-03-04T12:00:00',
  publishedAt: null,
}

describe('AdminFilesPage', () => {
  /** Whoever uploaded a file is a person with an admin screen of their own, so the name leads there. */
  it('links the uploader to their account', async () => {
    listForAdmin.mockResolvedValue({ content: [FILE], page: 0, size: 25, totalElements: 1, totalPages: 1 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <ConfirmDialogProvider>
            <AdminFilesPage />
          </ConfirmDialogProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const table = await screen.findByRole('table', { name: 'Archivos' })
    expect(await within(table).findByRole('link', { name: 'ana' })).toHaveAttribute('href', '/admin/users/user-5')
  })

  /** One file, its tables: each one leads to its admin page, and past three the rest is only counted. */
  it('links the tables that use the file and counts the ones beyond the first three', async () => {
    const many = Array.from({ length: 5 }, (_, index) => ({ id: `table-${index + 1}`, name: `Mesa ${index + 1}` }))
    listForAdmin.mockResolvedValue({ content: [{ ...FILE, tables: many }], page: 0, size: 25, totalElements: 1, totalPages: 1 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <ConfirmDialogProvider>
            <AdminFilesPage />
          </ConfirmDialogProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const table = await screen.findByRole('table', { name: 'Archivos' })
    expect(await within(table).findByRole('link', { name: 'Mesa 1' })).toHaveAttribute('href', '/admin/tables/table-1')
    expect(within(table).getByRole('link', { name: 'Mesa 3' })).toHaveAttribute('href', '/admin/tables/table-3')
    expect(within(table).queryByRole('link', { name: 'Mesa 4' })).not.toBeInTheDocument()
    expect(within(table).getByText('y 2 más')).toBeInTheDocument()
    // The phone card renders the same column, so the link is there too.
    expect(screen.getAllByRole('link', { name: 'Mesa 1' })).toHaveLength(2)
  })
})
