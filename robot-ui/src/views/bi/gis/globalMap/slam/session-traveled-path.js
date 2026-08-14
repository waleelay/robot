import { isFixedCamera } from '@/constants/robot.js'
import { isActiveTaskStatus } from '../../../patrol/business/execution-status'
import { buildPathDirectionArrows } from './path-direction-arrows.js'

const MAX_SESSION_TRAVELED_POINTS = 4000

function toPointsAttr(points) {
  if (!points || points.length < 2) return ''
  return points.map(p => `${p.x},${p.y}`).join(' ')
}

export default {
  name: 'SessionTraveledPath',
  data() {
    return {
      // ????????¹×??????????????????¡¤??????
      sessionTraveledByRobot: {}
    }
  },
  computed: {
    sessionTraveledSyncKey() {
      const mapId = this.map?.id
      const robots = this.slamOfRobot?.[String(mapId)]?.robots || []
      const live = robots.map((item) => {
        const robotId = item.robotId
        const loc = this.robotLocation?.[robotId] || {}
        const info = this.robotBaseInfo?.[robotId] || {}
        const taskId = info.runningTaskId
        const status = this.taskData?.[taskId]?.status || ''
        const x = loc.x ?? loc.coordinateX ?? ''
        const y = loc.y ?? loc.coordinateY ?? ''
        return `${robotId}:${taskId || ''}:${status}:${x}:${y}`
      }).join('|')
      const stored = Object.keys(this.sessionTraveledByRobot).map((robotId) => {
        const rec = this.sessionTraveledByRobot[robotId] || {}
        const info = this.robotBaseInfo?.[robotId] || {}
        const taskId = info.runningTaskId || rec.taskId
        const status = this.taskData?.[taskId]?.status || ''
        return `${robotId}:${taskId || ''}:${status}`
      }).join('|')
      return `${live}#${stored}`
    },
    sessionTraveledPathLayers() {
      const mapId = this.map?.id
      return Object.entries(this.sessionTraveledByRobot).map(([robotId, rec]) => {
        if (!rec || String(rec.mapId) !== String(mapId)) return null
        if (!rec.points || rec.points.length < 2) return null
        const traveledPoints = toPointsAttr(rec.points)
        if (!traveledPoints) return null
        return {
          robotId,
          taskId: rec.taskId,
          traveledPoints,
          arrows: buildPathDirectionArrows(rec.points, this.zoom)
        }
      }).filter(Boolean)
    }
  },
  watch: {
    sessionTraveledSyncKey: {
      immediate: true,
      handler() {
        this.syncSessionTraveledPaths()
      }
    }
  },
  methods: {
    canTrackSessionTraveled(robotId) {
      if (!robotId) return false
      // ???????? mock-task-execution ??????????????
      if (String(robotId).startsWith('mock-')) return false
      if (this.mockExecRobotId && String(this.mockExecRobotId) === String(robotId) && !this.mockExecDone) {
        return false
      }
      const robot = this.robotBaseInfo?.[robotId] || {}
      if (isFixedCamera(robot)) return false
      return true
    },
    getSessionActiveTaskId(robotId) {
      const robot = this.robotBaseInfo?.[robotId] || {}
      const taskId = robot.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return null
      const task = this.taskData?.[taskId] || robot.runningTask
      if (task?.status && !isActiveTaskStatus(task.status)) return null
      return taskId
    },
    getSessionSnappedPixel(robotId) {
      const location = this.robotLocation?.[robotId] || this.robotBaseInfo?.[robotId]?.location
      if (!location || !this.map) return null
      const locMapId = location.mapId
      if (locMapId != null && this.map.id != null && String(locMapId) !== String(this.map.id)) return null
      const coordinateX = location.x ?? location.coordinateX
      const coordinateY = location.y ?? location.coordinateY
      if (coordinateX === undefined || coordinateX === null || coordinateY === undefined || coordinateY === null) {
        return null
      }
      const pixel = this.mapPointToPixel({ coordinateX, coordinateY }, this.map)
      if (!pixel) return null
      const zoom = Number(this.zoom) || 1
      return {
        x: Math.round(pixel.x * zoom) / zoom,
        y: Math.round(pixel.y * zoom) / zoom
      }
    },
    syncSessionTraveledPaths() {
      if (this.hasPreview && this.map?.id) {
        const mapId = this.map.id
        const robots = this.slamOfRobot?.[String(mapId)]?.robots || []
        robots.forEach((item) => {
          const robotId = item.robotId
          if (!this.canTrackSessionTraveled(robotId)) return
          const taskId = this.getSessionActiveTaskId(robotId)
          if (!taskId) return
          this.appendSessionTraveledPoint(robotId, taskId, mapId)
        })
      }
      Object.keys(this.sessionTraveledByRobot).forEach((robotId) => {
        const rec = this.sessionTraveledByRobot[robotId]
        const taskId = this.getSessionActiveTaskId(robotId)
        if (!taskId || !rec || String(taskId) !== String(rec.taskId)) {
          this.clearSessionTraveledPath(robotId)
        }
      })
    },
    appendSessionTraveledPoint(robotId, taskId, mapId) {
      const pixel = this.getSessionSnappedPixel(robotId)
      if (!pixel) return
      const rec = this.sessionTraveledByRobot[robotId]
      if (!rec || String(rec.taskId) !== String(taskId) || String(rec.mapId) !== String(mapId)) {
        this.$set(this.sessionTraveledByRobot, robotId, {
          taskId,
          mapId,
          points: [pixel]
        })
        return
      }
      const last = rec.points[rec.points.length - 1]
      if (last && last.x === pixel.x && last.y === pixel.y) return
      const points = rec.points.concat(pixel)
      const nextPoints = points.length > MAX_SESSION_TRAVELED_POINTS
        ? points.slice(points.length - MAX_SESSION_TRAVELED_POINTS)
        : points
      this.$set(this.sessionTraveledByRobot, robotId, {
        ...rec,
        points: nextPoints
      })
    },
    clearSessionTraveledPath(robotId) {
      if (!this.sessionTraveledByRobot[robotId]) return
      this.$delete(this.sessionTraveledByRobot, robotId)
      if (this.robotId && String(this.robotId) === String(robotId)
        && typeof this.hideTempTaskDestination === 'function') {
        this.hideTempTaskDestination()
      }
    },
    clearAllSessionTraveledPaths() {
      this.sessionTraveledByRobot = {}
    }
  }
}
