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
import ptzMixin from '../patrol/monitor/second/components/ptz-control-mixin.js'
import { mapActions } from 'vuex';
export default {
  name: "ControlInner",
  mixins: [ptzMixin],
  props: {
    cameraInfo: {
      type: Object,
      default: () => {},
    },
    // one-split: mount on .item so panel can vertical-center without stretching .bottom
    verticalCenter: {
      type: Boolean,
      default: false,
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
    this.ensureCameraControlProfile()
  },
  watch: {
    visible(val) {
      if (val) this.ensureCameraControlProfile()
      this.$emit('visible-change', !!val)
      if (val && this.verticalCenter) {
        this.$nextTick(() => this.mountToVideoItem())
      }
    },
    'cameraInfo.robotId'(robotId) {
      if (robotId) this.ensureCameraControlProfile()
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['loadControlProfile']),
    // 一级监控未进入控制中心时，mixin 仍能从 robotBaseInfo 拿到 selectedRobot，
    // 不能据此跳过控制画像；否则本体/云台控制没有 devices，会报「未找到控制设备」。
    ensureCameraControlProfile() {
      const robotId = this.cameraInfo?.robotId || this.singleSelectedRobotId
      if (!robotId) return
      if (this.controlProfiles[robotId]) return
      this.loadControlProfile(robotId)
    },
    // mount on video cell so absolute positioning uses .item as containing block
    mountToVideoItem() {
      const el = this.$el
      if (!el || !el.classList || !el.closest) return
      const item = el.closest('.item')
      if (item && el.parentElement !== item) {
        item.appendChild(el)
      }
    }
  },
};
</script>
<style lang="scss" scoped>
/* dock above toolbar; one-split vertical center via bi-new .item.one > .inner-video-control */
.inner-video-control {
  position: absolute;
  top: auto;
  bottom: calc(100% + 8px);
  right: 30px;
  left: unset;
  z-index: 5;
  transform-origin: bottom right;

  ::v-deep .outer {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  /* 仅视频框内控制器中心字号 */
  .circle {
    font-size: 16px !important;
  }
}
</style>
