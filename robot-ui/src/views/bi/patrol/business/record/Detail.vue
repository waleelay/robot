<template>
  <div>
    <div class="page-action-header">
      <el-button type="primary" @click="$emit('close')">返回执行记录</el-button>
      <div class="page-action-header__title">
        <strong>{{ instance.workflowName || "执行记录详情" }}</strong>
        <span>{{ instance.businessKey || instance.id || "-" }}</span>
      </div>
      <div class="page-action-header__actions">
        <!-- <StatusTag :value="instance.status" /> -->
        <el-button type="primary" :loading="loading" @click="loadReplay">刷新</el-button>
      </div>
    </div>
  
    <div :loading="loading" class="replay-layout">
      <section class="summary-grid">
        <div class="summary-item">
          <span>开始时间</span>
          <strong>{{ formatDateTime(replay?.startedAt || instance.startedAt) }}</strong>
        </div>
        <div class="summary-item">
          <span>结束时间</span>
          <strong>{{ formatDateTime(replay?.completedAt || instance.completedAt) }}</strong>
        </div>
        <div class="summary-item">
          <span>告警</span>
          <strong>{{ alarmEvents.length }}</strong>
        </div>
        <div class="summary-item">
          <span>视频</span>
          <strong>{{ playbackItems.length }}</strong>
        </div>
        <div class="summary-item">
          <span>轨迹状态</span>
          <strong>{{ trackStatusLabel(replay?.trackStatus || instance.trackStatus) }}</strong>
        </div>
      </section>
  
      <section class="playback-panel">
        <div class="playback-toolbar">
          <el-button
            type="primary"
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
            @change="loadHighResolutionSamples"
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
            :style="{ left: `${alarm.percent}%` }"
            :title="alarm.title"
            @click="jumpToAlarm(alarm)"
          />
        </div>
  
        <div class="replay-grid">
          <div class="map-panel">
            <div class="panel-title">
              <strong>运行轨迹</strong>
              <span v-if="samplesLoading">加载中</span>
              <span v-else>
                {{ trackGroups.length || groupedTrackSamples.length }} 台设备 / {{ trackSamples.length }} 个采样点
              </span>
            </div>
            <div v-if="trackGroups.length" class="track-legend">
              <el-checkbox-group v-model="visibleTrackKeys">
                <el-checkbox
                  v-for="group in trackGroups"
                  :key="trackGroupKey(group)"
                  :label="trackGroupKey(group)"
                >
                  <span class="track-legend__swatch" :style="{ background: group.color || defaultTrackColor(group) }" />
                  {{ group.deviceName || group.serialNumber || "设备轨迹" }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <div v-if="trackSamples.length" class="track-stage" :class="{ 'has-map': hasCalibratedMap && mapImageUrl }">
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
                <g v-for="line in fullTrackPolylines" :key="`full-${line.key}`">
                  <polyline :points="line.points" class="track-line full" :style="{ stroke: line.color }" />
                </g>
                <g v-for="line in visitedTrackPolylines" :key="`visited-${line.key}`">
                  <polyline :points="line.points" class="track-line visited-halo" />
                  <polyline :points="line.points" class="track-line visited" :style="{ stroke: line.color }" />
                </g>
                <g v-for="point in currentTrackPoints" :key="`current-${point.key}`">
                  <circle
                    class="track-current-halo"
                    :cx="projectPoint(point.sample).x"
                    :cy="projectPoint(point.sample).y"
                    r="14"
                  />
                  <circle
                    class="track-current"
                    :style="{ fill: point.color }"
                    :cx="projectPoint(point.sample).x"
                    :cy="projectPoint(point.sample).y"
                    r="8"
                  />
                  <text
                    class="track-label"
                    :x="projectPoint(point.sample).x + 12"
                    :y="projectPoint(point.sample).y - 12"
                  >
                    {{ point.label }}
                  </text>
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
            <div v-if="playbackItems.length" class="video-list">
              <div
                v-for="(video, index) in playbackItems"
                :key="video.videoId || index"
                class="video-card"
                :class="{ 'is-primary': primaryVideoKey === videoKey(video, index) }"
                @click="primaryVideoKey = videoKey(video, index)"
              >
                <div
                  v-if="isDemoVideo(video)"
                  class="demo-video-surface"
                  :class="demoVideoClass(video)"
                  :style="demoVideoStyle(video)"
                >
                  <div class="demo-video-sky" />
                  <div class="demo-video-road" />
                  <div class="demo-video-target" />
                  <div class="demo-video-scan" />
                  <div class="demo-video-caption">
                    <strong>{{ videoSourceLabel(video) }}</strong>
                    <span>{{ formatOffset(videoPlaybackSeconds(video)) }}</span>
                  </div>
                </div>
                <video
                  v-else-if="videoUrl(video)"
                  :ref="setVideoRef"
                  muted
                  controls
                  preload="metadata"
                  :src="videoUrl(video)"
                  :data-video-index="index"
                  @loadedmetadata="syncVideos"
                  @play="handleVideoNativePlay"
                  @pause="handleVideoNativePause"
                />
                <div v-else class="video-placeholder">
                  <strong>{{ video.videoId || "-" }}</strong>
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
            <el-empty v-else description="暂无视频结果" />
          </div>
        </div>
      </section>
  
      <section class="panel-row">
        <div class="panel">
          <div class="panel-title">
            <strong>告警信息</strong>
            <span>{{ alarmEvents.length }} 条</span>
          </div>
          <el-table :data="alarmEvents" border height="260" @row-click="jumpToAlarmEvent">
            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
            </el-table-column>
            <el-table-column label="类型" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ alarmType(row) }}</template>
            </el-table-column>
            <el-table-column label="内容" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ alarmContent(row) }}</template>
            </el-table-column>
            <el-table-column prop="eventStatus" label="状态" width="110" />
          </el-table>
        </div>
  
        <div class="panel">
          <div class="panel-title">
            <strong>回放状态</strong>
            <span>{{ trackStatusLabel(replay?.trackStatus || instance.trackStatus) }}</span>
          </div>
          <div class="replay-status-list">
            <div>
              <span>执行设备</span>
              <strong>{{ deviceSummary }}</strong>
            </div>
            <div>
              <span>轨迹</span>
              <strong>{{ trackStatusLabel(replay?.trackStatus || instance.trackStatus) }}</strong>
            </div>
            <div>
              <span>视频</span>
              <strong>{{ playbackItems.length }} 路</strong>
            </div>
            <div>
              <span>告警</span>
              <strong>{{ alarmEvents.length }} 条</strong>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import { previewImageBlob, getTrackRecordSamples, getTaskRecordReplay } from "../../../../../api/new-bi";
// import { runtimeConfig } from "./runtime";

export default {
  name: "TaskExecutionRecordDetailPage",
  data() {
    return {
      loading: false,
      samplesLoading: false,
      replay: null,
      trackSamples: [],
      visibleTrackKeys: [],
      currentOffset: 0,
      playing: false,
      playbackRate: 1,
      videoRefs: [],
      primaryVideoKey: "",
      mapImageUrl: "",
      mapImageStatus: "地图加载中",
      playTimer: null,
      syncingVideos: false,
      mapImageObjectUrl: "",
      mapImageLoadSeq: 0,
    };
  },
  computed: {
    instance() {
      return (this.replay && this.replay.detail && this.replay.detail.instance) || {};
    },
    trackGroups() {
      return (this.replay && this.replay.trackGroups) || [];
    },
    alarmEvents() {
      return (this.replay && this.replay.alarmEvents) || [];
    },
    videoResults() {
      return (this.replay && this.replay.videoResults) || [];
    },
    playbackItems() {
      return (this.replay && this.replay.mediaPlaybackItems) || this.videoResults;
    },
    replayMap() {
      return (this.replay && this.replay.replayMap) || null;
    },
    hasCalibratedMap() {
      const map = this.replayMap;
      return !!(
        map &&
        map.id &&
        map.previewFileId &&
        Number(map.previewWidth) &&
        Number(map.previewHeight) &&
        Number(map.resolution) > 0 &&
        map.originX !== undefined &&
        map.originY !== undefined &&
        map.originYaw !== undefined
      );
    },
    trackViewBox() {
      if (this.hasCalibratedMap) {
        return `0 0 ${Number(this.replayMap.previewWidth)} ${Number(this.replayMap.previewHeight)}`;
      }
      return "0 0 1000 560";
    },
    startedAt() {
      const val =
        this.replay &&
        (this.replay.timelineStartAt || this.replay.startedAt || this.instance.startedAt);
      return this.parseDate(val);
    },
    completedAt() {
      const val =
        this.replay &&
        (this.replay.timelineEndAt || this.replay.completedAt || this.instance.completedAt);
      return this.parseDate(val);
    },
    durationSeconds() {
      if (!this.startedAt || !this.completedAt) return 0;
      return Math.max(0, Math.floor((this.completedAt.getTime() - this.startedAt.getTime()) / 1000));
    },
    currentDateTime() {
      if (!this.startedAt) return null;
      return new Date(this.startedAt.getTime() + this.currentOffset * 1000);
    },
    sortedSamples() {
      return [...this.trackSamples].sort((left, right) => this.sampleTime(left) - this.sampleTime(right));
    },
    visibleSamples() {
      return this.sortedSamples.filter((sample) => {
        if (!this.trackGroups.length) return true;
        return this.visibleTrackKeys.includes(this.sampleTrackKey(sample));
      });
    },
    trackGroupMap() {
      const map = new Map();
      this.trackGroups.forEach((group) => {
        map.set(this.trackGroupKey(group), group);
      });
      return map;
    },
    groupedTrackSamples() {
      const groups = new Map();
      this.visibleSamples.forEach((sample) => {
        const key = this.sampleTrackKey(sample);
        if (!groups.has(key)) {
          const configuredGroup = this.trackGroupMap.get(key);
          groups.set(key, {
            key,
            color: configuredGroup && configuredGroup.color
              ? configuredGroup.color
              : this.defaultTrackColor({
                  serialNumber: sample.serialNumber,
                  deviceTaskInstanceId: sample.deviceTaskInstanceId,
                }),
            label: (configuredGroup && configuredGroup.deviceName) || sample.deviceName || sample.serialNumber || "设备轨迹",
            samples: [],
          });
        }
        groups.get(key).samples.push(sample);
      });
      return [...groups.values()];
    },
    currentTrackPoints() {
      if (!this.currentDateTime) return [];
      return this.groupedTrackSamples
        .map((group) => {
          if (!group.samples.length) return null;
          const firstTime = this.sampleTime(group.samples[0]);
          if (this.currentDateTime.getTime() < firstTime) {
            return null;
          }
          const best = this.interpolatedSampleAt(group.samples, this.currentDateTime);
          return {
            key: group.key,
            color: group.color,
            sample: best,
            label: best.pointName || best.nodeId || group.label,
          };
        })
        .filter(Boolean);
    },
    trackBounds() {
      const xs = this.visibleSamples.map((sample) => Number(sample.x)).filter(Number.isFinite);
      const ys = this.visibleSamples.map((sample) => Number(sample.y)).filter(Number.isFinite);
      if (!xs.length || !ys.length) {
        return { minX: 0, maxX: 1, minY: 0, maxY: 1 };
      }
      const minX = Math.min(...xs);
      const maxX = Math.max(...xs);
      const minY = Math.min(...ys);
      const maxY = Math.max(...ys);
      return {
        minX,
        maxX: maxX === minX ? minX + 1 : maxX,
        minY,
        maxY: maxY === minY ? minY + 1 : maxY,
      };
    },
    fullTrackPolylines() {
      return this.groupedTrackSamples.flatMap((group) =>
        this.trackSegments(group.samples).map((segment, index) => ({
          key: `${group.key}-${index}`,
          color: group.color,
          points: segment.map(this.projectPointText).join(" "),
        }))
      );
    },
    visitedTrackPolylines() {
      return this.groupedTrackSamples.flatMap((group) => {
        const samples = group.samples.filter(
          (sample) =>
            !this.currentDateTime || this.sampleTime(sample) <= this.currentDateTime.getTime()
        );
        return this.trackSegments(samples).map((segment, index) => ({
          key: `${group.key}-${index}`,
          color: group.color,
          points: segment.map(this.projectPointText).join(" "),
        }));
      });
    },
    deviceSummary() {
      const devices =
        (this.replay && (this.replay.deviceSummaries || this.instance.deviceSummaries)) || [];
      if (!devices.length) return "-";
      if (devices.length === 1) return devices[0].deviceName || devices[0].serialNumber || "-";
      return (
        devices
          .slice(0, 2)
          .map((item) => item.deviceName || item.serialNumber || "-")
          .join("、") +
        (devices.length > 2 ? ` 等 ${devices.length} 台` : "")
      );
    },
    alarmMarkers() {
      if (!this.startedAt || !this.durationSeconds) return [];
      return this.alarmEvents
        .map((event) => {
          const time = this.parseDate(event.occurredAt);
          if (!time) return null;
          const offset = Math.max(0, Math.floor((time.getTime() - this.startedAt.getTime()) / 1000));
          return {
            id: event.id,
            offset,
            percent: Math.min(100, Math.max(0, (offset / this.durationSeconds) * 100)),
            title: `${this.formatDateTime(event.occurredAt)} ${this.alarmType(event)}`,
          };
        })
        .filter(Boolean);
    },
  },
  watch: {
    // "$route.params.id": "loadReplay",
    replayMap: {
      handler(newVal) {
        this.loadReplayMapImage({
          id: newVal && newVal.id,
          cacheKey: (newVal && (newVal.previewGeneratedAt || newVal.previewFileId)) || undefined,
          enabled: this.hasCalibratedMap,
        });
      },
      immediate: true,
    },
    currentOffset() {
      this.syncVideos();
    },
    playbackItems(items) {
      if (!items.length) {
        this.primaryVideoKey = "";
        return;
      }
      if (!items.some((item, index) => this.videoKey(item, index) === this.primaryVideoKey)) {
        this.primaryVideoKey = this.videoKey(items[0], 0);
      }
    },
  },
  mounted() {
    // this.loadReplay();
  },
  beforeDestroy() {
    this.stopPlayback();
    this.revokeMapImageUrl();
  },
  beforeUpdate() {
    this.videoRefs = [];
  },
  methods: {
    // ---- 工具函数 ----
    parseDate(value) {
      if (!value) return null;
      const date = value instanceof Date ? value : new Date(this.normalizeDateText(value));
      return Number.isNaN(date.getTime()) ? null : date;
    },
    normalizeDateText(value) {
      let text = String(value).trim().replace(" ", "T");
      text = text.replace(/([+-]\d{2}:?\d{2}|Z)$/i, "");
      text = text.replace(/(\.\d{3})\d+/, "$1");
      return text;
    },
    formatDateTime(value) {
      const date = this.parseDate(value);
      if (!date) return "-";
      const pad = (part) => String(part).padStart(2, "0");
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    },
    formatOffset(value) {
      const seconds = Math.max(0, Math.floor(Number(value) || 0));
      const hour = Math.floor(seconds / 3600);
      const minute = Math.floor((seconds % 3600) / 60);
      const second = seconds % 60;
      return [hour, minute, second]
        .map((part) => String(part).padStart(2, "0"))
        .join(":");
    },
    sampleTime(sample) {
      const date = this.parseDate(sample.sampledAt);
      return date ? date.getTime() : 0;
    },
    trackGroupKey(group) {
      return String(group.deviceTaskInstanceId || group.serialNumber || group.deviceName || "track");
    },
    sampleTrackKey(sample) {
      return String(sample.deviceTaskInstanceId || sample.serialNumber || sample.deviceName || "track");
    },
    defaultTrackColor(seed) {
      const colors = ["#2563eb", "#f97316", "#14b8a6", "#a855f7", "#ef4444", "#22c55e", "#f59e0b"];
      const value = String(seed.deviceTaskInstanceId || seed.serialNumber || seed.deviceName || "track");
      let hash = 0;
      for (const char of value) {
        hash = (hash * 31 + char.charCodeAt(0)) % colors.length;
      }
      return colors[Math.abs(hash) % colors.length];
    },
    trackSegments(samples) {
      const segments = [];
      let current = [];
      for (const sample of samples) {
        if (current.length) {
          const previous = current[current.length - 1];
          if (Math.abs(this.sampleTime(sample) - this.sampleTime(previous)) > 10000) {
            if (current.length > 1) segments.push(current);
            current = [];
          }
        }
        current.push(sample);
      }
      if (current.length > 1) segments.push(current);
      return segments;
    },
    interpolatedSampleAt(samples, time) {
      if (!samples.length || !time) return samples[0] || {};
      const currentTime = time.getTime();
      let previous = samples[0];
      for (const sample of samples) {
        const timeValue = this.sampleTime(sample);
        if (timeValue >= currentTime) {
          if (sample === previous) return sample;
          const previousTime = this.sampleTime(previous);
          const span = Math.max(1, timeValue - previousTime);
          const ratio = Math.min(1, Math.max(0, (currentTime - previousTime) / span));
          return {
            ...sample,
            x: this.interpolateNumber(previous.x, sample.x, ratio),
            y: this.interpolateNumber(previous.y, sample.y, ratio),
            z: this.interpolateNumber(previous.z, sample.z, ratio),
            yaw: this.interpolateNumber(previous.yaw, sample.yaw, ratio),
            pointName: sample.pointName || previous.pointName,
            nodeId: sample.nodeId || previous.nodeId,
          };
        }
        previous = sample;
      }
      return samples[samples.length - 1];
    },
    interpolateNumber(left, right, ratio) {
      const start = Number(left);
      const end = Number(right);
      if (!Number.isFinite(start)) return right;
      if (!Number.isFinite(end)) return left;
      return start + (end - start) * ratio;
    },
    projectPoint(sample) {
      if (this.hasCalibratedMap) {
        const pixel = this.mapSampleToPixel(sample, this.replayMap);
        if (pixel) return pixel;
      }
      const bounds = this.trackBounds;
      const width = bounds.maxX - bounds.minX || 1;
      const height = bounds.maxY - bounds.minY || 1;
      const padding = 36;
      return {
        x: padding + ((Number(sample.x) - bounds.minX) / width) * (1000 - padding * 2),
        y: 560 - padding - ((Number(sample.y) - bounds.minY) / height) * (560 - padding * 2),
      };
    },
    mapSampleToPixel(sample, map) {
      if (
        !map ||
        !Number.isFinite(Number(sample && sample.x)) ||
        !Number.isFinite(Number(sample && sample.y))
      )
        return null;
      const height = Number(map.previewHeight);
      const resolution = Number(map.resolution);
      const originX = Number(map.originX);
      const originY = Number(map.originY);
      const originYaw = Number(map.originYaw);
      if (!height || !resolution) return null;
      const dx = Number(sample.x) - originX;
      const dy = Number(sample.y) - originY;
      const cos = Math.cos(originYaw);
      const sin = Math.sin(originYaw);
      const localX = dx * cos + dy * sin;
      const localY = -dx * sin + dy * cos;
      return { x: localX / resolution, y: height - localY / resolution };
    },
    projectPointText(sample) {
      const point = this.projectPoint(sample);
      return `${point.x.toFixed(1)},${point.y.toFixed(1)}`;
    },
    videoUrl(video) {
      const directUrl =
        (video && video.metadata && video.metadata.playbackUrl) ||
        (video && video.playbackUrl) ||
        "";
      if (directUrl && !this.isDemoUrl(directUrl)) return directUrl;
      if (this.isDemoUrl(directUrl)) return "";
      // if (!(video && video.videoId) || !runtimeConfig.mediaPlaybackUrlTemplate) return "";
      // return runtimeConfig.mediaPlaybackUrlTemplate
      //   .replaceAll("{videoId}", encodeURIComponent(video.videoId))
      //   .replaceAll(":videoId", encodeURIComponent(video.videoId));
    },
    isDemoUrl(value) {
      return typeof value === "string" && value.startsWith("eiop-demo://");
    },
    isDemoVideo(video) {
      const directUrl =
        (video && video.metadata && video.metadata.playbackUrl) ||
        (video && video.playbackUrl) ||
        "";
      return this.isDemoUrl(directUrl);
    },
    demoVideoClass(video) {
      const key =
        (video && video.metadata && video.metadata.playbackUrl) ||
        (video && video.playbackUrl) ||
        (video && video.videoId) ||
        "";
      if (key.includes("thermal")) return "demo-video-surface--thermal";
      if (key.includes("ptz")) return "demo-video-surface--ptz";
      return "demo-video-surface--body";
    },
    demoVideoProgress(video) {
      const timing = this.videoTiming(video);
      const current = this.currentDateTime;
      if (!current || !timing.start || !timing.end) return 0;
      const span = Math.max(1, timing.end.getTime() - timing.start.getTime());
      return Math.min(1, Math.max(0, (current.getTime() - timing.start.getTime()) / span));
    },
    demoVideoStyle(video) {
      const progress = this.demoVideoProgress(video);
      return {
        "--demo-target-offset": `${progress * 62}%`,
        "--demo-hotspot-offset": `${16 + progress * 60}%`,
        "--demo-scan-offset": `${-80 + progress * 160}%`,
      };
    },
    videoPlaybackSeconds(video) {
      const timing = this.videoTiming(video);
      const current = this.currentDateTime;
      if (!current || !timing.start) return 0;
      return Math.max(0, Math.floor((current.getTime() - timing.start.getTime()) / 1000));
    },
    videoKey(video, index) {
      return (video && video.videoId) || `${(video && video.actionRef) || "video"}-${index}`;
    },
    videoPlaybackState(video) {
      const current = this.currentDateTime;
      if (!current) return { state: "UNKNOWN", label: "等待时间轴" };
      const timing = this.videoTiming(video);
      if (timing.start && current.getTime() < timing.start.getTime()) {
        return { state: "BEFORE_START", label: "未开始" };
      }
      if (timing.end && current.getTime() > timing.end.getTime()) {
        return { state: "ENDED", label: "已结束" };
      }
      if (!this.videoUrl(video) && !this.isDemoVideo(video)) {
        return { state: "NO_URL", label: "视频地址处理中" };
      }
      return { state: "PLAYING", label: "播放中" };
    },
    videoTiming(video) {
      const timelineStart = this.startedAt;
      const timelineEnd = this.completedAt || timelineStart;
      let start = this.parseDate(video && video.startedAt);
      let end = this.parseDate(video && video.endedAt);
      const overlapsTimeline =
        timelineStart &&
        timelineEnd &&
        start &&
        start.getTime() <= timelineEnd.getTime() &&
        (!end || end.getTime() >= timelineStart.getTime());
      if (!start || !overlapsTimeline) {
        start = timelineStart;
      }
      if (!end || (start && end.getTime() < start.getTime())) {
        end = timelineEnd;
      }
      return { start, end };
    },
    videoPositionForTimeline(current, timing, video) {
      if (!timing.start) {
        return { currentTime: 0, seekable: false, playable: false };
      }
      if (current.getTime() < timing.start.getTime()) {
        return { currentTime: 0, seekable: true, playable: false };
      }
      const elapsed = Math.max(0, (current.getTime() - timing.start.getTime()) / 1000);
      const duration =
        video && Number.isFinite(video.duration) && video.duration > 0 ? video.duration : null;
      if (timing.end && current.getTime() > timing.end.getTime()) {
        const endElapsed = Math.max(0, (timing.end.getTime() - timing.start.getTime()) / 1000);
        return {
          currentTime: duration == null ? endElapsed : Math.min(duration, endElapsed),
          seekable: true,
          playable: false,
        };
      }
      return {
        currentTime: duration == null ? elapsed : Math.min(duration, elapsed),
        seekable: true,
        playable: Boolean(
          this.videoUrl(this.playbackItems[Number(video && video.dataset && video.dataset.videoIndex)])
        ),
      };
    },
    mediaTypeLabel(value) {
      return (
        {
          VISIBLE: "可见光",
          THERMAL: "红外",
          OTHER: "其他",
        }[value] || value || "其他"
      );
    },
    alarmType(row) {
      const payload = this.parseJson(row.payloadJson);
      return payload.alarmType || payload.type || row.eventSubtype || row.eventType || "-";
    },
    alarmContent(row) {
      const payload = this.parseJson(row.payloadJson);
      return (
        payload.content ||
        payload.message ||
        payload.title ||
        payload.alarmContent ||
        row.eventSubtype ||
        "-"
      );
    },
    trackStatusLabel(value) {
      return (
        {
          AVAILABLE: "轨迹正常",
          PROCESSING: "轨迹处理中",
          MISSING: "轨迹缺失",
        }[value] || value || "轨迹处理中"
      );
    },
    parseJson(value) {
      if (!value) return {};
      if (typeof value === "object") return value;
      try {
        return JSON.parse(value);
      } catch {
        return {};
      }
    },
    toApiDate(date) {
      if (!date) return undefined;
      const pad = (value) => String(value).padStart(2, "0");
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    },

    // ---- 业务方法 ----
    async loadReplay(id) {
      this.id = id || this.id;
      this.stopPlayback();
      this.loading = true;
      try {
        this.replay = await getTaskRecordReplay(Number(this.id));
        this.visibleTrackKeys = this.trackGroups.map(this.trackGroupKey);
        this.currentOffset = 0;
        this.trackSamples = [];
        const initialSamples =
          this.replay && Array.isArray(this.replay.initialTrackSamples)
            ? this.replay.initialTrackSamples
            : [];
        if (initialSamples.length) {
          this.trackSamples = this.mergeSamples([], initialSamples);
        } else {
          await this.loadTrackSamples();
        }
        await this.$nextTick();
        this.syncVideos();
      } catch (error) {
        this.$message.error(error?.message || '请求失败');
      } finally {
        this.loading = false;
      }
    },
    async loadReplayMapImage({ id, cacheKey, enabled } = {}) {
      const seq = ++this.mapImageLoadSeq;
      this.revokeMapImageUrl();
      this.mapImageStatus = "地图加载中";
      if (!enabled || !id) {
        return;
      }
      try {
        const res = await previewImageBlob(id, cacheKey);
        if (res.code !== '0') {
          this.$message.error(ref.message)
          return;
        } else {
          const nextUrl = URL.createObjectURL(res.data);
          if (seq !== this.mapImageLoadSeq) {
            URL.revokeObjectURL(nextUrl);
            return;
          }
          this.mapImageObjectUrl = nextUrl;
          this.mapImageUrl = nextUrl;
        }
      } catch {
        if (seq === this.mapImageLoadSeq) {
          this.mapImageStatus = "地图预览加载失败";
        }
      }
    },
    revokeMapImageUrl() {
      if (this.mapImageObjectUrl) {
        URL.revokeObjectURL(this.mapImageObjectUrl);
        this.mapImageObjectUrl = "";
      }
      this.mapImageUrl = "";
    },
    async loadTrackSamples(options = {}) {
      if (!this.id || !this.startedAt) {
        this.trackSamples = [];
        return;
      }
      this.samplesLoading = true;
      try {
        const longTask = this.durationSeconds > 7200;
        const params = {
          from: this.toApiDate(options.from || this.startedAt),
          to: this.toApiDate(options.to || this.completedAt || new Date()),
          resolutionSeconds:
            options.resolutionSeconds ||
            (longTask ? 10 : this.durationSeconds > 1800 ? 5 : 1),
        };
        const samples = await getTrackRecordSamples(
          Number(this.id),
          params
        );
        this.trackSamples = this.mergeSamples(this.trackSamples, samples || []);
      } catch (error) {
        this.$message.error(error?.message || '请求失败');
      } finally {
        this.samplesLoading = false;
      }
    },
    async loadHighResolutionSamples() {
      if (!this.startedAt) return;
      const center = this.currentDateTime || this.startedAt;
      await this.loadTrackSamples({
        from: new Date(center.getTime() - 120000),
        to: new Date(center.getTime() + 120000),
        resolutionSeconds: 1,
      });
    },
    mergeSamples(current, incoming) {
      const byKey = new Map();
      [...current, ...incoming].forEach((sample) => {
        byKey.set(
          `${sample.sampledAt || ""}:${this.sampleTrackKey(sample)}:${sample.nodeId || ""}:${
            sample.x
          }:${sample.y}`,
          sample
        );
      });
      return [...byKey.values()].sort((left, right) => this.sampleTime(left) - this.sampleTime(right));
    },
    togglePlay() {
      if (this.playing) {
        this.stopPlayback();
        return;
      }
      this.startPlayback();
    },
    startPlayback() {
      if (this.playing) {
        return;
      }
      this.playing = true;
      this.syncVideos();
      this.playTimer = window.setInterval(() => {
        this.currentOffset = Math.min(this.durationSeconds, this.currentOffset + this.playbackRate);
        if (this.currentOffset >= this.durationSeconds) {
          this.stopPlayback();
        }
      }, 1000);
    },
    stopPlayback() {
      this.playing = false;
      if (this.playTimer) {
        window.clearInterval(this.playTimer);
        this.playTimer = null;
      }
      this.setVideoSyncing(() => {
        this.videoRefs.forEach((video) => {
          if (video && video.pause) video.pause();
        });
      });
    },
    setVideoRef(el) {
      if (el) this.videoRefs.push(el);
    },
    syncVideos() {
      const current = this.currentDateTime;
      if (!current) return;
      this.setVideoSyncing(() => {
        this.videoRefs.forEach((video) => {
          const index = Number(video.dataset.videoIndex);
          const item = this.playbackItems[index];
          const timing = this.videoTiming(item);
          if (!timing.start) return;
          const position = this.videoPositionForTimeline(current, timing, video);
          if (position.seekable && Number.isFinite(position.currentTime)) {
            const threshold = this.playing ? 0.8 : 0.05;
            if (Math.abs((video.currentTime || 0) - position.currentTime) > threshold) {
              video.currentTime = position.currentTime;
            }
          }
          video.playbackRate = this.playbackRate;
          if (this.playing && position.playable && video.paused) {
            video.play().catch(() => undefined);
          }
          if ((!this.playing || !position.playable) && !video.paused) {
            video.pause();
          }
        });
      });
    },
    handleVideoNativePlay(event) {
      if (this.syncingVideos) return;
      this.syncVideos();
      this.startPlayback();
    },
    handleVideoNativePause() {
      if (this.syncingVideos) return;
      if (this.playing) {
        this.stopPlayback();
      }
    },
    setVideoSyncing(callback) {
      this.syncingVideos = true;
      try {
        callback();
      } finally {
        window.setTimeout(() => {
          this.syncingVideos = false;
        }, 0);
      }
    },
    jumpToAlarm(alarm) {
      this.stopPlayback();
      this.currentOffset = Math.min(this.durationSeconds, Math.max(0, alarm.offset || 0));
      this.loadHighResolutionSamples();
    },
    jumpToAlarmEvent(event) {
      if (!this.startedAt) return;
      const occurredAt = this.parseDate(event && event.occurredAt);
      if (!occurredAt) return;
      const offset = Math.floor((occurredAt.getTime() - this.startedAt.getTime()) / 1000);
      this.jumpToAlarm({ offset });
    },
    videoSourceLabel(video) {
      return (
        video.label ||
        video.sourceComponentName ||
        video.sourceComponentCode ||
        video.actionCode ||
        video.actionRef ||
        "视频来源"
      );
    },
  },
};
</script>