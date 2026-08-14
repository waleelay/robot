import {
  ENABLE_LIANTONG_SLAM_MOCK,
  ENABLE_LIANTONG_TASK_EXECUTION_MOCK,
  LIANTONG_TASK_MOVE_PIXELS_PER_SECOND
} from '../../../js/constants/gisMapPoints.js'
import { buildPathDirectionArrows } from './path-direction-arrows.js'

const TEXT_RUNNING = '\u6267\u884c\u4e2d'
const TEXT_NAV_MODE = '\u5bfc\u822a\u6a21\u5f0f'
const TEXT_COMPLETED = '\u5df2\u5b8c\u6210'
const TEXT_MANUAL_MODE = '\u624b\u52a8\u6a21\u5f0f'
const TEXT_NO_PATH = '\u65e0\u6cd5\u627e\u5230\u5b89\u5168\u8def\u5f84\uff0c\u8bf7\u5c1d\u8bd5\u5176\u4ed6\u7ec8\u70b9'

function polylineLength(points) {
  let total = 0
  for (let i = 1; i < points.length; i++) {
    total += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
  }
  return total
}

function pointAtDistance(points, distance) {
  if (!points.length) return null
  if (distance <= 0) return { x: points[0].x, y: points[0].y }
  let rest = distance
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
    return {
      x: a.x + (b.x - a.x) * t,
      y: a.y + (b.y - a.y) * t
    }
  }
  const last = points[points.length - 1]
  return { x: last.x, y: last.y }
}

function splitPolyline(points, distance) {
  if (!points.length) return { traveled: [], remaining: [] }
  if (distance <= 0) return { traveled: [points[0]], remaining: points.slice() }
  let rest = distance
  const traveled = [points[0]]
  for (let i = 1; i < points.length; i++) {
    const a = points[i - 1]
    const b = points[i]
    const len = Math.hypot(b.x - a.x, b.y - a.y)
    if (len < 1e-6) continue
    if (rest >= len) {
      traveled.push(b)
      rest -= len
      continue
    }
    const t = rest / len
    const mid = { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t }
    traveled.push(mid)
    return { traveled, remaining: [mid, ...points.slice(i)] }
  }
  return { traveled, remaining: [points[points.length - 1]] }
}

function toPointsAttr(points) {
  if (!points || points.length < 2) return ''
  return points.map(p => `${p.x},${p.y}`).join(' ')
}

export default {
  name: 'MockTaskExecution',
  data() {
    return {
      mockExecTraveledPx: 0,
      mockExecRaf: 0,
      mockExecLastTs: 0,
      mockExecDone: false,
      mockExecBindTaskId: null,
      mockExecRobotId: '',
      mockExecWalkPixels: []
    }
  },
  computed: {
    canRunMockTaskExecution() {
      return ENABLE_LIANTONG_SLAM_MOCK && ENABLE_LIANTONG_TASK_EXECUTION_MOCK && this.hasPreview
    },
    mockExecutionTaskId() {
      return this.mockExecBindTaskId || null
    },
    mockExecutionPathLayer() {
      const pixels = this.mockExecWalkPixels
      if (!pixels.length || pixels.length < 2) return null
      const split = splitPolyline(pixels, this.mockExecTraveledPx)
      return {
        taskId: this.mockExecBindTaskId,
        traveledPoints: toPointsAttr(split.traveled),
        arrows: buildPathDirectionArrows(split.traveled, this.zoom)
      }
    }
  },
  beforeDestroy() {
    this.stopMockTaskExecution()
  },
  methods: {
    nearestWalkablePixel(x, y) {
      const grid = this.grid
      const W = this.W
      const H = this.H
      if (!grid || !W || !H) return [x, y]
      const ix = Math.max(0, Math.min(W - 1, Math.round(x)))
      const iy = Math.max(0, Math.min(H - 1, Math.round(y)))
      if (grid[iy] && grid[iy][ix] === 0) return [ix, iy]
      for (let r = 1; r <= 16; r++) {
        for (let dy = -r; dy <= r; dy++) {
          for (let dx = -r; dx <= r; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue
            const nx = ix + dx
            const ny = iy + dy
            if (ny < 0 || ny >= H || nx < 0 || nx >= W) continue
            if (grid[ny] && grid[ny][nx] === 0) return [nx, ny]
          }
        }
      }
      return [ix, iy]
    },
    buildMockWalkPixels(startPoint, endPoint) {
      if (!startPoint || !endPoint) return null
      const origin = [Math.round(startPoint[0]), Math.round(startPoint[1])]
      const dest = [Math.round(endPoint[0]), Math.round(endPoint[1])]
      const start = this.nearestWalkablePixel(origin[0], origin[1])
      const end = this.nearestWalkablePixel(dest[0], dest[1])
      if (this.grid && this.W && this.H && typeof this.aStar === 'function') {
        const path = this.aStar(start, end)
        if (!path || path.length < 2) return null
        const pixels = path.map(p => ({ x: p[0], y: p[1] }))
        if (pixels[0].x !== origin[0] || pixels[0].y !== origin[1]) {
          pixels.unshift({ x: origin[0], y: origin[1] })
        }
        return pixels
      }
      return [{ x: origin[0], y: origin[1] }, { x: dest[0], y: dest[1] }]
    },
    startMockTaskExecution({ robotId, taskId, walkPixels } = {}) {
      const pixels = Array.isArray(walkPixels) ? walkPixels : []
      if (!this.canRunMockTaskExecution || pixels.length < 2 || !robotId) return
      this.stopMockTaskExecution()
      this.mockExecRobotId = robotId
      this.mockExecBindTaskId = taskId || null
      this.mockExecWalkPixels = pixels
      this.mockExecTraveledPx = 0
      this.mockExecDone = false
      this._mockSnapX = undefined
      this._mockSnapY = undefined
      this.ensureMockTaskRunning()
      this.mockExecLastTs = 0
      const loop = (ts) => {
        if (!this.mockExecRaf) return
        if (!this.mockExecLastTs) this.mockExecLastTs = ts
        const dt = Math.min(0.25, (ts - this.mockExecLastTs) / 1000)
        this.mockExecLastTs = ts
        this.advanceMockTaskExecution(dt)
        if (!this.mockExecDone) {
          this.mockExecRaf = requestAnimationFrame(loop)
        } else {
          this.mockExecRaf = 0
        }
      }
      this.mockExecRaf = requestAnimationFrame(loop)
    },
    stopMockTaskExecution() {
      const id = this.mockExecRaf
      this.mockExecRaf = 0
      if (id) cancelAnimationFrame(id)
      this.mockExecLastTs = 0
    },
    clearMockTaskExecution() {
      this.stopMockTaskExecution()
      this.mockExecWalkPixels = []
      this.mockExecBindTaskId = null
      this.mockExecRobotId = ''
      this.mockExecTraveledPx = 0
      this.mockExecDone = false
      this._mockSnapX = undefined
      this._mockSnapY = undefined
    },
    ensureMockTaskRunning() {
      const robotId = this.mockExecRobotId
      const robot = this.robotBaseInfo?.[robotId]
      const taskId = this.mockExecBindTaskId
      if (!robot || !taskId) return
      const task = this.taskData?.[taskId]
      if (task && String(task.status).toLowerCase() !== 'running') {
        this.$store.commit('websocketExtraData/SET_TASK_INFO', {
          ...task,
          status: 'running',
          statusName: TEXT_RUNNING
        })
      }
      this.$store.commit('websocketExtraData/SET_ROBOT_BASE_INFO', {
        robotId,
        robotInfo: {
          ...robot,
          controlMode: TEXT_NAV_MODE
        }
      })
    },
    advanceMockTaskExecution(dt) {
      const pixels = this.mockExecWalkPixels
      const robotId = this.mockExecRobotId
      if (pixels.length < 2 || !robotId) return
      const total = polylineLength(pixels)
      if (total <= 0) return
      this.mockExecTraveledPx = Math.min(total, this.mockExecTraveledPx + LIANTONG_TASK_MOVE_PIXELS_PER_SECOND * dt)
      const pos = pointAtDistance(pixels, this.mockExecTraveledPx)
      if (!pos) return
      const zoom = Number(this.zoom) || 1
      const snapX = Math.round(pos.x * zoom) / zoom
      const snapY = Math.round(pos.y * zoom) / zoom
      const moved = this._mockSnapX !== snapX || this._mockSnapY !== snapY
      if (moved) {
        this._mockSnapX = snapX
        this._mockSnapY = snapY
        const slam = this.pixelToMapPoint({ x: snapX, y: snapY }, this.map, { round: false })
        if (slam) {
          const ahead = pointAtDistance(pixels, Math.min(total, this.mockExecTraveledPx + 1))
          const nextSlam = ahead ? this.pixelToMapPoint(ahead, this.map, { round: false }) : slam
          const yaw = Math.atan2(
            (nextSlam?.coordinateY ?? slam.coordinateY) - slam.coordinateY,
            (nextSlam?.coordinateX ?? slam.coordinateX) - slam.coordinateX
          )
          const prev = this.robotLocation?.[robotId] || this.robotBaseInfo?.[robotId]?.location || {}
          this.$store.commit('websocketExtraData/SET_ROBOT_LOCATION', {
            robotId,
            location: {
              ...prev,
              mapId: this.map?.id,
              x: slam.coordinateX,
              y: slam.coordinateY,
              coordinateX: slam.coordinateX,
              coordinateY: slam.coordinateY,
              yaw
            }
          })
        }
      }
      if (this.mockExecTraveledPx >= total) {
        this.finishMockTaskExecution()
      }
    },
    finishMockTaskExecution() {
      this.mockExecDone = true
      this.stopMockTaskExecution()
      if (typeof this.hideTempTaskDestination === 'function') {
        this.hideTempTaskDestination()
      }
      const taskId = this.mockExecBindTaskId
      const robotId = this.mockExecRobotId
      const task = taskId ? this.taskData?.[taskId] : null
      if (task) {
        this.$store.commit('websocketExtraData/SET_TASK_INFO', {
          ...task,
          status: 'completed',
          statusName: TEXT_COMPLETED
        })
      }
      const robot = this.robotBaseInfo?.[robotId]
      if (robot) {
        this.$store.commit('websocketExtraData/SET_ROBOT_BASE_INFO', {
          robotId,
          robotInfo: {
            ...robot,
            controlMode: TEXT_MANUAL_MODE
          }
        })
      }
    },
    showMockNoPathError() {
      if (typeof this.showSlamError === 'function') this.showSlamError(TEXT_NO_PATH)
    }
  }
}
