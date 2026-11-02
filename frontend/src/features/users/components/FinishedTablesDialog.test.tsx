import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { PageResponse } from '@/types/api'
import type { FinishedTable } from '../types'
import { FinishedTablesDialog } from './FinishedTablesDialog'

const PAGE: PageResponse<FinishedTable> = {
  content: [
    { id: 'table-1', name: 'La Cripta', systems: ['D&D 5e', 'Pathfinder 2e'], masters: ['Lead', 'Co'], closedAt: '2026-05-01T12:00:00' },
    { id: 'table-2', name: 'Sin cierre', systems: [], masters: ['Lead'], closedAt: null },
  ],
  page: 0,
  size: 10,
  totalElements: 2,
  totalPages: 1,
}

let result: { data: PageResponse<FinishedTable> | undefined; isPending: boolean; isLoadingError: boolean; refetch: () => void }

vi.mock('../api/useFinishedTables', () => ({ useFinishedTables: () => result }))

function renderDialog(canOpenTables: boolean) {
  return render(
    <MemoryRouter>
      <FinishedTablesDialog userId="user-9" canOpenTables={canOpenTables} open onOpenChange={vi.fn()} />
    </MemoryRouter>,
  )
}

describe('FinishedTablesDialog', () => {
  beforeEach(() => {
    result = { data: PAGE, isPending: false, isLoadingError: false, refetch: vi.fn() }
  })

  it('shows what is public of each table: name, systems, masters and closing date', () => {
    renderDialog(true)

    expect(screen.getByText('La Cripta')).toBeInTheDocument()
    expect(screen.getByText('Sistema: D&D 5e, Pathfinder 2e · Master: Lead, Co')).toBeInTheDocument()
    expect(screen.getAllByText(/Terminó el/)).toHaveLength(1)
  })

  it('links each name to the public detail of its table for a viewer who can open it', () => {
    renderDialog(true)

    expect(screen.getByRole('link', { name: 'La Cripta' })).toHaveAttribute('href', '/player/tables/table-1')
  })

  it('leaves the names as plain text for a viewer without the Player role', () => {
    renderDialog(false)

    expect(screen.getByText('La Cripta')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('shows an error with a retry when the list cannot be read', () => {
    result = { data: undefined, isPending: false, isLoadingError: true, refetch: vi.fn() }
    renderDialog(true)

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('says so when there is nothing to show', () => {
    result = { data: { ...PAGE, content: [], totalElements: 0, totalPages: 0 }, isPending: false, isLoadingError: false, refetch: vi.fn() }
    renderDialog(true)

    expect(screen.getByText('Ninguna que puedas ver')).toBeInTheDocument()
  })
})
