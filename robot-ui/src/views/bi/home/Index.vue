<template>
  <div class="h100 pt80 common-scroll bi-index-div" :class="{ collapse }">
    <Header />
    <BiIndexLeft @changeCollapse="changeCollapse" :collapse="collapse" />
    <BiIndexRight @changeCollapse="changeCollapse" :collapse="collapse" />
    <div class="map-div h100 flx-center pt57" style="align-items: start;" :class="{ full: collapse }">
      <div class="hp742 flx-center" style="width: 1118px; background: #071735;">
        <SlamMap :map="slamInfo.map" :points="slamInfo.points" :pathPointIds="slamInfo.pathPointIds" :show-labels="true" />
      </div>
    </div>
  </div>
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions } from 'vuex';
import Header from './Header.vue'
import BiIndexLeft from './Left.vue'
import BiIndexRight from './Right.vue'
import SlamMap from './slam/Index.vue';
import mapInfo from './slam/mapInfo.json'
import pathInfo from './slam/pathInfo.json'
import mapPoints from './slam/map-points.json'

export default {
  name: 'BiIndex',
  components: {Header, BiIndexLeft, BiIndexRight, SlamMap},
  data() {
    return {
      collapse: false,
      slamInfo: {
        map: mapInfo.data,
        points: mapPoints.data,
        pathPointIds: this.detailPointId(),
        showLabels: true
      }
    }
  },
  async mounted() {
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket']),
    ...mapActions('websocketExtraData', ['setAll']),
    detailPointId() {
      const record = pathInfo.data
      return [...(record?.points || [])].sort((a, b) => a.pointOrder - b.pointOrder).map((item) => item.mapPointId)
    },
    changeCollapse() {
      this.collapse = !this.collapse
    }
  },
  beforeDestroy() {
    // mqttClient.disconnect()
  },
}
</script>

<!-- <style lang="scss" scoped>
  .bi {
    width: 100vw;
    height: 100vh;
    background: #021328;
  }
</style> -->
<style lang="scss">
@import './index.scss';
</style>