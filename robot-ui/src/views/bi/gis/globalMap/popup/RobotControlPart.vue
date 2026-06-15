
<template>
  <div class="machine-container robot-control-container" :class="{ visible }">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">监区巡逻机器狗-01</div>
          <div class="status success ml10">空闲中</div>
        </div>
        <div class="close mr10" @click="visible = false">
          <svg-icon icon-class="close"></svg-icon>
        </div>
      </div>
      <div class="info-content pt10 pb20 pl10 flex" style="align-items: flex-start">
        <div style="border: 1px solid #2AA6F6">
          <div class="d-flex hp202">
            <div class="wp360 h100 main">
              <VideoBox
                @toggleFullscreen="toggleFullscreen"
                :videoIndex="`${robot.robotId}_0`"
                :prefixId="prefixId"
                :ZQL_videosInfos="ZQL_videosInfos"
                className="six-1" />
            </div>
            <div v-if="robot?.cameras?.length > 1" class="ml10 p5 side-list common-scroll ovya">
              <div v-for="(camera, cameraIndex) in robot.cameras.slice(1)" class="wp160 hp90 main" :class="{ 'mt10': cameraIndex !== 0 }">
                <VideoBox
                  @toggleFullscreen="toggleFullscreen"
                  :videoIndex="`${robot.robotId}_${cameraIndex + 1}`"
                  :prefixId="prefixId"
                  :ZQL_videosInfos="ZQL_videosInfos"
                  className="six-1" />
              </div>
            </div>
          </div>
        </div>
        <div class="flex1 pl28 pr15" style="position: unset;">
          <div class="custom-tab-button flex">
            <div v-for="item in tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
          </div>
          <div class="mt24 flx-center">
            <ControlPart :tabIndex="tabIndex" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import ControlPart from './ControlPart.vue'
import VideoBox from '../../../components/modal/VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../../utils/fullscreen.js';
import { mapActions } from 'vuex';
export default {
  name: 'RobotControlPart',
  components: {
    ControlPart,
    VideoBox
  },
  mixins: [common],
  data() {
    return {
      visible: false,
      tabList: [
        {
          label: '基础控制',
          value: 0
        },
        {
          label: '高级控制',
          value: 1
        }
      ],
      tabIndex: 0,
      prefixId: 'robot-video-div',
      robot: {}
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot']
    },
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId']),
    show(visible) {
      this.visible = visible;
    }
  },
  watch: {
    selectedRobot: {
      handler(newVal, oldVal) {
        // console.log('aaa========', this.selectedRobotId, this.selectedRobot);
        if (!newVal || !this.selectedRobotId || !this.visible) return
        this.setPrefixId(this.prefixId)
        this.robot = Object.assign({}, newVal);
        this.updateInfo()
      },
      deep: true
    },
    visible: {
      async handler(newVal) {
        if (!newVal) {
          // console.log('close');
          await this.stopAll()
          this.started = false
          return
        }
        this.started = true
      },
      immediate: true
    }
  }
}
</script>

<style lang="scss" scoped>
.machine-container.robot-control-container {
  position: fixed;
  bottom: 30px;
  margin: auto;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: scale(0);
  &.visible {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: scale(1);
  }
  .box {
    // width: 944px;
    width: auto;
    background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 5.51%, rgba(4, 23, 62, 0.32) 51.52%, rgba(7, 45, 94, 0.31) 92.62%, rgba(4, 62, 151, 0.40) 100.03%);
    border-color: #2CADFF;
  }
}
.task-type {
  color: #00AC3A;
  font-family: "Alibaba PuHuiTi";
  font-size: 12px;
  line-height: 12px; /* 100% */
  letter-spacing: 0.857px;
  border-radius: 2px;
  border: 1px solid #00AC3A;
  background: rgba(17, 108, 31, 0.50);
}
</style>