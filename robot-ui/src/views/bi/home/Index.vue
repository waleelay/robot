<template>
  <div class="h100 pt80 common-scroll bi-index-div" :class="{ collapse }">
    <Header />
    <BiIndexLeft @changeCollapse="changeCollapse" :collapse="collapse" />
    <BiIndexRight @changeCollapse="changeCollapse" :collapse="collapse" />
    <div class="map-div flx-center" style="height: calc(100% + 55px); margin-top: -55px; align-items: start;" :class="{ full: collapse }">
    <!-- <div class="map-div h100 flx-center pt57" style="align-items: start;" :class="{ full: collapse }"> -->
      <!-- <div class="hp742 flx-center" style="width: 1118px; background: #071735;">
        <SlamMap :map="slamInfo.map" :points="slamInfo.points" :pathPointIds="slamInfo.pathPointIds" :show-labels="true" />
      </div> -->
      
      <GlobalMap style="z-index: 0;" ref="globalMapRef" />
      <MapTool @changeMapZoom="changeMapZoom" @changeMapType="changeMapType" @setCenter="setCenter " />
      <!-- <div class="map-footer"></div> -->
    </div>
    <el-select
      :value="$route.name"
      placeholder="看板中心"
      @change="changePage"
      class="wp130 tac page-select"
      title="看板中心"
      popper-class="custom-select page-select-popper"
    >
      <el-option v-for="item in pageList" :key="item.label" :label="item.label" :value="item.value" />
    </el-select>
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
import GlobalMap from './../gis/globalMap/Index.vue'
import MapTool from './../patrol/panorama/map/MapTool.vue'

export default {
  name: 'BiIndex',
  components: {Header, BiIndexLeft, BiIndexRight, SlamMap, GlobalMap, MapTool},
  data() {
    return {
      collapse: false,
      slamInfo: {
        map: mapInfo.data,
        points: mapPoints.data,
        pathPointIds: this.detailPointId(),
        showLabels: true
      },
      pageList: [
        {
          label: '指挥中心',
          value: 'biIndex'
        },
        {
          label: '巡逻巡查',
          value: 'biPatrol'
        },
        {
          label: '人员管理',
          value: 'biStaff'
        },
        {
          label: '车辆监管',
          value: 'biVehicle'
        }
      ]
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
    },
    changePage(pathName) {
      this.$router.push({ name: pathName })
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