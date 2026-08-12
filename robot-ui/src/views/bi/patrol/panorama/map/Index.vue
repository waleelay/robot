<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-14 11:22:21
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\panorama\map\Index.vue
 * @Version: 
-->
<template>
  <div class="map-div h100" :class="{ full: collapse }">
    <transition name="slam-map-loading-fade">
      <div
        v-if="!overviewReady"
        class="slam-map-loading flx-center flex-column"
        @wheel.prevent
        @mousedown.stop
      >
        <div class="slam-map-loading__spinner" aria-hidden="true"></div>
        <p class="slam-map-loading__text">正在获取地图数据</p>
      </div>
    </transition>
    <div v-if="globalMapId && globalMapId !== 'gis'" class="slam-map-host w100 h100" style="z-index: 0;">
      <GlobalSlamMap
        :map="slamMapPayload"
        :pathPointIds="slamPathPointIds"
        :collapse="collapse"
        visible-layout="panorama"
        ref="globalMapRef"
        :show-labels="true"
        @changeMapType="changeMapType"
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
      :angle="angle"
      @changeMapZoom="changeMapZoom"
      @changeMapType="changeMapType"
      @changeSlamMap="changeSlamMap"
      @setCenter="setCenter"
      @changeMapAngle="changeMapAngle"
      @togglePath="togglePath"
      @toggleTaskPaths="toggleTaskPaths"
      @toggleRanging="toggleRanging"
    />
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex'
import MapTool from './MapTool.vue'
import GlobalGisMap from '../../../gis/globalMap/GlobalGisMap.vue'
import GlobalSlamMap from '../../../gis/globalMap/slam/GlobalSlamMap.vue'

export default {
  name: 'BiPatrolPanoramaMap',
  props: {
    collapse: {
      type: Boolean,
      default: false
    }
  },
  components: {
    GlobalGisMap,
    MapTool,
    GlobalSlamMap
  },
  data() {
    return {
      count: 0,
      intervalId: null,
      angle: '2D',
      isSlam: false,
      currentSlamMapId: null,
      autoSwitchedSlam: false,
      currentGisZoom: null
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot', 'defaultGpsDevices', 'globalMapId', 'overviewReady']),
    currentSlamMap() {
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.mapInfo || this.slamMapList.find(item => String(item.id) === String(this.currentSlamMapId)) || null
    },
    slamPoints() {
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.points || this.currentSlamMap?.points || []
    },
    slamPathPointIds() {
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.pathPointIds || []
    },
    slamMapPayload() {
      if (!this.currentSlamMap) return null
      return {
        ...this.currentSlamMap,
        points: this.slamPoints.length ? this.slamPoints : (this.currentSlamMap.points || [])
      }
    }
  },
  mounted() {
    // this.intervalId = setInterval(() => {
    //   this.count++
    // }, 1000)
  },
  beforeDestroy() {
    clearInterval(this.intervalId)
  },
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    ...mapActions('websocketExtraData', ['setShowRobotIds']),
    changeMapAngle() {
      this.angle = this.angle === '3D' ? '2D' : '3D'
    },
    getDogList(data) {
      this.$emit('getDogList', { dogList: data.dogList })
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
    // Robot1 / 地图内部关闭路径时，同步 MapTool 激活态
    onPathVisibleChange(visible) {
      const tool = this.$refs.mapToolRef
      if (!tool || tool.pathActive === !!visible) return
      tool.pathActive = !!visible
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
    getStyle() {
      return {
        width: '38px',
        height: '28px',
        top: '200px',
        left: 500 + this.count + 'px'
      }
    }
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
}
</script>

<style lang="scss">
@import './index.scss';
.image1 {
  position: absolute;
}
</style>
