/** A point on the canvas, in flow coordinates. */
export interface GraphPoint {
  x: number
  y: number
}

/**
 * Where the satellites of a star go: evenly around a centre, starting at the top and turning
 * clockwise (#275).
 *
 * It is the whole layout a depth-1 group needs, which is why the canvas pattern carries no layout
 * engine (dagre, ELK): those solve layered graphs of any depth, and a synonym group is a head with
 * its members around it and nothing more (#59). A pure function, so the geometry is tested without
 * rendering anything.
 *
 * @param center where the head of the star sits
 * @param count  how many satellites to place
 * @param radius how far from the centre they go
 * @returns one point per satellite, in the order they were asked for
 */
export function radialLayout(center: GraphPoint, count: number, radius: number): GraphPoint[] {
  return Array.from({ length: count }, (_, index) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * index) / count
    return { x: Math.round(center.x + radius * Math.cos(angle)), y: Math.round(center.y + radius * Math.sin(angle)) }
  })
}

/**
 * How far a star's satellites go from its head so that none of them touch: the circle grows with
 * the number of members, and never shrinks under `minRadius`.
 *
 * @param count     how many satellites the star has
 * @param spacing   the arc length each satellite needs - roughly a node's width plus a gap
 * @param minRadius the radius of a small star, so two or three members do not crowd their head
 * @returns the radius to hand to {@link radialLayout}
 */
export function radiusFor(count: number, spacing: number, minRadius: number): number {
  return Math.max(minRadius, Math.round((count * spacing) / (2 * Math.PI)))
}
