<template>
  <ScaleScreen>
    <div class="bi">
      <router-view :key="$route.path" />
    </div>
    <WarningPending />
  </ScaleScreen>
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions } from 'vuex';
import { getPatrolPanoramaOverview } from '../../api/new-bi';
import ScaleScreen from './../../components/largeScreen/scale-screen.vue'
import WarningPending from './patrol/panorama/warning/WarnPending1.vue';
export default {
  name: 'Bi',
  components: {
    ScaleScreen,
    WarningPending
  },
  data() {
    return {

    }
  },
  computed: {
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
  },
  async mounted() {
    await this.clearCameras()
    const res = await getPatrolPanoramaOverview()
    this.setAll(res)
    this.connectMediaWebSocket()
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket', 'stopCamera']),
    ...mapActions('websocketExtraData', ['setAll']),
    async clearCameras() {
      for (const [index, key] of Object.keys(this.activeCameras).entries()) {
        if (this.activeCameras[key]?.camera) {
          await this.stopCamera(this.activeCameras[key].camera);
        }
      }
    },
  },
  beforeDestroy() {
    // console.log('11111111111111111111111111111111');
    
    // mqttClient.disconnect()
  },
  // ✅ 组件内守卫，离开当前组件时触发
  async beforeRouteLeave(to, from, next) {
    // console.log('🚪 准备离开当前页面');
    // console.log('从：', from.path);
    // console.log('到：', to.path);
    await this.clearCameras()
    next(); // 或允许离开
  },
  watch: {
    '$route.name': {
      async handler(newVal) {
        await this.clearCameras()
      },
      deep: false
    }
  }
}
</script>

<style lang="scss" scoped>
  .bi {
    width: 100vw;
    height: 100vh;
    width: 1920px;
    height: 1080px;
    background: #021328;
    transform-origin: 0 0;
  }
</style>
