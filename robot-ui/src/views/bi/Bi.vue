<template>
  <ScaleScreen>
    <div class="bi">
      <router-view :key="$route.path" />
    </div>
    <div v-if="authorizationUnavailable" class="realtime-connection-status">
      实时数据连接暂不可用，正在自动恢复
    </div>
    <WarningPending />
    <WarnInfo />
    <IncomingIntercomCall />
  </ScaleScreen>
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions } from 'vuex';
import { Message } from 'element-ui'
import ScaleScreen from './../../components/largeScreen/scale-screen.vue'
import WarningPending from './patrol/panorama/warning/WarnPending.vue';
import WarnInfo from './patrol/panorama/warning/WarnInfo.vue';
import IncomingIntercomCall from './components/IncomingIntercomCall.vue';
export default {
  name: 'Bi',
  components: {
    ScaleScreen,
    WarningPending,
    WarnInfo,
    IncomingIntercomCall
  },
  data() {
    return {

    }
  },
  computed: {
    authorizationUnavailable() {
      return this.$store.getters['websocketRobot/getAuthorizationUnavailable']
    },
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
  },
  async mounted() {
    await this.clearCameras()
    try {
      await this.refreshOverviewResources({ failClosed: false })
    } catch (error) {
      this.markOverviewLoadFailed()
      Message.error('大屏数据暂不可用，请稍后刷新页面重试')
    } finally {
      this.connectMediaWebSocket()
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket', 'stopCamera']),
    ...mapActions('websocketExtraData', ['refreshOverviewResources', 'markOverviewLoadFailed']),
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

  .realtime-connection-status {
    position: fixed;
    top: 86px;
    left: 50%;
    z-index: 101;
    transform: translateX(-50%);
    padding: 6px 16px;
    border: 1px solid rgba(255, 190, 90, 0.55);
    border-radius: 3px;
    color: #ffd591;
    background: rgba(35, 28, 16, 0.9);
  }
</style>
