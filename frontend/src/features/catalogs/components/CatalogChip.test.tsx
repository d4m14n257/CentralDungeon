import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { CatalogChip } from './CatalogChip'
import type { CatalogValue } from '../types'

/**
 * Builds a value for one case, so each test says only what it is about.
 *
 * @param overrides what this case changes
 * @returns a catalog value
 */
function value(overrides: Partial<CatalogValue> = {}): CatalogValue {
  return { id: 'c-1', name: 'D&D 5e', status: 'Accepted', ...overrides }
}

describe('CatalogChip', () => {
  it('shows the alias its master chose, not the group it belongs to (#58)', () => {
    render(<CatalogChip value={value({ name: 'DANDD' })} />)

    expect(screen.getByText('DANDD')).toBeInTheDocument()
  })

  it('says out loud that a proposed value is still pending (#57)', () => {
    render(<CatalogChip value={value({ status: 'Created' })} />)

    expect(screen.getByText('· pendiente')).toBeInTheDocument()
  })

  it('does not mark an accepted value as pending', () => {
    render(<CatalogChip value={value()} />)

    expect(screen.queryByText('· pendiente')).not.toBeInTheDocument()
  })

  /**
   * Being pending is carried by the label, not only by the styling - the same rule the state badges
   * follow (frontend-diseno.md 3). The dimming is a second signal, never the only one.
   */
  it('pairs the dimming with a readable label', () => {
    const { container } = render(<CatalogChip value={value({ status: 'Created' })} />)

    expect(container.querySelector('.border-dashed')).not.toBeNull()
    expect(screen.getByText('· pendiente')).toBeInTheDocument()
  })

  it('is read-only when no onRemove is given', () => {
    render(<CatalogChip value={value()} />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('removes by id, so the caller never has to match on the name', async () => {
    const onRemove = vi.fn()
    render(<CatalogChip value={value({ id: 'c-9' })} onRemove={onRemove} />)

    await userEvent.click(screen.getByRole('button', { name: 'Quitar D&D 5e' }))

    expect(onRemove).toHaveBeenCalledWith('c-9')
  })
})
