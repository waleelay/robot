<template>
  <div class="robot-control inner-video-control" v-if="visible">
    <div style="text-align: right;">
      <span title="关闭" @click="visible = false" style="cursor: pointer;">
        <svg-icon icon-class="close-fill" style="font-size: 24px; color: #3877F2;" />
      </span>
    </div>
    <div class="outer" style="margin-top: -4px;">
      <div class="inner flx-center">
        <div class="circle flx-center">{{ cameraInfo.groupType === 'body' ? '本体' : '云台' }}</div>
      </div>
      <div
        v-for="key in ['advance', 'back', 'turn-left' , 'turn-right']"
        :key="key"
        :class="['arrow', robotControlObj[key].class]"
        :title="robotControlObj[key].label"
          @mousedown="startFrameControl(robotControlObj[key].key)"
          @mouseup="stopFrameControl(robotControlObj[key].key)"
          @mouseleave="stopFrameControl(robotControlObj[key].key)"
          @touchstart.prevent="startFrameControl(robotControlObj[key].key)"
          @touchend.prevent="stopFrameControl(robotControlObj[key].key)"
      >
        <svg-icon icon-class="control-arrow" />
      </div>
    </div>
    <ControlModeWarning ref="controlModeWarningRef" />
  </div>
</template>

<script>
import { robotControlObj } from '../js/constants/robot-control.js';
import ptzMixin from '../patrol/monitor/second/components/yuntai.js'
import { mapActions } from 'vuex';
export default {
  name: "ControlInner",
  mixins: [ptzMixin],
  props: {
    cameraInfo: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      visible: false,
      robotControlObj: this.cameraInfo.groupType === 'body' ? robotControlObj : {
        advance: { label: '上', class: 'up', key: 'ptz-up' },
        back: { label: '下', class: 'down', key: 'ptz-down' },
        'turn-left': { label: '左转', class: 'left', key: 'ptz-left' },
        'turn-right': { label: '右转', class: 'right', key: 'ptz-right' },
      },
      singleSelectedRobotId: this?.cameraInfo?.robotId
    };
  },
  created() {
    if (!this.selectedRobot?.robotId) {
      this.loadControlProfile(this.cameraInfo.robotId)
    }
  },
  // beforeDestroy() {
  // },
  methods: {
    ...mapActions('websocketRobot', ['loadControlProfile']),
  },
};
</script>
<style lang="scss" scoped>
.inner-video-control {
  position: absolute;
  top: -180px;
  right: 30px;
  left: unset;
}
</style>
