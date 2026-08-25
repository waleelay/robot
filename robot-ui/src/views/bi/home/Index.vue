<template>
  <div class="h100 pt80 common-scroll bi-index-div" :class="{ collapse }">
    <Header />
    <BiIndexLeft ref="leftRef" @changeCollapse="changeCollapse" :collapse="collapse" @patrol-select-change="onPatrolSelectChange" />
    <BiIndexRight @changeCollapse="changeCollapse" :collapse="collapse" />
    <div
      class="map-div flx-center"
      style="height: calc(100% + 55px); margin-top: -55px; align-items: start;"
      :class="{ full: collapse }"
      @click.capture="onMapBlankClick"
    >
    <!-- <div class="map-div h100 flx-center pt57" style="align-items: start;" :class="{ full: collapse }"> -->
      <!-- <div class="hp742 flx-center" style="width: 1118px; background: #112B4D;"> -->
      <transition name="slam-map-loading-fade">
        <div
          v-if="!overviewReady"
          class="slam-map-loading flx-center flex-column"
          @wheel.prevent
          @mousedown.stop
        >
          <div v-if="!overviewLoadError" class="slam-map-loading__spinner" aria-hidden="true"></div>
          <p class="slam-map-loading__text">{{ overviewLoadError ? '大屏数据暂不可用，请刷新页面重试' : '正在获取地图数据' }}</p>
        </div>
      </transition>
      <div v-if="globalMapId && globalMapId !== 'gis'" class="slam-map-host w100 h100" style="z-index: 0;">
        <GlobalSlamMap
          :map="slamMapPayload"
          :pathPointIds="slamInfo.pathPointIds"
          :collapse="collapse"
          visible-layout="home"
          ref="globalMapRef"
          :show-labels="true"
          @changeMapType="changeMapType"
          @preview-unavailable="onSlamPreviewUnavailable"
        />
      </div>
      <template v-if="globalMapId === 'gis'">
        <GlobalGisMap
          v-if="angle === '2D'"
          style="z-index: 0;"
          ref="globalMapRef"
          @pathVisibleChange="onPathVisibleChange"
          @zoom-change="onGisZoomChange"
        />
        <img v-if="angle !== '2D'" src="@/assets/images/new-bi/map-3d.png" width="100%" height="100%" style="z-index: 0;" />
      </template>
      <MapTool
        ref="mapToolRef"
        :isSlam="isSlam"
        :showAngle="!isSlam"
        :currentSlam="currentSlamMapId"
        :currentGisZoom="currentGisZoom"
        @changeMapAngle="changeMapAngle"
        :angle="angle"
        @changeMapZoom="changeMapZoom"
        @changeMapType="changeMapType"
        @changeSlamMap="changeSlamMap"
        @setCenter="setCenter"
        @togglePath="togglePath"
        @toggleTaskPaths="toggleTaskPaths"
        @toggleRanging="toggleRanging"
      />
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
import mapInfo from '../gis/globalMap/slam/mapInfo.json'
import pathInfo from '../gis/globalMap/slam/pathInfo.json'
import mapPoints from '../gis/globalMap/slam/map-points.json'
import GlobalGisMap from '../gis/globalMap/GlobalGisMap.vue'
import GlobalSlamMap from '../gis/globalMap/slam/GlobalSlamMap.vue'
import MapTool from './../patrol/panorama/map/MapTool.vue'
import PageChangeDropdown from './PageChangeDropdown.vue'

export default {
  name: 'BiIndex',
  components: {Header, BiIndexLeft, BiIndexRight, GlobalSlamMap, GlobalGisMap, MapTool, PageChangeDropdown},
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
      angle: '2D',
      autoSwitchedSlam: false,
      patrolSelectVisible: false,
      currentGisZoom: null
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot', 'defaultGpsDevices', 'globalMapId', 'overviewReady', 'overviewLoadError']),
    currentSlamMap() {
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.mapInfo || this.slamMapList.find(item => String(item.id) === String(this.currentSlamMapId)) || this.slamInfo.map
    },
    slamMapPayload() {
      const map = this.currentSlamMap || {}
      const points = map.points?.length ? map.points : (this.slamInfo.points || [])
      return { ...map, points }
    }
  },
  async mounted() {
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket', 'setSelectedRobotId']),
    ...mapActions('websocketExtraData', ['setShowRobotIds']),
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
    onPatrolSelectChange(visible) {
      this.patrolSelectVisible = visible
    },
    onMapBlankClick(e) {
      if (!this.patrolSelectVisible) return
      const selectEl = this.$refs.leftRef?.$el?.querySelector?.('.equipment-screen-select')
      if (selectEl && selectEl.contains(e.target)) return
      this.$refs.leftRef?.closeEquipmentSelect?.()
    },
    changeMapZoom(data) {
      this.$refs.globalMapRef?.changeMapZoom(data)
    },
    onGisZoomChange(zoom) {
      this.currentGisZoom = zoom
    },
    changeMapType(type) {
      this.isSlam = type ? type === 'slam' : !this.isSlam
      this.clearMapSelectionUI()
      if (!this.isSlam) {
        this.currentSlamMapId = null
      } else if (!this.currentSlamMapId) {
        this.selectDefaultSlamMap()
      }
    },
    changeSlamMap(mapInfo) {
      const nextId = mapInfo?.id ?? null
      const changed = String(this.currentSlamMapId) !== String(nextId)
      this.currentSlamMapId = nextId
      this.isSlam = true
      // 地图绘制复位由 GlobalSlamMap.previewSource 在同 tick 处理，这里只同步工具栏点位态
      if (changed) {
        this.clearMapSelectionUI()
        this.$refs.mapToolRef?.resetPathActive?.()
      }
    },
    clearMapSelectionUI() {
      this.$refs.globalMapRef?.clearRobotSelectionUI?.()
      // 地图组件可能已销毁/切换，仍清理全局选中与高亮
      this.setSelectedRobotId('')
      this.setShowRobotIds([])
    },
    selectDefaultSlamMap() {
      const list = Array.isArray(this.slamMapList) ? this.slamMapList : []
      if (!list.length) return
      // const preferred = list.find(item => String(item.id) === '1') || list[0]
      const preferred = list[0]
      this.currentSlamMapId = preferred?.id ?? null
      this.isSlam = true
    },
    togglePath(visible) {
      this.$refs.globalMapRef?.togglePath?.(visible)
    },
    toggleTaskPaths(visible) {
      this.$refs.globalMapRef?.toggleTaskPaths?.(visible)
    },
    toggleRanging(visible) {
      this.$refs.globalMapRef?.toggleRanging?.(visible)
    },
    onPathVisibleChange(visible) {
      const tool = this.$refs.mapToolRef
      if (!tool || tool.pathActive === !!visible) return
      tool.pathActive = !!visible
    },
    onSlamPreviewUnavailable(payload) {
      if (!this.isSlam) return
      this.$refs.mapToolRef?.showSlamEmptyTip?.(payload?.text)
    },
    setCenter() {
      const mapRef = this.$refs.globalMapRef
      if (this.isSlam) {
        // SLAM：恢复默认加载时的大小与位置
        if (typeof mapRef?.resetView === 'function') mapRef.resetView()
        else mapRef?.backCenter?.()
      } else {
        // GIS：定位到中心点 + 初始层级
        mapRef?.setCenter()
      }
    },
  },
  watch: {
    // overview 就绪后按 globalMapId 同步本地态，避免先渲染 GIS 再切 SLAM
    globalMapId: {
      immediate: true,
      handler(id) {
        if (!id || this.autoSwitchedSlam) return
        if (id === 'gis') {
          this.isSlam = false
          this.currentSlamMapId = null
        } else {
          this.currentSlamMapId = id
          this.isSlam = true
        }
        this.autoSwitchedSlam = true
      }
    },
    slamMapList: {
      immediate: true,
      handler(list) {
        if (!Array.isArray(list) || !list.length) return
        if (!this.autoSwitchedSlam) return
        if (this.isSlam && this.currentSlamMapId != null) {
          const stillExists = list.some(item => String(item.id) === String(this.currentSlamMapId))
          if (!stillExists) this.selectDefaultSlamMap()
        }
      }
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
