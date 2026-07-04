<!--
 * @Author: dengxumei
 * @Date: 2026-04-03 10:06:11
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-05-11 10:33:49
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\gis\globalMap\popup\Robot.vue
 * @Version: 
-->
<!-- 机器狗信息 -->
<template>
  <div class="flex popver-machine-info">
    <div :class="'machine-container robot-container ' + className" style="margin-right: 45px; margin-bottom: -30px;">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="flx-align-center">
            <div class="title ml10">{{currentInfo.deviceName || '监区点名机器人'}}</div>
            <div class="status ml10">{{ currentInfo.dogState || '空闲中' }}</div>
          </div>
          <div class="close mr10" @click="onClose()">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <!-- <img src="@/assets/images/bi/bg.png" alt="" class="p10 w100" height="190" style="padding-bottom: 0 !important;"> -->
        <div class="info-content pr10 pl10 flex flex-wrap">
          <div class="item wp154 mt10">装备类型：{{ currentInfo.dogType || '-' }}</div>
          <div class="item wp151 ml26 mt10">当前电量：{{ currentInfo.battery || '-' }}%</div>
          <div class="item wp154 mt10">装备型号：{{ currentInfo.model || '-' }}</div>
          <div class="item wp151 ml26 mt10">是否告警：{{ currentInfo.isWarning || '-' }}</div>
          <div class="item wp154 mt10">控制模型：{{ currentInfo.controlMode || '-' }}</div>
          <div class="item wp151 ml26 mt10">上装设备：{{ '3' }}台</div>
          <div class="item wp154 mt10">当前速度：{{ currentInfo.speed || '-' }}m/s</div>
          <div class="item wp151 ml26 mt10 flx-align-center">
            当前状态：
            <span class="status">{{ currentInfo.dogState || '-' }}</span>
          </div>
        </div>
        <div class="btns m10 mb20 flx-align-center">
          <el-button class="pr20 pl20" type="primary" @click="onCustomEvent()">远程控制</el-button>
          <el-button class="pr20 pl20" type="primary" @click="onShutdown()">一键返航</el-button>
          <el-button class="pr20 pl20" type="primary" @click="onStartup()">退出充电桩</el-button>
          <!-- <el-button type="primary" @click="onAddTask()">添加任务</el-button> -->
        </div>
      </div>
      <div class="guideline">
        <svg-icon icon-class="guideline1" class="wp106 hp46"></svg-icon>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PopupRobot',
  props: {
    currentInfo: {
      type: Object,
      default: () => ({})
    },
    deviceInfo: {
      type: Object,
      default: () => ({})
    },
    className: {
      type: String,
      default: ''
    },
    onShutdown: Function,
    onStartup: Function,
    onClose: Function,
    onCustomEvent: Function,
    onAddTask: Function
  },
}
</script>

<style lang="scss" scoped>
.machine-container {
  position: relative;
  .box {
    width: 355px !important;
  }
  .guideline {
    position: absolute;
    bottom: 120px;
    left: -106px;
    // transform: translate(-50%, -50%);
  }
}
</style>
