<template>
  <div class="flx-center robot-control3" :class="{ 'is-small flex-column': showSmall }">
    <div
      class="common-control flx-center"
      :class="{ 'is-disabled': isBodyControlDisabled, 'flex-column': showSmall }"
    >
      <div class="outer">
        <div class="inner w100 h100 flx-center m0">
          <div class="circle flx-center">移动</div>
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
      <div :class="{ 'ml20': !showSmall, 'mt15': showSmall }">
        <div class="btns control-btns">
          <div class="btn-box flx-center flex-column" :class="{ 'wp166': !showSmall, 'wp249': showSmall }">
            <div class="flx-justify-between flex-wrap" :class="{ 'w100': tabIndex === 1, 'wp166': tabIndex === 0 }" style="margin-top: -10px; margin-left: -10px;">
              <template v-for="(item, index) in operList.slice(tabIndex === 0 ? 0 : 4, tabIndex === 0 ? 4 : 20)">
                <el-button
                  v-if="item.key !== 'step'"
                  :key="item.key"
                  type="primary"
                  class="mt10 ml10 wp73"
                  :class="{ 'hp36': !showSmall, 'hp26': showSmall, 'is-disabled': isBodyControlDisabled }"
                  :disabled="isBodyControlDisabled"
                  @mousedown="['zuoyi', 'youyi'].includes(item.key) && startFrameControl(robotControlObj[item.key].key)"
                  @mouseup="['zuoyi', 'youyi'].includes(item.key) && stopFrameControl(robotControlObj[item.key].key)"
                  @mouseleave="['zuoyi', 'youyi'].includes(item.key) && stopFrameControl(robotControlObj[item.key].key)"
                  @touchstart.prevent="['zuoyi', 'youyi'].includes(item.key) && startFrameControl(robotControlObj[item.key].key)"
                  @touchend.prevent="['zuoyi', 'youyi'].includes(item.key) && stopFrameControl(robotControlObj[item.key].key)"
                  @click="controlRobot(item.key)"
                >
                  {{ item.label }}
                </el-button>
                <el-select
                  v-else
                  :key="item.key"
                  v-model="butaiValue"
                  placeholder="切换步态"
                  :disabled="isBodyControlDisabled"
                  @change="changeStep"
                  class="wp73 ml10 mt10 butai-select"
                  :class="{ 'tac': butaiValue === 0, 'is-disabled': isBodyControlDisabled }"
                  title="切换步态"
                  popper-class="custom-select control-select-popper p10"
                >
                  <el-option v-for="item in butaiList" :key="item.label" :label="item.label" :value="item.value" />
                </el-select>
              </template>
            </div>
          </div>
        </div>
        <div v-if="vehicleLightDevice && !showSmall" class="lights flx-align-center mt15 ml10">
          <span>车灯：</span>
          <el-switch
            :value="vehicleLightEnabled"
            active-text="开启"
            inactive-text="关闭"
            active-color="#3DB56A"
            inactive-color="#5E5E5E"
            @change="setVehicleLights">
          </el-switch>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { butaiList, robotControlObj } from '../../../js/constants/robot-control';
import yuntai from '../../../patrol/monitor/second/components/ptz-control-mixin';

export default {
  name: "Control",
  props: {
    tabIndex: {
      type: Number,
      default: 0
    },
    showSmall: {
      type: Boolean,
      default: false
    }
  },
  mixins: [yuntai],
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
      butaiList,
      butaiValue: 0
    };
  },
  computed: {
    isBodyControlDisabled() {
      return this.selectedRobot?.controlMode !== '手动模式'
    }
  },
  methods: {
    controlRobot(key) {
      if (['zuoyi', 'youyi'].includes(key)) return
      switch (key) {
        case 'step':
          break;
        case 'speed':
          this.changeSpeed()
          break;
        default:
          break;
      }
    },
    changeSpeed() {
      this.$refs.speedRef.show();
    },
    changeStep(e) {}
  },
};
</script>
<style lang="scss" scoped>
@keyframes fade-scale {
  0% {
    opacity: 0.8;
    transform: scale(0.95);
  }
  50% {
    opacity: 1;
    transform: scale(1.005);
  }
  100% {
    opacity: 0.8;
    transform: scale(0.95);
  }
}
.robot-control3 {
  &.is-small {
    .common-control {
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
            left: 38px;
          }
          &.right {
            top: 38px;
            right: 5px;
          }
          &.down {
            bottom: 5px;
            left: 38px;
          }
          &.left {
            top: 38px;
            left: 5px;
          }
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
    .btns {
      ::v-deep .el-button {
        line-height: 26px;
      }
    }
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
      &__label {
        position: absolute;
        display: none !important;
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

  .common-control {
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
          font-size: 16px;
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
    &.is-disabled {
      .outer {
        background: #101214;
        border-color: #4D4D4D;
        box-shadow: 0 0 16.544px 3.309px #838383 inset;
        .inner {
          .circle {
            background: #4D4D4D;
            border-color: #4D4D4D;
          }
        }
        .arrow {
          color: #4D4D4D;
        }
      }
    }
  }

  .btns {
    margin-top: -10px;
    margin-left: -10px;
    ::v-deep .el-button {
      padding: 0;
      color: #FFF;
      font-size: 12px;
      letter-spacing: 0.24px;
      background: #021328;
      box-shadow: 0 0 14px 2px #09F inset;
      border-radius: 4px;
      border: none;
      text-align: center;
      &.is-disabled {
        background: #0a0a0a;
        box-shadow: 0 0 14px 2px #a6a6a6 inset;
        cursor: not-allowed;
      }
      &:not(.is-disabled) {
        &:active {
          color: #0BF9FE;
          box-shadow: 0 0 10px 3px #0BF9FE inset;
        }
      }
    }
  }
}
::v-deep .el-select {
  &.butai-select {
    height: 36px;
    &.tac {
      .el-input__inner {
        padding-right: 20px;
        text-align: center;
      }
      .el-input__suffix {
        right: 8px;
      }
    }
    .el-input__inner {
      height: 36px;
      padding: 0 6px;
      font-size: 12px;
      line-height: 0;
      text-align: left;
      color: #FFF;
      border-radius: 4px;
      border: none;
      background: #021328;
      box-shadow: 0 0 14px 2px #09F inset;
    }
    .el-input__suffix {
      right: 3px;
      .el-input__icon {
        width: 12px;
        font-size: 12px;
        line-height: 36px;
        color: #FFF;
      }
    }
    &.is-disabled {
      .el-input__inner {
        background: #0a0a0a;
        box-shadow: 0 0 14px 2px #a6a6a6 inset;
        cursor: not-allowed;
      }
    }
  }
}
.custom-tab-button .tab-button-item {
  color: #4AB8FF;
  &.is-active {
    border: 1px solid #4AB8FF;
    background: #0A3560;
    box-shadow: 0 0 6px 0 #69C4FF inset;
  }
}
</style>
