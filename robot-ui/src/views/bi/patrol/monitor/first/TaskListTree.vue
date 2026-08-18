<template>
  <div class="w100 flex1" style="min-height: 0">
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
                :key="item.robotId"
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
                  <template v-if="showBattery(item)">
                    <span class="wp36 tar">{{ robotBaseInfo[item.robotId]?.battery }}%</span>
                    <svg-icon
                      class="ml10 battery-svg"
                      :icon-class="robotBaseInfo[item.robotId]?.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : robotBaseInfo[item.robotId]?.battery >= 50 ? 'battery-2' : robotBaseInfo[item.robotId]?.battery >= 40 ? 'battery-1' : 'battery-0'"
                      :style="{ color: robotBaseInfo[item.robotId]?.battery < 50 ? '#D33333' : '#3DB56A' }"
                    >
                    </svg-icon>
                  </template>
                  <svg-icon
                    v-if="isRobotFault(item)"
                    class="ml10 warning-svg"
                    icon-class="warning"
                    title="故障"
                    style="color: #FFDD00 !important; font-size: 14px; cursor: default;"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="task-div1 mt10 common-scroll pr10" style="height: calc(100% - 119px); margin-right: -10px;">
        <div class="task-list" v-if="taskData1?.length">
          <div
            v-for="(item, index) in taskData1"
            :key="item.taskId"
            class="task-item p10 curp"
            :class="{ 'is-active': String(item.taskId) === String(selectedTaskId) }"
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
                  orange: item.status === 'waiting',
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
                :key="equipment.robotId || equipment.name"
                class="item flx-justify-between"
                :class="{ 'is-active': isRobotChecked(equipment.robotId) }"
                :draggable="canDragTaskEquipment(equipment)"
                @dragstart.stop="onTaskEquipmentDragStart($event, equipment)"
                @dragend="onDragEnd"
                @click.stop="handleClickRobot(getTaskEquipmentRobot(equipment))"
                :style="{ cursor: canDragTaskEquipment(equipment) ? 'grab' : (getTaskEquipmentStatus(equipment) === 'offline' ? 'not-allowed' : 'default') }"
              >
                <div class="flx-center">
                  <svg-icon :icon-class="ROBOT_TYPE_INFO[getTaskEquipmentRobot(equipment)?.type || equipment.type]?.icon || 'robot'" />
                  <span class="ml10">{{ getTaskEquipmentRobot(equipment)?.name || equipment.name }}</span>
                </div>
                <div class="flx-center">
                  <svg-icon
                    v-if="isRobotFault(getTaskEquipmentRobot(equipment))"
                    class="mr6"
                    icon-class="warning"
                    title="故障"
                    style="color: #FFDD00; font-size: 14px"
                  />
                  <svg-icon
                    :icon-class="robotBaseInfo[equipment.robotId]?.battery >= 90 ? 'battery-4' : robotBaseInfo[equipment.robotId]?.battery >= 80 ? 'battery-3' : robotBaseInfo[equipment.robotId]?.battery >= 50 ? 'battery-2' : robotBaseInfo[equipment.robotId]?.battery >= 40 ? 'battery-1' : 'battery-0'"
                    :style="{ color: robotBaseInfo[equipment.robotId]?.battery < 50 ? '#D33333' : '#3DB56A' }"
                  >
                  </svg-icon>
                  <span class="ml4 battery wp30">{{ robotBaseInfo[equipment.robotId]?.battery || 0 }}%</span>  
                  <span class="status ml10 p4" :class="robotBaseInfo[equipment.robotId]?.statusClass">{{ robotBaseInfo[equipment.robotId]?.customStatusName || robotBaseInfo[equipment.robotId]?.status || '-' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <Empty v-else width="126px" :opacity="0.7" textColor="#BEE1FF" text="暂无运行中的任务计划" />
      </div>
    </div>
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex'
import { onDragStart, onDragEnd } from '../../../../../store/modules/dragVideo';
import { ROBOT_TYPE_INFO } from '../../../../../constants/robot';
import { getDescArr } from '../../../../../utils';
import Empty from '../../../components/Empty.vue';
export default {
  name: 'TaskListTree',
  components: { Empty },
  props: {
    updateVideoHandler: {
      type: Function,
      default: null
    },
    syncTaskVideos: {
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
      appliedRouteTaskId: '',
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
    },
    tasks() {
      return getDescArr(this.taskData || {}, 'timestamp')
    },
    taskData1() {
      // 只显示执行中和暂停中的任务；路由自动选中的任务不在过滤结果里时仍展示，保证高亮可见
      const list = this.tasks.filter(item => ['running', 'paused'].includes(item.status)) || []
      const selected = this.resolveTask(this.selectedTaskId)
      if (selected?.taskId && !list.some(item => String(item.taskId) === String(selected.taskId))) {
        return [selected, ...list]
      }
      return list
    }
  },
  mounted() {
    this.scheduleRouteTaskPlay()
  },
  activated() {
    this.scheduleRouteTaskPlay()
  },
  methods: {
    ...mapActions('dragVideo', ['setSplitType']),
    resolveTask(taskId) {
      if (taskId === undefined || taskId === null || taskId === '') return null
      const data = this.taskData || {}
      return data[taskId]
        || data[String(taskId)]
        || (Number.isNaN(Number(taskId)) ? null : data[Number(taskId)])
        || Object.values(data).find(item => String(item?.taskId) === String(taskId))
        || null
    },
    splitTypeForCount(count) {
      const n = Math.max(Number(count) || 0, 1)
      return [1, 4, 6, 9].find(item => item >= n) || 9
    },
    getTaskRobotIds(task) {
      const equipmentList = Array.isArray(task?.equipmentList) ? task.equipmentList : []
      return equipmentList
        .map(item => item?.robotId || item?.id)
        .filter(id => id !== undefined && id !== null && id !== '')
        .map(id => String(id))
    },
    isRobotOnline(robotId) {
      return this.resolveRobotStatus(robotId) !== 'offline'
    },
    isRobotFault(robotOrId) {
      const status = typeof robotOrId === 'object'
        ? robotOrId?.status
        : this.resolveRobotStatus(robotOrId)
      return status === 'fault'
    },
    showBattery(item) {
      return item.battery != null && item.battery !== ''
    },
    resolveRobotStatus(robotId) {
      if (robotId === undefined || robotId === null || robotId === '') return ''
      const targetId = String(robotId)
      return this.robotBaseInfo?.[robotId]?.status
        || this.robotBaseInfo?.[targetId]?.status
        || (this.robots || []).find(item => String(item.robotId) === targetId)?.status
        || ''
    },
    async waitTicks(times = 1) {
      for (let i = 0; i < times; i++) {
        await new Promise(resolve => this.$nextTick(resolve))
      }
    },
    routeTaskId() {
      return this.$route.query.taskId
    },
    scheduleRouteTaskPlay() {
      const taskId = this.routeTaskId()
      if (taskId === undefined || taskId === null || taskId === '') return
      this.hasLoad = false
      this.executePlay()
    },
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
      const routeTaskId = this.routeTaskId()
      if (routeTaskId !== undefined && routeTaskId !== null && routeTaskId !== '') {
        await this.applyRouteTaskSelection()
        return
      }
      const onlineList = this.equipmentInfo.online.list || []
      if (this.hasLoad || !onlineList.length) return
      this.hasLoad = true
      this.setSplitType(this.splitTypeForCount(onlineList.length))
      await this.waitTicks(2)
      for (const item of onlineList) {
        await this.handleClickRobot(item)
      }
    },
    async applyRouteTaskSelection() {
      const routeTaskId = this.routeTaskId()
      if (routeTaskId === undefined || routeTaskId === null || routeTaskId === '') return false
      this.tabIndex = 1
      const task = this.resolveTask(routeTaskId)
      if (!task?.taskId) return false
      if (this._applyingRouteTask) {
        this._pendingRouteTask = true
        return false
      }

      const robotIds = this.getTaskRobotIds(task)
      const onlineIds = robotIds.filter(id => this.isRobotOnline(id))
      const playIds = onlineIds.length ? onlineIds : robotIds
      const alreadySelected = String(this.selectedTaskId) === String(task.taskId)
      const playing = new Set((this.checkedRobotIds || []).map(id => String(id)))
      const needPlay = playIds.filter(id => this.isRobotOnline(id) && !playing.has(String(id)))
      if (alreadySelected && !needPlay.length && String(this.appliedRouteTaskId) === String(task.taskId)) {
        return true
      }

      this._applyingRouteTask = true
      try {
        this.setSplitType(this.splitTypeForCount(playIds.length || 1))
        await this.waitTicks(2)
        await this.handleSelectTask(task, { force: true, robotIds: playIds })
        this.appliedRouteTaskId = String(task.taskId)
        this.hasLoad = true
        return true
      } finally {
        this._applyingRouteTask = false
        if (this._pendingRouteTask) {
          this._pendingRouteTask = false
          this.executePlay()
        }
      }
    },
    async handleClickRobot(item) {
      if (!item?.robotId) return
      // console.log('this.splitType===========handleClickRobot', this.splitType);
      if (this.splitType === 1 || this.splitType !== this.checkedRobotIds.length) {
        // console.log('------------------------------------handleClickRobot----------------------------------------', item.status, this.equipmentInfo.online.list.find(e => e.robotId === item.robotId).status);
        
        await this.updateVideo(item)
      }
    },
    isRobotChecked(robotId) {
      if (robotId === undefined || robotId === null || robotId === '') return false
      const targetId = String(robotId)
      return this.checkedRobotIds.some(id => String(id) === targetId)
    },
    getTaskEquipmentRobot(equipment) {
      if (!equipment) return null
      const robotId = equipment.robotId || equipment.id
      if (robotId === undefined || robotId === null || robotId === '') return equipment
      return this.robotBaseInfo?.[robotId]
        || this.robotBaseInfo?.[String(robotId)]
        || (this.robots || []).find(item => String(item.robotId) === String(robotId))
        || { ...equipment, robotId }
    },
    getTaskEquipmentStatus(equipment) {
      const robot = this.getTaskEquipmentRobot(equipment)
      return robot?.status || equipment?.status || ''
    },
    /** 与装备列表一致：未在播放中且非离线才可拖 */
    canDragTaskEquipment(equipment) {
      const robot = this.getTaskEquipmentRobot(equipment)
      if (!robot?.robotId) return false
      if (this.isRobotChecked(robot.robotId)) return false
      return this.getTaskEquipmentStatus(equipment) !== 'offline'
    },
    onTaskEquipmentDragStart(event, equipment) {
      const robot = this.getTaskEquipmentRobot(equipment)
      if (!robot?.robotId || !this.canDragTaskEquipment(equipment)) {
        event.preventDefault()
        return
      }
      onDragStart(event, robot, 'equipmentListComponent')
    },
    refreshEquipmentLists() {
      const newRobots = this.robots || []
      if (!newRobots.length) {
        this.equipmentInfo.online.list = []
        this.equipmentInfo.offline.list = []
        const taskId = this.routeTaskId()
        if (taskId !== undefined && taskId !== null && taskId !== '') {
          this.executePlay()
        }
        return
      }
      const onlineList = []
      const offlineList = []
      newRobots.forEach(item => {
        const robot = {
          ...item,
          ...(this.robotBaseInfo?.[item.robotId] || {})
        }
        // fault 计入在线装备
        if (robot.status !== 'offline') {
          onlineList.push(robot)
        } else {
          offlineList.push(robot)
        }
      })
      this.equipmentInfo.online.list = onlineList
      this.equipmentInfo.offline.list = offlineList
      this.executePlay()
    },
    async updateVideo(robot) {
      // console.log('of this.updateVideoHandler', typeof this.updateVideoHandler);
      
      if (typeof this.updateVideoHandler === 'function') {
        await this.updateVideoHandler(robot)
        return
      }
      await this.updateVideoHandler(robot)
    },
    /**
     * 任务卡片点击：
     * - 切换任务：共用装备视频复用；非共用装备关闭旧的、打开新的
     * - 再次点击已选任务：取消选中，并关闭全部装备视频
     */
    async handleSelectTask(task, options = {}) {
      if (!task?.taskId) return
      if (typeof this.syncTaskVideos !== 'function') return

      const force = Boolean(options.force)
      // 取消选中：关闭当前所有装备视频（路由自动选中时不切换）
      if (!force && String(this.selectedTaskId) === String(task.taskId)) {
        this.selectedTaskId = ''
        this.selectedEquipmentList = []
        await this.syncTaskVideos([])
        return
      }

      const equipmentList = Array.isArray(task.equipmentList) ? task.equipmentList : []
      const robotIds = Array.isArray(options.robotIds)
        ? options.robotIds.map(id => String(id)).filter(Boolean)
        : equipmentList
          .map(item => item?.robotId || item?.id)
          .filter(id => id !== undefined && id !== null && id !== '')
          .map(id => String(id))
          .slice(0, this.splitType)
      const idSet = new Set(robotIds)

      this.tabIndex = 1
      this.selectedTaskId = task.taskId
      this.selectedEquipmentList = equipmentList.filter(item => idSet.has(String(item?.robotId || item?.id)))
      await this.syncTaskVideos(robotIds)
    },
  },
  watch: {
    '$route.query.taskId'() {
      this.appliedRouteTaskId = ''
      this.scheduleRouteTaskPlay()
    },
    taskData: {
      handler() {
        const taskId = this.routeTaskId()
        if (taskId === undefined || taskId === null || taskId === '') return
        if (String(this.appliedRouteTaskId) === String(this.resolveTask(taskId)?.taskId || '')) return
        this.executePlay()
      },
      deep: true
    },
    activeCameras: {
      handler(newVal) {
      },
      deep: true
    },
    robots: {
      handler() {
        this.refreshEquipmentLists()
      },
      deep: true,
      immediate: true
    },
    robotBaseInfo: {
      handler() {
        this.refreshEquipmentLists()
      },
      deep: true
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
