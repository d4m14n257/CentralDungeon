import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { StoredFile } from '@/features/files'

import { MyFilesPage } from './MyFilesPage'

const listMine = vi.hoisted(() => vi.fn())
const listMyCategories = vi.hoisted(() => vi.fn())
const remove = vi.hoisted(() => vi.fn())

vi.mock('@/features/files/api/filesApi', () => ({ filesApi: { listMine, listMyCategories, remove } }))
vi.mock('@/hooks/useHasPersonalLibrary', () => ({ useHasPersonalLibrary: () => ({ hasPersonalLibrary: true, isPending: false }) }))

function file(id: string, name: string): StoredFile {
  return {
    id,
    name,
    mimeType: 'application/pdf',
    sizeBytes: 1024,
    fileType: 'Private',
    categories: [],
    usages: [],
    lastUsedAt: null,
    createdAt: '2026-03-04T12:00:00',
  }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ConfirmDialogProvider>
          <MyFilesPage />
        </ConfirmDialogProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Clicks the row action `name` of the row listing `fileName`, with Shift held when asked. */
async function clickRowAction(user: ReturnType<typeof userEvent.setup>, fileName: string, name: string, withShift = false) {
  const row = screen.getByText(fileName).closest('li')!
  if (withShift) await user.keyboard('{Shift>}')
  await user.click(within(row).getByRole('button', { name }))
  if (withShift) await user.keyboard('{/Shift}')
}

describe('MyFilesPage batch removal', () => {
  beforeEach(() => {
    listMine.mockReset().mockResolvedValue({
      content: [file('f1', 'uno.pdf'), file('f2', 'dos.pdf'), file('f3', 'tres.pdf')],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    })
    listMyCategories.mockReset().mockResolvedValue(['TableMaterial'])
    remove.mockReset().mockResolvedValue(undefined)
  })

  /** Without Shift nothing changes: one row, one confirmation that names it. */
  it('still asks about one file when the action is clicked without Shift', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('uno.pdf')

    await clickRowAction(user, 'uno.pdf', 'Dar de baja')

    expect(await screen.findByRole('dialog')).toHaveTextContent('«uno.pdf»')
    expect(remove).not.toHaveBeenCalled()
  })

  it('marks rows with Shift without asking or removing, then removes them all after one confirmation', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('uno.pdf')

    await clickRowAction(user, 'uno.pdf', 'Dar de baja', true)
    await clickRowAction(user, 'tres.pdf', 'Dar de baja', true)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
    const bar = screen.getByRole('region', { name: 'Acciones en lote' })
    expect(within(bar).getByText('2 marcados')).toBeInTheDocument()
    // Marked rows say so in words, and the unmarked one does not.
    expect(screen.getAllByText('Marcado')).toHaveLength(2)
    expect(screen.getByText('dos.pdf').closest('li')).not.toHaveTextContent('Marcado')

    await user.click(within(bar).getByRole('button', { name: 'Aplicar' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('¿Dar de baja los 2 archivos marcados?')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Dar de baja' }))

    await waitFor(() => expect(remove).toHaveBeenCalledTimes(2))
    expect(remove.mock.calls).toEqual([['f1'], ['f3']])
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Acciones en lote' })).not.toBeInTheDocument())
  })

  it('drops the marks on Cancelar without removing anything', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('uno.pdf')
    await clickRowAction(user, 'uno.pdf', 'Dar de baja', true)

    await user.click(within(screen.getByRole('region', { name: 'Acciones en lote' })).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('region', { name: 'Acciones en lote' })).not.toBeInTheDocument()
    expect(screen.queryByText('Marcado')).not.toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
  })

  it('keeps going when one removal fails, and leaves only that file marked', async () => {
    remove.mockImplementation((id: string) => (id === 'f1' ? Promise.reject(new Error('boom')) : Promise.resolve()))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('uno.pdf')
    await clickRowAction(user, 'uno.pdf', 'Dar de baja', true)
    await clickRowAction(user, 'dos.pdf', 'Dar de baja', true)

    await user.click(screen.getByRole('button', { name: 'Aplicar' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Dar de baja' }))

    await waitFor(() => expect(remove).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('1 marcado')).toBeInTheDocument()
    expect(screen.getByText('uno.pdf').closest('li')).toHaveTextContent('Marcado')
  })
})
