<template>
  <!-- <ScaleScreen> -->
    <div class="bi">
      <router-view :key="$route.path" />
    </div>
  <!-- </ScaleScreen> -->
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions } from 'vuex';
import { getPatrolPanoramaOverview } from '../../api/new-bi';
import ScaleScreen from './../../components/largeScreen/scale-screen.vue'
export default {
  name: 'Bi',
  components: {
    ScaleScreen
  },
  data() {
    return {

    }
  },
  async mounted() {
    const res = await getPatrolPanoramaOverview()
    this.setAll(res)
    this.connectMediaWebSocket()
  },
  methods: {
    ...mapActions('websocketRobot', ['connectMediaWebSocket']),
    ...mapActions('websocketExtraData', ['setAll']),
  },
  beforeDestroy() {
    // mqttClient.disconnect()
  },
}
</script>

<style lang="scss" scoped>
  .bi {
    width: 100vw;
    height: 100vh;
    background: #021328;
  }
</style>
