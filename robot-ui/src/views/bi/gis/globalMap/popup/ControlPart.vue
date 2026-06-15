<template>
  <div class="flx-center robot-control3">
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
    <div class="control-btns ml20">
      <div class="btn-box flx-justify-between flex-column wp166">
        <div class="flx-justify-between w100 flex-wrap" style="margin-top: -10px; margin-left: -10px;">
          <template v-for="(item, index) in operList.slice(tabIndex === 0 ? 0 : 4, tabIndex === 0 ? 4 : 20)">
            <el-button
              v-if="item.key !== 'step'"
              :key="item.key"
              type="primary"
              class="wp73 hp36 mt10 ml10"
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
              @change="changeStep"
              class="wp73 ml10 mt10 hp36 butai-select"
              :class="{ 'tac': butaiValue === 0 }"
              title="切换步态"
              popper-class="custom-select control-select-popper p10"
            >
              <el-option v-for="item in butaiList" :key="item.label" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { butaiList, robotControlObj } from '../../../js/constants/robot-control';
// import robotControlByKeyboard from '../../../js/mixins/robotControlByKeyboard';
import yuntai from '../../../patrol/monitor/second/components/yuntai';

export default {
  name: "Control",
  props: {
    tabIndex: {
      type: Number,
      default: 0
    }
  },
  mixins: [yuntai], // robotControlByKeyboard
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
  mounted() {},
  methods: {
    controlRobot(key) {
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

  .control-btns {
    .el-button, .el-select .el-input__inner {
      padding: 7px 10px;
      color: #fff;
      text-align: center;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      font-style: normal;
      font-weight: 400;
      line-height: 16px; /* 100% */
      letter-spacing: 0.24px;
      border: none;
      border-radius: 4px;
      background: #021328;
      box-shadow: 0 0 14px 2px #09F inset;
      &.is-disabled {
        color: rgba(183, 188, 194, 0.50);
        background: rgba(34, 53, 67, 0.50);
      }
    }
    .el-select {
      .el-input__inner {
        height: 30px !important;
        padding-right: 27px;
        line-height: 30px !important;
        &::placeholder {
          color: #fff;
        }
      }
      .el-input__suffix .el-input__icon {
        color: #fff;
        font-size: 12px;
        line-height: 100%;
      }
    }
  }
}
::v-deep .el-select {
  &.butai-select {
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
  }
}
</style>
