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
  return { id: 'c-1', name: 'D&D 5e', status: 'Accepted', category: null, description: null, ...overrides }
}

describe('CatalogChip', () => {
  it('shows the alias its master chose, not the group it belongs to (ADR-12 §4)', () => {
    render(<CatalogChip value={value({ name: 'DANDD' })} />)

    expect(screen.getByText('DANDD')).toBeInTheDocument()
  })

  it('says out loud that a proposed value is still pending (ADR-12 §3)', () => {
    render(<CatalogChip value={value({ status: 'Created' })} />)

    expect(screen.getByText('· pendiente')).toBeInTheDocument()
  })

  it('does not mark an accepted value as pending', () => {
    render(<CatalogChip value={value()} />)

    expect(screen.queryByText('· pendiente')).not.toBeInTheDocument()
  })

  /**
   * Being pending is carried by the label, not only by the styling - the same rule the state badges
   * follow (skill `diseno` §3). The dimming is a second signal, never the only one.
   */
  it('pairs the dimming with a readable label', () => {
    const { container } = render(<CatalogChip value={value({ status: 'Created' })} />)

    expect(container.querySelector('.border-dashed')).not.toBeNull()
    expect(screen.getByText('· pendiente')).toBeInTheDocument()
  })

  it('wears its status when it was disabled after being linked', () => {
    const { container } = render(<CatalogChip value={value({ status: 'Disabled' })} />)

    expect(screen.getByText('Dado de baja')).toBeInTheDocument()
    expect(container.querySelector('.border-dashed')).not.toBeNull()
  })

  it('does not wear a status badge when it is accepted', () => {
    render(<CatalogChip value={value()} />)

    expect(screen.queryByText('Dado de baja')).not.toBeInTheDocument()
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

  it("shows its group's definition in a tooltip the keyboard can reach", async () => {
    render(<CatalogChip value={value({ name: 'One-shot', description: 'A single session.' })} />)

    await userEvent.tab()

    expect(await screen.findByRole('tooltip')).toHaveTextContent('A single session.')
  })

  it('offers no tooltip when the group has no definition', () => {
    render(<CatalogChip value={value()} />)

    expect(screen.getByText('D&D 5e')).not.toHaveAttribute('tabindex')
  })
})
