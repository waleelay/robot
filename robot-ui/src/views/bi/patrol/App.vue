<!--
 * @Author: error: error: git config user.name & please set dead value or install git && error: git config user.email & please set dead value or install git & please set dead value or install git
 * @Date: 2026-06-08 13:57:39
 * @LastEditors: error: error: git config user.name & please set dead value or install git && error: git config user.email & please set dead value or install git & please set dead value or install git
 * @LastEditTime: 2026-06-08 14:48:40
 * @FilePath: \robot-ui-new\src\views\bi\patrol\App.vue
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
-->
<template>
  <div class="d-flex h100 pr20 pl10 pb10 pt20">
    <div class="wp800">
      <!-- <Gis style="height: 460px !important; border-radius: 4px 4px 0 0;" /> -->
       <!-- <GisSimulateMove /> -->
    </div>
    <Slam :map="slamInfo.map" :points="slamInfo.points" :pathPointIds="slamInfo.pathPointIds" :show-labels="true" />
  </div>
</template>

<script>
// import Gis from '../components/Gis.vue'
import GisSimulateMove from '../gis/gis模拟移动/Index.vue'
import Slam from './slam/Index.vue'
import mapInfo from './slam/mapInfo.json'
import pathInfo from './slam/pathInfo.json'
import mapPoints from './slam/map-points.json'
export default {
  name: 'BiPatrolApp',
  components: {GisSimulateMove, Slam},
  data() {
    return {
      slamInfo: {
        map: mapInfo.data,
        points: mapPoints.data,
        pathPointIds: this.detailPointId(),
        showLabels: true
      }
    }
  },
  methods: {
    detailPointId() {
      const record = pathInfo.data
      return [...(record?.points || [])].sort((a, b) => a.pointOrder - b.pointOrder).map((item) => item.mapPointId)
    }
  }
}
</script>