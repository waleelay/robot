/** Figma 85783:8749 Group 1321316583: 14x18, interval 104 */
export const PATH_ARROW_WIDTH = 14
export const PATH_ARROW_HEIGHT = 18
export const PATH_ARROW_INTERVAL = 104

function polylineLength(points) {
  let total = 0
  for (let i = 1; i < points.length; i++) {
    total += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
  }
  return total
}

function pointAndTangent(points, distance) {
  if (!points.length) return null
  let rest = Math.max(0, distance)
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]
    const b = points[i]
    const len = Math.hypot(b.x - a.x, b.y - a.y)
    if (len < 1e-6) continue
    if (rest > len) {
      rest -= len
      continue
    }
    const t = rest / len
    const dx = b.x - a.x
    const dy = b.y - a.y
    return {
      x: a.x + dx * t,
      y: a.y + dy * t,
      deg: Math.atan2(dy, dx) * 180 / Math.PI + 180
    }
  }
  const n = points.length
  const a = points[n - 2]
  const b = points[n - 1]
  return {
    x: b.x,
    y: b.y,
    deg: Math.atan2(b.y - a.y, b.x - a.x) * 180 / Math.PI + 180
  }
}

export function buildPathDirectionArrows(points, zoom = 1) {
  if (!points || points.length < 2) return []
  const z = Number(zoom) || 1
  const interval = PATH_ARROW_INTERVAL / z
  const pad = 8 / z
  const total = polylineLength(points)
  if (total < interval) {
    if (total < PATH_ARROW_HEIGHT / z) return []
    const mid = pointAndTangent(points, total / 2)
    return mid ? [mid] : []
  }
  const arrows = []
  for (let d = interval; d < total - pad; d += interval) {
    const item = pointAndTangent(points, d)
    if (item) arrows.push(item)
  }
  return arrows
}
