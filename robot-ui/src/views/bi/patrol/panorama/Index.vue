<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-02 15:16:05
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\panorama\Index.vue
 * @Version: 
-->
<template>
  <div class="panorama-div h100" :class="{ collapse }">
    <Left @changeSide="$emit('changeSide')" @changeCollapse="changeCollapse" :collapse="collapse" />
    <!-- <Right @changeCollapse="changeCollapse" :collapse="collapse" /> -->
    <BiPatrolPanoramaMap :collapse="collapse" />
  </div>
</template>

<script>
import BiPatrolPanoramaMap from './map/Index.vue';
// import GlobalMap from './../../gis/globalMap/Index.vue'
import Left from './Left.vue';
// import Right from './Right.vue';
export default {
  name: 'BiPatrolPanorama',
  components: {
    BiPatrolPanoramaMap,
    Left,
    // Right,
    // GlobalMap
  },
  data() {
    return {
      collapse: false,
    }
  },
  methods: {
    changeCollapse() {
      this.collapse = !this.collapse
    }
  }
}
</script>

<style lang="scss">
@import './index.scss';
</style>