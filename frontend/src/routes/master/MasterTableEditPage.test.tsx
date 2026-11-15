import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { ConfirmDialogProvider } from '@/components/ConfirmDialog'
import type { GameTableDetail } from '@/features/tables'

import { MasterTableEditPage } from './MasterTableEditPage'

const get = vi.hoisted(() => vi.fn())
const managedById = vi.hoisted(() => vi.fn())
const update = vi.hoisted(() => vi.fn())
const list = vi.hoisted(() => vi.fn())
const me = vi.hoisted(() => vi.fn())

vi.mock('@/api/client', () => ({ api: { get } }))
vi.mock('@/features/tables/api/gameTablesApi', () => ({ gameTablesApi: { managedById, update }, tableTypesApi: { list } }))
vi.mock('@/features/users/api/usersApi', () => ({ usersApi: { me } }))

/** A draft whose only linked system was disabled after it was chosen. */
function table(): GameTableDetail {
  return {
    id: 't1',
    name: 'Curse of Strahd',
    description: null,
    permitted: null,
    requirements: null,
    tableTypeName: null,
    tableTypeCode: null,
    status: 'Draft',
    maxPlayers: 5,
    playerCount: 0,
    startDate: null,
    totalSessions: null,
    schedule: [{ weekday: 'Monday', hourtime: '20:00:00', duration: '03:00:00' }],
    sessions: [],
    files: [],
    systems: [{ id: 's1', name: 'Old System', status: 'Disabled' }],
    tags: [{ id: 'g1', name: 'Horror', status: 'Accepted' }],
    platforms: [{ id: 'p1', name: 'Discord', status: 'Accepted' }],
    masters: [{ userId: 'me', name: 'Yo', masterType: 'Primary' }],
    createdAt: '2026-01-01T00:00:00Z',
    closedAt: null,
  } as unknown as GameTableDetail
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // A data router: the page guards unsaved changes with `useBlocker`, which needs one.
  const router = createMemoryRouter([{ path: '/master/tables/:id/edit', element: <MasterTableEditPage /> }], {
    initialEntries: ['/master/tables/t1/edit'],
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfirmDialogProvider>
        <RouterProvider router={router} />
      </ConfirmDialogProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  // jsdom has no scrolling; the week grid scrolls itself to the first busy hour.
  Element.prototype.scrollTo = vi.fn()
  get.mockReset()
  managedById.mockReset()
  update.mockReset()
  list.mockReset()
  me.mockReset()
  get.mockImplementation((url: string) =>
    Promise.resolve(url.includes('/settings/limits') ? { maxFileSizeBytes: 1, claimTimeoutMinutes: 15, maxPlayersCap: 12 } : []),
  )
  list.mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
  me.mockResolvedValue({ id: 'me', name: 'Yo', country: 'AR', needsOnboarding: false, roles: ['Master'], hasManagedTables: true })
  managedById.mockResolvedValue(table())
  update.mockResolvedValue(table())
})

describe('MasterTableEditPage catalog values', () => {
  /** The master reads the managed endpoint, which keeps a value after it was disabled: it has to say so, and saving must not drop it. */
  it('marks a linked value that is no longer accepted and keeps it when saving', async () => {
    renderPage()

    expect(await screen.findByText('Old System')).toBeInTheDocument()
    expect(screen.getByText('Dado de baja')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Guardar cambios', hidden: false }))

    await waitFor(() => expect(update).toHaveBeenCalled())
    expect(update.mock.calls[0]?.[1]).toMatchObject({ systemIds: ['s1'], tagIds: ['g1'], platformIds: ['p1'] })
  })
})
