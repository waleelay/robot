<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="添加任务"
  >
    <template slot="footer"></template>
    <div class="flex mb20 ml72">
      <div class="custom-modal-container add-task-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="title ml10">添加新任务</div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt10 pr20 pb20 pl20 flx-justify-between">
            <div class="flex1 task">
              <div class="second-title">选择预设任务</div>
              <div class="mt10 pr4 list">
                <div v-for="item in taskInfo.taskList" :key="item.name" class="item p20 flx-justify-between" style="align-items: flex-start;" :class="{ selected: taskInfo.selectedTaskRows.includes(item) }" @click="handleClickTaskRow(item)">
                  <div>
                    <div class="name">{{ item.name }}</div>
                    <div class="flx-center mt10">
                      <div> 
                        <svg-icon icon-class="time"></svg-icon>
                        <span class="ml10">{{ item.time }}</span>
                      </div>
                      <div class="ml10">
                        <svg-icon icon-class="address"></svg-icon>
                        <span class="ml10">{{ item.address }}</span>
                      </div>
                    </div>
                  </div>
                  <div :class="'p4 status ' + (item.status === 0 ? 'primary' : 'purple')">{{ item.status === 0 ? '单设备任务' : '多设备任务' }}</div>
                </div>
              </div>
            </div>
            <div class="flex1 robot">
              <div class="flx-justify-between">
                <div class="second-title">执行装备列表</div>
                <div class="selected-info flx-align-center">
                  <template v-if="taskInfo.selectedRobotRows.length !== taskInfo.count">
                    <svg-icon icon-class="info"></svg-icon>
                    <span class="ml6">当前{{ taskInfo.selectedRobotRows.length }}/{{ taskInfo.count }}台</span>
                  </template>
                  <template v-else>
                    <svg-icon icon-class="success-l" style="color: #0BF9FE;"></svg-icon>
                    <span class="ml6" style="color: #0BF9FE;">准备就绪</span>
                  </template>
                </div>
              </div>
              <div class="mt10" style="position: relative;">
                <div class="pb82 list p20" style="padding-right: 16px !important;">
                  <div v-for="(item, index) in taskInfo.robotList" :key="item.name">
                    <div class="item pt9 pr10 pb9 pl10 flx-justify-between" :class="{ selected: taskInfo.selectedRobotRows.includes(item) || (index === 0 && taskInfo.fromMain) }" @click="handleClickRobotRow(item, index)">
                      <div class="flx-align-center">
                        <span class="p4 flx-center symbol">
                          <svg-icon icon-class="robot"></svg-icon>
                        </span>
                        <div class="ml10">
                          <div class="name">{{ item.name }}</div>
                          <div class="flx-align-center mt4"> 
                            <svg-icon
                              :icon-class="item.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : item.battery >= 50 ? 'battery-2' : item.battery >= 40 ? 'battery-1' : 'battery-0'"
                              :style="{ color: item.battery < 50 ? '#D33333' : '#3DB56A' }"
                            >
                            </svg-icon>
                            <span class="ml4">{{ item.battery }}%</span>
                          </div>
                        </div>
                      </div>
                      <div class="selected-symbol">
                        <svg-icon icon-class="success"></svg-icon>
                      </div>
                    </div>
                    <div v-if="index === 0 && taskInfo.fromMain">
                      <div class="w100 hp1 mt10 mb10" style="background: #1B2839;"></div>
                      <div style="color: #D0DEEE; font-size: 14px; line-height: 19px">选择空闲装备</div>
                    </div>
                  </div>
                </div>
                <div class="btns flx-center">
                  <el-button v-if="taskInfo.selectedRobotRows.length !== taskInfo.count" type="primary" :disabled="true" class="w100 flx-center is-disabled">还需选择{{ taskInfo.count - taskInfo.selectedRobotRows.length }}台设备</el-button>
                  <el-button v-else type="primary" class="w100 flx-center">立即执行任务</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <!-- <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div> -->
      </div>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'TaskAdd',
  data() {
    return {
      dialogVisible: false,
      taskInfo: {
        taskList: [
          {
            name: '全域核心区域安全巡检',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 0,
          },
          {
            name: '监狱上空A区多维度监控',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 1,
          },
          {
            name: '夜间巡逻2号大门',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 0,
          },
        ],
        robotList: [
          {
            name: '绝影X30PRO',
            battery: 60,
          },
          {
            name: '绝影002',
            battery: 60,
          },
          {
            name: '绝影003',
            battery: 60,
          },
          {
            name: '绝影004',
            battery: 30,
          },
          {
            name: '绝影005',
            battery: 20,
          },
        ],
        count: 3,
        selectedTaskRows: [],
        selectedRobotRows: [],
        fromMain: true
      },
    }
  },
  methods: {
    showModal() {
      this.dialogVisible = true;
    },
    handleClickTaskRow(item, index) {
      // if (this.taskInfo.selectedTaskRows.includes(item)) {
      //   this.taskInfo.selectedTaskRows.splice(this.taskInfo.selectedTaskRows.indexOf(item), 1);
      // } else {
      //   this.taskInfo.selectedTaskRows.push(item);
      // }
      this.taskInfo.selectedTaskRows = [item];
    },
    handleClickRobotRow(item, index) {
      if (this.taskInfo.selectedRobotRows.includes(item)) {
        this.taskInfo.selectedRobotRows.splice(this.taskInfo.selectedRobotRows.indexOf(item), 1);
      } else {
        this.taskInfo.selectedRobotRows.push(item);
      }
    }
  }
}
</script>