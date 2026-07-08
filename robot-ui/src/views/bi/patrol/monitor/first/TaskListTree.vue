<template>
  <div class="w100 hp500">
    <div class="card-title">
      <div class="text">
        视频数据源
      </div>
    </div>
    <!-- common-scroll -->
    <div class="tab-content mt10 pr10 pb10 pl10" style="height: calc(100% - 47px); min-height: 452px;">
      <div class="task-div-tab mt20 mb20 flx-center">
        <div
          class="tab-item flex1"
          v-for="(item, index) in tabList"
          :key="item"
          :class="{ 'is-active': tabIndex === index }"
          @click="tabChange(index)"
        >
          {{ item }}
        </div>
      </div>
      <div class="custom-search-div">
        <el-input
          placeholder="请输入内容"
          v-model="searchValue">
          <svg-icon slot="prefix" icon-class="search"></svg-icon>
        </el-input>
      </div>
      <div v-if="tabIndex === 0" class="equipment-div mt10 common-scroll" style="height: calc(100% - 119px); margin-right: -10px;">
        <div class="collapse-box pl26 h100">
          <div
            v-for="(equipment, typeIndex) in Object.values(equipmentInfo)"
            :key="equipment.type"
            class="custom-collapse-div"
            :class="{ collapse: collapseArr[typeIndex] }"
          >
            <div class="collapse-header p10 flx-justify-between" @click="toggleCollapse(typeIndex)">
              <div class="flx-center">
                <svg-icon :icon-class="equipment.type.includes('在线') ? 'open-wifi' : 'close-wifi'" />
                <span class="ml10">{{ equipment.type }}({{ equipment.list.length }})</span>
              </div>
              <svg-icon :icon-class="collapseArr[typeIndex] ? 'down' : 'up'" style="color: #6A788B" />
            </div>
            <div class="collapse-content pl12 common-scroll mr10 pr7">
              <div
                v-for="(item, index) in equipment.list"
                :key="item.name"
                class="item flx-justify-between"
                :class="{ 'is-active': checkedRobotIds.includes(item.robotId) }"
                :draggable="!checkedRobotIds.includes(item.robotId) && item.status !== 'offline'"
                @dragstart="onDragStart($event, item, 'equipmentListComponent')"
                @dragend="onDragEnd"
                @click="item.status !== 'offline' ? handleClickRobot(item) : ''"
                :style="{ cursor: (item.status !== 'offline' && !checkedRobotIds.includes(item.robotId)) ? 'grab' : item.status === 'offline' ? 'no-allowed' : 'default' }"
              >
                <!-- @click="handleSelectEquipment(item)" -->
                <div class="flx-center">
                  <svg-icon :icon-class="ROBOT_TYPE_INFO[item.type]?.icon || 'robot'" />
                  <span class="ml10">{{ item.name }}</span>
                </div>
                <div v-if="equipment.type.includes('在线')" class="flx-center">
                  <svg-icon
                    class="battery-svg"
                    :icon-class="robotBaseInfo[item.robotId]?.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : robotBaseInfo[item.robotId]?.battery >= 50 ? 'battery-2' : robotBaseInfo[item.robotId]?.battery >= 40 ? 'battery-1' : 'battery-0'"
                    :style="{ color: robotBaseInfo[item.robotId]?.battery < 50 ? '#D33333' : '#3DB56A' }"
                  >
                  </svg-icon>
                  <span class="ml10 wp36 tar">{{ robotBaseInfo[item.robotId]?.battery }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="task-div1 mt10 common-scroll pr10" style="height: calc(100% - 119px); margin-right: -10px;">
        <div class="task-list">
          <div
            v-for="(item, key, index) of taskData || {}"
            :key="index"
            class="task-item p10"
            :class="{ 'is-active': item.taskId === selectedTaskId }"
            @click="handleSelectTask(item)"
            >
            <div class="flx-justify-between title">
              <div class="flx-align-center">
                <span class="location" style="font-size: 16px;">{{ item.currentLocation }}</span>-
                <span>{{ item.name }}</span>
              </div>
              <span
                class="status p4 wp50 text-ellipsis"
                :class="{
                  green: item.status === 'running',
                  orange: item.status === 'pending',
                  blue: item.status === 'completed',
                  red: item.status?.includes('failed'),
                  gray: item.status === 'paused'
                }">{{ item.statusName }}</span>
            </div>
            <div class="info mt10">
              <div class="flx-align-center">
                <svg-icon icon-class="time" />
                <span class="ml10">{{ item.timeRange }}（预计{{ item.duration }}分钟）</span>
              </div>
              <div class="flx-align-center mt6">
                <svg-icon icon-class="location" />
                <span class="ml10">{{ item.currentLocation }}</span>
              </div>
              <div class="flx-align-center mt6">
                <span>执行装备（{{ item.equipmentList?.length }}）</span>
              </div>
            </div>
            <div class="device mt10" v-if="item.equipmentList?.length">
              <div
                v-for="equipment in item.equipmentList"
                :key="equipment.name"
                class="item flx-justify-between"
                :class="{ 'is-active': checkedRobotIds.includes(equipment.robotId) }"
                :draggable="!checkedRobotIds.includes(equipment.robotId) && equipment.status === 'online'"
                @dragstart="onDragStart($event, robotBaseInfo[equipment.robotId], 'equipmentListComponent')"
                @dragend="onDragEnd"
                @click="handleClickRobot(robotBaseInfo[equipment.robotId])"
                :style="{ cursor: !checkedRobotIds.includes(equipment.robotId) ? 'grab' : 'default' }"
              >
                <!-- @click="handleSelectEquipment(equipment)" -->
                <div class="flx-center">
                  <svg-icon :icon-class="ROBOT_TYPE_INFO[equipment.type]?.icon || 'robot'" />
                  <span class="ml10">{{ equipment.name }}</span>
                </div>
                <div class="flx-center">
                  <svg-icon
                    :icon-class="robotBaseInfo[equipment.robotId]?.battery >= 90 ? 'battery-4' : robotBaseInfo[equipment.robotId]?.battery >= 80 ? 'battery-3' : robotBaseInfo[equipment.robotId]?.battery >= 50 ? 'battery-2' : robotBaseInfo[equipment.robotId]?.battery >= 40 ? 'battery-1' : 'battery-0'"
                    :style="{ color: robotBaseInfo[equipment.robotId]?.battery < 50 ? '#D33333' : '#3DB56A' }"
                  >
                  </svg-icon>
                  <span class="ml4 battery wp30">{{ robotBaseInfo[equipment.robotId]?.battery }}%</span>  
                  <span class="status ml10 p4" :class="robotBaseInfo[equipment.robotId]?.statusClass">{{ robotBaseInfo[equipment.robotId]?.customStatusName || robotBaseInfo[equipment.robotId]?.status || '-' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState, mapActions, mapGetters } from 'vuex'
import { onDragStart, onDragEnd } from '../../../../../store/modules/dragVideo';
import { ROBOT_TYPE_INFO } from '../../../../../constants/robot';
export default {
  name: 'TaskListTree',
  props: {
    updateVideoHandler: {
      type: Function,
      default: null
    }
  },
  data() {
    return {
      tabList: ['装备列表', '任务列表'],
      tabIndex: this.$route.query.taskId !== undefined ? 1 : 0,
      // tabIndex: 1,
      searchValue: '',
      selectedTaskId: '',
      equipmentInfo: {
        online: {
          type: '在线装备',
          list: []
        },
        // unAssociation: {
        //   type: '未关联装备',
        //   list: []
        // },
        // pending: {
        //   type: '任务中装备',
        //   list: []
        // },
        offline: {
          type: '离线装备',
          list: []
        }
      },
      collapseArr: [],
      selectedEquipmentList2: [],
      hasLoad: false,
      ROBOT_TYPE_INFO,
    }
  },
  computed: {
    ...mapState('dragVideo', ['dropResult', 'splitType']),
    ...mapState('websocketExtraData', ['robotBaseInfo', 'taskData']),
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
    // 获取基础信息
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    checkedRobotIds() {
      return [...new Set(Object.values(this.activeCameras).map(item => item.robot.robotId))];
    }
  },
  mounted() {},
  methods: {
    ...mapActions('dragVideo', ['dragStart','setSplitType']),
    tabChange(index) {
      this.tabIndex = index
      // if (index && this.taskList.length) {
      //   let task = this.taskList.filter(item => item.id === this.selectedTaskId)[0]
      //   const taskItem = task || this.taskList[0]
      //   this.selectedTaskId = taskItem.id
      //   this.selectedEquipmentList2 = taskItem.robots.map(item => item.name).slice(0, this.splitType)
      // }
      // this.$emit('select-task', index ? this.selectedEquipmentList2 : [])
    },
    toggleCollapse(typeIndex) {
      this.$set(this.collapseArr, typeIndex, !this.collapseArr[typeIndex])
    },
    // 拖拽开始: 将任务数据存入 dataTransfer
    onDragStart,
    onDragEnd,
    async executePlay() {
      const onlineList = this.equipmentInfo.online.list || []
      if (this.hasLoad || !onlineList.length) return
      this.hasLoad = true
      this.setSplitType([1, 4, 6, 9].filter(item => item >= onlineList.length)?.[0] || 9)
      if (this.$route.query.taskId !== undefined) {
        await this.handleSelectTask(this.taskData[this.$route.query.taskId])
      } else {
        for (const item of onlineList) {
          await this.handleClickRobot(item)
        }
      }
    },
    async handleClickRobot(item) {
      if (this.splitType === 1 || this.splitType !== this.checkedRobotIds.length) {
        await this.updateVideo(item)
      }
    },
    async updateVideo(robot) {
      if (typeof this.updateVideoHandler === 'function') {
        await this.updateVideoHandler(robot)
        return
      }
      this.$emit('updateVideo', robot)
    },
    
    async handleSelectTask(task) {
      this.selectedTaskId = task.taskId
      // 获取新旧设备列表的 robotId 集合
      const newIds = new Set(task.equipmentList.slice(0, this.splitType).map(item => item.robotId))

      // 找出需要关闭的设备（旧有但新列表中没有的）
      const closeIds = [...this.checkedRobotIds].filter(id => !newIds.has(id))
      const addIds = [...newIds].filter(id => !this.checkedRobotIds.includes(id))

      // 关闭对应的视频
      for (const id of [...closeIds, ...addIds]) {
        console.log(123, id)
        const robot = this.robots.find(e => e.robotId === id)
        if (robot) {
          await this.updateVideo(robot)
        }
      }
      // 更新选中设备列表
      this.selectedEquipmentList = task.equipmentList.slice(0, this.splitType);
    },
  },
  watch: {
    activeCameras: {
      handler(newVal) {
      },
      deep: true
    },
    robots: {
      handler(newRobots) {        
        if (!newRobots.length) {
          this.equipmentInfo.online.list = []
          this.equipmentInfo.offline.list = []
          return
        }
        const onlineList = []
        const offlineList = []
        newRobots.map(item => {
          if (this.robotBaseInfo?.[item.robotId]?.status === 'online') {
            onlineList.push(this.robotBaseInfo?.[item.robotId])
          } else {
            offlineList.push(this.robotBaseInfo?.[item.robotId])  
          }
        })
        this.equipmentInfo.online.list = onlineList
        this.equipmentInfo.offline.list = offlineList
        this.executePlay()
      },
      deep: true,
      immediate: true
    }
  }
}
</script>
<style lang="scss" scoped>
.tab-content {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
  border: 1px solid #005BB5;
  box-shadow: 0 0 20px 0 rgba(0, 166, 255, 0.50) inset;
}
</style>
