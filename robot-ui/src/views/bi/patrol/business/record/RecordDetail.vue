<template>
  <div>
    <div class="page-action-header table-btns">
      <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="$emit('close')">返回执行记录</el-button>
      <div class="page-action-header__title">
        {{ instance.workflowName || '执行记录详情' }}
        <span>{{ instance.businessKey || instance.id || '-' }}</span>
      </div>
      <div class="page-action-header__actions">
        <span class="status" :class="statusTagType(instance.status)">{{ statusLabel(instance.status) }}</span>
        <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" :loading="loading" @click="loadReplay">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="replay-layout">
      <section class="summary-grid">
        <div class="summary-item"><span>开始时间</span><strong>{{ formatDateTime(replayStartedAt) }}</strong></div>
        <div class="summary-item"><span>结束时间</span><strong>{{ formatDateTime(replayCompletedAt) }}</strong></div>
        <div class="summary-item"><span>告警</span><strong>{{ alarmEvents.length }}</strong></div>
        <div class="summary-item"><span>视频源</span><strong>{{ playbackItems.length }}</strong></div>
        <div class="summary-item"><span>轨迹状态</span><strong>{{ trackStatusLabel(replayTrackStatus) }}</strong></div>
      </section>

      <section class="playback-panel">
        <div class="playback-toolbar">
          <el-button
            circle
            type="primary"
            :icon="playing ? 'el-icon-video-pause' : 'el-icon-video-play'"
            :disabled="!durationSeconds"
            @click="togglePlay"
          />
          <div class="time-label">{{ formatDateTime(currentDateTime) }}</div>
          <el-slider
            v-model="currentOffset"
            class="time-slider"
            :min="0"
            :max="durationSeconds"
            :step="1"
            :show-tooltip="false"
            @change="syncVideos"
          />
          <div class="time-label right">{{ formatOffset(currentOffset) }} / {{ formatOffset(durationSeconds) }}</div>
          <el-select v-model="playbackRate" class="rate-select" @change="syncVideos">
            <el-option label="0.5x" :value="0.5" />
            <el-option label="1x" :value="1" />
            <el-option label="2x" :value="2" />
            <el-option label="4x" :value="4" />
          </el-select>
        </div>
        <div class="alarm-strip">
          <button
            v-for="alarm in alarmMarkers"
            :key="alarm.id"
            class="alarm-marker"
            :style="{ left: alarm.percent + '%' }"
            :title="alarm.title"
            @click="jumpToAlarm(alarm)"
          />
        </div>

        <div class="replay-grid">
          <div class="map-panel">
            <div class="panel-title">
              <strong>运行轨迹</strong>
              <span v-if="samplesLoading">加载中</span>
              <span v-else>{{ trackGroups.length || groupedTrackSamples.length }} 台设备 / {{ timelineSamples.length }} 个采样点</span>
            </div>
            <div v-if="trackGroups.length" class="track-legend">
              <el-checkbox-group v-model="visibleTrackKeys">
                <el-checkbox v-for="group in trackGroups" :key="trackGroupKey(group)" :label="trackGroupKey(group)">
                  {{ group.deviceName || group.serialNumber || '设备轨迹' }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <div v-if="timelineSamples.length" class="track-stage">
              <svg class="track-svg" :viewBox="trackViewBox" preserveAspectRatio="xMidYMid meet">
                <image
                  v-if="hasCalibratedMap && mapImageUrl"
                  :href="mapImageUrl"
                  x="0"
                  y="0"
                  :width="Number(replayMap.previewWidth)"
                  :height="Number(replayMap.previewHeight)"
                  preserveAspectRatio="xMidYMid meet"
                />
                <g v-if="!hasCalibratedMap || !mapImageUrl" class="track-fallback-grid">
                  <rect x="0" y="0" width="1000" height="560" />
                </g>
                <g v-for="line in fullTrackPolylines" :key="'full-' + line.key">
                  <polyline :points="line.points" class="track-line full" :style="{ stroke: line.color }" />
                </g>
                <g v-for="line in visitedTrackPolylines" :key="'visited-' + line.key">
                  <polyline :points="line.points" class="track-line visited-halo" />
                  <polyline :points="line.points" class="track-line visited" :style="{ stroke: line.color }" />
                </g>
                <g v-for="point in currentTrackPoints" :key="'current-' + point.key">
                  <circle class="track-current-halo" :cx="projectPoint(point.sample).x" :cy="projectPoint(point.sample).y" r="14" />
                  <circle class="track-current" :style="{ fill: point.color }" :cx="projectPoint(point.sample).x" :cy="projectPoint(point.sample).y" r="8" />
                  <text class="track-label" :x="projectPoint(point.sample).x + 12" :y="projectPoint(point.sample).y - 12">{{ point.label }}</text>
                </g>
              </svg>
              <div v-if="hasCalibratedMap && !mapImageUrl" class="map-overlay-state">{{ mapImageStatus }}</div>
              <div v-else-if="!hasCalibratedMap" class="map-overlay-state">地图标定缺失，已使用临时网格展示</div>
            </div>
            <el-empty v-else description="轨迹文件处理中或未上传" />
          </div>

          <div class="video-panel">
            <div class="panel-title">
              <strong>视频</strong>
              <span>{{ playbackItems.length }} 路</span>
            </div>
            <div v-if="playbackItems.length" class="video-groups">
              <section v-for="group in renderVideoGroups" :key="group.key" class="video-device-group">
                <div class="video-device-header">
                  <strong>{{ group.deviceName || group.serialNumber || '设备视频' }}</strong>
                  <span>{{ group.items.length }} 路</span>
                </div>
                <div class="video-list">
                  <div
                    v-for="video in group.items"
                    :key="videoKey(video, video.flatIndex)"
                    class="video-card"
                    :class="{ 'is-primary': primaryVideoKey === videoKey(video, video.flatIndex) }"
                    @click="primaryVideoKey = videoKey(video, video.flatIndex)"
                  >
                    <video
                      v-if="videoUrl(video)"
                      ref="videoPlayers"
                      muted
                      controls
                      preload="auto"
                      :data-video-index="video.flatIndex"
                      :data-video-url="videoUrl(video)"
                      @loadedmetadata="syncVideos"
                      @canplay="handleVideoCanPlay"
                      @play="handleVideoNativePlay"
                      @pause="handleVideoNativePause"
                    />
                    <div v-else class="video-placeholder">
                      <strong>{{ videoFileId(video) || '-' }}</strong>
                      <span>{{ videoSourceLabel(video) }}</span>
                    </div>
                    <div v-if="videoPlaybackState(video).state !== 'PLAYING'" class="video-state">
                      {{ videoPlaybackState(video).label }}
                    </div>
                    <div class="video-meta">
                      <strong>{{ mediaTypeLabel(video.mediaType) }}</strong>
                      <span>{{ videoSourceLabel(video) }}</span>
                    </div>
                  </div>
                </div>
              </section>
            </div>
            <el-empty v-else description="暂无视频结果" />
          </div>
        </div>
      </section>

      <section class="panel-row">
        <div class="panel">
          <div class="panel-title"><strong>告警信息</strong><span>{{ alarmEvents.length }} 条</span></div>
          <div v-if="alarmEvents.length" class="alarm-list">
            <button
              v-for="(event, index) in alarmEvents"
              :key="event.id || event.eventId || index"
              class="alarm-card"
              type="button"
              @click="jumpToAlarmEvent(event)"
            >
              <img
                v-if="alarmImageUrl(event)"
                class="alarm-card__image"
                :src="alarmImageUrl(event)"
                :alt="alarmType(event)"
              >
              <div v-else class="alarm-card__empty">告警</div>
              <div class="alarm-card__body">
                <div class="alarm-card__meta">
                  <span>{{ formatDateTime(alarmTimeValue(event)) }}</span>
                  <span class="status" :class="alarmStatusType(event)">{{ alarmStatusLabel(event) }}</span>
                </div>
                <strong>{{ alarmType(event) }}</strong>
                <p>{{ alarmContent(event) }}</p>
                <span>{{ alarmDeviceLabel(event) }}</span>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无告警" />
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import HlsModule from 'hls.js'
import { getTaskRecordReplay, previewImageBlob } from '@/api/new-bi'
import { getFilePlayUrl } from '@/api/media'

const ImportedHls = HlsModule && (HlsModule.default || HlsModule)

export default {
  name: 'BiPatrolBusiness2RecordDetail',
  props: {
    id: {
      type: [String, Number],
      required: true
    }
  },
  data() {
    return {
      loading: false,
      samplesLoading: false,
      replay: null,
      visibleTrackKeys: [],
      currentOffset: 0,
      playing: false,
      playbackRate: 1,
      primaryVideoKey: '',
      videoPlayUrlMap: {},
      videoPlayLoadingIds: [],
      mapImageUrl: '',
      mapImageStatus: '地图加载中',
      playTimer: null,
      syncingVideos: false,
      hlsPlayers: new Map(),
      mapImageObjectUrl: '',
      mapImageLoadSeq: 0
    }
  },
  computed: {
    instance() {
      return (this.replay && this.replay.detail && this.replay.detail.instance) || {}
    },
    trackGroups() {
      return (this.replay && this.replay.trackGroups) || []
    },
    alarmEvents() {
      return (this.replay && this.replay.alarmEvents) || []
    },
    videoGroups() {
      return (this.replay && this.replay.videoGroups) || []
    },
    renderVideoGroups() {
      let flatIndex = 0
      const groups = this.videoGroups.length
        ? this.videoGroups
        : [{ key: 'legacy-video-group', items: (this.replay && this.replay.mediaPlaybackItems) || this.videoResults }]
      return groups.map((group, groupIndex) => {
        const sourceItems = group.items || group.videoResults || group.mediaPlaybackItems || []
        const items = sourceItems.map(item => Object.assign({}, this.normalizeVideoItem(item), {
          flatIndex: flatIndex++,
          groupDeviceTaskInstanceId: group.deviceTaskInstanceId,
          groupSerialNumber: group.serialNumber,
          groupDeviceName: group.deviceName
        }))
        return Object.assign({}, group, {
          key: this.videoGroupKey(group, groupIndex),
          items
        })
      }).filter(group => group.items.length)
    },
    videoResults() {
      return (this.replay && this.replay.videoResults) || []
    },
    playbackItems() {
      return this.renderVideoGroups.reduce((result, group) => result.concat(group.items), [])
    },
    replayMap() {
      return (this.replay && this.replay.replayMap) || null
    },
    timelineSamples() {
      return this.mergeSamples([], this.trackGroups.reduce((result, group) => {
        const samples = Array.isArray(group.samples) ? group.samples : []
        return result.concat(samples.map(sample => Object.assign({}, sample, {
          deviceTaskInstanceId: sample.deviceTaskInstanceId || group.deviceTaskInstanceId,
          serialNumber: sample.serialNumber || group.serialNumber,
          deviceName: sample.deviceName || group.deviceName,
          color: sample.color || group.color
        })))
      }, []))
    },
    replayStartedAt() {
      return (this.replay && (this.replay.startedAt || this.replay.timelineStartAt)) || this.instance.startedAt
    },
    replayCompletedAt() {
      return (this.replay && (this.replay.completedAt || this.replay.timelineEndAt)) || this.instance.completedAt
    },
    replayTrackStatus() {
      return (this.replay && this.replay.trackStatus) || this.instance.trackStatus
    },
    hasCalibratedMap() {
      const map = this.replayMap
      return Boolean(map && map.id && map.previewFileId && Number(map.previewWidth) && Number(map.previewHeight) && Number(map.resolution) > 0 && map.originX !== undefined && map.originY !== undefined && map.originYaw !== undefined)
    },
    trackViewBox() {
      return this.hasCalibratedMap ? `0 0 ${Number(this.replayMap.previewWidth)} ${Number(this.replayMap.previewHeight)}` : '0 0 1000 560'
    },
    startedAt() {
      return this.parseDate(this.replayStartedAt)
    },
    completedAt() {
      const completedAt = this.parseDate(this.replayCompletedAt)
      if (completedAt) return completedAt
      const alarmTimes = this.alarmEvents.map(event => this.parseDate(this.alarmTimeValue(event))).filter(Boolean)
      if (alarmTimes.length) {
        return new Date(Math.max.apply(null, alarmTimes.map(date => date.getTime())))
      }
      return this.startedAt ? new Date() : null
    },
    durationSeconds() {
      if (!this.startedAt || !this.completedAt) return 0
      return Math.max(0, Math.floor((this.completedAt.getTime() - this.startedAt.getTime()) / 1000))
    },
    currentDateTime() {
      if (!this.startedAt) return null
      return new Date(this.startedAt.getTime() + this.currentOffset * 1000)
    },
    sortedSamples() {
      return this.timelineSamples.slice().sort((left, right) => this.sampleTime(left) - this.sampleTime(right))
    },
    visibleSamples() {
      return this.sortedSamples.filter(sample => !this.trackGroups.length || this.visibleTrackKeys.indexOf(this.sampleTrackKey(sample)) !== -1)
    },
    trackGroupMap() {
      const map = {}
      this.trackGroups.forEach(group => { map[this.trackGroupKey(group)] = group })
      return map
    },
    groupedTrackSamples() {
      const groups = {}
      this.visibleSamples.forEach(sample => {
        const key = this.sampleTrackKey(sample)
        if (!groups[key]) {
          const configuredGroup = this.trackGroupMap[key]
          groups[key] = {
            key,
            color: configuredGroup && configuredGroup.color ? configuredGroup.color : this.defaultTrackColor(sample),
            label: (configuredGroup && configuredGroup.deviceName) || sample.deviceName || sample.serialNumber || '设备轨迹',
            samples: []
          }
        }
        groups[key].samples.push(sample)
      })
      return Object.keys(groups).map(key => groups[key])
    },
    currentTrackPoints() {
      if (!this.currentDateTime) return []
      return this.groupedTrackSamples.map(group => {
        if (!group.samples.length) return null
        if (this.currentDateTime.getTime() < this.sampleTime(group.samples[0])) return null
        const best = this.interpolatedSampleAt(group.samples, this.currentDateTime)
        return { key: group.key, color: group.color, sample: best, label: best.pointName || best.nodeId || group.label }
      }).filter(Boolean)
    },
    trackBounds() {
      const xs = this.visibleSamples.map(sample => Number(sample.x)).filter(Number.isFinite)
      const ys = this.visibleSamples.map(sample => Number(sample.y)).filter(Number.isFinite)
      if (!xs.length || !ys.length) return { minX: 0, maxX: 1, minY: 0, maxY: 1 }
      const minX = Math.min.apply(null, xs)
      const maxX = Math.max.apply(null, xs)
      const minY = Math.min.apply(null, ys)
      const maxY = Math.max.apply(null, ys)
      return { minX, maxX: maxX === minX ? minX + 1 : maxX, minY, maxY: maxY === minY ? minY + 1 : maxY }
    },
    fullTrackPolylines() {
      return this.buildPolylines(false)
    },
    visitedTrackPolylines() {
      return this.buildPolylines(true)
    },
    deviceSummary() {
      const devices = (this.replay && (this.replay.deviceSummaries || this.instance.deviceSummaries)) || []
      if (!devices.length) return '-'
      if (devices.length === 1) return devices[0].deviceName || devices[0].serialNumber || '-'
      return devices.slice(0, 2).map(item => item.deviceName || item.serialNumber || '-').join('、') + (devices.length > 2 ? ` 等 ${devices.length} 台` : '')
    },
    alarmMarkers() {
      if (!this.startedAt || !this.durationSeconds) return []
      return this.alarmEvents.map((event, index) => {
        const alarmTime = this.alarmTimeValue(event)
        const time = this.parseDate(alarmTime)
        if (!time) return null
        const offset = Math.min(this.durationSeconds, Math.max(0, Math.floor((time.getTime() - this.startedAt.getTime()) / 1000)))
        return {
          id: event.id || event.eventId || `alarm-${index}`,
          offset,
          percent: Math.min(100, Math.max(0, (offset / this.durationSeconds) * 100)),
          title: `${this.formatDateTime(alarmTime)} ${this.alarmType(event)}`
        }
      }).filter(Boolean)
    }
  },
  watch: {
    id: 'loadReplay',
    replayMap: {
      handler(newVal) {
        this.loadReplayMapImage({
          id: newVal && newVal.id,
          cacheKey: newVal && (newVal.previewGeneratedAt || newVal.previewFileId),
          enabled: this.hasCalibratedMap
        })
      },
      immediate: true
    },
    currentOffset() {
      this.syncVideos()
    },
    playbackItems(items) {
      if (!items.length) {
        this.primaryVideoKey = ''
      } else if (!items.some((item, index) => this.videoKey(item, index) === this.primaryVideoKey)) {
        this.primaryVideoKey = this.videoKey(items[0], 0)
      }
      this.loadVideoPlayUrls(items)
    }
  },
  mounted() {
    this.loadReplay()
  },
  beforeDestroy() {
    this.stopPlayback()
    this.destroyAllHlsPlayers()
    this.revokeMapImageUrl()
  },
  updated() {
    this.attachVideoSources()
  },
  methods: {
    async loadReplay() {
      if (!this.id) return
      this.stopPlayback()
      this.loading = true
      try {
        this.replay = this.unwrap(await getTaskRecordReplay(this.id))
        this.visibleTrackKeys = this.trackGroups.map(this.trackGroupKey)
        this.currentOffset = 0
        await this.$nextTick()
        await this.loadVideoPlayUrls(this.playbackItems)
        await this.$nextTick()
        this.attachVideoSources()
        this.syncVideos()
      } catch (error) {
        this.showError(error)
      } finally {
        this.loading = false
      }
    },
    async loadReplayMapImage(options) {
      const seq = ++this.mapImageLoadSeq
      this.revokeMapImageUrl()
      this.mapImageStatus = '地图加载中'
      if (!options || !options.enabled || !options.id) return
      try {
        const res = await previewImageBlob(options.id, options.cacheKey)
        const blob = res && res.data instanceof Blob ? res.data : res
        const nextUrl = URL.createObjectURL(blob)
        if (seq !== this.mapImageLoadSeq) {
          URL.revokeObjectURL(nextUrl)
          return
        }
        this.mapImageObjectUrl = nextUrl
        this.mapImageUrl = nextUrl
      } catch (error) {
        if (seq === this.mapImageLoadSeq) this.mapImageStatus = '地图预览加载失败'
      }
    },
    revokeMapImageUrl() {
      if (this.mapImageObjectUrl) URL.revokeObjectURL(this.mapImageObjectUrl)
      this.mapImageObjectUrl = ''
      this.mapImageUrl = ''
    },
    buildPolylines(visitedOnly) {
      return this.groupedTrackSamples.reduce((result, group) => {
        const samples = visitedOnly && this.currentDateTime
          ? group.samples.filter(sample => this.sampleTime(sample) <= this.currentDateTime.getTime())
          : group.samples
        this.trackSegments(samples).forEach((segment, index) => {
          result.push({ key: `${group.key}-${index}`, color: group.color, points: segment.map(this.projectPointText).join(' ') })
        })
        return result
      }, [])
    },
    mergeSamples(current, incoming) {
      const byKey = {}
      current.concat(incoming).forEach(sample => {
        byKey[`${sample.sampledAt || ''}:${this.sampleTrackKey(sample)}:${sample.nodeId || ''}:${sample.x}:${sample.y}`] = sample
      })
      return Object.keys(byKey).map(key => byKey[key]).sort((left, right) => this.sampleTime(left) - this.sampleTime(right))
    },
    trackSegments(samples) {
      const segments = []
      let current = []
      samples.forEach(sample => {
        if (current.length) {
          const previous = current[current.length - 1]
          if (Math.abs(this.sampleTime(sample) - this.sampleTime(previous)) > 10000) {
            if (current.length > 1) segments.push(current)
            current = []
          }
        }
        current.push(sample)
      })
      if (current.length > 1) segments.push(current)
      return segments
    },
    interpolatedSampleAt(samples, time) {
      if (!samples.length || !time) return samples[0] || {}
      const currentTime = time.getTime()
      let previous = samples[0]
      for (const sample of samples) {
        const timeValue = this.sampleTime(sample)
        if (timeValue >= currentTime) {
          if (sample === previous) return sample
          const previousTime = this.sampleTime(previous)
          const ratio = Math.min(1, Math.max(0, (currentTime - previousTime) / Math.max(1, timeValue - previousTime)))
          return Object.assign({}, sample, {
            x: this.interpolateNumber(previous.x, sample.x, ratio),
            y: this.interpolateNumber(previous.y, sample.y, ratio),
            z: this.interpolateNumber(previous.z, sample.z, ratio),
            yaw: this.interpolateNumber(previous.yaw, sample.yaw, ratio),
            pointName: sample.pointName || previous.pointName,
            nodeId: sample.nodeId || previous.nodeId
          })
        }
        previous = sample
      }
      return samples[samples.length - 1]
    },
    projectPoint(sample) {
      if (this.hasCalibratedMap) {
        const pixel = this.mapSampleToPixel(sample, this.replayMap)
        if (pixel) return pixel
      }
      const bounds = this.trackBounds
      const padding = 36
      return {
        x: padding + ((Number(sample.x) - bounds.minX) / (bounds.maxX - bounds.minX || 1)) * (1000 - padding * 2),
        y: 560 - padding - ((Number(sample.y) - bounds.minY) / (bounds.maxY - bounds.minY || 1)) * (560 - padding * 2)
      }
    },
    mapSampleToPixel(sample, map) {
      if (!map || !Number.isFinite(Number(sample && sample.x)) || !Number.isFinite(Number(sample && sample.y))) return null
      const height = Number(map.previewHeight)
      const resolution = Number(map.resolution)
      const originX = Number(map.originX)
      const originY = Number(map.originY)
      const originYaw = Number(map.originYaw)
      if (!height || !resolution) return null
      const dx = Number(sample.x) - originX
      const dy = Number(sample.y) - originY
      const cos = Math.cos(originYaw)
      const sin = Math.sin(originYaw)
      return { x: (dx * cos + dy * sin) / resolution, y: height - (-dx * sin + dy * cos) / resolution }
    },
    projectPointText(sample) {
      const point = this.projectPoint(sample)
      return `${point.x.toFixed(1)},${point.y.toFixed(1)}`
    },
    togglePlay() {
      this.playing ? this.stopPlayback() : this.startPlayback()
    },
    startPlayback() {
      if (this.playing) return
      this.playing = true
      this.loadVideoPlayUrls(this.playbackItems).then(() => {
        if (this.playing) this.syncVideos()
      })
      this.playTimer = window.setInterval(() => {
        this.currentOffset = Math.min(this.durationSeconds, this.currentOffset + this.playbackRate)
        if (this.currentOffset >= this.durationSeconds) this.stopPlayback()
      }, 1000)
    },
    stopPlayback() {
      this.playing = false
      if (this.playTimer) window.clearInterval(this.playTimer)
      this.playTimer = null
      this.setVideoSyncing(() => this.getVideoRefs().forEach(video => video && video.pause && video.pause()))
    },
    getVideoRefs() {
      const refs = this.$refs.videoPlayers
      if (!refs) return []
      return Array.isArray(refs) ? refs : [refs]
    },
    async loadVideoPlayUrls(items) {
      const videos = Array.isArray(items) ? items : this.playbackItems
      const fileIds = Array.from(new Set(videos.map(video => this.videoFileId(video)).filter(Boolean)))
      const pendingIds = fileIds.filter(fileId => !this.videoPlayUrlMap[fileId] && this.videoPlayLoadingIds.indexOf(fileId) === -1)
      if (!pendingIds.length) {
        this.$nextTick(this.attachVideoSources)
        return
      }
      this.videoPlayLoadingIds = this.videoPlayLoadingIds.concat(pendingIds)
      await Promise.all(pendingIds.map(async fileId => {
        try {
          const result = this.unwrap(await getFilePlayUrl(fileId))
          const playUrl = this.normalizeResourceUrl(result && result.playUrl)
          if (playUrl) {
            this.videoPlayUrlMap = Object.assign({}, this.videoPlayUrlMap, { [fileId]: playUrl })
          }
        } catch (error) {
          this.$message.warning(`视频 ${fileId} 播放地址获取失败`)
        } finally {
          this.videoPlayLoadingIds = this.videoPlayLoadingIds.filter(id => id !== fileId)
        }
      }))
      await this.$nextTick()
      this.attachVideoSources()
    },
    attachVideoSource(video, item) {
      const source = this.videoUrl(item)
      if (!video || !source || (video.dataset && video.dataset.boundVideoUrl === source)) return
      this.destroyHlsPlayer(video)
      video.dataset.boundVideoUrl = source
      const HlsPlayer = this.resolveHlsPlayer()
      if (this.isHlsUrl(source) && !this.nativeHlsSupported(video) && HlsPlayer && HlsPlayer.isSupported()) {
        const startPosition = this.videoStartPosition(item)
        const player = new HlsPlayer({
          autoStartLoad: true,
          startPosition,
          startFragPrefetch: true
        })
        player.on(HlsPlayer.Events.MEDIA_ATTACHED, () => {
          player.loadSource(source)
        })
        player.on(HlsPlayer.Events.MANIFEST_PARSED, () => {
          if (video.dataset && video.dataset.boundVideoUrl === source) {
            player.startLoad(this.videoStartPosition(item))
            this.primeVideoBuffer(video)
            this.syncSingleVideo(video)
          }
        })
        player.attachMedia(video)
        this.hlsPlayers.set(video, player)
        return
      }
      video.src = source
      video.load && video.load()
      this.primeVideoBuffer(video)
      this.$nextTick(() => this.syncSingleVideo(video))
    },
    attachVideoSources() {
      this.getVideoRefs().forEach(video => {
        const index = Number(video && video.dataset && video.dataset.videoIndex)
        this.attachVideoSource(video, this.playbackItems[index])
      })
    },
    destroyHlsPlayer(video) {
      const player = this.hlsPlayers.get(video)
      if (player) {
        player.destroy()
        this.hlsPlayers.delete(video)
      }
      if (video) {
        video.removeAttribute('src')
        if (video.dataset) delete video.dataset.boundVideoUrl
      }
    },
    destroyAllHlsPlayers() {
      this.hlsPlayers.forEach(player => player.destroy())
      this.hlsPlayers.clear()
    },
    nativeHlsSupported(video) {
      return Boolean(video && video.canPlayType && video.canPlayType('application/vnd.apple.mpegurl'))
    },
    isHlsUrl(value) {
      return typeof value === 'string' && (/\.m3u8(\?|#|$)/i.test(value) || value.indexOf('m3u8') !== -1)
    },
    resolveHlsPlayer() {
      if (ImportedHls && ImportedHls.isSupported) return ImportedHls
      return window.Hls && window.Hls.isSupported ? window.Hls : null
    },
    primeVideoBuffer(video) {
      if (!video || this.playing || video.dataset.primeRequested === 'true') return
      video.dataset.primeRequested = 'true'
      video.dataset.primingBuffer = 'true'
      const playPromise = video.play && video.play()
      if (playPromise && playPromise.then) {
        playPromise
          .then(() => {
            if (!this.playing) video.pause()
            delete video.dataset.primingBuffer
          })
          .catch(() => {
            delete video.dataset.primeRequested
            delete video.dataset.primingBuffer
          })
      } else {
        if (!this.playing && video.pause) video.pause()
        delete video.dataset.primingBuffer
      }
    },
    videoStartPosition(video) {
      const current = this.currentDateTime || this.startedAt
      const timing = this.videoTiming(video)
      if (!current || !timing.start) return 0
      return Math.max(0, (current.getTime() - timing.start.getTime()) / 1000)
    },
    syncVideos() {
      const current = this.currentDateTime
      if (!current) return
      this.setVideoSyncing(() => {
        this.getVideoRefs().forEach(video => this.syncSingleVideo(video))
      })
    },
    syncSingleVideo(video) {
      const current = this.currentDateTime
      if (!current || !video) return
      const index = Number(video.dataset && video.dataset.videoIndex)
      const item = this.playbackItems[index]
      if (!item) return
      this.attachVideoSource(video, item)
      const timing = this.videoTiming(item)
      if (!timing.start) return
      const position = this.videoPositionForTimeline(current, timing, item)
      if (position.seekable && Number.isFinite(position.currentTime)) {
        const threshold = this.playing ? 0.8 : 0.05
        if (Math.abs((video.currentTime || 0) - position.currentTime) > threshold) {
          try {
            video.currentTime = position.currentTime
          } catch (error) {
            // Metadata may not be ready yet; loadedmetadata/canplay will resync.
          }
        }
      }
      video.playbackRate = this.playbackRate
      if (this.playing && position.playable && video.paused) video.play().catch(() => undefined)
      if ((!this.playing || !position.playable) && !video.paused) video.pause()
    },
    handleVideoCanPlay(event) {
      if (this.playing) this.syncSingleVideo(event && event.target)
    },
    handleVideoNativePlay(event) {
      if (this.syncingVideos || (event && event.target && event.target.dataset && event.target.dataset.primingBuffer === 'true')) return
      this.syncVideos()
      this.startPlayback()
    },
    handleVideoNativePause(event) {
      if (event && event.target && event.target.dataset && event.target.dataset.primingBuffer === 'true') return
      if (!this.syncingVideos && this.playing) this.stopPlayback()
    },
    setVideoSyncing(callback) {
      this.syncingVideos = true
      try {
        callback()
      } finally {
        window.setTimeout(() => { this.syncingVideos = false }, 0)
      }
    },
    jumpToAlarm(alarm) {
      this.stopPlayback()
      this.currentOffset = Math.min(this.durationSeconds, Math.max(0, alarm.offset || 0))
      this.syncVideos()
    },
    jumpToAlarmEvent(event) {
      if (!this.startedAt) return
      const occurredAt = this.parseDate(this.alarmTimeValue(event))
      if (!occurredAt) return
      this.jumpToAlarm({ offset: Math.floor((occurredAt.getTime() - this.startedAt.getTime()) / 1000) })
    },
    videoUrl(video) {
      const metadata = (video && video.metadata) || {}
      const directUrl = (video && video.playUrl) || metadata.playUrl || metadata.playbackUrl || (video && video.playbackUrl) || ''
      if (directUrl && !this.isDemoUrl(directUrl)) return this.normalizeResourceUrl(directUrl)
      const fileId = this.videoFileId(video)
      return fileId ? this.videoPlayUrlMap[fileId] || '' : ''
    },
    resourceBaseOrigin() {
      return (process.env.VUE_APP_BASE_ORIGIN || window.location.origin || '').replace(/\/$/, '')
    },
    normalizeMediaFilePath(path) {
      return String(path || '').replace(/^\/api\/media\/files\//, '/api/control/files/')
    },
    normalizeResourceUrl(value) {
      if (!value) return ''
      const url = String(value).trim()
      const baseOrigin = this.resourceBaseOrigin()
      if (/^https?:\/\//i.test(url)) {
        try {
          const parsed = new URL(url)
          const pathname = this.normalizeMediaFilePath(parsed.pathname)
          if (/^\/api\/control\/files\//.test(pathname)) {
            return `${baseOrigin}${pathname}${parsed.search}${parsed.hash}`
          }
          return url
        } catch (error) {
          return url
        }
      }
      const pathname = this.normalizeMediaFilePath(url.charAt(0) === '/' ? url : `/${url}`)
      return `${baseOrigin}${pathname}`
    },
    mediaFileContentUrl(fileId) {
      return fileId ? `${this.resourceBaseOrigin()}/api/control/files/${encodeURIComponent(fileId)}/content` : ''
    },
    isDemoUrl(value) {
      return typeof value === 'string' && value.indexOf('eiop-demo://') === 0
    },
    videoKey(video, index) {
      return this.videoFileId(video) || `${(video && video.actionRef) || 'video'}-${index}`
    },
    videoGroupKey(group, index) {
      return String((group && (group.key || group.deviceTaskInstanceId || group.serialNumber || group.deviceName)) || `video-group-${index}`)
    },
    videoFileId(video) {
      if (typeof video === 'string') return video
      return (video && (video.fileId || (video.metadata && video.metadata.fileId) || video.videoId)) || ''
    },
    normalizeVideoItem(item) {
      if (typeof item === 'string') return { fileId: item }
      return item || {}
    },
    videoPlaybackState(video) {
      const current = this.currentDateTime
      if (!current) return { state: 'UNKNOWN', label: '等待时间轴' }
      const timing = this.videoTiming(video)
      if (timing.start && current.getTime() < timing.start.getTime()) return { state: 'BEFORE_START', label: '未开始' }
      if (timing.end && current.getTime() > timing.end.getTime()) return { state: 'ENDED', label: '已结束' }
      if (!this.videoUrl(video)) return { state: 'NO_URL', label: '视频地址处理中' }
      return { state: 'PLAYING', label: '播放中' }
    },
    videoTiming(video) {
      const timelineStart = this.startedAt
      const timelineEnd = this.completedAt || timelineStart
      let start = this.parseDate(video && video.startedAt)
      let end = this.parseDate(video && video.endedAt)
      const overlaps = timelineStart && timelineEnd && start && start.getTime() <= timelineEnd.getTime() && (!end || end.getTime() >= timelineStart.getTime())
      if (!start || !overlaps) start = timelineStart
      if (!end || (start && end.getTime() < start.getTime())) end = timelineEnd
      return { start, end }
    },
    videoPositionForTimeline(current, timing, video) {
      if (!timing.start) return { currentTime: 0, seekable: false, playable: false }
      const elapsed = Math.max(0, (current.getTime() - timing.start.getTime()) / 1000)
      if (current.getTime() < timing.start.getTime()) return { currentTime: 0, seekable: true, playable: false }
      if (timing.end && current.getTime() > timing.end.getTime()) {
        const endElapsed = Math.max(0, (timing.end.getTime() - timing.start.getTime()) / 1000)
        const duration = Number.isFinite(video && video.duration) && video.duration > 0 ? video.duration : null
        return { currentTime: duration == null ? endElapsed : Math.min(duration, endElapsed), seekable: true, playable: false }
      }
      const duration = Number.isFinite(video && video.duration) && video.duration > 0 ? video.duration : null
      return {
        currentTime: duration == null ? elapsed : Math.min(duration, elapsed),
        seekable: true,
        playable: Boolean(this.videoUrl(video))
      }
    },
    videoSourceLabel(video) {
      return (video && (
        video.label ||
        video.sourceComponentName ||
        video.sourceComponentCode ||
        video.actionCode ||
        video.actionRef ||
        video.deviceName ||
        video.groupDeviceName
      )) || '视频来源'
    },
    mediaTypeLabel(value) {
      return { VISIBLE: '可见光', THERMAL: '红外', OTHER: '其他' }[value] || value || '其他'
    },
    alarmType(row) {
      const payload = this.alarmPayload(row)
      return (row && (row.alarmType || row.title)) || payload.alarmType || payload.type || (row && (row.eventSubtype || row.eventType)) || '-'
    },
    alarmContent(row) {
      const payload = this.alarmPayload(row)
      return (row && row.content) || payload.content || payload.message || payload.title || payload.alarmContent || (row && row.eventSubtype) || '-'
    },
    alarmImageFileId(row) {
      const payload = this.alarmPayload(row)
      return (row && row.imageFileId) || payload.fileId || ''
    },
    alarmImageUrl(row) {
      const fileId = this.alarmImageFileId(row)
      return this.normalizeResourceUrl((row && row.imageUrl) || this.mediaFileContentUrl(fileId))
    },
    alarmDeviceLabel(row) {
      const payload = this.alarmPayload(row)
      return (row && (row.deviceName || row.serialNumber || row.sourceComponentCode)) ||
        payload.deviceName ||
        payload.serialNumber ||
        payload.sourceComponentCode ||
        '告警设备'
    },
    alarmPayload(row) {
      const payload = this.parseJson(row && row.payloadJson)
      const rawPayload = this.parseJson(payload.rawPayload)
      return Object.assign({}, payload, rawPayload)
    },
    alarmStatusLabel(row) {
      const status = row && (row.status || row.eventStatus)
      return { ACTIVE: '告警中', RESOLVED: '已恢复', CLOSED: '已关闭', IGNORED: '已忽略' }[status] || status || '-'
    },
    alarmStatusType(row) {
      const status = row && (row.status || row.eventStatus)
      return { ACTIVE: 'red', RESOLVED: 'green', CLOSED: 'info', IGNORED: 'info' }[status] || 'orange'
    },
    alarmTimeValue(row) {
      const payload = this.alarmPayload(row)
      return (row && (
        row.occurredAt ||
        row.eventTime ||
        row.alarmTime ||
        row.timestamp ||
        row.createdAt ||
        row.createTime ||
        row.time
      )) || payload.occurredAt || payload.eventTime || payload.alarmTime || payload.timestamp || payload.createdAt || payload.time || null
    },
    statusLabel(value) {
      return { PREPARING: '准备中', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', CANCELED: '已取消' }[value] || value || '-'
    },
    statusTagType(value) {
      return { COMPLETED: 'green', FAILED: 'red', CANCELED: 'info', RUNNING: 'orange', PREPARING: 'orange' }[value] || 'info'
    },
    trackStatusLabel(value) {
      return { AVAILABLE: '轨迹正常', PROCESSING: '轨迹处理中', MISSING: '轨迹缺失' }[value] || value || '轨迹处理中'
    },
    parseDate(value) {
      if (!value) return null
      const date = value instanceof Date ? value : new Date(String(value).trim().replace(' ', 'T').replace(/([+-]\d{2}:?\d{2}|Z)$/i, '').replace(/(\.\d{3})\d+/, '$1'))
      return Number.isNaN(date.getTime()) ? null : date
    },
    formatDateTime(value) {
      const date = this.parseDate(value)
      if (!date) return '-'
      const pad = part => String(part).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    },
    formatOffset(value) {
      const seconds = Math.max(0, Math.floor(Number(value) || 0))
      const hour = Math.floor(seconds / 3600)
      const minute = Math.floor((seconds % 3600) / 60)
      const second = seconds % 60
      return [hour, minute, second].map(part => String(part).padStart(2, '0')).join(':')
    },
    sampleTime(sample) {
      const date = this.parseDate(sample && sample.sampledAt)
      return date ? date.getTime() : 0
    },
    trackGroupKey(group) {
      return String(group.deviceTaskInstanceId || group.serialNumber || group.deviceName || 'track')
    },
    sampleTrackKey(sample) {
      return String(sample.deviceTaskInstanceId || sample.serialNumber || sample.deviceName || 'track')
    },
    defaultTrackColor(seed) {
      const colors = ['#2563eb', '#f97316', '#14b8a6', '#a855f7', '#ef4444', '#22c55e', '#f59e0b']
      const value = String(seed.deviceTaskInstanceId || seed.serialNumber || seed.deviceName || 'track')
      let hash = 0
      for (const char of value) hash = (hash * 31 + char.charCodeAt(0)) % colors.length
      return colors[Math.abs(hash) % colors.length]
    },
    interpolateNumber(left, right, ratio) {
      const start = Number(left)
      const end = Number(right)
      if (!Number.isFinite(start)) return right
      if (!Number.isFinite(end)) return left
      return start + (end - start) * ratio
    },
    parseJson(value) {
      if (!value) return {}
      if (typeof value === 'object') return value
      try {
        return JSON.parse(value)
      } catch (error) {
        return {}
      }
    },
    unwrap(res) {
      if (res && res.code !== undefined) {
        if (res.code === '0' || res.code === 0 || res.code === 200) return res.data || {}
        throw new Error(res.message || '请求失败')
      }
      return res || {}
    },
    showError(error) {
      this.$message.error((error && error.message) || '请求失败')
    }
  }
}
</script>

<style scoped lang="scss">
@import '../common.scss';
</style>
