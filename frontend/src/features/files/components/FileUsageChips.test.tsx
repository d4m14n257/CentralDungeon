import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import type { FileUsage } from '../types'
import { FileUsageChips } from './FileUsageChips'

function usage(contextId: string, contextName: string, membership: FileUsage['membership']): FileUsage {
  return { category: 'TableMaterial', contextId, contextName, membership }
}

describe('FileUsageChips', () => {
  it("links each table to the reader's own view of it, and leaves the name bare when they are neither", () => {
    render(
      <MemoryRouter>
        <FileUsageChips
          usages={[usage('t-run', 'Corrida', 'Master'), usage('t-play', 'Jugada', 'Player'), usage('t-none', 'Ajena', null)]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Corrida' })).toHaveAttribute('href', '/master/tables/t-run/files')
    expect(screen.getByRole('link', { name: 'Jugada' })).toHaveAttribute('href', '/player/my-tables/t-play')
    expect(screen.getByText('Ajena')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Ajena' })).toBeNull()
  })

  it('says the file is unused when nothing points at it', () => {
    render(
      <MemoryRouter>
        <FileUsageChips usages={[]} />
      </MemoryRouter>,
    )

    expect(screen.getByText('Sin usar')).toBeInTheDocument()
  })
})
