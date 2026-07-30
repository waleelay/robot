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
    <div :class="{ 'd-flex': !vehicleLightDevice && !warningLightDevice }">
      <div class="mode d-flex" :class="{ 'flex-column': !vehicleLightDevice && !warningLightDevice, 'flx-align-center': vehicleLightDevice || warningLightDevice }">
        <span>当前状态：</span>
        <el-dropdown trigger="click" :class="{ 'mt10': !vehicleLightDevice && !warningLightDevice, 'ml10': vehicleLightDevice || warningLightDevice }" @command="handleModeChange">
          <div class="mode-status success flex-column">
            <span>{{ selectedRobot?.controlModeName || '-' }}<svg-icon icon-class="d-down" class="ml4"></svg-icon></span>
          </div>
          <el-dropdown-menu slot="dropdown" class="wp100 mt2 custom-dropdown-menu mode-dropdown-menu p4">
            <el-dropdown-item command="NAVIGATION" :class="{ 'is-active': selectedRobot?.controlMode === 'NAVIGATION' }">导航模式</el-dropdown-item>
            <el-dropdown-item command="MANUAL" :class="{ 'is-active': selectedRobot?.controlMode === 'MANUAL' }">手动模式</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <div class="mt16 d-flex common-control" :class="{ 'ml30': !vehicleLightDevice && !warningLightDevice, 'is-disabled': selectedRobot?.controlMode !== 'MANUAL' }">
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
        <div class="lights ml38 flex-column" style="justify-content: center;">
          <div class="flx-center lights-container">
            <div v-if="vehicleLightDevice" class="flx-align-center">
              <span class="wp60 tal">车灯：</span>
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
            <div v-if="warningLightDevice" class="flx-align-center">
              <span class="wp90 tal">红蓝警示灯：</span>
              <el-switch
                :value="isWarningLightOn(warningLightDevice)"
                :disabled="!hasDeviceAction(warningLightDevice, 'set_state')"
                active-text="开启"
                inactive-text="关闭"
                active-color="#3DB56A"
                inactive-color="#5E5E5E"
                @change="setWarningLight(warningLightDevice, $event)">
              </el-switch>
              <el-button
                class="warning-mode-button ml10"
                size="mini"
                icon="el-icon-refresh"
                :disabled="!hasDeviceAction(warningLightDevice, 'set_mode')"
                @click="switchWarningLightMode(warningLightDevice)"
              >切换模式</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <ControlModeWarning ref="controlModeWarningRef" />
  </div>
</template>

<script>
import { robotControlObj } from '../../../../js/constants/robot-control.js';
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
    }
  }
}
</script>
<style scoped lang="scss">
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

::v-deep .warning-mode-button.el-button {
  padding: 8px 10px;
  color: #FFF;
  font-size: 12px;
  background: #021328;
  box-shadow: 0 0 14px 2px #09F inset;
  border: none;
  border-radius: 4px;
  &:hover,
  &:focus {
    color: #FFF;
    background: #021328;
    box-shadow: 0 0 14px 2px #09F inset;
  }
  &:active {
    color: #0BF9FE;
    background: #021328;
    box-shadow: 0 0 10px 3px #0BF9FE inset;
  }
  &.is-disabled,
  &.is-disabled:hover,
  &.is-disabled:focus {
    color: #8F8F8F;
    background: #080808;
    box-shadow: 0 0 14px 2px #515151 inset;
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

</style>
