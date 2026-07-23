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
    <div v-if="isSlam" class="slam-map-host w100 h100" style="z-index: 0;">
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
    <template v-else>
      <GlobalGisMap v-if="angle === '2D'" style="z-index: 0;" ref="globalMapRef" />
      <img v-else src="@/assets/images/new-bi/map-3d.png" width="100%" height="100%" style="z-index: 0;" />
    </template>
    <MapTool
      ref="mapToolRef"
      :isSlam="isSlam"
      :showAngle="!isSlam"
      :currentSlam="currentSlamMapId"
      :angle="angle"
      @changeMapZoom="changeMapZoom"
      @changeMapType="changeMapType"
      @changeSlamMap="changeSlamMap"
      @setCenter="setCenter"
      @changeMapAngle="changeMapAngle"
      @togglePath="togglePath"
    />
  </div>
</template>

<script>
import { mapState } from 'vuex'
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
      autoSwitchedSlam: false
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot']),
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
    changeMapAngle() {
      this.angle = this.angle === '3D' ? '2D' : '3D'
    },
    getDogList(data) {
      this.$emit('getDogList', { dogList: data.dogList })
    },
    changeMapZoom(data) {
      if (this.isSlam) {
        this.$refs.globalMapRef?.changeMapZoom(data)
      } else {
        this.$refs.globalMapRef?.map?.[data.method]?.(data.value || 1)
      }
    },
    changeMapType(type) {
      this.isSlam = type ? type === 'slam' : !this.isSlam
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
      if (changed) {
        this.$nextTick(() => {
          this.$refs.globalMapRef?.resetSlamDrawState?.()
          this.$refs.mapToolRef?.resetPathActive?.()
        })
      }
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
    setCenter() {
      const mapRef = this.$refs.globalMapRef
      if (this.isSlam) {
        mapRef?.backCenter()
      } else {
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
    slamMapList: {
      immediate: true,
      handler(list) {
        if (!Array.isArray(list) || !list.length) return
        // slamList 有数据时首次自动切到 SLAM，并优先选择 id=1
        if (!this.autoSwitchedSlam) {
          this.selectDefaultSlamMap()
          this.autoSwitchedSlam = true
          return
        }
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
