<!--
 * @Author: dengxumei
 * @Date: 2026-04-03 10:06:11
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-07 10:37:50
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\gis\globalMap\popup\UavPort.vue
 * @Version: 
-->
<!-- 无人机信息 -->
<template>
  <div class="flex popver-machine-info">
    <div :class="'machine-container uav-container ' + className" style="margin-right: 45px; margin-bottom: -30px;">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="flx-align-center">
            <div class="title ml10">无人机机场</div>
            <div class="status ml10">正常</div>
          </div>
          <div class="close mr10" @click="onClose()">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content pt20 pr10 pb20 pl10 flex flex-wrap">
          <div class="flx-justify-between task-info w100 hp66 pr10 pl10">
            <div class="item h100 flx-center flex-column">
              <div>
                <span class="count">128</span>
                <span class="unit"> 个</span>
              </div>
              <div class="title mt6">待执行任务</div>
            </div>
            <div class="item h100 flx-center flex-column">
              <div>
                <span class="count">888</span>
                <span class="unit"> 个</span>
              </div>
              <div class="title mt6">已完成任务</div>
            </div>
            <div class="item h100 flx-center flex-column">
              <div>
                <span class="count">2</span>
                <span class="unit"> 个</span>
              </div>
              <div class="title mt6">异常任务</div>
            </div>
          </div>
          <div class="list mt20">
            <div class="w100 flx-align-center flex title">
              <div class="flex1">无人机名称</div>
              <div class="wp47 ml12">状态</div>
              <div class="wp40 ml32 mr20">电量</div>
              <div class="flex1 ml12">无人机位置</div>
              <div class="flex1 ml12">任务状况</div>
            </div>
            <div class="content w100 flx-align-center flex pt6 pb6">
              <div v-for="item in uavInfo.uavList" :key="item.name" class="item hp36 w100 flx-align-center flex">
                <div class="name flex1 text-ellipsis" :title="item.name">{{item.name}}</div>
                <div class="status wp47 ml12" :class="{ success: item.status === '空闲中', yellow: item.status === '外出中' }">
                  {{item.status}}
                </div>
                <div class="battery wp40 ml32 mr20">{{item.battery}}</div>
                <div class="address flex1 ml12 text-ellipsis" :title="item.address">{{ item.address }}</div>
                <div class="task-status flex1 ml12 text-ellipsis" :title="item.taskStatus">{{ item.taskStatus }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="btns m10 mb20 mt6 flx-align-center" style="margin-top: 0 !important;">
          <el-button type="primary" class="flex1">设为优先返航点</el-button>
          <el-button type="primary" class="flex1 error">紧急召回</el-button>
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
  name: 'PopupUavPort',
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
  data() {
    return {
      uavInfo : {
        uavList: [
          {
            name: '无人机1',
            status: '充电中',
            battery: '98%',
            address: '123456',
            taskStatus: '待命',
          },
          {
            name: '无人机2',
            status: '外出中',
            battery: '80%',
            address: '654321',
            taskStatus: '14:00巡逻',
          },
          {
            name: '无人机3',
            status: '空闲中',
            battery: '90%',
            address: '112233',
            taskStatus: '待命',
          },
        ]
      },
    }
  }
}
</script>