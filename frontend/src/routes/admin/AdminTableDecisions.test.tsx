import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import '@/providers/i18n'
import { AdminTableDecisions, hasAdminDecisions } from './AdminTableDecisions'

const idle = { mutate: vi.fn(), isPending: false, error: null }

vi.mock('@/features/tables', () => ({
  JustifiedTableActionDialog: () => null,
  useApproveTable: () => idle,
  useRequestChanges: () => idle,
  useCancelTable: () => idle,
}))
vi.mock('@/features/help', () => ({ HelpLink: () => null }))
vi.mock('@/hooks/useConfirm', () => ({ useConfirm: () => vi.fn() }))

describe('AdminTableDecisions', () => {
  /** #286: an admin who read the table resolves its review from it - approve, or send it back. */
  it('offers approving and asking for changes while the table is in review', () => {
    render(<AdminTableDecisions table={{ id: 't1', name: 'La Cripta', status: 'Preparation' }} scope="review" />)

    expect(screen.getByRole('button', { name: 'Aprobar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pedir cambios' })).toBeInTheDocument()
    // The cancellation is the status tab's block, not the review's.
    expect(screen.queryByRole('button', { name: 'Cancelar mesa' })).not.toBeInTheDocument()
  })

  /** Reviewing only exists in review; a running table can still be cancelled, with its reason. */
  it('offers only cancelling once the table is running', () => {
    render(<AdminTableDecisions table={{ id: 't1', name: 'La Cripta', status: 'InProgress' }} scope="cancel" />)

    expect(hasAdminDecisions('InProgress', 'review')).toBe(false)
    expect(screen.getByRole('button', { name: 'Cancelar mesa' })).toBeInTheDocument()
  })

  /**
   * A table nobody ever saw is removed, not cancelled (#175), and a closed one is a record: neither
   * has a decision left, so the tab says so instead of drawing an empty block.
   */
  it('has nothing to decide for a table without masters or one already closed', () => {
    expect(hasAdminDecisions('Unassigned', 'cancel')).toBe(false)
    expect(hasAdminDecisions('Finished', 'cancel')).toBe(false)
    expect(hasAdminDecisions('Canceled', 'cancel')).toBe(false)
    expect(hasAdminDecisions('Preparation', 'review')).toBe(true)
    expect(hasAdminDecisions('Preparation', 'cancel')).toBe(true)
  })
})
