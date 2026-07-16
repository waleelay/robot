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
    <div v-if="isSlam" class="w100 h100 flx-center" style="z-index: 0; background: #112B4D;">
      <SlamMap
        :map="currentSlamMap"
        :points="slamPoints"
        :pathPointIds="slamPathPointIds"
        ref="globalMapRef"
        :show-labels="true"
        @changeMapType="changeMapType"
      />
    </div>
    <template v-else>
      <GlobalMap v-if="angle === '2D'" style="z-index: 0;" ref="globalMapRef" />
      <img v-else src="@/assets/images/new-bi/map-3d.png" width="100%" height="100%" style="z-index: 0;" />
    </template>
    <MapTool
      :isSlam="isSlam"
      :showAngle="!isSlam"
      :currentSlam="currentSlamMapId"
      :angle="angle"
      @changeMapZoom="changeMapZoom"
      @changeMapType="changeMapType"
      @changeSlamMap="changeSlamMap"
      @setCenter="setCenter"
      @changeMapAngle="changeMapAngle"
    />
  </div>
</template>

<script>
import { mapState } from 'vuex'
import MapTool from './MapTool.vue'
import GlobalMap from './../../../gis/globalMap/Index.vue'
import SlamMap from './../../../home/slam/Index.vue'

export default {
  name: 'BiPatrolPanoramaMap',
  props: {
    collapse: {
      type: Boolean,
      default: false
    }
  },
  components: {
    GlobalMap,
    MapTool,
    SlamMap
  },
  data() {
    return {
      count: 0,
      intervalId: null,
      angle: '2D',
      isSlam: false,
      currentSlamMapId: null
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
      return group?.points || []
    },
    slamPathPointIds() {
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      return group?.pathPointIds || []
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
    getStyle() {
      return {
        width: '38px',
        height: '28px',
        top: '200px',
        left: 500 + this.count + 'px'
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
