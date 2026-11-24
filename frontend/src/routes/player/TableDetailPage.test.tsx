import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { GameTableDetail, MyRejection } from '@/features/tables'

import { TableDetailPage } from './TableDetailPage'

const byId = vi.hoisted(() => vi.fn())

vi.mock('@/features/tables/api/gameTablesApi', () => ({ gameTablesApi: { byId }, tableTypesApi: { list: vi.fn() } }))
vi.mock('@/features/users', () => ({ useMe: () => ({ data: { roles: ['Player'] } }) }))
// The blocks the page composes are other features' concern; only the notice and the button matter here.
vi.mock('@/features/tasks', () => ({ TableTasksSection: () => null }))
vi.mock('@/features/help', () => ({ HelpButton: () => null, HelpLink: () => null }))
vi.mock('@/features/files', () => ({
  FileList: () => null,
  FilePicker: () => null,
  useCommitStagedFiles: () => ({ mutateAsync: vi.fn() }),
}))
vi.mock('@/features/registrations', () => ({
  ApplyToTableDialog: ({ open }: { open: boolean }) => (open ? <div role="dialog">Postularme a la mesa</div> : null),
}))

function table(myRejection: MyRejection | null, overrides: Partial<GameTableDetail> = {}): GameTableDetail {
  return {
    id: 't1',
    name: 'La maldición de Strahd',
    description: null,
    permitted: null,
    requirements: null,
    sections: { characterCreation: null, whatToExpect: null, equipment: null, safety: null },
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
    joined: false,
    seekingPlayers: false,
    myRejection,
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={['/player/tables/t1']}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/player/tables/:id" element={<TableDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  byId.mockReset()
})

describe('TableDetailPage rejection notice', () => {
  it("shows the master's own words and the date, and applies again from the notice", async () => {
    byId.mockResolvedValue(table({ justification: 'No encaja con el tono', reasonCode: null, rejectedAt: '2026-10-01T12:00:00' }))

    renderPage()

    expect(await screen.findByText('Te rechazaron en esta mesa')).toBeInTheDocument()
    expect(screen.getByText(/Motivo: No encaja con el tono/)).toBeInTheDocument()
    expect(screen.getByText(/oct/i)).toBeInTheDocument()

    expect(screen.queryByRole('button', { name: /^Postularme$/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Postularme de nuevo' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('says the automatic rejection from its code, never with an empty reason', async () => {
    byId.mockResolvedValue(table({ justification: null, reasonCode: 'TABLE_FULL', rejectedAt: '2026-10-01T12:00:00' }))

    renderPage()

    expect(await screen.findByText('Te rechazaron en esta mesa')).toBeInTheDocument()
    expect(screen.getByText(/Motivo: La mesa se llenó/)).toBeInTheDocument()
  })

  it('shows no notice when the reader was not rejected', async () => {
    byId.mockResolvedValue(table(null))

    renderPage()

    expect(await screen.findByText('La maldición de Strahd')).toBeInTheDocument()
    expect(screen.queryByText('Te rechazaron en esta mesa')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Postularme de nuevo' })).not.toBeInTheDocument()
  })
})

describe('TableDetailPage applying to a running table', () => {
  it('lets the reader apply while the table is looking for players', async () => {
    byId.mockResolvedValue(table(null, { status: 'InProgress', seekingPlayers: true }))

    renderPage()

    expect(await screen.findByRole('button', { name: 'Postularme' })).toBeEnabled()
  })

  it('says it is not open when the running table is not looking for players', async () => {
    byId.mockResolvedValue(table(null, { status: 'InProgress', seekingPlayers: false }))

    renderPage()

    expect(await screen.findByRole('button', { name: 'No está abierta a postulaciones' })).toBeDisabled()
  })
})

describe('TableDetailPage optional sections', () => {
  /** Each section the master wrote is shown under its heading; one left unwritten has no empty heading. */
  it('shows the written sections under their headings and leaves out the rest', async () => {
    byId.mockResolvedValue(
      table(null, {
        sections: { characterCreation: '<p>Nivel 3, compra de puntos</p>', whatToExpect: null, equipment: null, safety: '<p>Carta X</p>' },
      }),
    )

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Creación de personaje' })).toBeInTheDocument()
    expect(screen.getByText('Nivel 3, compra de puntos')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Seguridad y content warnings' })).toBeInTheDocument()
    expect(screen.getByText('Carta X')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Qué esperar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Equipo' })).not.toBeInTheDocument()
  })
})
