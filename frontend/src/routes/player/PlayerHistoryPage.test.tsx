import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { GameTableHistoryEntry, GameTableStatus } from '@/features/tables'
import { PlayerHistoryPage } from './PlayerHistoryPage'

const FINISHED_ENTRY: GameTableHistoryEntry = {
  id: 'table-1',
  name: 'La Cripta',
  status: 'Finished',
  tableTypeCode: 'Public',
  tableTypeName: 'Public',
  closedAt: '2026-08-01T20:00:00',
  attendance: { present: 8, excused: 1, absent: 0, registered: 9 },
}

interface HistoryQueryResult {
  data: { pages: { content: GameTableHistoryEntry[]; page: number; size: number; totalElements: number; totalPages: number }[] } | undefined
  isPending: boolean
  isLoadingError: boolean
  refetch: () => void
  fetchNextPage: () => void
  hasNextPage: boolean
  isFetchingNextPage: boolean
}

function page(content: GameTableHistoryEntry[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }
}

let queryResult: HistoryQueryResult = {
  data: { pages: [page([FINISHED_ENTRY])] },
  isPending: false,
  isLoadingError: false,
  refetch: vi.fn(),
  fetchNextPage: vi.fn(),
  hasNextPage: false,
  isFetchingNextPage: false,
}

// `vi.mock` is hoisted above these imports by Vitest, so `queryResult` above is only read, never
// captured stale, each time a test reassigns it before rendering (same pattern as ProfilePage.test.tsx).
vi.mock('@/features/tables', () => ({
  useTableHistory: () => queryResult,
  TableStatusBadge: ({ status }: { status: GameTableStatus }) => <span>{status}</span>,
}))

function renderPage() {
  return render(
    <MemoryRouter>
      <PlayerHistoryPage />
    </MemoryRouter>,
  )
}

function showing(...entries: GameTableHistoryEntry[]): HistoryQueryResult {
  return {
    data: { pages: [page(entries)] },
    isPending: false,
    isLoadingError: false,
    refetch: vi.fn(),
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  }
}

describe('PlayerHistoryPage', () => {
  it('links each table to its public detail', () => {
    queryResult = showing(FINISHED_ENTRY)
    renderPage()

    expect(screen.getByRole('link', { name: 'La Cripta' })).toHaveAttribute('href', '/player/tables/table-1')
  })

  it('renders a row without a closing date instead of breaking on it', () => {
    queryResult = showing({ ...FINISHED_ENTRY, closedAt: null })
    renderPage()

    expect(screen.getByText('La Cripta')).toBeInTheDocument()
    expect(screen.queryByText(/cerr/i)).not.toBeInTheDocument()
  })

  it('shows a closed table with its final status and attendance', () => {
    queryResult = {
      data: { pages: [page([FINISHED_ENTRY])] },
      isPending: false,
      isLoadingError: false,
      refetch: vi.fn(),
      fetchNextPage: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
    }
    renderPage()

    expect(screen.getByText('La Cripta')).toBeInTheDocument()
    expect(screen.getByText('Finished')).toBeInTheDocument()
    // AttendanceSummaryView's four counts.
    expect(screen.getByText('8')).toBeInTheDocument()
    expect(screen.getByText('9')).toBeInTheDocument()
  })

  it('shows a skeleton while loading', () => {
    queryResult = {
      data: undefined,
      isPending: true,
      isLoadingError: false,
      refetch: vi.fn(),
      fetchNextPage: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
    }
    const { container } = renderPage()

    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })

  it('offers a retry when the load fails', () => {
    queryResult = {
      data: undefined,
      isPending: false,
      isLoadingError: true,
      refetch: vi.fn(),
      fetchNextPage: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
    }
    renderPage()

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  /**
   * The empty state has to read as neutral news - "you have not finished a table yet" - and not as
   * an error or as something missing, per diseno §1.
   */
  it('reads the empty state as neutral news, not as an error', () => {
    queryResult = {
      data: { pages: [page([])] },
      isPending: false,
      isLoadingError: false,
      refetch: vi.fn(),
      fetchNextPage: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
    }
    renderPage()

    expect(screen.getByText('Todavía no terminaste ninguna mesa')).toBeInTheDocument()
    expect(screen.queryByText(/algo salió mal/i)).not.toBeInTheDocument()
  })
})
