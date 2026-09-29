import { BaseEdge, getStraightPath, useInternalNode, type EdgeProps } from '@xyflow/react'

/**
 * The edge of the node canvas pattern (skill `diseno` §5, #275): a straight line from the centre of
 * one node to the centre of the other, drawn under both.
 *
 * React Flow's own edges run from handle to handle, which suits a flow that reads left to right and
 * not a star: a member placed to the right of its head would have its edge leave from its right
 * side, loop around and cross the head to reach the left handle. Centre to centre, every member's
 * edge is a spoke, whatever side of the head it sits on. The nodes are opaque and drawn above the
 * edges, so the line appears to start at each node's border.
 *
 * The handles are still where a new connection is dragged from and dropped on; this only decides how
 * an existing one is drawn.
 *
 * @param props.id        the edge's id
 * @param props.source    the node it starts from
 * @param props.target    the node it ends on
 * @param props.markerEnd the arrow at the end, if the canvas gives it one
 * @param props.style     the stroke, if the canvas overrides it
 */
export function GraphStraightEdge({ id, source, target, markerEnd, style }: EdgeProps) {
  const from = useInternalNode(source)
  const to = useInternalNode(target)
  if (!from || !to) return null

  const [path] = getStraightPath({
    sourceX: from.internals.positionAbsolute.x + (from.measured.width ?? 0) / 2,
    sourceY: from.internals.positionAbsolute.y + (from.measured.height ?? 0) / 2,
    targetX: to.internals.positionAbsolute.x + (to.measured.width ?? 0) / 2,
    targetY: to.internals.positionAbsolute.y + (to.measured.height ?? 0) / 2,
  })

  return <BaseEdge id={id} path={path} {...(markerEnd ? { markerEnd } : {})} {...(style ? { style } : {})} />
}
