import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'

import { UserPicker } from './UserPicker'

const search = vi.hoisted(() => vi.fn())
const searchMasterCandidates = vi.hoisted(() => vi.fn())
/** What the box last confirmed: the test plays the person who typed and pressed Enter. */
const confirmed = vi.hoisted(() => ({ query: '' }))

vi.mock('../api/usersApi', () => ({ usersApi: { search, searchMasterCandidates } }))
vi.mock('@/hooks/useSearchQuery', () => ({
  useSearchQuery: ({ fields }: { fields: unknown }) => ({
    fields,
    value: { terms: [], draft: '', pendingConnector: 'and' },
    onChange: vi.fn(),
    query: confirmed.query,
    onSearch: vi.fn(),
  }),
}))

const PAGE = { content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 }

function renderPicker(tableId = '') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      {tableId ? <UserPicker onSelect={vi.fn()} tableId={tableId} /> : <UserPicker onSelect={vi.fn()} />}
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  search.mockReset().mockResolvedValue(PAGE)
  searchMasterCandidates.mockReset().mockResolvedValue(PAGE)
  confirmed.query = ''
})

describe('UserPicker minimum length', () => {
  it('asks for nothing while the box is empty', () => {
    renderPicker()

    expect(screen.getByText(/Enter dos veces/)).toBeInTheDocument()
    expect(search).not.toHaveBeenCalled()
  })

  it.each(['a', '/discord_name a', 'ab,c'])('asks for nothing and says how much to type for %s', (query) => {
    confirmed.query = query
    renderPicker()

    expect(screen.getByText('Escribí al menos 2 letras para buscar.')).toBeInTheDocument()
    expect(screen.queryByText('Nadie coincide con esa búsqueda.')).not.toBeInTheDocument()
    expect(search).not.toHaveBeenCalled()
  })

  it('searches the directory once the query has two characters', async () => {
    confirmed.query = 'ab'
    renderPicker()

    expect(await screen.findByText('Nadie coincide con esa búsqueda.')).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith('ab')
    expect(screen.queryByText('Escribí al menos 2 letras para buscar.')).not.toBeInTheDocument()
  })

  it('applies the same minimum to the co-master search of a table', async () => {
    confirmed.query = 'a'
    const { unmount } = renderPicker('table-1')
    expect(searchMasterCandidates).not.toHaveBeenCalled()
    unmount()

    confirmed.query = 'ab'
    renderPicker('table-1')
    expect(await screen.findByText('Nadie coincide con esa búsqueda.')).toBeInTheDocument()
    expect(searchMasterCandidates).toHaveBeenCalledWith('table-1', 'ab')
  })
})
