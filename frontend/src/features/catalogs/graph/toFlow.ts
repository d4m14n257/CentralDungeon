import type { Edge, Node } from '@xyflow/react'

import type { GraphNodeRole } from '@/components/graph/GraphNode'
import { radialLayout, radiusFor } from '@/components/graph/radialLayout'

import type { AdminCatalogValue } from '../types'

/** What a catalog node carries: the value it draws and the place it holds in its star. */
export interface CatalogNodeData extends Record<string, unknown> {
  /** The value, exactly as the server sent it. */
  value: AdminCatalogValue
  /** Head of a group, member of one, or floating - a proposal nobody classified yet. */
  role: GraphNodeRole
}

/** A node of the catalog canvas. The one node type the canvas registers. */
export type CatalogFlowNode = Node<CatalogNodeData, 'catalog'>

/** An edge of the catalog canvas: always from a member to the head of its group (#59). */
export type CatalogFlowEdge = Edge

/** The edge type's name: the pattern's centre-to-centre spoke, registered by `GraphCanvas`. */
export const GRAPH_EDGE_TYPE = 'straight-center'

/** The node type's name, shared by `toFlow` and the canvas that registers it. */
export const CATALOG_NODE_TYPE = 'catalog'

/** Width of a node, as `.graph-node` draws it (`w-56`), so the layout can centre on it. */
const NODE_WIDTH = 224

/** A node's height, roughly: name, badge and one line under it. */
const NODE_HEIGHT = 80

/** Arc length each member takes around its head, so neighbours do not overlap. */
const MEMBER_SPACING = 300

/** Smallest circle a group is drawn with: a node's width plus a gap, so a member placed level with
 *  its head never touches it. */
const MIN_RADIUS = NODE_WIDTH + 96

/** Room left between two groups' circles. */
const GROUP_GAP = 160

/** Vertical step of the floating column. */
const FLOATING_STEP = 104

/**
 * Turns the groups on screen and the proposals waiting for one into React Flow's nodes and edges
 * (#275).
 *
 * - **Groups** are stars, laid out left to right: the head in the centre, its members around it
 *   with {@link radialLayout}. Depth is always 1 (#59), so this is the whole drawing.
 * - **Floating proposals** stand in a column to the left of every group, connected to nothing -
 *   the "floating in the air until you connect it" the canvas was asked for.
 *
 * The same group can arrive twice - two ids on the URL that a merge has since folded into one - so
 * groups are deduplicated by their head. A floating proposal that is already drawn inside a group is
 * not drawn twice either.
 *
 * Pure, so the layout is tested without a canvas.
 *
 * @param groups  each group as `GET …/{id}/group` returns it: head first, then its members
 * @param pending proposals waiting to be classified
 * @returns the nodes and edges to hand to the canvas
 */
export function toFlow(
  groups: AdminCatalogValue[][],
  pending: AdminCatalogValue[],
): { nodes: CatalogFlowNode[]; edges: CatalogFlowEdge[] } {
  const nodes: CatalogFlowNode[] = []
  const edges: CatalogFlowEdge[] = []
  const drawn = new Set<string>()

  let left = NODE_WIDTH + GROUP_GAP * 2
  for (const group of groups) {
    const head = group.find((member) => member.canonicalId === null)
    if (!head || drawn.has(head.id)) continue

    const members = group.filter((member) => member.id !== head.id)
    const radius = radiusFor(members.length, MEMBER_SPACING, MIN_RADIUS)
    const center = { x: left + radius, y: radius }

    nodes.push(node(head, 'head', center))
    drawn.add(head.id)
    const points = radialLayout(center, members.length, radius)
    members.forEach((member, index) => {
      const point = points[index] ?? center
      nodes.push(node(member, 'member', point))
      drawn.add(member.id)
      edges.push({ id: `${member.id}->${head.id}`, type: GRAPH_EDGE_TYPE, source: member.id, target: head.id })
    })

    left += radius * 2 + NODE_WIDTH + GROUP_GAP
  }

  pending
    .filter((proposal) => !drawn.has(proposal.id))
    .forEach((proposal, index) => {
      nodes.push(node(proposal, 'floating', { x: NODE_WIDTH / 2, y: NODE_HEIGHT / 2 + index * FLOATING_STEP }))
    })

  return { nodes, edges }
}

/**
 * One node, centred on a point rather than anchored by its corner as React Flow expects.
 *
 * @param value the value it draws
 * @param role  its place in the star
 * @param at    where its centre goes
 * @returns the node
 */
function node(value: AdminCatalogValue, role: GraphNodeRole, at: { x: number; y: number }): CatalogFlowNode {
  return {
    id: value.id,
    type: CATALOG_NODE_TYPE,
    position: { x: at.x - NODE_WIDTH / 2, y: at.y - NODE_HEIGHT / 2 },
    data: { value, role },
  }
}
