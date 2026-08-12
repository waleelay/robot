<template>
  <div class="flx-center custom-video-info" :class="className">
    <div class="info-item flx-center">
      <svg-icon
        :icon-class="currentRobot?.battery >= 90 ? 'battery-4' : currentRobot?.battery >= 80 ? 'battery-3' : currentRobot?.battery >= 50 ? 'battery-2' : currentRobot?.battery >= 40 ? 'battery-1' : 'battery-0'"
        :style="{ color: currentRobot?.battery < 50 ? '#D33333' : '#3DB56A' }"
      >
      </svg-icon>
      <span class="ml4">{{ currentRobot?.battery || 0 }}%</span>
    </div>
    <div class="info-item flx-center ml20">
      <svg-icon icon-class="meter" style="color: #21CCE7" />
      <span class="ml4">{{ Number(currentRobot?.speed || 0).toFixed(2) }}m/s</span>
    </div>
    <!-- <div class="info-item flx-center ml20">
      <svg-icon icon-class="data" style="color: #159AFF" />
      <span class="ml4">{{ latencyText }}</span>
    </div> -->
    <div class="info-item flx-center ml20">
      <span class="status p4" :class="currentRobot?.statusClass || ''">{{ currentRobot?.customStatusName || currentRobot?.status || '-' }}</span>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex';

export default {
  name: 'VideoInfo',
  props: {
    className: {
      type: Object,
      default: () => ({ one: true }),
    },
    cameraKey: {
      type: String,
      default: '',
    },
  },
  computed: {
    ...mapState('websocketRobot', ['cameras']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    cameraInfo() {
      return this.cameras?.[this.cameraKey] || {}
    },
    currentRobot() {
      const robotId = this.cameraInfo?.robotId
      return this.robotBaseInfo?.[robotId] || {}
    },
    latencyText() {
      const latencyMs = this.cameraInfo?.latencyMs
      if (latencyMs === undefined || latencyMs === null || !Number.isFinite(Number(latencyMs))) {
        return '-'
      }
      return `${Math.round(Number(latencyMs))}ms`
    },
  }
}
</script>
