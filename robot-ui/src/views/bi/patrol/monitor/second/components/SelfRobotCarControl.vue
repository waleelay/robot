<!--
 * @Author: dengxumei
 * @Date: 2026-04-08 09:24:32
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-17 13:44:12
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\second\components\SelfControl.vue
 * @Version: 
-->
<template>
  <div class="flx-align-center h100 pt20 pb20">
    <div class="flex flex-column h100 pl30" style="border-left: 1px solid #123F8C;">
      <div class="d-flex flex-column" style="align-items: start; display: none;" v-if="vehicleLightDevice">
        <div class="custom-tab-button flex">
          <div v-for="item in tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
        </div>
        <div class="mt15 mode d-flex">
          <el-switch
            :value="vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].mode === 'ON'"
            active-text="开启"
            inactive-text="关闭"
            active-color="#3DB56A"
            inactive-color="#5E5E5E"
            @change="e => setVehicleLightMode(tabIndex === 0 ? 'front' : 'rear', e ? 'ON' : 'OFF')"
          >
          </el-switch>
          <div
            style="display: none"
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
                <div class="percent mt1 mb5">{{ vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].brightness }}%</div>
                <el-slider
                  :value="vehicleLightState[tabIndex === 0 ? 'front' : 'rear'].brightness"
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
    <div class="ml54" :class="{ 'flx-align-center': !vehicleLightDevice && !warningLightDevices?.length }">
      <div class="mode d-flex" :class="{ 'flex-column': !vehicleLightDevice && !warningLightDevices?.length, 'flx-align-center': vehicleLightDevice || warningLightDevices?.length }">
        <span>控制模式：</span>
        <el-dropdown trigger="click" :class="{ 'mt10': !vehicleLightDevice && !warningLightDevices?.length, 'ml10': vehicleLightDevice || warningLightDevices?.length }" @command="handleModeChange">
          <div class="mode-status success flex-column">
            <span>{{ selectedRobot?.controlMode }}模式<svg-icon icon-class="d-down" class="ml4"></svg-icon></span>
          </div>
          <el-dropdown-menu slot="dropdown" class="wp100 mt2 custom-dropdown-menu mode-dropdown-menu p4">
            <el-dropdown-item command="NAVIGATION">自动巡航模式</el-dropdown-item>
            <el-dropdown-item command="MANUAL">手动控制模式</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <div class="mt16 d-flex common-control" :class="{ 'ml30': !vehicleLightDevice && !warningLightDevices?.length, 'is-disabled': selectedRobot?.controlMode !== 'MANUAL' }">
        <div class="outer flx-center">
          <div class="inner flx-center">
            <div class="circle flx-center">移动</div>
          </div>
          <!-- 'is-disabled' -->
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
        <div class="lights ml38">
          <div v-if="vehicleLightDevice && !hasVehicleLightStatus(vehicleLightDevice)" class="light-pending mb10">车灯状态同步中</div>
          <div class="flx-center lights-container">
            <div v-if="vehicleLightDevice" class="d-flex flex-column" style="align-items: end;">
              <div class="flx-align-center">
                <span class="wp60 tal">前车灯：</span>
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
                <span class="wp60 tal">后车灯：</span>
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
            <div v-if="warningLightDevices?.length" class="flx-center flex-column">
              <div v-for="device in warningLightDevices" :key="device.deviceId" class="flx-align-center">
                <span class="wp76 tal">{{ device.displayName || device.deviceId }}：</span>
                <el-switch
                  v-if="hasWarningLightStatus(device)"
                  :value="isWarningLightOn(device)"
                  active-text="开启"
                  inactive-text="关闭"
                  active-color="#3DB56A"
                  inactive-color="#5E5E5E"
                  @change="setWarningLight(device, $event)">
                </el-switch>
                <span v-else class="light-pending">同步中</span>
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
import { butaiList, robotControlObj } from '../../../../js/constants/robot-control.js';
import Speed from '../../../../components/modal/Speed.vue';
import yuntai from './yuntai.js';
export default {
  name: 'CarSelfControl',
  mixins: [yuntai],
  components: {
    Speed
  },
  data() {
    return {
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
      operList: [
        { key: 'zuoyi', label: '左平移' },
        { key: 'youyi', label: '右平移' },
        { key: 'shutdown', label: '一键返航' },
        { key: 'startup', label: '退出充电' },
        { key: 'zhanli', label: '站立' },
        { key: 'paxia', label: '趴下' },
        { key: 'tztb', label: '停止踏步' },
        { key: 'jiting', label: '急停' },
        { key: 'step', label: '切换步态' },
        { key: 'speed', label: '设定速度' },
      ],
      modes: [],
      modes1: [
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
    }
  },
  methods: {
    getLightDevice(displayName) {
      return this.warningLightDevices.find(item => item.displayName === displayName) || {}
    },
    handleClickMode(e, index, item) {
      if (e.target.className.includes('el-slider')) return
      this.setVehicleLightMode(this.tabIndex === 0 ? 'front' : 'rear', index === 0 ? 'OFF' : item.key)
    },
    async handleModeChange(controlMode) {
      this.$refs.controlModeWarningRef.open({ robotId: this.selectedRobotId, controlMode })
    }
  }
}
</script>
<style scoped lang="scss">
.custom-tab-button .tab-button-item {
  padding: 5px 10px;
  font-size: 12px;
}

.status {
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 16px;
  span + span {
    padding: 2px 4px;
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

.light-pending {
  color: rgba(255, 255, 255, 0.65);
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 18px;
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

.btns {
  margin-top: -10px;
  margin-left: -10px;
  ::v-deep .el-button {
    padding: 10px;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    background: #021328;
    box-shadow: 0 0 14px 2px #09F inset;
    border-radius: 4px;
    border: none;
    text-align: center;
    &.is-disabled {
      background: #080808;
      box-shadow: 0 0 14px 2px #515151 inset;
      cursor: not-allowed;
      // pointer-events: none;
    }
  }
}
::v-deep .el-select {
  .el-input__inner {
    height: 30px;
    padding: 0 6px;
    font-size: 12px;
    line-height: 0;
    text-align: left;
    color: #FFF;
    background: #080808;
    border-radius: 4px;
    border: none;
    -webkit-box-shadow: 0 0 14px 2px #515151 inset;
    box-shadow: 0 0 14px 2px #515151 inset;
  }
  .el-input__suffix {
    .el-input__icon {
      width: 12px;
      font-size: 12px;
      line-height: 30px;
      color: #FFF;
    }
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
  .lights-container > div {
    & + div {
      margin-left: 30px;
    }
    & > div + div {
      margin-top: 15px;
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
  width: 110px;
  height: 110px;
  margin: 0 auto;
  // background: #080808;
  // border: 1px solid #434343;
  box-shadow: 0 0 14.154px 2.831px #09F inset;
  border-radius: 50%;
  .inner {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    .circle {
      width: 100%;
      height: 100%;
      color: #fff;
      font-size: 8.625px;
      line-height: 56px;
      text-align: center;
    }
  }
  .arrow {
    width: 15.4px;
    height: 15.4px;
    &.up {
      top: 6px;
      left: 47.3px;
    }
    &.right {
      top: 47.3px;
      right: 4.95px;
    }
    &.down {
      bottom: 6px;
      left: 47.3px;
    }
    &.left {
      top: 47.3px;
      left: 4.95px;
    }
    &.is-disabled {
      cursor: not-allowed !important;
      opacity: 0.7;
      &:hover {
        cursor: not-allowed;
      }
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