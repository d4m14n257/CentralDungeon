import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { TableStatusBadge } from './TableStatusBadge'

describe('TableStatusBadge', () => {
  it.each([
    ['Unassigned', 'Sin asignar', 'bg-state-draft-dot'],
    ['Preparation', 'En preparación', 'bg-state-pending-dot'],
    ['ChangesRequested', 'Con cambios pedidos', 'bg-state-warning-dot'],
    ['Opened', 'Abierta', 'bg-state-open-dot'],
    ['InProgress', 'En curso', 'bg-state-active-dot'],
    ['PauseRequested', 'Pausa solicitada', 'bg-state-pending-dot'],
    ['Pause', 'Pausada', 'bg-state-paused-dot'],
    ['Finished', 'Finalizada', 'bg-state-done-dot'],
    ['Canceled', 'Cancelada', 'bg-state-canceled-dot'],
  ] as const)('renders the %s label and dot colour', (status, label, dotClass) => {
    const { container } = render(<TableStatusBadge status={status} />)

    expect(screen.getByText(label)).toBeInTheDocument()
    expect(container.querySelector(`.${dotClass}`)).not.toBeNull()
  })

  it('never carries color as the only signal - the dot is always paired with a text label', () => {
    render(<TableStatusBadge status="Opened" />)

    const badge = screen.getByText('Abierta').closest('span')
    expect(badge?.querySelector('span')).not.toBeNull()
  })
})
