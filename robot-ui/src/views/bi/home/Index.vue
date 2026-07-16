<template>
  <div class="h100 pt80 common-scroll bi-index-div" :class="{ collapse }">
    <Header />
    <BiIndexLeft @changeCollapse="changeCollapse" :collapse="collapse" />
    <BiIndexRight @changeCollapse="changeCollapse" :collapse="collapse" />
    <div class="map-div flx-center" style="height: calc(100% + 55px); margin-top: -55px; align-items: start;" :class="{ full: collapse }">
    <!-- <div class="map-div h100 flx-center pt57" style="align-items: start;" :class="{ full: collapse }"> -->
      <!-- <div class="hp742 flx-center" style="width: 1118px; background: #112B4D;"> -->
      <div v-if="isSlam" class="w100 h100 flx-center" style="z-index: 0; background: #112B4D;">
        <SlamMap
          :map="currentSlamMap"
          :points="slamInfo.points"
          :pathPointIds="slamInfo.pathPointIds"
          ref="globalMapRef"
          :show-labels="true"
           @changeMapType="changeMapType"
        />
      </div>
      <template v-else>
        <GlobalMap v-if="angle === '2D'" style="z-index: 0;" ref="globalMapRef" />
        <img v-else src="@/assets/images/new-bi/map-3d.png" width="100%" height="100%" style="z-index: 0;" />
      </template>
      <MapTool :isSlam="isSlam" :showAngle="!isSlam" :currentSlam="currentSlamMapId" @changeMapAngle="changeMapAngle" :angle="angle" @changeMapZoom="changeMapZoom" @changeMapType="changeMapType" @changeSlamMap="changeSlamMap" @setCenter="setCenter" />
      <!-- <div class="map-footer"></div> -->
    </div>
    <!-- <el-select
      :value="$route.name"
      placeholder="看板中心"
      @change="changePage"
      class="wp130 tac page-select"
      title="看板中心"
      popper-class="custom-select page-select-popper"
    >
      <el-option v-for="item in pageList" :key="item.label" :label="item.label" :value="item.value" />
    </el-select> -->
    <!-- <div class="page-change">
      <PageChangeDropdown />
    </div> -->

  </div>
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions, mapState } from 'vuex';
import Header from './Header.vue'
import BiIndexLeft from './Left.vue'
import BiIndexRight from './Right.vue'
import SlamMap from './slam/Index.vue';
import mapInfo from './slam/mapInfo.json'
import pathInfo from './slam/pathInfo.json'
import mapPoints from './slam/map-points.json'
import GlobalMap from './../gis/globalMap/Index.vue'
import MapTool from './../patrol/panorama/map/MapTool.vue'
import PageChangeDropdown from './PageChangeDropdown.vue'

export default {
  name: 'BiIndex',
  components: {Header, BiIndexLeft, BiIndexRight, SlamMap, GlobalMap, MapTool, PageChangeDropdown},
  data() {
    return {
      collapse: false,
      isSlam: false,
      slamInfo: {
        map: mapInfo.data,
        points: mapPoints.data,
        pathPointIds: this.detailPointId(),
        showLabels: true
      },
      currentSlamMapId: null,
      angle: '2D'
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot']),
    currentSlamMap() {
      // console.log('slamInfo.map:', this.slamInfo, this.slamInfo.map)
      // return this.slamInfo.map
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.mapInfo || this.slamMapList.find(item => String(item.id) === String(this.currentSlamMapId)) || this.slamInfo.map
    }
  },
  async mounted() {
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket']),
    ...mapActions('websocketExtraData', ['setAll']),
    changeMapAngle(angle) {
      this.angle = this.angle === '3D' ? '2D' : '3D'
    },
    detailPointId() {
      const record = pathInfo.data
      return [...(record?.points || [])].sort((a, b) => a.pointOrder - b.pointOrder).map((item) => item.mapPointId)
    },
    changeCollapse() {
      this.collapse = !this.collapse
    },
    changeMapZoom(data) {
      this.$refs.globalMapRef?.changeMapZoom(data)
    },
    changeMapType(type) {
      this.isSlam = type ? type === 'slam' : !this.isSlam
      if (type !== 'slam') {
        this.currentSlamMapId = null
      }
    },
    changeSlamMap(mapInfo) {
      this.currentSlamMapId = mapInfo?.id ?? null
      this.isSlam = true
    },
    setCenter() {
      const mapRef = this.$refs.globalMapRef
      if (this.isSlam) {
        mapRef?.backCenter()
      } else {
        mapRef?.setCenter()
      }
    },
  },
  watch: {
    // slamMapList: {
    //   immediate: false,
    //   handler(list) {
    //     if (!Array.isArray(list) || !list.length) return
    //     const selectedExists = list.some(item => String(item.id) === String(this.currentSlamMapId))
    //     if (!selectedExists) {
    //       this.currentSlamMapId = (list.find(item => item.previewFileId) || list[0]).id
    //     }
    //   }
    // }
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
.page-change {
  position: absolute;
  bottom: 168px;
  right: 398px;
  cursor: pointer;
}
// ::v-deep .el-select {
.page-select {
  position: absolute;
  bottom: 144px;
  right: 398px;
  height: 42px;
  &:hover .el-input__inner {
    border: 1px rgba(44, 173, 255, 0.50) solid;
  }
  .el-input__inner {
    height: 42px;
    background: linear-gradient(0deg, rgba(16, 61, 135, 0.80) 14.29%, rgba(41, 113, 216, 0.80) 90.48%);
    border: 1px solid #1E4D91;
    color: #FFF;
    text-shadow: 0 1px 0 rgba(0, 22, 35, 0.20);
    font-family: "Microsoft YaHei";
    font-size: 20px;
    line-height: 20px; /* 100% */
    text-align: center;
  }
  .el-input__suffix {
    .el-input__icon {
      line-height: 42px;
    }
  }
}
// }
</style>
