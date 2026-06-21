<!--
 * @Author: dengxumei
 * @Date: 2026-04-03 10:06:11
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-07 10:05:43
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\gis\globalMap\popup\Battery.vue
 * @Version: 
-->
<!-- 机器狗信息 -->
<template>
  <div class="flex popver-machine-info">
    <div :class="'machine-container battery-container ' + className" style="margin-right: 45px; margin-bottom: -30px;">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="flx-align-center">
            <div class="title ml10">智能充电站01</div>
          </div>
          <div class="close mr10" @click="onClose()">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content pt20 pr6 pl6 pb20">
          <div class="item">
            <div class="label flx-align-center">
              <svg-icon icon-class="verify"></svg-icon>
              <span class="ml6">充电站状态</span>
            </div>
            <div class="value with-border mt10 status success">正常运行</div>
          </div>
          <div class="item mt20">
            <div class="label flx-align-center">
              <svg-icon icon-class="robot"></svg-icon>
              <span class="ml6">正在充电设备</span>
            </div>
            <div class="value with-border mt10" style="color: #fff;">监区机器人-01</div>
          </div>
          <div class="item mt20">
            <div class="label flx-justify-between">
              <div class="flx-align-center">
                <svg-icon icon-class="battery-charge"></svg-icon>
                <span class="ml6">充电情况</span>
              </div>
              <span>60%</span>
            </div>
            <div class="value mt10 with-border progress hp10 p0" :style="{'--rate': 60 + '%'}"></div>
          </div>
          <div class="item mt20">
            <div class="label flx-align-center">
              <svg-icon icon-class="time"></svg-icon>
              <span class="ml6">预计充满时间</span>
            </div>
            <div class="value with-border mt10">2小时</div>
          </div>
        </div>
        <div class="btns m10 mb20 flx-align-center" style="margin-top: 0 !important;">
          <el-button type="primary" class="w100">设为优先返航点</el-button>
        </div>
      </div>
      <div class="guideline w100 hp29 mt9 pr35" style="text-align: right;">
        <svg-icon icon-class="guideline" class="wp157 h100" style="vertical-align: top;"></svg-icon>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PopupBattery',
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
  }
}
</script>