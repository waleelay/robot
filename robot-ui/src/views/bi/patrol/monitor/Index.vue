<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-16 18:06:25
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\Index.vue
 * @Version: 
-->
<template>
  <FirstScreen v-if="isFirst" :prefixId="prefixId + '-first'"></FirstScreen>
  <SecondScreen v-else :prefixId="prefixId + '-second'"></SecondScreen>
</template>

<script>
import { mapActions } from 'vuex/dist/vuex.common.js';
import FirstScreen from './first/Index.vue'
import SecondScreen from './second/Index.vue'
export default {
  name: 'BiPatrolMonitor',
  components: {FirstScreen, SecondScreen},
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    }
  },
  data() {
    return {
      isFirst: true
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
  },
  watch: {
    selectedRobotId: {
      handler(newVal, oldVal) {
        this.isFirst = !Boolean(newVal)
      },
      immediate: true
    },
  }
}
</script>