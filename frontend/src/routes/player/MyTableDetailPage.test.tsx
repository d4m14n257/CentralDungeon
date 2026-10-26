import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { GameTableDetail } from '@/features/tables'
import { ApiError } from '@/types/api'

import { MyTableDetailPage } from './MyTableDetailPage'

const byId = vi.hoisted(() => vi.fn())
const mine = vi.hoisted(() => vi.fn())

vi.mock('@/features/tables/api/gameTablesApi', () => ({ gameTablesApi: { byId }, tableTypesApi: { list: vi.fn() } }))
vi.mock('@/features/tables/api/sessionsApi', () => ({ sessionsApi: { mine } }))

function failure(status: number) {
  return new ApiError(status, { title: 'Error', status, detail: 'detail', errorCode: 'ERROR' })
}

function table(): GameTableDetail {
  return {
    id: 't1',
    name: 'La maldición de Strahd',
    description: null,
    permitted: null,
    requirements: null,
    tableTypeName: null,
    tableTypeCode: null,
    status: 'Opened',
    maxPlayers: 5,
    playerCount: 1,
    startDate: null,
    totalSessions: null,
    schedule: [],
    sessions: [],
    files: [],
    systems: [],
    tags: [],
    platforms: [],
    masters: [],
    createdAt: '2026-01-01T00:00:00',
    closedAt: null,
    scheduleConflict: false,
    applied: false,
    joined: true,
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={['/player/my-tables/t1']}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/player/my-tables/:id" element={<MyTableDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  byId.mockReset()
  mine.mockReset()
})

describe('MyTableDetailPage', () => {
  /** The public read succeeds for anybody; a table that is not mine must not paint empty blocks that claim there is nothing. */
  it('refuses the whole screen for a table that is not mine', async () => {
    byId.mockResolvedValue(table())
    mine.mockRejectedValue(failure(403))

    renderPage()

    expect(await screen.findByText('No tenés permiso')).toBeInTheDocument()
    expect(screen.queryByText('La maldición de Strahd')).not.toBeInTheDocument()
    expect(screen.queryByText('Esta mesa no te pide nada')).not.toBeInTheDocument()
  })

  it('says a table that does not exist does not exist, without a retry', async () => {
    byId.mockRejectedValue(failure(404))
    mine.mockRejectedValue(failure(404))

    renderPage()

    expect(await screen.findByText('No existe')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('offers a retry when the table could not be read', async () => {
    byId.mockRejectedValue(failure(500))
    mine.mockRejectedValue(failure(500))

    renderPage()

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
