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
  <div class="box p20">
    <div class="flx-justify-between" style="margin-right: -10px;">
      <div class="custom-tab-button flex">
        <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value">{{ item.label }}</div>
      </div>
      <div class="status success flx-align-center" style="color: #fff;">
        <span>当前状态</span>
        <span>自动模式</span>
      </div>
    </div>
    <div class="outer flx-center mt20">
      <div class="inner flx-center">
        <div class="circle flx-center">移动</div>
      </div>
      <div
        v-for="key in ['advance', 'back', 'turn-left' , 'turn-right']"
        :key="key"
        :class="['arrow', robotControlObj[key].class, deviceData.onDockState === '1' ? 'is-disabled': '']"
        :title="robotControlObj[key].label"
        @mousedown="handleControl(robotControlObj[key])"
      >
        <svg-icon icon-class="control-arrow" />
      </div>
    </div>
    <div class="btns flx-center flex-column mt20">
      <div>
        <el-button
          type="primary"
          class="wp80 hp30"
          @click="handleControl(robotControlObj['zuoyi'])"
          :disabled="deviceData.onDockState === '1'"
        >
          左平移
        </el-button>
        <el-button
          type="primary"
          class="wp80 hp30 ml10"
          @click="handleControl(robotControlObj['youyi'])"
          :disabled="deviceData.onDockState === '1'"
        >
          右平移
        </el-button>
      </div>
      <div class="mt10">
        <el-button type="primary" class="wp80 hp30" @click="controlRobot('shutdown')">一键返航</el-button>
        <el-button type="primary" class="wp80 hp30 ml10" @click="controlRobot('startup')">退出充电桩</el-button>
      </div>
    </div>
    <Speed @changeSpeed="changeSpeed" ref="speedRef" />
  </div>
</template>

<script>
import robotControl from '../../../../js/mixins/robotControl';
import Speed from '../../../../components/modal/Speed.vue';
export default {
  name: 'SelfControl',
  mixins: [robotControl],
  components: {
    Speed,
  },
  data() {
    return {
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
    }
  }
}
</script>
<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
}

.custom-tab-button .tab-button-item {
  padding: 5px 10px;
  font-size: 12px;
}

.btns {
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