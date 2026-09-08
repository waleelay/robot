<template>
  <div class="machine-container robot-control-container" :class="{ visible }">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">{{ baseInfo?.name || '-' }}</div>
          <div class="status ml10" :class="baseInfo?.statusClass || ''">{{ baseInfo?.customStatusName || baseInfo?.status || '-' }}</div>
        </div>
        <div class="flx-center">
          <div class="setting flx-center curp" @click="goControl">
            <svg-icon icon-class="setting"></svg-icon>
            <span class="ml4">控制中心</span>
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
          <div class="d-flex flx-align-center">
            <div v-if="tabList.length > 1" class="custom-tab-button flex" style="height: fit-content">
              <div v-for="item in tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
            </div>
            <ControlModeActions
              :is-nav-mode="isNavMode"
              :show-resume="showTaskResumeActions"
              @takeover="handleTakeover"
              @resume="handleResumeActiveTask"
              @terminate="handleTerminateActiveTask"
              class="flex1"
            />
            <div v-if="vehicleLightDevice && showTalk" class="lights ml30 flx-align-center">
              <span>车灯：</span>
              <el-switch
                :value="vehicleLightEnabled"
                :disabled="!hasDeviceAction(vehicleLightDevice, 'light.vehicle.set')"
                active-text="开启"
                inactive-text="关闭"
                active-color="#3DB56A"
                inactive-color="#5E5E5E"
                @change="setVehicleLights">
              </el-switch>
            </div>
          </div>
          <div class="mt24 d-flex">
            <Talk v-if="showTalk" :isMapInner="showTalk" />
            <ControlPart :tabIndex="tabIndex" :showSmall="showTalk" :class="{'ml68': showTalk }" @handleModeChange="handleModeChange" />
          </div>
        </div>
      </div>
    </div>
    <ControlModeWarning ref="controlModeWarningRef" />
  </div>
</template>

<script>
import ControlPart from './ControlPart.vue'
import VideoBox from '../../../components/modal/VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../../utils/fullscreen.js';
import { mapActions, mapState } from 'vuex';
import Talk from '../../../patrol/monitor/second/components/Talk.vue'
import yuntai from '../../../patrol/monitor/second/components/ptz-control-mixin.js'
import { setControlMode } from '../../../../../api/media.js';
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
        // {
        //   label: '高级控制',
        //   value: 1
        // }
      ],
      tabIndex: 0,
      prefixId: 'robot-video-div',
      robot: {},
      cameraOrderByRobot: {},
      selectModelValue: this.selectedRobot?.controlMode || 0,
      /** 遥控打开时绑定的装备；关 Robot1 清选中后仍可继续播控 */
      boundRobotId: ''
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    ...mapState('websocketRobot', ['robots']),
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId'] || this.boundRobotId || ''
    },
    /** 面板打开期间以绑定装备为准，避免地图改选未走 rebind 就换源 */
    effectiveRobotId() {
      if (this.visible && this.boundRobotId) return this.boundRobotId
      return this.selectedRobotId
    },
    baseInfo() {
      return this.robotBaseInfo?.[this.effectiveRobotId] || {}
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot']
    },
    controlRobot() {
      const id = this.effectiveRobotId
      if (!id) return {}
      const live = (this.robots || []).find(item => String(item.robotId) === String(id))
        || (this.selectedRobot?.robotId && String(this.selectedRobot.robotId) === String(id) ? this.selectedRobot : null)
      const base = this.robotBaseInfo?.[id] || this.robotBaseInfo?.[String(id)] || {}
      if (!live && !base.robotId) {
        return { ...base, robotId: id }
      }
      return { ...base, ...live, robotId: live?.robotId || base.robotId || id }
    },
    cameras() {
      return this.$store.getters['websocketRobot/getCameras']
    },
    camerasRevision() {
      return this.$store.getters['websocketRobot/getCamerasRevision']
    },
    cameraKeys() {
      if (!this.controlRobot?.robotId) return []
      return (this.controlRobot.cameras || []).map(camera => this.cameraIdentity(this.controlRobot.robotId, camera))
    },
    cameraStateSignature() {
      return [this.camerasRevision, this.effectiveRobotId]
        .concat(this.cameraKeys.map(key => `${key}:${this.cameras[key]?._revision || 0}`))
        .join('|')
    },
    showTalk() {
      return Boolean(this.audioDevice)
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId', 'setSelectedRobotId', 'setControlCenterReturnTo']),
    async show(visible) {
      if (!visible) {
        if (!this.visible) return
        // 先停流再关面板，避免 visible watcher 丢旧 camera 引用
        await this.stopAll()
        this.started = false
        this.boundRobotId = ''
        this.visible = false
        return
      }
      const nextId = this.$store.getters['websocketRobot/getSelectedRobotId'] || this.boundRobotId
      // 已打开时点另一装备遥控：先停旧流再绑新装备
      if (this.visible && nextId && String(nextId) !== String(this.boundRobotId)) {
        await this.rebindToRobot(nextId)
        return
      }
      this.boundRobotId = nextId || this.boundRobotId
      this.visible = true
    },
    /** 遥控面板保持打开时切换装备：先停当前流再起新装备流 */
    async rebindToRobot(nextId) {
      if (!nextId) return
      if (String(nextId) === String(this.boundRobotId) && this.visible) return
      await this.stopAll()
      this.boundRobotId = nextId
      this.started = true
      if (!this.visible) this.visible = true
      await this.syncRobot()
    },
    async goControl() {
      await this.stopAll()
      this.setControlCenterReturnTo(this.$route.fullPath)
      this.setSelectedRobotId(this.effectiveRobotId)
      this.$router.push({ path: '/bi/patrol/monitor' })
    },
    cameraIdentity(robotId, camera) {
      return camera.key || `${robotId}-${camera.deviceId || camera.cameraId}`
    },
    orderedCameras(robot) {
      const cameras = (robot.cameras || [])
        .map(camera => this.cameras[this.cameraIdentity(robot.robotId, camera)] || camera)
        .sort((a, b) => a.groupType === 'body' ? -1 : b.groupType === 'body' ? 1 : 0)
      const availableKeys = cameras.map(camera => this.cameraIdentity(robot.robotId, camera))
      const previousOrder = this.cameraOrderByRobot[robot.robotId] || []
      const order = previousOrder
        .filter(key => availableKeys.includes(key))
        .concat(availableKeys.filter(key => !previousOrder.includes(key)))
      this.$set(this.cameraOrderByRobot, robot.robotId, order)
      return order.map(key => this.cameras[key] || cameras.find(camera => this.cameraIdentity(robot.robotId, camera) === key))
    },
    async syncRobot() {
      if (!this.controlRobot?.robotId || !this.visible) return
      this.setPrefixId(this.prefixId)
      this.robot = { ...this.controlRobot, cameras: this.orderedCameras(this.controlRobot) }
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
    // 关 Robot1 清选中时保留 boundRobotId；换装备起流由 show/rebindToRobot 显式处理
    cameraStateSignature: {
      async handler() {
        await this.syncRobot()
      },
      deep: false,
      immediate: true
    },
    visible: {
      async handler(newVal, oldVal) {
        if (!newVal) {
          // show(false) 已停流；点 X 直接改 visible 时若仍在播则兜底
          if (oldVal && this.started) {
            await this.stopAll()
            this.started = false
          }
          this.boundRobotId = ''
          return
        }
        this.started = true
        const storeId = this.$store.getters['websocketRobot/getSelectedRobotId']
        if (!this.boundRobotId && storeId) {
          this.boundRobotId = storeId
        }
        await this.syncRobot()
      },
      immediate: true
    }
  }
}
</script>

<style lang="scss" scoped>
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
.setting {
  padding: 4px 6px;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 10px;
  line-height: 13px;
  border-radius: 2px;
  background: rgba(38, 84, 152, 0.50);
}


.lights {
  span {
    color: #fff;
    font-size: 12px;
    font-family: Alibaba PuHuiTi;
    letter-spacing: 0.86px;
    line-height: 20px;
  }
}

::v-deep {
  .el-switch {
    line-height: 18px !important;
    line-height: 16px;
    // &.is-checked .el-switch__core {
    //   border-color: var(--success-color) !important;
    //   background-color: var(--success-color) !important;
    // }
    // &.with-text {
      .el-switch__label.el-switch__label--right {
        margin-left: 3px;
      }
      .el-switch__core {
        width: 50px !important;
        &:after {
          top: 2px;
          left: 2px;
          width: 14px;
          height: 14px;
        }
      }
    // }
    &__label {
      position: absolute;
      display: none !important;
      // height: 16px;
      font-weight: normal !important;
      z-index: 2000;
      * {
        font-size: 12px !important;
      }
      &.el-switch__label--left {
        margin-right: 0;
        margin-left: 19px;
      }
      &.el-switch__label--right {
        margin-left: 3px;
      }
      &.is-active {
        display: inline-block !important;
        color: #fff !important
      }
    }
    &.is-checked .el-switch__core::after {
      left: unset;
      right: 3px;
    }
  }
}
</style>
