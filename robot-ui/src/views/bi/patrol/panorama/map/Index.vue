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
    <GlobalMap @getDogList="getDogList"  @changeWebrtcServer="changeWebrtcServer" style="z-index: 0;" ref="globalMapRef" />
    <MapTool @changeMapZoom="changeMapZoom" @changeMapType="changeMapType" @setCenter="setCenter " />
    <!-- <img src="../../../../../assets/images/new-bi/robot_dog.png" alt="地图" :style="getStyle()" class="image1" ref="mapImage"> -->
  </div>
</template>

<script>
import MapTool from './MapTool.vue';
import GlobalMap from './../../../gis/globalMap/Index.vue'
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
    MapTool
  },
  data() {
    return {
      count: 0,
      intervalId: null
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
    getDogList(data) {
      this.$emit('getDogList', { dogList: data.dogList })
    },
    changeWebrtcServer(type) {
      this.$emit('changeWebrtcServer', type)
    },
    changeMapZoom(data) {
      this.$refs.globalMapRef.map[data.method](data.value || 1)
    },
    changeMapType() {
      this.$refs.globalMapRef.changeMapType()
    },
    setCenter() {
      this.$refs.globalMapRef.setCenter()
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
