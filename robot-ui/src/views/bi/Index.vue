<template>
  <div class="bi">
    <router-view :key="$route.path" />
  </div>
</template>

<script>
import mqttClient from '@/plugins/mqtt-client'
import { mapActions } from 'vuex';
import { getPatrolPanoramaOverview } from '../../api/new-bi';
export default {
  name: 'Bi',
  data() {
    return {
      
    }
  },
  async mounted() {
    // mqttClient.connect()
    // await this.loadRobots();
    const res = await getPatrolPanoramaOverview()
    this.loadRobots(res?.devices || [])
    this.setDeviceObj(res?.devices[0] || {})
    this.setTaskList(res?.tasks || [])
    this.setAlarmList(res?.alarms || [])
    this.connectMediaWebSocket()
  },
  methods: {
    ...mapActions('websocketRobot', ['loadRobots', 'connectMediaWebSocket']),
    ...mapActions('websocketExtraData', ['setDeviceObj', 'setTaskList', 'setAlarmList']),
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