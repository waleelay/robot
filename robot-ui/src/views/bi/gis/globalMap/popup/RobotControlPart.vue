
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
        <div class="flx-center">
          <div class="setting flx-center curp" @click="goControl">
            <svg-icon icon-class="setting"></svg-icon>
            <span class="ml4">深度控制</span>
          </div>
          <div class="close mr10 ml10" @click="visible = false">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
      </div>
      <div class="info-content pt10 pb10 pl10 flex" style="align-items: flex-start">
        <div style="border: 1px solid #2AA6F6">
          <div class="d-flex hp222 p10">
            <div class="wp360 h100 main">
              <!-- <span style="color: #fff">{{ robot?.cameras?.[0]?.name }}</span> -->
              <VideoBox
                @toggleFullscreen="toggleFullscreen"
                :videoIndex="`${robot.robotId}_0`"
                :prefixId="prefixId"
                :ZQL_videosInfos="ZQL_videosInfos"
                className="six-1" />
            </div>
            <div v-if="robot?.cameras?.length > 1" class="ml10 p5 side-list common-scroll ovya">
              <div v-for="(camera, cameraIndex) in robot.cameras.slice(1)" :key="cameraIdentity(robot.robotId, camera)" class="wp160 hp90 main curp" :class="{ 'mt10': cameraIndex !== 0 }">
                <!-- <span style="color: #fff">{{ camera.name }}</span> -->
                <VideoBox
                  @toggleFullscreen="toggleFullscreen"
                  @select="swapWithMain(cameraIndex + 1)"
                  :videoIndex="`${robot.robotId}_${cameraIndex + 1}`"
                  :prefixId="prefixId"
                  :ZQL_videosInfos="ZQL_videosInfos"
                  className="six-1" />
              </div>
            </div>
          </div>
        </div>
        <div class="flex1 pl28 pr15" style="position: unset;">
          <div class="d-flex hp29">
            <div class="custom-tab-button flex" style="height: fit-content">
              <div v-for="item in tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
            </div>
            <div class="ml30 mode" :class="{'flex-column': !showTalk, 'flx-align-center': showTalk }">
              <span>控制模式：</span>
              <el-dropdown :class="{ 'mt10': !showTalk, 'ml10': showTalk }" trigger="click">
                <div class="mode-status success flex-column">
                  <span>{{ selectedRobot?.mode || 'MANUAL'  }}模式<svg-icon icon-class="d-down" class="ml4"></svg-icon></span>
                </div>
                <el-dropdown-menu slot="dropdown" class="wp100 mt2 custom-dropdown-menu mode-dropdown-menu p4">
                  <el-dropdown-item>自动巡航模式</el-dropdown-item>
                  <el-dropdown-item>手动控制模式</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </div>
          </div>
          <div class="mt24 d-flex">
            <Talk v-if="showTalk" :isMapInner="showTalk" />
            <ControlPart :tabIndex="tabIndex" :showSmall="showTalk" :class="{'ml68': showTalk }" />
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
import Talk from '../../../patrol/monitor/second/components/Talk.vue'
import yuntai from '../../../patrol/monitor/second/components/yuntai.js'
export default {
  name: 'RobotControlPart',
  components: {
    ControlPart,
    VideoBox,
    Talk
  },
  mixins: [common, yuntai],
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
      robot: {},
      cameraOrderByRobot: {},
      selectModelValue: this.selectedRobot?.controlMode || 0,
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot']
    },
    cameras() {
      return this.$store.getters['websocketRobot/getCameras']
    },
    camerasRevision() {
      return this.$store.getters['websocketRobot/getCamerasRevision']
    },
    cameraKeys() {
      if (!this.selectedRobot?.robotId) return []
      return (this.selectedRobot.cameras || []).map(camera => this.cameraIdentity(this.selectedRobot.robotId, camera))
    },
    cameraStateSignature() {
      return [this.camerasRevision]
        .concat(this.cameraKeys.map(key => `${key}:${this.cameras[key]?._revision || 0}`))
        .join('|')
    },
    showTalk() {
      return Boolean(this.audioDevice)
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId', 'setSelectedRobotId']),
    show(visible) {
      this.visible = visible;
    },
    goControl() {
      this.setSelectedRobotId(this.selectedRobotId)
      this.$router.push({ path: '/bi/patrol/monitor' })
    },
    cameraIdentity(robotId, camera) {
      return camera.key || `${robotId}-${camera.deviceId || camera.cameraId}`
    },
    orderedCameras(robot) {
      const cameras = (robot.cameras || [])
        .map(camera => this.cameras[this.cameraIdentity(robot.robotId, camera)] || camera)
      const availableKeys = cameras.map(camera => this.cameraIdentity(robot.robotId, camera))
      const previousOrder = this.cameraOrderByRobot[robot.robotId] || []
      const order = previousOrder
        .filter(key => availableKeys.includes(key))
        .concat(availableKeys.filter(key => !previousOrder.includes(key)))
      this.$set(this.cameraOrderByRobot, robot.robotId, order)
      return order.map(key => this.cameras[key] || cameras.find(camera => this.cameraIdentity(robot.robotId, camera) === key))
    },
    async syncRobot() {
      if (!this.selectedRobot?.robotId || !this.visible) return
      this.setPrefixId(this.prefixId)
      this.robot = { ...this.selectedRobot, cameras: this.orderedCameras(this.selectedRobot) }
      await this.updateInfo()
    },
    async swapWithMain(cameraIndex) {
      if (cameraIndex <= 0 || cameraIndex >= (this.robot.cameras || []).length) return
      const cameras = [...this.robot.cameras]
      const mainCamera = cameras[0]
      cameras[0] = cameras[cameraIndex]
      cameras[cameraIndex] = mainCamera
      this.robot = { ...this.robot, cameras }
      this.$set(this.cameraOrderByRobot, this.robot.robotId, cameras.map(camera => this.cameraIdentity(this.robot.robotId, camera)))
      await this.updateInfo()
      this.rebindCameraTracks([cameras[0], cameras[cameraIndex]])
    }
  },
  watch: {
    // 切换机器人控制模式
    handleChangeMode(e) {},
    cameraStateSignature: {
      async handler() {
        await this.syncRobot()
      },
      deep: false,
      immediate: true
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
        await this.syncRobot()
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
.mode {
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 16px;
  .mode-status {
    cursor: default;
    span {
      padding: 3px 4px;
      color: #00AC3A;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      line-height: 12px; /* 100% */
      letter-spacing: 0.857px;
      border-radius: 2px;
      border: 1px solid var(---, #00AC3A);
      background: rgba(17, 108, 31, 0.50);
    }
  }
}
.setting {
  padding: 4px 6px;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 10px;
  line-height: 13px;
  border-radius: 2px;
  background: rgba(38, 84, 152, 0.50);
}
</style>
