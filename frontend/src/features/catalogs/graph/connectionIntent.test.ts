import { describe, expect, it } from 'vitest'

import type { AdminCatalogValue } from '../types'
import { connectionIntent } from './connectionIntent'

function value(id: string, overrides: Partial<AdminCatalogValue> = {}): AdminCatalogValue {
  return {
    id,
    name: id,
    status: 'Accepted',
    canonicalId: null,
    canonicalName: null,
    uses: 0,
    aliasCount: 0,
    createdAt: '2026-09-01T00:00:00Z',
    ...overrides,
  }
}

const head = value('dnd')

describe('connectionIntent', () => {
  it('accepts a floating proposal into the group it is dropped on (#55)', () => {
    expect(connectionIntent(value('dandd', { status: 'Created' }), head)).toBe('accept')
  })

  it('lets a rejected proposal be reconsidered into a group (#55)', () => {
    expect(connectionIntent(value('dandd', { status: 'Rejected' }), head)).toBe('accept')
  })

  it('moves an alias to another group (#276)', () => {
    expect(connectionIntent(value('dnd-alias', { canonicalId: 'pf2' }), head)).toBe('reassign')
  })

  it('merges a whole group when its head is dropped on another (#55)', () => {
    expect(connectionIntent(value('pf2'), head)).toBe('merge')
  })

  it('refuses a disabled head, since the server merges only accepted groups', () => {
    expect(connectionIntent(value('old', { status: 'Disabled' }), head)).toBeNull()
  })

  it('refuses to hang anything from an alias - depth is always 1 (#59)', () => {
    expect(connectionIntent(value('x', { status: 'Created' }), value('dnd-alias', { canonicalId: 'dnd' }))).toBeNull()
  })

  it('refuses a target that is not accepted', () => {
    expect(connectionIntent(value('x', { status: 'Created' }), value('y', { status: 'Created' }))).toBeNull()
    expect(connectionIntent(value('x', { status: 'Created' }), value('y', { status: 'Disabled' }))).toBeNull()
  })

  it('refuses a value connected to itself or to the group it is already in', () => {
    expect(connectionIntent(head, head)).toBeNull()
    expect(connectionIntent(value('dnd-alias', { canonicalId: 'dnd' }), head)).toBeNull()
  })
})
