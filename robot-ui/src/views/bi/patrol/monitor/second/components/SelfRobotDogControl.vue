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
      <div class="custom-tab-button flex">
        <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value">{{ item.label }}</div>
      </div>
      <div class="status success flex-column mt12" style="color: #fff;">
        <span>当前状态：</span>
        <span class="mt10">{{ selectedRobot?.mode || 'MANUAL'  }}模式<svg-icon icon-class="d-down" class="ml4"></svg-icon></span>
      </div>
    </div>
    <div class="flx-center ml54">
      <div class="outer flx-center">
        <div class="inner flx-center no_move">
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
      <div class="btns flx-center wp180 ml28 flex-wrap">
          <!-- :disabled="deviceData.onDockState === '1'" -->
        <template v-for="(item, index) in operList.slice(tabIndex === 0 ? 0 : 4, tabIndex === 0 ? 4 : 20)">
          <el-button
            v-if="item.key !== 'step'"
            :key="item.key"
            type="primary"
            class="wp80 hp30 mt10 ml10"
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
            class="wp80 ml10 mt10 hp30 butai-select"
            :class="{ 'tac': butaiValue == 0 }"
            title="切换步态"
            popper-class="custom-select control-select-popper control-select-popper1 p10"
          >
            <el-option v-for="item in butaiList" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
        </template>
      </div>
    </div>
    <Speed @changeSpeed="changeSpeed" ref="speedRef" />
  </div>
</template>

<script>
import { butaiList, robotControlObj } from '../../../../js/constants/robot-control.js';
import Speed from '../../../../components/modal/Speed.vue';
import yuntai from './yuntai.js';
export default {
  name: 'DogSelfControl',
  mixins: [yuntai],
  components: {
    Speed,
  },
  data() {
    return {
      robotControlObj,
      tabList: [
        {
          label: '基础',
          value: 0
        },
        {
          label: '高级',
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
      butaiList,
      butaiValue: 0
    }
  },
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

.btns {
  margin-top: -10px;
  margin-left: -10px;
  ::v-deep .el-button {
    padding: 10px;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    background: #080808;
    border-radius: 4px;
    border: none;
    box-shadow: 0 0 14px 2px #515151 inset;
    text-align: center;
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
}

.outer {
  position: relative;
  width: 110px;
  height: 110px;
  margin: 0 auto;
  background: #080808;
  border: 1px solid #434343;
  box-shadow: 0 0 12px 2px #303030 inset;
  border-radius: 50%;
  .inner {
    width: 56px;
    height: 56px;
    background: #181818;
    border: 1px solid #5E5E5E;
    border-radius: 50%;
    .circle {
      position: relative;
      color: #fff;
      font-size: 8.625px;
      border-radius: 50%;
    }
    &.no_move {
      position: relative;
      &::after {
        position: absolute;
        content: '';
        width: 3.7px;
        height: 100%;
        border: 1px solid #181818;
        background: #8F8F8F;
        transform: rotate(-45deg);
      }
    }
  }
  .arrow {
    position: absolute;
    width: 15.4px;
    height: 15.4px;
    text-align: center;
    color: #8F8F8F;
    cursor: pointer;
    &.up {
      top: 6px;
      left: 47.3px;
    }
    &.right {
      top: 47.3px;
      right: 4.95px;
      transform: rotate(90deg);
      transform-origin: center;
    }
    &.down {
      bottom: 6px;
      left: 47.3px;
      transform: rotateZ(-180deg);
      transform-origin: center;
    }
    &.left {
      top: 47.3px;
      left: 4.95px;
      transform: rotate(-90deg);
      transform-origin: center;
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