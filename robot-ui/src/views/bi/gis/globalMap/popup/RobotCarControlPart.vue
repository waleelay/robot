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
          <div class="lights d-flex">
            <div class="flx-align-center" v-if="getLightDevice('左警示灯').deviceId">
              <span>左警示灯：</span>
              <el-switch
                class="ml10"
                :value="isWarningLightOn(getLightDevice('左警示灯'))"
                active-text="开启"
                inactive-text="关闭"
                active-color="#3DB56A"
                inactive-color="#5E5E5E"
                @change="setWarningLight(getLightDevice('左警示灯'), $event)">
              </el-switch>
            </div>
            <div class="flx-align-center ml42" v-if="getLightDevice('右警示灯').deviceId">
              <span>右警示灯：</span>
              <el-switch
                class="ml10"
                :value="isWarningLightOn(getLightDevice('右警示灯'))"
                active-text="开启"
                inactive-text="关闭"
                active-color="#3DB56A"
                inactive-color="#5E5E5E"
                @change="setWarningLight(getLightDevice('右警示灯'), $event)">
              </el-switch>
            </div>
          </div>
          <div class="mt16 d-flex">
            <div class="flex" style="align-items: end;">
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
                  @mousedown="handleControl(robotControlObj[key])"
                >
                  <svg-icon icon-class="control-arrow" />
                </div>
              </div>
            </div>
            <div class="ml24" v-if="vehicleLightDevice">
              <div class="custom-tab-button flex">
                <div v-for="item in tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
              </div>
              <div class="mt15 mode d-flex">
                <div
                  class="option wp100 hp122 p10"
                  v-for="(item, index) in modes"
                  :key="item.key"
                  :class="{ ml11 : index !== 0, 'is-active': (vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].mode === item.key) || index === 0 && ['ON', 'OFF'].includes(vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].mode) }"
                  @click="e => handleClickMode(e, index, item)"
                >
                  <div class="order pl15">模式{{ index === 0 ? '一' : index === 1 ? '二' : '三' }}</div>
                  <div class="mt10 tac name">{{ item.name }}</div>
                  <div class="mt2 tac desc">{{ item.desc }}</div>
                  <div class="mt6 tac flex-column flx-center" :class="{ 'mb6': index > 0 }" :style="{ marginTop: index > 0 ? '1px' : '6px' }">
                    <el-switch
                      :value="vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].mode === item.key"
                      v-if="index === 0"
                      active-text="开启"
                      inactive-text="关闭"
                      active-color="#3DB56A"
                      inactive-color="#5E5E5E"
                      @change="e => setVehicleLightMode(tabIndex === 0 ? 'front' : 'rear', e ? 'ON' : 'OFF')"
                    >
                    </el-switch>
                    <template v-else-if="index === 2">
                      <div class="percent mt1 mb5">{{ vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].customValue }}%</div>
                      <el-slider
                        :value="vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].customValue"
                        :min="0"
                        :max="100"
                        @input="value => updateVehicleLightBrightness(tabIndex === 0 ? 'front' : 'rear', value)"
                        @change="value => setVehicleLightBrightness(tabIndex === 0 ? 'front' : 'rear', value)"
                        :show-tooltip="false"
                      ></el-slider>
                    </template>
                    <svg-icon :icon-class="item.icon" class="mt4"></svg-icon>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import VideoBox from '../../../components/modal/VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../../utils/fullscreen.js';
import { robotControlObj } from '../../../js/constants/robot-control.js';
import yuntai from '../../../patrol/monitor/second/components/yuntai.js';
import { mapActions } from 'vuex';
export default {
  name: 'RobotCarControlPart',
  components: {
    VideoBox
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
    },
    handleControl(key) {},
    getLightDevice(displayName) {
      return this.warningLightDevices.find(item => item.displayName === displayName) || {}
    },
    handleClickMode(e, index, item) {
      if (e.target.className.includes('el-slider')) return
      this.setVehicleLightMode(this.tabIndex === 0 ? 'front' : 'rear', index === 0 ? 'OFF' : item.key)
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
.lights {
  span {
    color: #fff;
    font-size: 14px;
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
      // background: #159AFF;
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
</style>