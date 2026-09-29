import { describe, expect, it } from 'vitest'

import type { AdminCatalogValue } from '../types'
import { toFlow } from './toFlow'

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

const dnd = [value('dnd'), value('dnd-a', { canonicalId: 'dnd' }), value('dnd-b', { canonicalId: 'dnd' })]
const pf2 = [value('pf2')]

describe('toFlow', () => {
  it('draws each group as a star: the head, and one edge from every member to it (#59)', () => {
    const { nodes, edges } = toFlow([dnd], [])

    expect(nodes.map((node) => [node.id, node.data.role])).toEqual([
      ['dnd', 'head'],
      ['dnd-a', 'member'],
      ['dnd-b', 'member'],
    ])
    expect(edges.map((edge) => [edge.source, edge.target])).toEqual([
      ['dnd-a', 'dnd'],
      ['dnd-b', 'dnd'],
    ])
  })

  it('leaves proposals floating, connected to nothing, to the left of every group (#275)', () => {
    const { nodes, edges } = toFlow([dnd], [value('new', { status: 'Created' })])

    const floating = nodes.find((node) => node.id === 'new')
    expect(floating?.data.role).toBe('floating')
    expect(edges.some((edge) => edge.source === 'new' || edge.target === 'new')).toBe(false)
    const leftmostGroupNode = Math.min(...nodes.filter((node) => node.id !== 'new').map((node) => node.position.x))
    expect(floating!.position.x).toBeLessThan(leftmostGroupNode)
  })

  it('draws a group once even when two ids on the URL resolve to it after a merge', () => {
    const { nodes } = toFlow([dnd, dnd, pf2], [])

    expect(nodes.filter((node) => node.id === 'dnd')).toHaveLength(1)
    expect(nodes).toHaveLength(4)
  })

  it('does not draw a proposal twice when it is already inside a group on screen', () => {
    const { nodes } = toFlow([[value('solo', { status: 'Created' })]], [value('solo', { status: 'Created' })])

    expect(nodes.filter((node) => node.id === 'solo')).toHaveLength(1)
    expect(nodes[0]?.data.role).toBe('head')
  })

  it('lays groups out left to right without overlapping', () => {
    const { nodes } = toFlow([dnd, pf2], [])

    const rightmostOfFirst = Math.max(...nodes.filter((node) => node.id.startsWith('dnd')).map((node) => node.position.x))
    const pf2Node = nodes.find((node) => node.id === 'pf2')!
    expect(pf2Node.position.x).toBeGreaterThan(rightmostOfFirst)
  })
})
