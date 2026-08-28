<template>
  <div class="record-detail-page flex-column h100">
    <div class="page-action-header flx-justify-between mb10">
      <div class="record-breadcrumb flx-align-center">
        <span>业务管理</span>
        <span class="record-breadcrumb__sep flx-center wp20 hp20">
          <svg-icon icon-class="right" />
        </span>
        <span class="record-breadcrumb__link" @click="$emit('close')">执行记录</span>
        <span class="record-breadcrumb__sep flx-center wp20 hp20">
          <svg-icon icon-class="right" />
        </span>
        <span class="is-current">执行记录详情</span>
      </div>
      <button type="button" class="record-back" @click="$emit('close')">
        <svg-icon icon-class="back1" class="record-back__icon" />
      </button>
    </div>

    <div
      v-if="instance.terminationMode === 'FORCED'"
      class="record-status-banner is-warning flx-align-start mb10 p10"
      role="alert"
    >
      <svg-icon icon-class="warning" class="record-status-banner__icon mr10" />
      <p class="record-status-banner__desc">
        {{ instance.terminationSummary || '任务由平台强制结束，边缘端终止结果未确认。' }}
      </p>
    </div>

    <div
      v-else-if="instance.status === 'FAILED'"
      class="record-status-banner is-error flx-align-start mb10 p10"
      role="alert"
    >
      <svg-icon icon-class="error" class="record-status-banner__icon mr10" />
      <div class="record-status-banner__body">
        <strong class="record-status-banner__title">任务执行失败</strong>
        <p class="record-status-banner__desc">
          {{ instance.failureReason || '未获取到具体失败原因，请查看下方执行事件。' }}
        </p>
      </div>
    </div>

    <div v-loading="loading" class="replay-layout flex1 flex-column">
      <section class="summary-grid">
        <div class="summary-item"><span>开始时间</span><strong>{{ formatDateTime(replayStartedAt) }}</strong></div>
        <div class="summary-item"><span>结束时间</span><strong>{{ formatDateTime(replayCompletedAt) }}</strong></div>
        <div class="summary-item"><span>告警</span><strong>{{ alarmEvents.length }}</strong></div>
        <div class="summary-item"><span>视频</span><strong>{{ playbackItems.length }}</strong></div>
        <div class="summary-item">
          <span>轨迹状态</span>
          <strong :class="{ 'is-ok': replayTrackStatus === 'AVAILABLE' }">{{ trackStatusShortLabel(replayTrackStatus) }}</strong>
        </div>
      </section>

      <section class="playback-panel flex1 flex-column p10">
        <div class="replay-grid flex1">
          <div class="map-panel flex-column">
            <div class="panel-title flx-justify-between mb10">
              <strong>运动轨迹</strong>
            </div>
            <div class="track-toolbar flx-justify-between mb10">
              <el-checkbox-group v-if="trackGroups.length" v-model="visibleTrackKeys" class="track-legend">
                <el-checkbox v-for="group in trackGroups" :key="trackGroupKey(group)" :label="trackGroupKey(group)">
                  <span class="track-legend__swatch" :style="{ background: group.color || defaultTrackColor(group) }" />
                  <span>{{ group.deviceName || group.serialNumber || '设备轨迹' }}</span>
                </el-checkbox>
              </el-checkbox-group>
              <span v-if="samplesLoading">加载中</span>
              <span v-else>{{ trackGroups.length || groupedTrackSamples.length }}台装备/{{ timelineSamples.length }}个采样点</span>
            </div>
            <div
              v-if="timelineSamples.length"
              ref="trackViewport"
              class="track-stage"
              :class="{ 'has-map': hasCalibratedMap && mapImageUrl, 'is-dragging': trackDragging }"
              @wheel.prevent="handleTrackWheel"
            >
              <div
                class="track-map-stage"
                :style="trackStageStyle"
                @mousedown.prevent="handleTrackMouseDown"
              >
                <svg class="track-svg" :viewBox="trackViewBox" preserveAspectRatio="none">
                  <image
                    v-if="hasCalibratedMap && mapImageUrl"
                    :href="mapImageUrl"
                    x="0"
                    y="0"
                    :width="trackMapWidth"
                    :height="trackMapHeight"
                    preserveAspectRatio="none"
                  />
                  <g v-if="!hasCalibratedMap || !mapImageUrl" class="track-fallback-grid">
                    <rect x="0" y="0" :width="trackMapWidth" :height="trackMapHeight" />
                  </g>
                  <g v-for="line in fullTrackPolylines" :key="'full-' + line.key">
                    <polyline :points="line.points" class="track-line full" :style="{ stroke: line.color }" />
                  </g>
                  <g v-for="line in visitedTrackPolylines" :key="'visited-' + line.key">
                    <polyline :points="line.points" class="track-line visited-halo" />
                    <polyline :points="line.points" class="track-line visited" :style="{ stroke: line.color }" />
                  </g>
                  <g
                    v-for="point in currentTrackPoints"
                    :key="'current-' + point.key"
                    :transform="'translate(' + projectPoint(point.sample).x + ' ' + projectPoint(point.sample).y + ') scale(' + trackMarkerScale + ')'"
                  >
                    <circle class="track-current-halo" cx="0" cy="0" r="14" />
                    <circle class="track-current" :style="{ fill: point.color }" cx="0" cy="0" r="8" />
                    <text class="track-label" x="12" y="-12">{{ point.label }}</text>
                  </g>
                </svg>
              </div>
              <div v-if="hasCalibratedMap && !mapImageUrl" class="map-overlay-state">{{ mapImageStatus }}</div>
              <div v-else-if="!hasCalibratedMap" class="map-overlay-state">地图标定缺失，已使用临时网格展示</div>
            </div>
            <Empty
              v-else
              class="record-empty record-empty--track flex1 flx-center"
              width="126px"
              :opacity="0.7"
              textColor="#BEE1FF"
              text="轨迹文件处理中或未上传"
            />
          </div>

          <div class="replay-grid__divider mt28" aria-hidden="true" />

          <div class="video-panel flex-column">
            <div class="panel-title flx-justify-between mb10">
              <strong>轨迹视频</strong>
            </div>
            <div
              v-if="renderVideoGroups.length"
              class="video-tabs custom-tab-button"
              :class="{ 'is-scrollable': videoTabsScrollable }"
            >
              <span
                v-show="videoTabsScrollable"
                class="video-tabs__nav-prev"
                :class="{ 'is-disabled': !videoTabsCanScrollPrev }"
                @click="scrollVideoTabs('prev')"
              >
                <i class="el-icon-arrow-left" />
              </span>
              <div ref="videoTabsNavWrap" class="video-tabs__nav-wrap">
                <div
                  ref="videoTabsNav"
                  class="video-tabs__nav"
                  :style="{ transform: `translateX(${videoTabsOffset}px)` }"
                >
                  <div
                    v-for="group in renderVideoGroups"
                    :key="group.key"
                    class="tab-button-item"
                    :class="{ 'is-active': selectedVideoGroupKey === group.key }"
                    @click="selectVideoGroup(group.key)"
                  >
                    {{ group.deviceName || group.serialNumber || '设备' }}
                  </div>
                </div>
              </div>
              <span
                v-show="videoTabsScrollable"
                class="video-tabs__nav-next"
                :class="{ 'is-disabled': !videoTabsCanScrollNext }"
                @click="scrollVideoTabs('next')"
              >
                <i class="el-icon-arrow-right" />
              </span>
            </div>
            <div v-if="activeVideoItems.length" class="video-stage">
              <div class="video-main">
                <video
                  v-if="activePrimaryVideo && videoUrl(activePrimaryVideo) && !videoUnavailable(activePrimaryVideo, activePrimaryVideo.flatIndex)"
                  :key="'main-' + primaryVideoKey"
                  ref="mainPlayer"
                  muted
                  playsinline
                  preload="auto"
                  :data-video-index="activePrimaryVideo.flatIndex"
                  :data-video-url="videoUrl(activePrimaryVideo)"
                  @loadedmetadata="syncVideos"
                  @canplay="handleVideoCanPlay"
                  @error="handleVideoError($event, activePrimaryVideo)"
                  @play="handleVideoNativePlay"
                  @pause="handleVideoNativePause"
                />
                <div v-else class="video-placeholder">
                  <strong>{{ activePrimaryVideo && activePrimaryVideo.playbackStatus === 'PENDING' ? '视频待补传' : '视频暂不可用' }}</strong>
                </div>
                <div
                  v-if="activePrimaryVideo && (videoUnavailable(activePrimaryVideo, activePrimaryVideo.flatIndex) || activePrimaryVideo.playbackStatus === 'PENDING')"
                  class="video-state is-badge"
                >
                  {{ videoPlaybackState(activePrimaryVideo).label }}
                </div>
                <div class="video-frame-meta">
                  <span v-if="videoSourceLabel(activePrimaryVideo)">{{ videoSourceLabel(activePrimaryVideo) }}</span>
                  <span v-else />
                  <button
                    v-if="videoFileId(activePrimaryVideo)"
                    type="button"
                    class="video-download is-label"
                    @click.stop="downloadVideo(activePrimaryVideo)"
                  >
                    <svg-icon icon-class="download1" />
                    下载视频
                  </button>
                </div>
              </div>
              <div class="video-thumbs common-scroll">
                <div
                  v-for="video in activeVideoItems"
                  :key="videoKey(video, video.flatIndex)"
                  class="video-thumb"
                  :class="{ 'is-active': primaryVideoKey === videoKey(video, video.flatIndex) }"
                  @click="selectPrimaryVideo(video, true)"
                >
                  <video
                    v-if="videoUrl(video) && !videoUnavailable(video, video.flatIndex)"
                    ref="thumbPlayers"
                    muted
                    playsinline
                    preload="auto"
                    :data-video-index="video.flatIndex"
                    :data-video-url="videoUrl(video)"
                    @loadedmetadata="syncVideos"
                    @canplay="handleVideoCanPlay"
                    @error="handleVideoError($event, video)"
                  />
                  <div v-else class="video-placeholder is-thumb">
                    <span>{{ video.playbackStatus === 'PENDING' ? '待补传' : '不可用' }}</span>
                  </div>
                  <span v-if="videoSourceLabel(video)" class="video-thumb__name">{{ videoSourceLabel(video) }}</span>
                  <button
                    v-if="videoFileId(video)"
                    type="button"
                    class="video-download is-icon"
                    title="下载视频"
                    @click.stop="downloadVideo(video)"
                  >
                    <svg-icon icon-class="download1" />
                  </button>
                </div>
              </div>
            </div>
            <Empty
              v-else
              class="record-empty record-empty--video flex1 flx-center mt29"
              width="126px"
              :opacity="0.7"
              textColor="#BEE1FF"
              text="暂无视频结果"
            />
          </div>
        </div>

        <div class="playback-toolbar record-playback-toolbar">
          <div class="playback-progress">
            <el-slider
              v-model="currentOffset"
              class="time-slider"
              :min="0"
              :max="durationSeconds"
              :step="1"
              :show-tooltip="false"
              @change="syncVideos"
            />
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
          </div>
          <div class="playback-controls">
            <div class="time-label">{{ formatOffset(currentOffset) }}/{{ formatOffset(durationSeconds) }}</div>
            <div class="playback-actions">
              <button
                type="button"
                class="playback-icon"
                :class="playing ? 'is-pause' : 'is-play'"
                :disabled="!durationSeconds"
                :title="playing ? '暂停' : '播放'"
                @click="playing ? stopPlayback() : startPlayback()"
              >
                <svg-icon :icon-class="playing ? 'pause' : 'play'" />
              </button>
            </div>
            <div ref="rateSelect" class="rate-select" :class="{ 'is-open': rateMenuOpen }">
              <button
                type="button"
                class="rate-select__trigger"
                @click.stop="toggleRateMenu"
              >
                <span>{{ playbackRateLabel }}</span>
                <svg-icon icon-class="down" class="rate-select__caret" />
              </button>
              <ul v-show="rateMenuOpen" class="rate-select__menu">
                <li
                  v-for="rate in playbackRates"
                  :key="rate"
                  class="rate-select__option"
                  :class="{ 'is-selected': playbackRate === rate }"
                  @click.stop="selectPlaybackRate(rate)"
                >
                  {{ formatPlaybackRate(rate) }}
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <section class="records-row">
        <div class="record-block">
          <div class="record-block__title">告警记录</div>
          <div class="record-table">
            <div class="record-table__head alarm-head">
              <span>序号</span>
              <span>告警类型</span>
              <span>告警地点</span>
              <span>告警信息</span>
              <span>告警图片</span>
              <span>告警时间</span>
            </div>
            <div v-if="alarmEvents.length" class="record-table__body common-scroll">
              <button
                v-for="(event, index) in alarmEvents"
                :key="event.id || event.eventId || index"
                class="record-table__row alarm-row"
                type="button"
                @click="jumpToAlarmEvent(event)"
              >
                <span class="td-index">{{ index + 1 }}</span>
                <span :title="alarmType(event)">{{ alarmType(event) }}</span>
                <span :title="alarmLocation(event)">{{ alarmLocation(event) }}</span>
                <span :title="alarmContent(event)">{{ alarmContent(event) }}</span>
                <span class="alarm-image-cell" @click.stop>
                  <el-image
                    v-if="alarmImageUrl(event, index)"
                    class="alarm-thumb"
                    :src="alarmImageUrl(event, index)"
                    :preview-src-list="[alarmImageUrl(event, index)]"
                    fit="cover"
                  />
                  <span v-else class="alarm-thumb is-empty">{{ event.imageStatus === 'PENDING' ? '待补传' : '无' }}</span>
                </span>
                <span class="td-time">{{ formatTableDateTime(alarmTimeValue(event)) }}</span>
              </button>
            </div>
            <Empty v-else class="record-empty" width="75px" :opacity="0.7" textColor="#BEE1FF" text="暂无告警" />
          </div>
        </div>
        <div class="record-block">
          <div class="record-block__title">事件记录</div>
          <div class="record-table">
            <div class="record-table__head event-head">
              <span>序号</span>
              <span>任务事件</span>
              <span>事件信息</span>
              <span>事件时间</span>
            </div>
            <div v-if="executionEvents.length" class="record-table__body common-scroll">
              <div
                v-for="(event, index) in executionEvents"
                :key="event.id || index"
                class="record-table__row event-row"
              >
                <span class="td-index">{{ index + 1 }}</span>
                <span :title="eventTypeLabel(event)">{{ eventTypeLabel(event) }}</span>
                <span :title="eventContent(event)">{{ eventContent(event) }}</span>
                <span class="td-time">{{ formatTableDateTime(event.occurredAt) }}</span>
              </div>
            </div>
            <Empty v-else class="record-empty" width="75px" :opacity="0.7" textColor="#BEE1FF" text="暂无事件" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import HlsModule from 'hls.js'
import { getTaskRecordReplay, previewImageBlob } from '@/api/new-bi'
import { createFileObjectUrl, fileDownloadUrl, getFileContent, getFilePlayUrl, revokeFileObjectUrl } from '@/api/media'
import Empty from '../../../components/Empty.vue'
import { saveAs } from 'file-saver'
import { withApiPrefix } from '@/utils/api-url'
import { isRequestErrorNotified } from '@/utils/request'
import {
  executionStatusLabel as resolveExecutionStatusLabel,
  executionStatusType as resolveExecutionStatusType
} from '../execution-status'

const ImportedHls = HlsModule && (HlsModule.default || HlsModule)

export default {
  name: 'BiPatrolBusiness2RecordDetail',
  components: { Empty },
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
      playbackRates: [0.5, 1, 1.25, 1.5, 2, 4],
      rateMenuOpen: false,
      primaryVideoKey: '',
      selectedVideoGroupKey: '',
      videoPlayUrlMap: {},
      videoPlayLoadingIds: [],
      mapImageUrl: '',
      mapImageStatus: '地图加载中',
      playTimer: null,
      syncingVideos: false,
      switchingPrimaryVideo: false,
      hlsPlayers: new Map(),
      mapImageObjectUrl: '',
      unavailableVideoKeys: [],
      alarmImageUrls: {},
      alarmImageLoadSeq: 0,
      mapImageLoadSeq: 0,
      trackZoom: 1,
      trackOffsetX: 0,
      trackOffsetY: 0,
      trackDragging: false,
      trackDefaultZoom: 1,
      trackMinZoom: 0.25,
      trackMaxZoom: 2.5,
      videoTabsOffset: 0,
      videoTabsScrollable: false,
      videoTabsCanScrollPrev: false,
      videoTabsCanScrollNext: false
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
    executionEvents() {
      const detail = this.replay && this.replay.detail
      return (detail && detail.events) || []
    },
    activeVideoGroup() {
      return this.renderVideoGroups.find(group => group.key === this.selectedVideoGroupKey) || this.renderVideoGroups[0] || null
    },
    activeVideoItems() {
      return (this.activeVideoGroup && this.activeVideoGroup.items) || []
    },
    activePrimaryVideo() {
      return this.activeVideoItems.find(item => this.videoKey(item, item.flatIndex) === this.primaryVideoKey) || this.activeVideoItems[0] || null
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
      return `0 0 ${this.trackMapWidth} ${this.trackMapHeight}`
    },
    trackMapWidth() {
      return this.hasCalibratedMap ? Number(this.replayMap.previewWidth) : 1000
    },
    trackMapHeight() {
      return this.hasCalibratedMap ? Number(this.replayMap.previewHeight) : 560
    },
    trackMarkerScale() {
      return 1 / (this.trackZoom || 1)
    },
    trackStageStyle() {
      return {
        width: `${this.trackMapWidth * this.trackZoom}px`,
        height: `${this.trackMapHeight * this.trackZoom}px`,
        transform: `translate(${this.trackOffsetX}px, ${this.trackOffsetY}px)`
      }
    },
    playbackRateLabel() {
      return this.formatPlaybackRate(this.playbackRate)
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
    primaryVideoKey() {
      this.$nextTick(() => {
        this.attachVideoSources()
        this.syncVideos()
      })
    },
    renderVideoGroups() {
      this.$nextTick(() => {
        this.updateVideoTabsScroll()
        this.scrollActiveVideoTabIntoView()
      })
    },
    selectedVideoGroupKey() {
      this.$nextTick(() => this.scrollActiveVideoTabIntoView())
    },
    playbackItems(items) {
      if (!this.renderVideoGroups.length) {
        this.selectedVideoGroupKey = ''
        this.primaryVideoKey = ''
      } else if (!this.renderVideoGroups.some(group => group.key === this.selectedVideoGroupKey)) {
        this.selectVideoGroup(this.renderVideoGroups[0].key)
      } else if (!items.some(item => this.videoKey(item, item.flatIndex) === this.primaryVideoKey)) {
        const first = this.activeVideoItems[0] || items[0]
        this.primaryVideoKey = first ? this.videoKey(first, first.flatIndex || 0) : ''
      }
      this.loadVideoPlayUrls(items)
    }
  },
  mounted() {
    this.loadReplay()
    document.addEventListener('mousedown', this.handleRateMenuOutside)
    window.addEventListener('resize', this.updateVideoTabsScroll)
  },
  beforeDestroy() {
    document.removeEventListener('mousedown', this.handleRateMenuOutside)
    window.removeEventListener('resize', this.updateVideoTabsScroll)
    this.stopPlayback()
    this.destroyAllHlsPlayers()
    this.unbindTrackDrag()
    this.unobserveTrackViewport()
    this.revokeMapImageUrl()
    this.revokeAlarmImages()
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
        this.unavailableVideoKeys = []
        this.visibleTrackKeys = this.trackGroups.map(this.trackGroupKey)
        this.currentOffset = 0
        if (this.renderVideoGroups.length) this.selectVideoGroup(this.renderVideoGroups[0].key)
        this.loadAlarmImages()
        await this.$nextTick()
        this.observeTrackViewport()
        this.updateTrackZoomBounds(true)
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
        if (!(blob instanceof Blob)) throw new Error('地图预览响应无效')
        let nextUrl = ''
        try {
          nextUrl = await this.colorizeSlamMapBlob(blob)
        } catch (error) {
          nextUrl = URL.createObjectURL(blob)
        }
        if (seq !== this.mapImageLoadSeq) {
          URL.revokeObjectURL(nextUrl)
          return
        }
        this.mapImageObjectUrl = nextUrl
        this.mapImageUrl = nextUrl
        this.$nextTick(() => {
          this.observeTrackViewport()
          this.updateTrackZoomBounds(true)
        })
      } catch (error) {
        if (seq === this.mapImageLoadSeq) this.mapImageStatus = '地图预览加载失败'
      }
    },
    colorizeSlamMapBlob(blob) {
      return new Promise((resolve, reject) => {
        const sourceUrl = URL.createObjectURL(blob)
        const img = new Image()
        img.onload = () => {
          try {
            const canvas = document.createElement('canvas')
            canvas.width = img.width
            canvas.height = img.height
            const ctx = canvas.getContext('2d')
            ctx.drawImage(img, 0, 0)
            const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
            const data = imageData.data
            for (let i = 0; i < data.length; i += 4) {
              if (data[i] > 230 && data[i + 1] > 230 && data[i + 2] > 230) {
                data[i] = 86
                data[i + 1] = 121
                data[i + 2] = 163
              } else if (data[i] > 10 && data[i + 1] > 10 && data[i + 2] > 10) {
                data[i] = 17
                data[i + 1] = 43
                data[i + 2] = 77
              } else {
                data[i] = 7
                data[i + 1] = 10
                data[i + 2] = 13
              }
            }
            ctx.putImageData(imageData, 0, 0)
            canvas.toBlob(colored => {
              URL.revokeObjectURL(sourceUrl)
              if (!colored) {
                reject(new Error('地图着色失败'))
                return
              }
              resolve(URL.createObjectURL(colored))
            }, 'image/png')
          } catch (error) {
            URL.revokeObjectURL(sourceUrl)
            reject(error)
          }
        }
        img.onerror = () => {
          URL.revokeObjectURL(sourceUrl)
          reject(new Error('地图预览无法着色'))
        }
        img.src = sourceUrl
      })
    },
    updateTrackZoomBounds(reset) {
      const viewport = this.$refs.trackViewport
      const mapWidth = this.trackMapWidth
      const mapHeight = this.trackMapHeight
      if (!viewport || !mapWidth || !mapHeight) return
      const curW = viewport.clientWidth
      const curH = viewport.clientHeight
      if (!curW || !curH) return
      const defaultZoom = Math.max(0.1, Math.min(curW / mapWidth, curH / mapHeight))
      this.trackDefaultZoom = defaultZoom
      this.trackMaxZoom = Math.max(defaultZoom, defaultZoom * 2.5)
      this.trackMinZoom = Math.max(0.1, defaultZoom * 0.25)
      if (reset) {
        this.trackZoom = Number(defaultZoom.toFixed(3))
        this.trackOffsetX = (curW - mapWidth * this.trackZoom) / 2
        this.trackOffsetY = (curH - mapHeight * this.trackZoom) / 2
      } else {
        this.trackZoom = Math.max(this.trackMinZoom, Math.min(this.trackMaxZoom, this.trackZoom))
        this.trackZoom = Number(this.trackZoom.toFixed(3))
      }
    },
    observeTrackViewport() {
      if (typeof ResizeObserver === 'undefined' || !this.$refs.trackViewport) return
      this.unobserveTrackViewport()
      this.trackResizeObserver = new ResizeObserver(() => this.updateTrackZoomBounds())
      this.trackResizeObserver.observe(this.$refs.trackViewport)
    },
    unobserveTrackViewport() {
      if (this.trackResizeObserver) {
        this.trackResizeObserver.disconnect()
        this.trackResizeObserver = null
      }
    },
    handleTrackWheel(event) {
      if (event.deltaY > 0) this.zoomTrackOut()
      else this.zoomTrackIn()
    },
    zoomTrackIn() {
      const step = Math.max(0.05, this.trackDefaultZoom * 0.05)
      this.trackZoom = Math.min(this.trackMaxZoom, Number((this.trackZoom + step).toFixed(3)))
    },
    zoomTrackOut() {
      const step = Math.max(0.05, this.trackDefaultZoom * 0.05)
      this.trackZoom = Math.max(this.trackMinZoom, Number((this.trackZoom - step).toFixed(3)))
    },
    handleTrackMouseDown(event) {
      if (event.button !== 0) return
      this.trackDragging = true
      this.trackDragStartX = event.clientX
      this.trackDragStartY = event.clientY
      this.trackDragOriginX = this.trackOffsetX
      this.trackDragOriginY = this.trackOffsetY
      document.addEventListener('mousemove', this.handleTrackMouseMove)
      document.addEventListener('mouseup', this.handleTrackMouseUp)
    },
    handleTrackMouseMove(event) {
      if (!this.trackDragging) return
      this.trackOffsetX = this.trackDragOriginX + event.clientX - this.trackDragStartX
      this.trackOffsetY = this.trackDragOriginY + event.clientY - this.trackDragStartY
    },
    handleTrackMouseUp() {
      this.trackDragging = false
      this.unbindTrackDrag()
    },
    unbindTrackDrag() {
      document.removeEventListener('mousemove', this.handleTrackMouseMove)
      document.removeEventListener('mouseup', this.handleTrackMouseUp)
    },
    revokeMapImageUrl() {
      if (this.mapImageObjectUrl) URL.revokeObjectURL(this.mapImageObjectUrl)
      this.mapImageObjectUrl = ''
      this.mapImageUrl = ''
    },
    alarmImageKey(row, index) {
      return String((row && (row.id || row.eventId)) || `alarm-${index}`)
    },
    async loadAlarmImages() {
      const seq = ++this.alarmImageLoadSeq
      this.revokeAlarmImages()
      const events = this.alarmEvents || []
      const nextUrls = {}
      await Promise.all(events.map(async (event, index) => {
        const key = this.alarmImageKey(event, index)
        const fileId = this.alarmImageFileId(event)
        if (!fileId) return
        try {
          const objectUrl = await createFileObjectUrl(fileId)
          if (seq !== this.alarmImageLoadSeq) {
            revokeFileObjectUrl(objectUrl)
            return
          }
          nextUrls[key] = objectUrl
        } catch (error) {
          // 无权限或文件不存在时保持占位，避免 <img> 直接请求 /content 出现 401。
        }
      }))
      if (seq !== this.alarmImageLoadSeq) {
        Object.keys(nextUrls).forEach(key => revokeFileObjectUrl(nextUrls[key]))
        return
      }
      this.alarmImageUrls = nextUrls
    },
    revokeAlarmImages() {
      Object.keys(this.alarmImageUrls || {}).forEach(key => revokeFileObjectUrl(this.alarmImageUrls[key]))
      this.alarmImageUrls = {}
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
    formatPlaybackRate(rate) {
      return `${rate}X`
    },
    toggleRateMenu() {
      this.rateMenuOpen = !this.rateMenuOpen
    },
    selectPlaybackRate(rate) {
      this.playbackRate = rate
      this.rateMenuOpen = false
      this.syncVideos()
    },
    handleRateMenuOutside(event) {
      const root = this.$refs.rateSelect
      if (!this.rateMenuOpen || !root || root.contains(event.target)) return
      this.rateMenuOpen = false
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
      this.setVideoSyncing(() => {
        this.getVideoRefs().forEach(video => video && video.pause && video.pause())
      })
    },
    getMainPlayer() {
      return this.$refs.mainPlayer || null
    },
    getThumbPlayers() {
      const thumbs = this.$refs.thumbPlayers
      if (!thumbs) return []
      return (Array.isArray(thumbs) ? thumbs : [thumbs]).filter(Boolean)
    },
    getVideoRefs() {
      return [this.getMainPlayer()].concat(this.getThumbPlayers()).filter(Boolean)
    },
    selectPrimaryVideo(video, play) {
      if (!video) return
      const nextKey = this.videoKey(video, video.flatIndex)
      if (this.primaryVideoKey === nextKey) {
        if (play && !this.playing) this.startPlayback()
        else this.$nextTick(() => this.syncVideos())
        return
      }
      this.switchingPrimaryVideo = true
      const main = this.getMainPlayer()
      if (main) this.destroyHlsPlayer(main)
      this.primaryVideoKey = nextKey
      this.$nextTick(() => {
        this.$nextTick(() => {
          this.attachVideoSources()
          this.syncVideos()
          this.switchingPrimaryVideo = false
          if (play && !this.playing) this.startPlayback()
        })
      })
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
            this.syncSingleVideo(video, video === this.getMainPlayer())
          }
        })
        player.on(HlsPlayer.Events.ERROR, (event, data) => {
          if (data && data.fatal) {
            this.markVideoUnavailable(item)
            this.destroyHlsPlayer(video)
          }
        })
        player.attachMedia(video)
        this.hlsPlayers.set(video, player)
        return
      }
      video.src = source
      video.load && video.load()
      this.primeVideoBuffer(video)
      this.$nextTick(() => this.syncSingleVideo(video, video === this.getMainPlayer()))
    },
    attachVideoSources() {
      const main = this.getMainPlayer()
      if (main && this.activePrimaryVideo) this.attachVideoSource(main, this.activePrimaryVideo)
      this.getThumbPlayers().forEach(video => {
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
        this.getVideoRefs().forEach(video => this.syncSingleVideo(video, true))
      })
    },
    syncSingleVideo(video, allowPlay) {
      const current = this.currentDateTime
      if (!current || !video) return
      const index = Number(video.dataset && video.dataset.videoIndex)
      const item = this.playbackItems[index] || (video === this.getMainPlayer() ? this.activePrimaryVideo : null)
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
      const shouldPlay = Boolean(allowPlay && this.playing && this.videoUrl(item) && position.playable)
      if (shouldPlay && video.paused) video.play().catch(() => undefined)
      if (!shouldPlay && !video.paused && video.dataset.primingBuffer !== 'true') video.pause()
    },
    handleVideoCanPlay(event) {
      const target = event && event.target
      if (!target) return
      this.syncSingleVideo(target, true)
    },
    handleVideoError(event, video) {
      this.markVideoUnavailable(video)
      const target = event && event.target
      if (target) this.destroyHlsPlayer(target)
    },
    handleVideoNativePlay(event) {
      const target = event && event.target
      if (this.syncingVideos || (target && target.dataset && target.dataset.primingBuffer === 'true')) return
      if (target && target !== this.getMainPlayer()) return
      this.syncVideos()
      this.startPlayback()
    },
    handleVideoNativePause(event) {
      const target = event && event.target
      if (target && target.dataset && target.dataset.primingBuffer === 'true') return
      // 切换主视频时旧节点卸载/销毁会触发 pause，不应打断时间轴播放
      if (this.switchingPrimaryVideo) return
      if (target && !target.isConnected) return
      if (target && target !== this.getMainPlayer()) return
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
    selectVideoGroup(key) {
      this.selectedVideoGroupKey = key
      const group = this.renderVideoGroups.find(item => item.key === key)
      const first = group && group.items && group.items[0]
      this.selectPrimaryVideo(first)
      this.$nextTick(() => this.scrollActiveVideoTabIntoView())
    },
    updateVideoTabsScroll() {
      const wrap = this.$refs.videoTabsNavWrap
      const nav = this.$refs.videoTabsNav
      if (!wrap || !nav) {
        this.videoTabsScrollable = false
        this.videoTabsOffset = 0
        this.videoTabsCanScrollPrev = false
        this.videoTabsCanScrollNext = false
        return
      }
      const navWidth = nav.scrollWidth
      // 先按是否超出当前可视宽度判断；加上箭头 padding 后需再量一次
      let wrapWidth = wrap.clientWidth
      let scrollable = navWidth > wrapWidth + 1
      if (scrollable !== this.videoTabsScrollable) {
        this.videoTabsScrollable = scrollable
        this.$nextTick(() => this.updateVideoTabsScroll())
        return
      }
      wrapWidth = wrap.clientWidth
      scrollable = navWidth > wrapWidth + 1
      this.videoTabsScrollable = scrollable
      if (!scrollable) {
        this.videoTabsOffset = 0
        this.videoTabsCanScrollPrev = false
        this.videoTabsCanScrollNext = false
        return
      }
      const minOffset = Math.min(0, wrapWidth - navWidth)
      this.videoTabsOffset = Math.max(minOffset, Math.min(0, this.videoTabsOffset))
      this.videoTabsCanScrollPrev = this.videoTabsOffset < 0
      this.videoTabsCanScrollNext = this.videoTabsOffset > minOffset
    },
    scrollVideoTabs(direction) {
      if (!this.videoTabsScrollable) return
      const wrap = this.$refs.videoTabsNavWrap
      const nav = this.$refs.videoTabsNav
      if (!wrap || !nav) return
      const wrapWidth = wrap.clientWidth
      const navWidth = nav.scrollWidth
      const minOffset = Math.min(0, wrapWidth - navWidth)
      const step = Math.max(wrapWidth * 0.6, 80)
      const nextOffset = direction === 'prev'
        ? Math.min(0, this.videoTabsOffset + step)
        : Math.max(minOffset, this.videoTabsOffset - step)
      this.videoTabsOffset = nextOffset
      this.videoTabsCanScrollPrev = nextOffset < 0
      this.videoTabsCanScrollNext = nextOffset > minOffset
    },
    scrollActiveVideoTabIntoView() {
      this.updateVideoTabsScroll()
      if (!this.videoTabsScrollable) return
      const wrap = this.$refs.videoTabsNavWrap
      const nav = this.$refs.videoTabsNav
      if (!wrap || !nav) return
      const active = nav.querySelector('.tab-button-item.is-active')
      if (!active) return
      const wrapWidth = wrap.clientWidth
      const navWidth = nav.scrollWidth
      const minOffset = Math.min(0, wrapWidth - navWidth)
      const activeLeft = active.offsetLeft
      const activeRight = activeLeft + active.offsetWidth
      let nextOffset = this.videoTabsOffset
      if (activeLeft + nextOffset < 0) {
        nextOffset = -activeLeft
      } else if (activeRight + nextOffset > wrapWidth) {
        nextOffset = wrapWidth - activeRight
      }
      this.videoTabsOffset = Math.max(minOffset, Math.min(0, nextOffset))
      this.videoTabsCanScrollPrev = this.videoTabsOffset < 0
      this.videoTabsCanScrollNext = this.videoTabsOffset > minOffset
    },
    async downloadVideo(video) {
      const fileId = this.videoFileId(video)
      if (!fileId) return
      const filename = this.videoDownloadName(video, fileId)
      try {
        const raw = await fileDownloadUrl(fileId)
        const payload = this.unwrap(raw)
        const url = this.normalizeResourceUrl(
          (payload && (payload.downloadUrl || payload.url))
          || (raw && raw.data && (raw.data.downloadUrl || raw.data.url))
          || (raw && (raw.downloadUrl || raw.url))
          || ''
        )
        if (url) {
          this.triggerFileDownload(url, filename)
          return
        }
        await this.downloadVideoBlob(fileId, filename)
      } catch (error) {
        try {
          await this.downloadVideoBlob(fileId, filename)
        } catch (inner) {
          this.showError(error)
        }
      }
    },
    videoDownloadName(video, fileId) {
      const label = this.videoSourceLabel(video) || fileId || 'video'
      return /\.[a-z0-9]+$/i.test(label) ? label : `${label}.mp4`
    },
    triggerFileDownload(url, filename) {
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      link.rel = 'noopener'
      link.target = '_blank'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    },
    async downloadVideoBlob(fileId, filename) {
      const data = await getFileContent(fileId)
      const blob = data instanceof Blob ? data : new Blob([data])
      saveAs(blob, filename)
    },
    videoUrl(video) {
      if (this.videoUnavailable(video, video && video.flatIndex)) return ''
      const metadata = (video && video.metadata) || {}
      const directUrl = (video && video.playUrl) || metadata.playUrl || metadata.playbackUrl || (video && video.playbackUrl) || ''
      if (directUrl && !this.isDemoUrl(directUrl)) return this.normalizeResourceUrl(directUrl)
      const fileId = this.videoFileId(video)
      return fileId ? this.videoPlayUrlMap[fileId] || '' : ''
    },
    resourceBaseOrigin() {
      // 媒体/HLS 必须同源请求当前页面，本地才能走 webpack /dev-api 代理。
      // VUE_APP_BASE_ORIGIN 是代理目标，不能写进 <video>/hls.js 的 src。
      return (typeof window !== 'undefined' && window.location.origin
        ? window.location.origin
        : (process.env.VUE_APP_BASE_ORIGIN || '')).replace(/\/$/, '')
    },
    normalizeMediaFilePath(path) {
      return String(path || '')
        .replace(/^\/api\/media\/files\//, '/api/bigscreen/control/files/')
        .replace(/^\/api\/control(?=\/|$)/, '/api/bigscreen/control')
    },
    normalizeResourceUrl(value) {
      if (!value) return ''
      const url = String(value).trim()
      const baseOrigin = this.resourceBaseOrigin()
      if (/^https?:\/\//i.test(url)) {
        try {
          const parsed = new URL(url)
          const pathname = this.normalizeMediaFilePath(parsed.pathname)
          if (/^\/api\/bigscreen\/control\/files\//.test(pathname)) {
            return `${baseOrigin}${withApiPrefix(pathname)}${parsed.search}${parsed.hash}`
          }
          return url
        } catch (error) {
          return url
        }
      }
      const pathname = this.normalizeMediaFilePath(url.charAt(0) === '/' ? url : `/${url}`)
      return `${baseOrigin}${withApiPrefix(pathname)}`
    },
    mediaFileContentUrl(fileId) {
      return fileId
        ? `${this.resourceBaseOrigin()}${withApiPrefix(`/api/bigscreen/control/files/${encodeURIComponent(fileId)}/content`)}`
        : ''
    },
    isDemoUrl(value) {
      return typeof value === 'string' && value.indexOf('eiop-demo://') === 0
    },
    videoKey(video, index) {
      return this.videoFileId(video) || `${(video && video.actionRef) || 'video'}-${index}`
    },
    videoUnavailable(video, index) {
      return this.unavailableVideoKeys.indexOf(this.videoKey(video, index)) !== -1
    },
    markVideoUnavailable(video) {
      const key = this.videoKey(video, video && video.flatIndex)
      if (this.unavailableVideoKeys.indexOf(key) === -1) {
        this.unavailableVideoKeys = this.unavailableVideoKeys.concat(key)
      }
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
      if (this.videoUnavailable(video, video && video.flatIndex)) return { state: 'UNAVAILABLE', label: '视频不可用' }
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
      if (!video) return ''
      const candidates = [
        video.label,
        video.sourceComponentName,
        video.cameraName,
        video.deviceName,
        video.groupDeviceName,
        video.mediaType === 'THERMAL' ? '红外' : '',
        video.mediaType === 'VISIBLE' ? '可见光' : ''
      ]
      const name = candidates.find(item => item && !this.isTechnicalVideoName(item))
      return name || '视频来源'
    },
    isTechnicalVideoName(value) {
      const text = String(value || '')
      return /action_node_|TASK-WF-|PTZ_CONTROL|PTZ_RECORD|SINGLE/i.test(text)
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
      return this.firstValue(row && row.imageFileIds) ||
        this.firstValue(payload.fileIds) ||
        (row && row.imageFileId) ||
        payload.fileId ||
        ''
    },
    alarmImageUrl(row, index) {
      return this.alarmImageUrls[this.alarmImageKey(row, index)] || ''
    },
    alarmDeviceLabel(row) {
      const payload = this.alarmPayload(row)
      return (row && (row.deviceName || row.serialNumber || row.sourceComponentCode)) ||
        payload.deviceName ||
        payload.serialNumber ||
        payload.sourceComponentCode ||
        '告警设备'
    },
    alarmLocation(row) {
      const location = (row && row.location) || this.alarmPayload(row).location || {}
      return location.address || location.pointName || location.name || this.alarmDeviceLabel(row) || '-'
    },
    eventTypeLabel(event) {
      const type = event && (event.eventType || event.eventSubtype)
      return {
        ACK: '任务确认',
        ACK_RECEIVED: '任务确认',
        PROGRESS: '任务进度',
        TASK_PROGRESS: '任务进度',
        RESULT: '任务结果',
        COMPLETED: '任务结果',
        ALARM: '设备告警',
        CONTROL: '任务控制',
        PAUSE: '任务暂停',
        RESUME: '任务恢复',
        TERMINATE: '任务终止',
        DISPATCH: '任务下发',
        REMOTE_CONTROL: '远程控制'
      }[type] || (event && (event.eventSubtype || event.eventType)) || '-'
    },
    eventContent(event) {
      const payload = this.parseJson(event && event.payloadJson)
      return payload.content ||
        payload.message ||
        payload.title ||
        payload.summary ||
        (event && (event.eventSubtype || event.eventType)) ||
        '-'
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
      return resolveExecutionStatusLabel(value)
    },
    statusTagType(value) {
      return resolveExecutionStatusType(value)
    },
    trackStatusLabel(value) {
      return { AVAILABLE: '轨迹正常', PROCESSING: '轨迹处理中', PENDING: '轨迹待补传', MISSING: '轨迹缺失' }[value] || value || '轨迹处理中'
    },
    trackStatusShortLabel(value) {
      return { AVAILABLE: '正常', PROCESSING: '处理中', PENDING: '待补传', MISSING: '缺失' }[value] || this.trackStatusLabel(value)
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
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}  ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    },
    formatTableDateTime(value) {
      const date = this.parseDate(value)
      if (!date) return '-'
      const pad = part => String(part).padStart(2, '0')
      return `${date.getFullYear()}.${date.getMonth() + 1}.${date.getDate()}  ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
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
    firstValue(value) {
      if (Array.isArray(value)) return value.find(item => item != null && item !== '') || ''
      return value || ''
    },
    unwrap(res) {
      if (res && res.code !== undefined) {
        if (res.code === '0' || res.code === 0 || res.code === 200) return res.data || {}
        throw new Error(res.message || '请求失败')
      }
      return res || {}
    },
    showError(error) {
      if (isRequestErrorNotified(error)) return
      this.$message.error((error && error.message) || '请求失败')
    }
  }
}
</script>

<style scoped lang="scss">
@import '../common.scss';

.record-detail-page {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  color: #FFDD00;
  font-family: "Microsoft YaHei", sans-serif;
}

.page-action-header {
  min-height: 32px;
}

.record-breadcrumb {
  gap: 10px;
  color: #fff;
  font-family: "Microsoft YaHei", sans-serif;
  font-size: 16px;
  font-weight: 400;
  line-height: 21px;

  .is-current {
    color: #0bf9fe;
  }

  &__link {
    color: #fff;
    cursor: pointer;

    &:hover {
      color: #0bf9fe;
    }
  }

  &__sep {
    font-size: 14px;
    color: #fff;
  }
}

.record-back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 32px;
  padding: 0;
  border: 1px solid #5aa0ff;
  border-radius: 100px;
  background: linear-gradient(180deg, #011c39 0%, #0073c1 100%);
  cursor: pointer;

  .svg-icon,
  &__icon {
    width: 18px;
    height: 18px;
    color: #fff;
    fill: none;
    vertical-align: 0;
  }
}

.replay-layout {
  min-height: 0;
  gap: 12px;
  overflow: hidden;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 20px;
  flex-shrink: 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  height: 68px;
  padding: 10px;
  border: none;
  background: #101f3c;

  span {
    color: #dfedff;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 19px;
  }

  strong {
    margin-top: 8px;
    color: #fff;
    font-family: Bahnschrift, "DIN Alternate", sans-serif;
    font-size: 18px;
    line-height: 24px;
    font-weight: 700;

    &.is-ok {
      color: #11f667;
    }
  }
}

.playback-panel {
  min-height: 0;
  border: none;
  background: #101f3c;
  overflow: visible;
}

.replay-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 1px minmax(320px, 686px);
  column-gap: 16px;
  min-height: 0;
  overflow: hidden;
}

.replay-grid__divider {
  align-self: start;
  width: 1px;
  height: calc(100% - 38px);
  background: repeating-linear-gradient(
    to bottom,
    rgba(190, 225, 255, 0.35) 0,
    rgba(190, 225, 255, 0.35) 4px,
    transparent 4px,
    transparent 8px
  );
}

.map-panel,
.video-panel {
  min-width: 0;
  min-height: 0;
  border: none;
  background: transparent;
}

.panel-title {
  flex-shrink: 0;

  strong {
    color: #fff;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 16px;
    font-weight: 600;
    line-height: 21px;
  }

  span {
    color: #fff;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 14px;
  }
}

.track-toolbar {
  gap: 12px;
  color: #fff;
  font-family: "Microsoft YaHei", sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 19px;
  flex-shrink: 0;
}

.track-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.track-legend__swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  vertical-align: middle;
}

.track-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #112b4d;
  cursor: grab;
  user-select: none;
  touch-action: none;

  &.is-dragging {
    cursor: grabbing;
  }
}

.track-map-stage {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}

.track-svg {
  display: block;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.track-fallback-grid rect {
  fill: #112b4d;
  stroke: #1b2d4d;
}

.map-overlay-state {
  pointer-events: none;
  z-index: 2;
}

.video-tabs.custom-tab-button {
  position: relative;
  display: inline-flex;
  align-items: stretch;
  // width: auto;
  max-width: 100%;
  margin-bottom: 12px;
  flex-shrink: 0;
  box-sizing: border-box;
  vertical-align: top;

  .video-tabs__nav-wrap {
    width: auto;
    max-width: 100%;
    overflow: hidden;
  }

  .video-tabs__nav {
    display: inline-flex;
    width: auto;
    max-width: none;
    flex: none;
    flex-wrap: nowrap;
    white-space: nowrap;
    transition: transform 0.3s;
  }

  .video-tabs__nav-prev,
  .video-tabs__nav-next {
    position: absolute;
    top: 0;
    bottom: 0;
    z-index: 3;
    width: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #6AC5FF;
    background: #0A2544;
    cursor: pointer;
    user-select: none;

    i {
      font-size: 12px;
    }

    &.is-disabled {
      color: #3A5A7A;
      cursor: not-allowed;
      pointer-events: none;
    }
  }

  .video-tabs__nav-prev {
    left: 0;
  }

  .video-tabs__nav-next {
    right: 0;
  }

  &.is-scrollable {
    display: flex;
    width: 100%;
    max-width: 100%;
    padding: 0 20px;

    .video-tabs__nav-wrap {
      flex: 1 1 auto;
      width: auto;
      min-width: 0;
      max-width: 100%;
    }

    /* 滚动时 nav 仍按内容宽度，不受 wrap 的 flex:1 拉伸 */
    .video-tabs__nav {
      display: inline-flex;
      width: auto;
      flex: 0 0 auto;
      max-width: none;
    }
  }

  .tab-button-item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    padding: 6px 10px;
    font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
    font-size: 14px;
    line-height: 12px;
    letter-spacing: 0.857px;
    white-space: nowrap;
  }
}

.video-stage {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  flex: 1;
}

.video-main {
  position: relative;
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #112B4D;

  video,
  .video-placeholder {
    width: 100%;
    height: 100%;
    aspect-ratio: auto;
    object-fit: cover;
  }
}

.video-frame-meta {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 8px 10px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.8) 0%, rgba(0, 0, 0, 0) 100%);
  color: #fff;
  font-family: "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 600;
  pointer-events: none;

  > * {
    pointer-events: auto;
  }
}

.video-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #fff;
  cursor: pointer;

  &.is-label {
    padding: 6px 10px;
    border-radius: 4px;
    background: rgba(0, 0, 0, 0.6);
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 12px;
    font-weight: 400;
  }

  &.is-icon {
    position: absolute;
    right: 8px;
    bottom: 8px;
    width: 14px;
    height: 14px;
    color: #fff;
  }

  .svg-icon {
    width: 14px;
    height: 14px;
  }
}

.video-thumbs {
  display: flex;
  gap: 10px;
  width: 100%;
  height: 80px;
  flex-shrink: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.video-thumb {
  position: relative;
  flex: 0 0 140px;
  width: 140px;
  height: 80px;
  overflow: hidden;
  background: #112B4D;
  cursor: pointer;

  &.is-active {
    box-shadow: inset 0 0 0 1px #0bf9fe;
  }

  video,
  .video-placeholder {
    width: 100%;
    height: 100%;
    aspect-ratio: auto;
    object-fit: cover;
  }

  &__name {
    position: absolute;
    top: 4px;
    left: 4px;
    z-index: 2;
    max-width: calc(100% - 28px);
    overflow: hidden;
    color: #fff;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 12px;
    font-weight: 600;
    line-height: 16px;
    white-space: nowrap;
    text-overflow: ellipsis;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8);
    pointer-events: none;
  }
}

.video-state {
  position: absolute;
  top: auto;
  right: 8px;
  bottom: 8px;
  left: auto;
  inset: auto;
  width: auto;
  height: auto;
  padding: 4px 10px;
  background: rgba(15, 23, 42, 0.56);
  font-size: 12px;
  font-weight: 600;
  transform: none;
  pointer-events: none;
}

.playback-toolbar,
.record-playback-toolbar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  margin-top: 12px;
  padding: 0;
  flex-shrink: 0;
  background: transparent;
  position: relative;
  z-index: 5;
}

.playback-progress {
  position: relative;
  width: 100%;
}

.time-slider {
  width: 100%;
}

.alarm-strip {
  position: absolute;
  left: 0;
  right: 0;
  top: -2px;
  height: 14px;
  margin: 0;
  pointer-events: none;

  .alarm-marker {
    pointer-events: auto;
  }
}

.playback-controls {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
}

.time-label {
  width: auto;
  color: #fff;
  font-size: 16px;
  line-height: 21px;
  font-family: Bahnschrift, "DIN Alternate", sans-serif;
}

.playback-actions {
  position: absolute;
  left: 50%;
  top: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%);
}

.playback-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 2px;
  background: #465d7b;
  color: #fff;
  cursor: pointer;

  .svg-icon {
    width: 14px;
    height: 14px;
    vertical-align: 0;
    color: #fff;
  }

  &.is-pause .svg-icon {
    fill: none;
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.rate-select {
  position: relative;
  width: 60px;
  margin-left: auto;
  z-index: 4;

  &.is-open .rate-select__caret {
    transform: rotate(180deg);
  }

  &__trigger {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    width: 60px;
    height: 24px;
    padding: 0 6px;
    border: 0;
    border-radius: 2px;
    background: #465d7b;
    color: #fff;
    font-family: Bahnschrift, "DIN Alternate", sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 24px;
    letter-spacing: 0.857px;
    cursor: pointer;
  }

  &__caret {
    width: 10px;
    height: 10px;
    color: #fff;
    flex-shrink: 0;
  }

  &__menu {
    position: absolute;
    right: 0;
    top: calc(100% + 10px);
    z-index: 5;
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 60px;
    margin: 0;
    padding: 6px;
    list-style: none;
    overflow: hidden;
    border-radius: 4px;
    background: #465d7b;
  }

  &__option {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    padding: 0;
    border-radius: 3px;
    color: #fff;
    font-family: Bahnschrift, "DIN Alternate", sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 20px;
    letter-spacing: 0.857px;
    text-align: center;
    cursor: pointer;

    &:hover,
    &.is-selected {
      background: #3877f2;
    }
  }
}

.records-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  max-height: 200px;
  min-height: 0;
  flex: 0 1 200px;
  overflow: hidden;
}

.record-block {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.record-block__title {
  margin-bottom: 10px;
  color: #fff;
  font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 400;
  line-height: 12px;
  letter-spacing: 0.857px;
  flex-shrink: 0;
}

.record-table {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.record-table__head,
.record-table__row {
  display: grid;
  align-items: center;
  height: 36px;
  font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 400;
  line-height: 12px;
  letter-spacing: 0.857px;
}

.alarm-head,
.alarm-row {
  grid-template-columns: 45px 104px 124px minmax(0, 1fr) 72px 140px;
}

.event-head,
.event-row {
  grid-template-columns: 45px 228px minmax(0, 1fr) 140px;
}

.record-table__head {
  flex-shrink: 0;
  padding: 0 10px;
  color: #159aff;
  background: rgba(21, 154, 255, 0.12);
}

.record-table__body {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.record-table__row {
  width: 100%;
  padding: 0 10px;
  border: 0;
  border-bottom: 1px solid rgba(66, 83, 111, 0.45);
  background: transparent;
  color: #d0deee;
  text-align: left;
  cursor: default;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

button.record-table__row {
  cursor: pointer;
}

.td-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 16px;
  color: #ff7734;
  font-family: "TeX Gyre Adventor", "Bahnschrift", sans-serif;
  font-size: 12px;
  font-style: italic;
  font-weight: 700;
  letter-spacing: 0.75px;
  line-height: 18px;
  background: rgba(255, 119, 52, 0.1);
  border: 0.5px solid #ff7734;
}

.td-time {
  font-family: Bahnschrift, "DIN Alternate", sans-serif;
  letter-spacing: 0.857px;
}

.alarm-image-cell {
  display: flex;
  align-items: center;
}

.alarm-thumb {
  width: 48px;
  height: 28px;
  border-radius: 2px;
  overflow: hidden;
  cursor: zoom-in;

  &.is-empty {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #8897ab;
    background: rgba(27, 45, 77, 0.72);
    cursor: default;
  }
}

.record-status-banner {
  border-radius: 2px;
  flex-shrink: 0;

  &__icon {
    flex-shrink: 0;
    width: 18px;
    height: 18px;
  }

  &__body {
    min-width: 0;
  }

  &__title {
    display: block;
    margin: 0 0 4px;
    color: #ed2c40;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 14px;
    font-weight: 600;
    line-height: 19px;
  }

  &__desc {
    margin: 0;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 14px;
    font-weight: 400;
    line-height: 19px;
  }

  &.is-error {
    background: rgba(237, 44, 64, 0.12);
    border: 1px solid rgba(237, 44, 64, 0.35);

    .record-status-banner__desc {
      color: rgba(255, 196, 196, 0.92);
    }
  }

  &.is-warning {
    background: rgba(255, 119, 52, 0.12);
    border: 1px solid rgba(255, 119, 52, 0.35);

    .record-status-banner__desc {
      color: #ffb48a;
      font-weight: 600;
    }
  }
}

.record-empty {
  flex: 1;
  min-height: 0;
}

.record-empty--track,
.record-empty--video {
  background: #112b4d;
}

::v-deep {
  .time-slider {
    .el-slider__runway {
      height: 8px;
      background: #2c3e55;
      border-radius: 0;
    }

    .el-slider__bar {
      height: 8px;
      background: #159aff;
      border-radius: 0;
    }

    .el-slider__button-wrapper {
      top: -13px;
    }

    .el-slider__button {
      width: 2px;
      height: 12px;
      border: none;
      border-radius: 0;
      background: #fff;
    }
  }

  .track-legend {
    .el-checkbox {
      display: inline-flex;
      align-items: center;
      margin-right: 0;
      color: #fff;
      font-weight: 400;
    }

    .el-checkbox__label {
      order: 0;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding-left: 0;
      color: #fff;
      font-family: "Microsoft YaHei", sans-serif;
      font-size: 14px;
      font-weight: 400;
      line-height: 19px;
    }

    .el-checkbox__input {
      order: 1;
      margin-left: 6px;
      line-height: 0;
    }

    .el-checkbox__inner {
      width: 16px;
      height: 16px;
      border-color: #fff;
      border-radius: 2px;
      background: transparent;

      &::after {
        left: 5px;
        top: 2px;
        width: 4px;
        height: 8px;
      }
    }

    .el-checkbox__input.is-checked .el-checkbox__inner {
      background: #2368d4;
      border-color: #2368d4;
    }

    .el-checkbox__input.is-checked + .el-checkbox__label {
      color: #fff;
    }
  }

  .el-image-viewer__wrapper {
    z-index: 3100;
  }
}

@media (max-width: 1600px) {
  .summary-grid,
  .replay-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .replay-grid__divider {
    display: none;
  }

  .records-row {
    grid-template-columns: 1fr;
    max-height: 36vh;
    flex: 0 1 36vh;
  }

  .video-main {
    min-height: 180px;
  }
}
</style>

<style lang="scss">
.record-detail-page .record-back .svg-icon {
  fill: none !important;
  color: #fff;
}

.record-detail-page .playback-panel {
  background: #101f3c;
}

.record-detail-page .record-playback-toolbar {
  display: flex !important;
  flex-direction: column !important;
  align-items: stretch !important;
  background: transparent;
}

.record-detail-page .playback-icon {
  width: 20px;
  height: 20px;
  border-radius: 2px;
  background: #465d7b;
  color: #fff;
}

.record-detail-page .playback-controls .time-label {
  width: auto;
  color: #fff;
  font-family: Bahnschrift, "DIN Alternate", sans-serif;
  font-size: 16px;
  font-weight: 400;
  line-height: 21px;
}

.record-detail-page .rate-select {
  width: 60px;
}

.el-image-viewer__wrapper {
  z-index: 4000 !important;
}
</style>
