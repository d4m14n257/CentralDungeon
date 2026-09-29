import { describe, expect, it } from 'vitest'

import { radialLayout, radiusFor } from './radialLayout'

describe('radialLayout', () => {
  it('places nothing when there is nothing to place', () => {
    expect(radialLayout({ x: 0, y: 0 }, 0, 100)).toEqual([])
  })

  it('starts at the top and turns clockwise', () => {
    const [top, right, bottom, left] = radialLayout({ x: 0, y: 0 }, 4, 100)

    expect(top).toEqual({ x: 0, y: -100 })
    expect(right).toEqual({ x: 100, y: 0 })
    expect(bottom).toEqual({ x: 0, y: 100 })
    expect(left).toEqual({ x: -100, y: 0 })
  })

  it('keeps every satellite at the same distance from an off-origin centre', () => {
    const center = { x: 500, y: 300 }

    for (const point of radialLayout(center, 7, 180)) {
      expect(Math.hypot(point.x - center.x, point.y - center.y)).toBeCloseTo(180, 0)
    }
  })
})

describe('radiusFor', () => {
  it('never goes under the minimum for a small star', () => {
    expect(radiusFor(2, 200, 160)).toBe(160)
  })

  it('grows with the number of satellites so they do not overlap', () => {
    expect(radiusFor(20, 200, 160)).toBeGreaterThan(radiusFor(10, 200, 160))
  })
})
