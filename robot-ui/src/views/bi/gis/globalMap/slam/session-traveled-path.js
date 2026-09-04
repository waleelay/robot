import { isFixedCamera } from '@/constants/robot.js'
import { normalizeExecutionStatus } from '../../../patrol/business/execution-status'
import { listTasksForRobot } from '../../../patrol/business/task-equipment'
import { buildPathDirectionArrows } from './path-direction-arrows.js'

const WATCHED_STATUSES = new Set(['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING', 'TERMINATING', 'FAILED'])

function toPointsAttr(points) {
  return points.length < 2 ? '' : points.map(point => `${point.x},${point.y}`).join(' ')
}

export default {
  name: 'SessionTraveledPath',
  data() {
    return {
      trajectoryOwnsWatching: false,
      trajectoryPreviousTargets: [],
      trajectoryPreviousMapId: null
    }
  },
  computed: {
    trajectoryRecords() {
      return this.$store.state.websocketExtraData?.trajectoryByRobot || {}
    },
    trajectoryWatchTargets() {
      if (this.showSmall || !this.hasPreview || this.map?.id == null) return []
      const robots = this.slamOfRobot?.[String(this.map.id)]?.robots || []
      return robots.map(robot => {
        const robotId = robot.robotId
        if (!this.canWatchTrajectory(robotId)) return null
        const robotTasks = listTasksForRobot(this.taskData, robotId)
        const task = robotTasks.find(item =>
          WATCHED_STATUSES.has(normalizeExecutionStatus(item?.status)) && item?.workflowInstanceId != null)
        if (task) return { robotId, workflowInstanceId: task.workflowInstanceId }
        // 任务摘要降级不等于任务结束，只沿用本页已订阅且仍在当前地图的目标。
        if (this.$store.state.websocketExtraData?.dataQuality?.tasks?.degraded
          && !robotTasks.length) {
          return this.trajectoryPreviousTargets.find(item => String(item.robotId) === String(robotId)) || null
        }
        return null
      }).filter(Boolean)
    },
    trajectoryWatchKey() {
      const targets = this.trajectoryWatchTargets
        .map(item => `${item.robotId}:${item.workflowInstanceId}`)
        .sort()
        .join('|')
      return `${this.map?.id ?? ''}#${targets}`
    },
    sessionTraveledPathLayers() {
      if (this.showSmall || !this.map) return []
      const robotIds = new Set((this.slamOfRobot?.[String(this.map.id)]?.robots || [])
        .map(item => String(item.robotId)))
      return Object.entries(this.trajectoryRecords).map(([robotId, record]) => {
        if (!robotIds.has(String(robotId))) return null
        const points = (record?.points || []).map(point =>
          this.mapPointToPixel({ coordinateX: point.x, coordinateY: point.y }, this.map)
        ).filter(Boolean)
        const traveledPoints = toPointsAttr(points)
        if (!traveledPoints) return null
        return {
          robotId,
          workflowInstanceId: record.workflowInstanceId,
          traveledPoints,
          arrows: buildPathDirectionArrows(points, this.zoom)
        }
      }).filter(Boolean)
    }
  },
  watch: {
    trajectoryWatchKey: {
      immediate: true,
      handler() {
        this.syncTrajectoryWatching()
      }
    }
  },
  beforeDestroy() {
    if (!this.trajectoryOwnsWatching) return
    this.$store.dispatch('websocketRobot/syncTrajectoryWatchTargets', [])
    this.$store.dispatch('websocketExtraData/clearAllTrajectories')
  },
  methods: {
    canWatchTrajectory(robotId) {
      if (!robotId || String(robotId).startsWith('mock-')) return false
      return !isFixedCamera(this.robotBaseInfo?.[robotId] || {})
    },
    syncTrajectoryWatching() {
      if (this.showSmall) return
      this.trajectoryOwnsWatching = true
      const mapId = this.map?.id ?? null
      if (this.trajectoryPreviousMapId != null && String(this.trajectoryPreviousMapId) !== String(mapId)) {
        this.$store.dispatch('websocketExtraData/clearAllTrajectories')
        this.trajectoryPreviousTargets = []
      }
      const next = this.trajectoryWatchTargets
      const nextKeys = new Set(next.map(item => `${item.robotId}:${item.workflowInstanceId}`))
      const currentRobotIds = new Set((this.slamOfRobot?.[String(mapId)]?.robots || [])
        .map(item => String(item.robotId)))
      this.trajectoryPreviousTargets.forEach(target => {
        if (nextKeys.has(`${target.robotId}:${target.workflowInstanceId}`)) return
        if (currentRobotIds.has(String(target.robotId))) {
          this.$store.dispatch('websocketExtraData/finishTrajectory', target)
        } else {
          this.$store.dispatch('websocketExtraData/clearTrajectory', target.robotId)
        }
      })
      next.forEach(target => {
        const record = this.trajectoryRecords[String(target.robotId)]
        if (record && String(record.workflowInstanceId) !== String(target.workflowInstanceId)) {
          this.$store.dispatch('websocketExtraData/clearTrajectory', target.robotId)
        }
      })
      this.trajectoryPreviousMapId = mapId
      this.trajectoryPreviousTargets = next.map(item => ({ ...item }))
      this.$store.dispatch('websocketRobot/syncTrajectoryWatchTargets', next)
    },
    getTrajectoryLocation(robotId, normalLocation) {
      if (this.showSmall) return normalLocation
      const record = this.trajectoryRecords[String(robotId)]
      const pose = record?.currentPose
      if (!pose || !Number.isFinite(Number(pose.x)) || !Number.isFinite(Number(pose.y))) return normalLocation
      if (record.stopped && String(normalLocation?.updatedAt) !== String(record.stoppedLocationUpdatedAt)) {
        return normalLocation
      }
      return { ...normalLocation, x: pose.x, y: pose.y, yaw: pose.yaw }
    }
  }
}
