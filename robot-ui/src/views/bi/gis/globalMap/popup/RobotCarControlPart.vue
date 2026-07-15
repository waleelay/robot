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
        <div class="flex1 flex-column pl28 pr15" style="position: unset;">
          <div class="mode d-flex flx-align-center">
            <span>控制模式：</span>
            <el-dropdown trigger="click" class="ml10" @command="handleModeChange">
              <div class="mode-status success flex-column">
                <span>{{ selectedRobot?.controlMode || 'MANUAL'  }}模式<svg-icon icon-class="d-down" class="ml4"></svg-icon></span>
              </div>
              <el-dropdown-menu slot="dropdown" class="wp100 mt2 custom-dropdown-menu mode-dropdown-menu p4">
                <el-dropdown-item command="NAVIGATION" :class="{ 'is-active': selectedRobot?.controlMode === 'NAVIGATION' }">导航模式</el-dropdown-item>
                <el-dropdown-item command="MANUAL" :class="{ 'is-active': selectedRobot?.controlMode === 'MANUAL' }">手动模式</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </div>

          <div class="d-flex mt16">
            <Talk v-if="showTalk" :isMapInner="showTalk" class="mr30" />
            <div class="flx-justify-between" :class="{ 'flex-column': showTalk }">
              <div class="d-flex">
                <div class="flex common-control" :class="{ 'is-small': showTalk }">
                  <div class="outer">
                    <div class="inner w100 h100 flx-center m0">
                      <div class="circle flx-center">移动</div>
                    </div>
                    <!-- is-disabled -->
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
                </div>
              </div>
              <div class="lights flx-align-center" :class="{ 'mt15': showTalk, 'ml30 flex-column': !showTalk }">
                <div v-if="vehicleLightDevice">
                  <div class="flx-align-center">
                    <span class="wp76 tal">前车灯：</span>
                    <el-switch
                      :value="vehicleLightState?.front?.mode === 'ON'"
                      active-text="开启"
                      inactive-text="关闭"
                      active-color="#3DB56A"
                      inactive-color="#5E5E5E"
                      @change="e => setVehicleLightMode('front', e ? 'ON' : 'OFF')"
                    >
                    </el-switch>
                  </div>
                  <div class="flx-align-center">
                    <span class="wp76 tal">后车灯：</span>
                    <el-switch
                      :value="vehicleLightState?.rear?.mode === 'ON'"
                      active-text="开启"
                      inactive-text="关闭"
                      active-color="#3DB56A"
                      inactive-color="#5E5E5E"
                      @change="e => setVehicleLightMode('rear', e ? 'ON' : 'OFF')"
                    >
                    </el-switch>
                  </div>
                </div>
                <div v-if="warningLightDevices?.length">
                  <div v-for="device in warningLightDevices" :key="device.deviceId" class="flx-align-center">
                    <span class="wp76 tal">{{ device.displayName || device.deviceId }}：</span>
                    <el-switch
                      :value="isWarningLightOn(device)"
                      active-text="开启"
                      inactive-text="关闭"
                      active-color="#3DB56A"
                      inactive-color="#5E5E5E"
                      @change="setWarningLight(device, $event)">
                    </el-switch>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
        </div>
      </div>
    </div>
    <ControlModeWarning ref="controlModeWarningRef" />
  </div>
</template>

<script>
import VideoBox from '../../../components/modal/VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../../utils/fullscreen.js';
import { robotControlObj } from '../../../js/constants/robot-control.js';
import yuntai from '../../../patrol/monitor/second/components/yuntai.js';
import { mapActions, mapState } from 'vuex';
import Talk from './../../../patrol/monitor/second/components/Talk.vue'
import { setControlMode } from '../../../../../api/media.js';
export default {
  name: 'RobotCarControlPart',
  components: {
    VideoBox,
    Talk
  },
  mixins: [common, yuntai],
  data() {
    return {
      visible: false,
      leftLightSwitch: false,
      rightLightSwitch: true,
      robotControlObj,
      tabList: [
        {
          label: '前车灯', 
          value: 0
        },
        {
          label: '后车灯',
          value: 1
        }
      ],
      tabIndex: 0,
      modes: [
        {
          code: 0,
          name: '普通模式',
          desc: '打开后灯光常亮',
          icon: 'light-high-beam',
          key: 'ON/OFF'
        },
        {
          code: 1,
          name: '呼吸灯模式',
          desc: '打开后双灯闪烁',
          icon: 'light-side',
          key: 'BREATH'
        },
        {
          code: 2,
          name: '自定义模式',
          desc: '滑动调节亮度',
          icon: 'light-car',
          key: 'CUSTOM'
        }
      ],
      selectMode: 1,
      prefixId: 'robot-car-video-div',
      robot: {},
      cameraOrderByRobot: {}
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
    },
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    ...mapState('websocketRobot', ['robots']),
    baseInfo() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    },
    canControl() {
      return this.selectedRobot?.controlMode === 'MANUAL'
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId', 'setSelectedRobotId']),
    show(visible) {
      this.visible = visible;
    },
    async goControl() {
      await this.stopAll()
      this.setSelectedRobotId(this.selectedRobotId)
      this.$router.push({ path: '/bi/patrol/monitor' })
    },
    handleControl(key) {},
    getLightDevice(displayName) {
      return this.warningLightDevices.find(item => item.displayName === displayName) || {}
    },
    handleClickMode(e, index, item) {
      if (e.target.className.includes('el-slider')) return
      this.setVehicleLightMode(this.tabIndex === 0 ? 'front' : 'rear', index === 0 ? 'OFF' : item.key)
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
    },
    async handleModeChange(controlMode) {
      this.$refs.controlModeWarningRef.open({ robotId: this.selectedRobotId, controlMode })
    }
  },
  watch: {
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
  bottom: 50px;
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
.lights {
  span {
    color: #fff;
    font-size: 14px;
    font-family: Alibaba PuHuiTi;
    letter-spacing: 0.86px;
    line-height: 20px;
  }
  & > div + div {
    margin-left: 20px;
  }
  & > div {
    div + div {
      margin-top: 10px;
    }
  }
  &.flex-column {
    & > div + div {
      margin-top: 10px;
      margin-left: 0;
    }
    & > div > div + div {
    }
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

.outer {
  position: relative;
  width: 150px;
  height: 150px;
  background: #021328;
  border: 0.75px solid #18ADFE;
  box-shadow: 0 0 16.544px 3.309px #09F inset;
  border-radius: 50%;
  .inner {
    border-radius: 50%;
    .circle {
      position: relative;
      width: 76.5px;
      height: 76.5px;
      margin: 0 auto;
      background: #159AFF;
      border: 0.466px solid #159AFF;
      aspect-ratio: 1/1;
      color: #fff;
      font-size: 11.76px;
      line-height: 76.5px;
      border-radius: 50%;
    }
  }
  .arrow {
    position: absolute;
    font-size: 21px;
    text-align: center;
    color: #159AFF;
    cursor: pointer;
    &.up {
      top: 8.25px;
      left: 64.25px;
    }
    &.right {
      top: 64.25px;
      right: 8.25px;
      transform: rotate(90deg);
      transform-origin: center;
    }
    &.down {
      bottom: 8.25px;
      left: 64.25px;
      transform: rotateZ(-180deg);
      transform-origin: center;
    }
    &.left {
      top: 64.25px;
      left: 8.25px;
      transform: rotate(-90deg);
      transform-origin: center;
    }
  }
}
.mode {
  .option {
    border-radius: 6px;
    border: 1px solid #004376;
    background: #021328;
    .order {
      position: relative;
      color: #C8D2E4;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      line-height: 16px;
      letter-spacing: 0.857px;
      &::before, &::after {
        position: absolute;
        border-radius: 50%;
        content: '';
      }
      &::before {
        top: 3px;
        left: 0;
        width: 10px;
        height: 10px;
        border: 1px solid #C8D2E4;
      }
      &::after {
        top: 6px;
        left: 3px;
        width: 4px;
        height: 4px;
        display: none;
        background: linear-gradient(180deg, #E1F7FF 0%, #35CAFF 100%);
      }
    }
    .name {
      color: #C8D2E4;
      font-family: "Alibaba PuHuiTi";
      font-size: 14px;
      line-height: 19px;
      letter-spacing: 0.857px;
    }
    .desc {
      color: #8D9BB5;
      font-family: "Alibaba PuHuiTi";
      font-size: 10px;
      line-height: 14px;
      letter-spacing: 0.857px;
    }
    .svg-icon {
      color: #FFF;
      font-size: 12px
    }
    &.is-active {
      border-color: #3CABFF;
      background: #021328;
      box-shadow: 0 0 20px 0 #159AFF inset;
      .order {
        color: #35CAFF;
        &::before {
          border-color: #35CAFF;
        }
        &::after {
          display: inline-block;
        }
      }
      .name {
        background: linear-gradient(180deg, #A3D9FF 42.11%, #4F9ADB 73.68%);
        background-clip: text;
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }
      .desc {
        color: #06AEFC
      }
    }
  }
}
.percent {
  width: auto;
  padding: 0 2px;
  color: #FFF;
  font-family: "Alibaba PuHuiTi";
  font-size: 8px;
  line-height: 11px;
  letter-spacing: 0.857px;
  border-radius: 1px;
  background: linear-gradient(214deg, #03AFFA 32.73%, #027FFA 87.27%);
}
::v-deep {
  .el-slider {
    width: 100%;
    &__runway {
      height: 4px;
      margin: 0;
      border-radius: 0;
      background: #D9D9D9;
    }
    &__bar {
      height: 4px;
      border-radius: 0;
      background: linear-gradient(90deg, #0278FA 0%, #03BBFA 100%);
    }
    .el-slider__button-wrapper {
      top: -8px;
      height: auto;      
      .el-slider__button {
        width: 10px;
        height: 10px;
        border-color: #03ADFA;
        border-width: 2px;
      }
    }
  }
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
.is-small {
  .outer {
    width: 90px;
    height: 90px;
    .inner {
      .circle {
        width: 46px;
        height: 46px;
        font-size: 10px;
        line-height: 46px;
      }
    }
    .arrow {
      font-size: 12px;
      &.up {
        top: 5px;
        left: 35px;
      }
      &.right {
        top: 35px;
        right: 5px;
      }
      &.down {
        bottom: 5px;
        left: 35px;
      }
      &.left {
        top: 35px;
        left: 5px;
      }
    }
  }
  ::v-deep .el-select {
    &.butai-select {
      height: 26px;
      .el-input__inner {
        height: 26px;
      }
      .el-input__suffix {
        .el-input__icon {
          line-height: 26px;
        }
      }
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
