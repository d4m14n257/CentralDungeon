import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router'

import '@/providers/i18n'
import { GameTableCard } from './GameTableCard'
import type { GameTableSummary } from '../types'

const baseTable: GameTableSummary = {
  id: 'table-1',
  name: 'Curse of Strahd',
  status: 'Opened',
  tableTypeName: 'Public',
  maxPlayers: 4,
  playerCount: 2,
  primaryMaster: { userId: 'master-1', name: 'BrowserTester', karma: 8000, masterType: 'Primary' },
}

function renderCard(table: GameTableSummary, options: { linkTo?: string; alreadyApplied?: boolean } = {}) {
  return render(
    <MemoryRouter>
      <GameTableCard table={table} {...options} />
    </MemoryRouter>,
  )
}

describe('GameTableCard', () => {
  it('links to the default player detail route when linkTo is not given', () => {
    renderCard(baseTable)

    expect(screen.getByRole('link')).toHaveAttribute('href', '/tables/table-1')
  })

  it('links to the given route when linkTo is passed (master detail route)', () => {
    renderCard(baseTable, { linkTo: '/master/tables/table-1' })

    expect(screen.getByRole('link')).toHaveAttribute('href', '/master/tables/table-1')
  })

  it('shows the capped player count when maxPlayers is set', () => {
    renderCard(baseTable)

    expect(screen.getByText('2 / 4 jugadores')).toBeInTheDocument()
  })

  it('shows the unlimited player count when maxPlayers is null', () => {
    renderCard({ ...baseTable, maxPlayers: null })

    expect(screen.getByText('2 jugadores')).toBeInTheDocument()
  })

  it('omits the table type line when tableTypeName is null', () => {
    renderCard({ ...baseTable, tableTypeName: null })

    expect(screen.queryByText('Public')).not.toBeInTheDocument()
  })

  it('shows the already-applied chip when alreadyApplied is true', () => {
    renderCard(baseTable, { alreadyApplied: true })

    expect(screen.getByText('Ya te postulaste')).toBeInTheDocument()
  })

  it('omits the already-applied chip by default', () => {
    renderCard(baseTable)

    expect(screen.queryByText('Ya te postulaste')).not.toBeInTheDocument()
  })
})
