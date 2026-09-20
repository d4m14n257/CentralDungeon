import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusBadge, type StatusTone } from './StatusBadge'

/**
 * The invariant that used to be asserted in one of nine places, now that there is one place (#261).
 *
 * `TableStatusBadge` was the only one of the nine badges with a test, so the rule «colour is never the
 * only carrier of meaning» (`frontend-diseno.md` §3) was pinned for tables and taken on trust for the
 * other eight. It holds here for all of them at once.
 */
describe('StatusBadge', () => {
  const TONES: StatusTone[] = ['draft', 'pending', 'open', 'active', 'paused', 'done', 'warning', 'canceled', 'blocked']

  /**
   * Every tone paints its own dot, and the class is the literal one Tailwind can see.
   *
   * Asserted tone by tone rather than on the map, because the failure this guards against is a tone
   * whose classes were built from a template string: that compiles, renders, and would pass a test
   * that only looked for the label.
   */
  it.each(TONES)('paints the %s dot with a class Tailwind can see', (tone) => {
    const { container } = render(<StatusBadge tone={tone} label="Algo" />)

    expect(container.querySelector(`.bg-state-${tone}-dot`)).not.toBeNull()
    expect(container.querySelector(`.bg-state-${tone}-bg`)).not.toBeNull()
  })

  /** The dot never travels alone: there is always something to read beside it. */
  it('never carries colour as the only signal', () => {
    render(<StatusBadge tone="open" label="Abierta" />)

    const badge = screen.getByText('Abierta').closest('span')
    expect(badge?.querySelector('span')).not.toBeNull()
  })

  /** The trailing child is what lets `ClaimBadge` add its "how long ago" without a shape of its own. */
  it('renders trailing detail inside the same badge', () => {
    render(
      <StatusBadge tone="active" label="Lo tenés vos">
        <span>hace 3 minutos</span>
      </StatusBadge>,
    )

    const badge = screen.getByText('Lo tenés vos').closest('span')
    expect(badge?.textContent).toContain('hace 3 minutos')
  })
})
