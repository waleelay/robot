<!--
 * @Author: dengxumei
 * @Date: 2026-03-05 18:03:19
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-13 15:22:40
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\Index.vue
 * @Version: 
-->
<template>
  <div class="h100 pt94 pl72 common-scroll">
    <Header activeHead="patrol" />
    <Side ref="sideRef" :activeSide="activeSide" @updateSide="activeSide = $event" />
    <div class="w100 h100 common-scroll" style="overflow-y: auto;">
      <!-- 此处用路由还是组件？ -->
      <!-- <router-view :key="$route.path + activeSide" /> -->
      <Panorama v-if="activeSide === 'panorama'" @changeSide = "changeSide('business')" />
      <Monitor v-if="activeSide === 'monitor'" />
      <Business v-if="activeSide === 'business'" />
      <App v-if="activeSide === 'app'" />
    </div>
  </div>
</template>

<script>
import Header from '../components/Header.vue';
import Side from '../components/Side.vue';
import Panorama from './panorama/Index.vue';
import Monitor from './monitor/Index.vue';
import Business from './business/Index.vue';
import App from './App.vue';
  export default {
    name: 'BiPatrol',
    components: { Header, Side, Panorama, Monitor, Business, App },
    data() {
      return {
        activeSide: 'panorama'
      }
    },
    computed: {
    },
    methods: {
      changeSide(activeSide) {
        this.activeSide = activeSide;
        this.$refs.sideRef.activeIndex = activeSide
      }
    },
    mounted() {
    },
    beforeDestroy() {
    }
  }
</script>