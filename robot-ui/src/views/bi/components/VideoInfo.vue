<template>
  <div class="flx-center custom-video-info" :class="className">
    <div class="info-item flx-center">
      <!-- <svg-icon icon-class="battery-2" style="color: #3DB56A" />
      <span class="ml4">60%</span> -->
      <!-- <svg-icon
        :icon-class="item.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : item.battery >= 50 ? 'battery-2' : item.battery >= 40 ? 'battery-1' : 'battery-0'"
        :style="{ color: item.battery < 50 ? '#D33333' : '#3DB56A' }"
      >
      </svg-icon>
      <span class="ml4">{{ item.battery }}%</span> -->
    </div>
    <!-- <div class="info-item flx-center ml20">
      <svg-icon icon-class="meter" style="color: #21CCE7" />
      <span class="ml4">1.2m/s</span>
    </div>
    <div class="info-item flx-center ml20">
      <svg-icon icon-class="data" style="color: #159AFF" />
      <span class="ml4">106ms</span>
    </div> -->
    <div class="info-item flx-center">
      <span class="status p4">{{ cameraInfo.robot.status }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'VideoInfo',
  props: {
    className: {
      type: Object,
      default: () => ({ one: true }),
    },
    cameraInfo: {
      type: Object,
      default: () => ({}),
    },
  },
}
</script>