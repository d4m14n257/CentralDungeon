import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import type { GameTableHistoryEntry, GameTableStatus } from '@/features/tables'
import { PlayerHistoryPage } from './PlayerHistoryPage'

const FINISHED_ENTRY: GameTableHistoryEntry = {
  id: 'table-1',
  name: 'La Cripta',
  status: 'Finished',
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

describe('PlayerHistoryPage', () => {
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
    render(<PlayerHistoryPage />)

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
    const { container } = render(<PlayerHistoryPage />)

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
    render(<PlayerHistoryPage />)

    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  /**
   * The empty state has to read as neutral news - "you have not finished a table yet" - and not as
   * an error or as something missing, per frontend-diseno.md §1.
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
    render(<PlayerHistoryPage />)

    expect(screen.getByText('Todavía no terminaste ninguna mesa')).toBeInTheDocument()
    expect(screen.queryByText(/algo salió mal/i)).not.toBeInTheDocument()
  })
})
