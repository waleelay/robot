<template>
  <div class="left-div pr28 h100 mt25 no-w-scroll" :class="{ 'ml20': !collapse, 'ml10': collapse }" :style="{ 'pointer-events': sidebarPointerEvents, maxHeight: 'calc(100% - 50px)', overflowY: 'auto' }">
    <div class="container flex-column w100 h100 common-scroll" style="flex-wrap: nowrap;">
      <!--  :class="{'hp264': deviceTypeStats?.length, 'hp155': !deviceTypeStats?.length}" -->
      <div class="box bi-corner-box hp264">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="getMoreRobotInfo">
          <span class="desc">装备类型</span>
          <!-- <span class="flx-center more curp">
            <span>更多</span>
            <svg-icon :icon-class="collapseArr[0] ? 'right' : 'down'" class="ml4" />
          </span> -->
        </div>
        <div class="pt20 pr18 pb20 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 flex1 pt9 pr5 pb5 pl9">
              <div class="desc">总数</div>
              <div class="value mt4">{{ deviceStats?.total ? String(deviceStats?.total).padStart(2, '0') : '0' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">在线</div>
              <div class="value mt4">{{ deviceStats?.online ? String(deviceStats?.online).padStart(2, '0') : '0' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">故障</div>
              <div class="value mt4">{{ deviceStats?.fault ? String(deviceStats?.fault).padStart(2, '0') : '0' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">离线</div>
              <div class="value mt4">{{ deviceStats?.offline ? String(deviceStats?.offline).padStart(2, '0') : '0' }}</div>
              <div class="tar hp16" style="margin-top: -4px">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
          </div>
          <div class="mt20">
            <div v-if="deviceTypeStats?.length" class="t2">设备类型</div>
            <div class="device_types mt10 flx-justify-between">
              <div
                v-for="(item, index) in deviceTypeStats || []"
                :key="item.type"
                class="item flex1 hp62"
                :class="{
                  'ml10': index !== 0,
                  'p9': !(item.name && item.name.length > 4 && deviceTypeStats.length > 4),
                  'is-long-name': item.name && item.name.length > 4 && deviceTypeStats.length > 4
                }"
              >
                <div class="desc">{{ item.name }}</div>
                <div class="value mt4">{{ item.count ? String(item.count).padStart(2, '0') : '-' }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="box bi-corner-box mt20 task" :class="{ 'no_data hp41': collapseArr[1], 'hp323': !collapseArr[1] }">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="toggleCollapse('collapseArr', 1)">
          <div class="flx-center">
            <span class="desc">任务列表</span>
            <div v-if="taskData1.length" class="ml4 notice pr10 pl10">{{ taskData1.length ? taskData1.length > 99 ? '99+' : taskData1.length : '-'  }}</div>
          </div>
          <!-- <span class="flx-center more curp">
            <span>更多</span>
            <svg-icon :icon-class="collapseArr[1] ? 'right' : 'down'" class="ml4" />
          </span> -->
        </div>
        <div ref="taskListRef" class="list pt10 pr20 pl20 mb20 common-scroll ovya" :style="{ height: collapseArr[2] ? '300px' : '262px' }">
          <template v-if="taskData1.length">
            <div
              v-for="(item, index) in taskData1 || []"
              :key="item.taskId"
              :data-task-id="item.taskId"
              class="item wp288 curp"
              :class="{
                'is-active': activeTaskId == item.taskId,
                'mb10': index !== taskData1.length - 1
              }"
              @click="selectTask(item.taskId)"
            >
              <div class="header flx-justify-between p10">
                <div class="flx-align-center flex1" style="min-width: 0">
                  <svg-icon icon-class="d-right"></svg-icon>
                  <span class="ml4 text-ellipsis" :title="item.name">{{ item.name }}</span>
                </div>
                <span class="status flx-center pt2 pr6 pb2 pl6 ml10" :class="getTaskStatusName(item.status)">
                  <svg-icon icon-class="security"></svg-icon>
                  <span class="ml4">{{ executionStatusLabel(item.status, '-') }}</span>
                </span>
              </div>
              <div class="desc">
                <div>
                  <template v-if="isManualWaitingTask(item)">执行方式：手动执行</template>
                  <template v-else-if="isScheduleWaitingTask(item)">计划开始时间：{{ item.startTime || '-' }}</template>
                  <template v-else>任务开始时间：{{ item.startTime || '-' }}</template>
                </div>
                <div class="flx-align-center">
                  <span class="wp150">预计时长：{{ formatEstimatedDuration(item.expectedDurationSeconds) }}</span>
                  <!-- <span class="ml20">执行装备：{{ item.equipmentList?.length || 0 }}台</span> -->
                </div>
                <div>执行装备：{{ item.equipmentList?.length || 0 }}台</div>
                <!-- <div class="text-ellipsis">当前位置：{{ item.currentLocation || '-' }}</div> -->
              </div>
              <!-- 执行中：详情 / 暂停/恢复 / 定位装备 / 终止 / 播放视频 -->
              <div v-if="item.status === 'running' || item.status === 'paused'" class="task-actions">
                <button type="button" class="action-btn action-detail" @click.stop="handleTaskDetail(item)">
                  <span>详情</span>
                  <svg-icon icon-class="right" class="ml4" />
                </button>
                <button
                  type="button"
                  class="action-btn action-icon wp30"
                  :disabled="isActingTaskRecord(item)"
                  :title="item.status === 'paused' ? '恢复' : '暂停'"
                  @click.stop="item.status === 'paused' ? handleResumeTask(item) : handlePauseTask(item)"
                >
                  <svg-icon :icon-class="item.status === 'paused' ? 'play' : 'pause'" />
                </button>
                <button
                  type="button"
                  class="action-btn action-icon wp30"
                  title="定位执行此任务的装备"
                >
                  <svg-icon icon-class="map-location" style="font-size: 16px;" />
                </button>
                <button
                  type="button"
                  class="action-btn action-icon wp30"
                  title="终止任务"
                  :disabled="isActingTaskRecord(item)"
                  @click.stop="handleTerminateTask(item)"
                >
                  <svg-icon icon-class="close1" />
                </button>
                <!-- <button
                  type="button"
                  class="action-btn action-video"
                  :class="{ 'is-active': activeTaskId == item.taskId || activeTaskId == key }"
                  @click.stop="handleClickTask(key)"
                >
                  <svg-icon icon-class="play" />
                </button> -->
                <div class="symbol wp36 hp28" @click.stop="openTaskVideo(item.taskId)">
                  <!-- <img :src="require(`../../../../assets/images/new-bi/camera-${activeTaskId == item.taskId ? 'active' : 'off1'}.png`)" class="w100 h100" alt="" srcset="" /> -->
                  <img :src="require(`../../../../assets/images/new-bi/camera${activeTaskId == item.taskId ? '2' : '1'}.png`)" class="w100 h100" alt="" srcset="" />
                </div>
              </div>
              <!-- 待执行：立即执行 -->
              <div v-else-if="item.status === 'waiting'" class="task-actions">
                <button
                  type="button"
                  class="action-btn action-execute"
                  :disabled="isStartingTask(item)"
                  @click.stop="handleExecuteTask(item)"
                >
                  <span>立即执行</span>
                  <svg-icon icon-class="right" class="ml4" />
                </button>
              </div>
            </div>
          </template>
          <Empty v-else width="126px" :opacity="0.7" textColor="#BEE1FF" :text="`${isGisMap ? '' : '此地图'}暂无任务计划`" />
        </div>
      </div>
      <div class="box bi-corner-box mt20 alert" :class="{ 'no_data hp41': collapseArr[2], 'hp323': !collapseArr[2] }" style="max-height: 446px;">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="toggleCollapse('collapseArr', 2)">
          <span class="desc">告警中心</span>
          <span v-if="hasAlarmData" class="flx-center more curp" @click.stop="handleClickAlert()">
            <span>更多</span>
            <!-- <svg-icon :icon-class="collapseArr[2] ? 'right' : 'down'" class="ml4" /> -->
            <svg-icon icon-class="right" class="ml4" />
          </span>
        </div>
        <div v-if="alarmsData" class="mt10 ml20 common-scroll ovya mb10" :style="{ maxHeight: collapseArr[1] ? '360px' : '262px', minHeight: '146px' }">
          <div v-for="(alarm, key, alarmIndex) in alarms" :key="key" class="type wp288 pt10 pr20 pb10 pl20" :class="[alarm.class, { 'hp42 ovyh': alertCollapseArr[alarmIndex], 'mt10': alarmIndex !== 0 }]">
            <div class="type_name flx-justify-between" @click="toggleCollapse('alertCollapseArr', alarmIndex)">
              <div class="flx-center">
                <span class="symbol flx-center">
                  <svg-icon icon-class="notice1"></svg-icon>
                </span>
                <span class="ml10">{{ alarm.name || '-' }}（{{ alarmsData?.[key]?.items?.length || 0 }}）</span>
              </div>
              <span class="flx-center curp">
                <svg-icon :icon-class="alertCollapseArr[alarmIndex] ? 'down' : 'up'" style="font-size: 14px;"></svg-icon>
              </span>
            </div>
            <div class="mt20 list">
              <div v-for="(item, index) in getObjByOrder(alarmsData?.[key]?.items || [], 'eventTime', 'array')" :key="item.alarmId" class="item flx-center" :class="{ 'mt40 mb10': index !== 0 }" @click="handleClickAlert(item)">
                <div class="img wp120 hp72 flx-center"
                >
                <!-- :style="{ background: `url(${getImageUrl(item.snapshotUrl?.visible) || (item.title.includes('火灾') ? img1 : img2)}) lightgray -4.267px -11.862px / 104% 118.678% no-repeat` }" -->
                  <img :src="getImageUrl(item.snapshotUrl?.visible)" alt="" srcset="" style="width: 100%; height: 100%; object-fit: cover;">
                  <span class="alert_type wp64 text-ellipsis">{{ item.categoryName }}</span>
                </div>
                <div class="ml6 flex1" style="min-width: 0;">
                  <div class="event text-ellipsis" :title="item.title">事件：{{ item.title }}</div>
                  <div class="time mt2 flx-align-start flex">
                    <span>时间：</span>
                    <div class="flex-column">
                      <span>{{ item.eventTime.split(' ')[0] }}</span>
                      <span>{{ item.eventTime.split(' ')[1] }}</span>
                    </div>
                  </div>
                  <div class="area mt5 text-ellipsis">位置：{{ item?.location?.address || '-' }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="collapse-left flx-center" @click="$emit('changeCollapse')">
      <div class="flx-center">
        <svg-icon :icon-class="collapse ? 'right-s' : 'left-s'" />
      </div>
    </div>
    <!-- <TaskRobotView ref="taskRobotViewRef" @handleClickTask="handleClickTask" /> -->
    <TaskRobotView ref="taskRobotViewRef" />
    <WarningBatch ref="warningBatchRef" />
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex';
import {
  pauseTaskRecord,
  resumeTaskRecord,
  startTask,
  startTaskPreview,
  terminateTaskRecord
} from '../../../../api/new-bi.js';
import TaskRobotView from '../../components/modal/TaskRobotView.vue';
import WarningBatch from './warning/WarningBatch.vue'
import { getDescArr } from '../../../../utils/index.js';
import Empty from '../../components/Empty.vue';
import { executionStatusLabel } from '../business/execution-status.js';
export default {
  name: 'BiPatrolPanoramaLeft',
  components: { TaskRobotView, WarningBatch, Empty },
  props: {
    collapse: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      tabList: [
        {
          label: '今日',
          value: 0
        },
        {
          label: '本月',
          value: 1
        },
        {
          label: '当年',
          value: 2
        }
      ],
      tabIndex: 0,
      collapseArr: [false, false, false],
      alertCollapseArr: [true, true, true],
      alertList: [1],
      activeTaskId: null,
      overviewInfo: {},
      alarms: {
        high: {
          name: '高风险',
          class: 'danger'
        },
        medium: {
          name: '中风险',
          class: 'warning'
        },
        low: {
          name: '低风险',
          class: 'green'
        },
      },
      updated: false,
      // img1: require('@/assets/images/new-bi/test.png'),
      // img2: require('@/assets/images/new-bi/warning1.png'),
      startingTaskIds: [],
      actingRecordIds: [],
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    ...mapState('websocketExtraData', ['taskData', 'alarmsData', 'deviceTypeStats', 'deviceStats', 'globalMapId', 'robotBaseInfo', 'taskPathPoints']),
    /** 选中固定摄像头时不禁用侧边栏 */
    isSelectedFixedCamera() {
      if (!this.selectedRobotId) return false
      const robot = this.robotBaseInfo?.[this.selectedRobotId]
        || this.$store.getters['websocketRobot/getSelectedRobot']
        || {}
      return robot.sourceType === 'FIXED_CAMERA'
        || robot.typeCode === 'FIXED_CAMERA'
        || robot.equipmentType === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    sidebarPointerEvents() {
      return (this.selectedRobotId && !this.isSelectedFixedCamera) ? 'none' : 'auto'
    },
    // GIS 展示全部任务；SLAM 仅展示与当前地图关联的任务（地图 → 任务单向联动）
    isGisMap() {
      const id = this.globalMapId
      return !id || id === 'gis'
    },
    taskData1() {
      const all = getDescArr(this.taskData || {}, 'timestamp') || []
      if (this.isGisMap) return all
      return all.filter(task => this.isTaskLinkedToMap(task, this.globalMapId))
    },
    hasAlarmData() {
      const data = this.alarmsData || {}
      return ['high', 'medium', 'low'].some(key => (data[key]?.items || []).length > 0)
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotAlarmInfo', 'setShowRobotIds', 'loadTaskDetail']),
    executionStatusLabel,
    getImageUrl(url) {
      const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
      return `${preUrl}${url}`
    },
    /**
     * 通用的按时间属性降序排序函数
     * @param {Array|Object} data - 要排序的数据
     * @param {string} timeKey - 时间属性的键名
     * @param {string} returnType - 返回类型：'array' 或 'object'
     * @returns {Array|Object} 排序后的数据
     */
    getObjByOrder(data, timeKey = 'time', returnType = 'array') {
      let sortedData = returnType === 'object' ? {} : [];
      if (returnType === 'object') {
        sortedData = Object.assign({}, data); // 创建数据的副本以避免修改原始数据
      } else {
        sortedData = [].concat(data); // 创建数据的副本以避免修改原始数据
      }

      // 如果是数组，直接排序
      if (Array.isArray(sortedData)) {
          return sortedData.sort((a, b) => {
              const timeA = new Date(a[timeKey]).getTime();
              const timeB = new Date(b[timeKey]).getTime();
              return timeB - timeA;
          });
      }

      // 如果是对象，转换为数组排序后再转回
      if (typeof sortedData === 'object' && sortedData !== null) {
          const entries = Object.entries(sortedData);
          const sorted = entries.sort(([, a], [, b]) => {
              const timeA = new Date(a[timeKey]).getTime();
              const timeB = new Date(b[timeKey]).getTime();
              return timeB - timeA;
          });

          if (returnType === 'object') {
              return sorted.reduce((result, [key, value]) => {
                  result[key] = value;
                  return result;
              }, {});
          }

          return sorted.map(([key, value]) => ({ key, ...value }));
      }

      throw new Error('数据类型必须是数组或对象');
    },
    getTaskStatusName(status) {
      switch (status) {
        case 'running':
          return 'green'
        case 'waiting':
          return 'orange'
        case 'paused':
          return 'gray'
        default:
          return 'gray'
      }
    },
    /** 手动执行且待执行 */
    isManualWaitingTask(item) {
      return item?.executionMode === 'MANUAL' && item?.status === 'waiting'
    },
    /** 计划执行且待执行 */
    isScheduleWaitingTask(item) {
      return item?.executionMode === 'SCHEDULE' && item?.status === 'waiting'
    },
    /**
     * expectedDurationSeconds（秒）→ 时分秒展示
     * - 不足 60 秒：只显示秒
     * - 不足 60 分钟：不显示时（60 秒显示为 1 分钟）
     * - 满 60 分钟显示为 1 小时
     */
    formatEstimatedDuration(expectedDurationSeconds) {
      if (expectedDurationSeconds == null || expectedDurationSeconds === '') return '-'
      const totalSeconds = Math.floor(Number(expectedDurationSeconds))
      if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return '-'
      const hours = Math.floor(totalSeconds / 3600)
      const minutes = Math.floor((totalSeconds % 3600) / 60)
      const seconds = totalSeconds % 60
      if (hours > 0) {
        return minutes > 0 ? `${hours}小时${minutes}分钟` : `${hours}小时`
      }
      if (minutes > 0) {
        return seconds > 0 ? `${minutes}分${seconds}秒` : `${minutes}分钟`
      }
      return `${seconds}秒`
    },
    getTaskPlanId(item) {
      return item?.planId || item?.id || item?.taskId || item?.taskPlanId
    },
    getTaskRecordId(item) {
      return item?.executionRecordId
        || item?.activeWorkflowInstanceId
        || item?.workflowInstanceId
        || item?.recordId
        || item?.taskInstanceId
    },
    isStartingTask(item) {
      const planId = this.getTaskPlanId(item)
      return planId != null && this.startingTaskIds.indexOf(planId) !== -1
    },
    isActingTaskRecord(item) {
      const recordId = this.getTaskRecordId(item)
      return recordId != null && this.actingRecordIds.indexOf(recordId) !== -1
    },
    getMoreRobotInfo() {

    },
    toggleCollapse(type, typeIndex) {
      this.$set(this[type], typeIndex, !this[type][typeIndex])
    },
    getTaskRobotIds(taskId) {
      return (this.taskData[taskId]?.equipmentList || []).map(robot => robot.robotId)
    },
    resolveTaskMapId(task) {
      if (!task) return null
      if (task.mapId !== undefined && task.mapId !== null && task.mapId !== '') return task.mapId
      const key = task.taskId
      const path = this.taskPathPoints?.[key] || this.taskPathPoints?.[String(key)]
      const mapId = path && path.mapId
      if (mapId !== undefined && mapId !== null && mapId !== '') return mapId
      return null
    },
    isTaskLinkedToMap(task, mapId) {
      if (mapId === undefined || mapId === null || mapId === '' || mapId === 'gis') return true
      const taskMapId = this.resolveTaskMapId(task)
      if (taskMapId === undefined || taskMapId === null || taskMapId === '') return false
      return String(taskMapId) === String(mapId)
    },
    resolveTaskListId(taskId) {
      const list = this.taskData1 || []
      const hit = list.find(item =>
        String(item.taskId) === String(taskId) ||
        String(item.planId) === String(taskId) ||
        String(item.id) === String(taskId) ||
        String(item.taskPlanId) === String(taskId)
      )
      return hit?.taskId ?? taskId
    },
    scrollTaskCardToFront(taskId) {
      const list = this.$refs.taskListRef
      if (!list) return
      const el = list.querySelector(`[data-task-id="${String(taskId)}"]`)
      if (!el) return
      const cards = list.querySelectorAll('[data-task-id]')
      const isLast = cards.length > 0 && el === cards[cards.length - 1]
      const maxScroll = Math.max(0, list.scrollHeight - list.clientHeight)
      if (isLast) {
        list.scrollTop = maxScroll
        return
      }
      // list 已 position:relative；offsetTop 含 padding-top，减去后卡片顶边对齐内容区最上方
      const padTop = parseFloat(window.getComputedStyle(list).paddingTop) || 0
      list.scrollTop = Math.max(0, Math.min(maxScroll, el.offsetTop - padTop))
    },
    /** 地图弹窗点击任务名称：选中对应卡片并滚到列表最前（不切换取消） */
    focusTaskFromPopup(taskId) {
      if (taskId == null || taskId === '') return
      const id = this.resolveTaskListId(taskId)
      this.activeTaskId = id
      this.setShowRobotIds(this.getTaskRobotIds(id))
      this.emitFocusTaskPath(id)
      if (this.$refs.taskRobotViewRef?.dialogVisible) {
        this.$refs.taskRobotViewRef.dialogVisible = false
      }
      this.$nextTick(() => this.scrollTaskCardToFront(id))
    },
    emitFocusTaskPath(taskId) {
      this.$emit('focus-task-path', taskId == null || taskId === '' ? null : taskId)
    },
    /** 点击任务卡片：选中/取消选中卡片，并在地图上高亮相关装备（不打开视频弹窗） */
    selectTask(taskId) {
      if (this.activeTaskId == taskId) {
        this.activeTaskId = null
        this.setShowRobotIds([])
        this.emitFocusTaskPath(null)
        if (this.$refs.taskRobotViewRef) {
          this.$refs.taskRobotViewRef.dialogVisible = false
        }
        return
      }
      this.activeTaskId = taskId
      this.setShowRobotIds(this.getTaskRobotIds(taskId))
      this.emitFocusTaskPath(taskId)
      // 切换任务时关闭上一任务的视频弹窗
      if (this.$refs.taskRobotViewRef?.dialogVisible) {
        this.$refs.taskRobotViewRef.dialogVisible = false
      }
    },
    /** 点击视频图标：打开/关闭任务视频弹窗，并同步选中卡片与地图装备 */
    async openTaskVideo(taskId) {
      const dialog = this.$refs.taskRobotViewRef
      if (!dialog) return
      // 已打开同一任务弹窗时再次点击：仅关闭弹窗，保留卡片与地图选中
      if (this.activeTaskId == taskId && dialog.dialogVisible) {
        dialog.dialogVisible = false
        return
      }
      this.activeTaskId = taskId
      let taskInfo = this.taskData[taskId] || {}
      try {
        const detail = await this.loadTaskDetail(taskId)
        if (detail) taskInfo = detail
      } catch (error) {
        // 详情是按需增强；失败时仍以首屏摘要打开已有视频入口，避免阻断正在值守的用户。
        this.$message.warning('任务详情暂不可用，已按当前任务信息打开视频')
      }
      const robotIds = this.getTaskRobotIds(taskId)
      this.setShowRobotIds(robotIds)
      this.emitFocusTaskPath(taskId)
      dialog.showModal({
        taskInfo: { ...taskInfo },
        robotIds
      })
    },
    /** 兼容旧调用名 */
    handleClickTask(taskId) {
      this.openTaskVideo(taskId)
    },
    handleClickTask1(taskId) {
      if (this.activeTaskId == taskId) {
        this.$refs.taskRobotViewRef.dialogVisible = false
        this.activeTaskId = null
        this.setShowRobotIds([])
        this.emitFocusTaskPath(null)
        return
      }
      this.activeTaskId = taskId
      const robotIds = (this.taskData[taskId]?.equipmentList || []).map(robot => robot.robotId)
      this.setShowRobotIds(robotIds)
      this.emitFocusTaskPath(taskId)
      this.$refs.taskRobotViewRef.showModal({
        taskInfo: { ...this.taskData[taskId] },
        robotIds
      })
    },
    /** 切换地图时关闭任务装备弹窗及相关高亮 */
    clearTaskRobotView() {
      this.activeTaskId = null
      this.setShowRobotIds([])
      this.emitFocusTaskPath(null)
      if (this.$refs.taskRobotViewRef) {
        this.$refs.taskRobotViewRef.dialogVisible = false
      }
    },
    handleTaskDetail() {
      // 详情入口预留
    },
    unwrap(res) {
      if (res && res.code !== undefined) {
        if (res.code === '0' || res.code === 0 || res.code === 200) return res.data || {}
        throw new Error(res.message || '请求失败')
      }
      return res || {}
    },
    clearActiveTaskView(item) {
      if (!item) return
      if (this.activeTaskId == item.taskId || this.activeTaskId == this.getTaskPlanId(item)) {
        this.activeTaskId = null
        this.setShowRobotIds([])
        this.emitFocusTaskPath(null)
        if (this.$refs.taskRobotViewRef) this.$refs.taskRobotViewRef.dialogVisible = false
      }
    },
    async requestTaskRecordAction({ item, action, confirmMessage, successMessage, failMessage, api }) {
      const recordId = this.getTaskRecordId(item)
      if (recordId == null || recordId === '') {
        this.$message.error('缺少执行记录标识，无法操作')
        return
      }
      if (this.isActingTaskRecord(item)) return
      try {
        await this.$primaryConfirm({
          title: '提示',
          message: confirmMessage,
          confirmText: '确定',
          cancelText: '取消',
          onConfirm: async () => {
            this.actingRecordIds = this.actingRecordIds.concat(recordId)
            try {
              const data = this.unwrap(await api(recordId, {}))
              if (data && data.accepted === false) {
                this.$message.warning((data && data.message) || '操作未接受')
                const rejected = new Error((data && data.message) || '操作未接受')
                rejected.handled = true
                throw rejected
              }
              this.$message.success((data && data.message) || successMessage)
              if (action === 'terminate') {
                this.clearActiveTaskView(item)
              }
            } catch (error) {
              if (!(error && error.handled)) {
                // this.$message.error((error && error.message) || failMessage)
              }
              throw error
            } finally {
              this.actingRecordIds = this.actingRecordIds.filter(id => id !== recordId)
            }
          }
        })
      } catch (error) {
        // 用户取消
      }
    },
    handlePauseTask(item) {
      return this.requestTaskRecordAction({
        item,
        action: 'pause',
        confirmMessage: '是否【暂停】该任务？',
        successMessage: '已暂停',
        failMessage: '暂停失败',
        api: pauseTaskRecord
      })
    },
    handleResumeTask(item) {
      return this.requestTaskRecordAction({
        item,
        action: 'resume',
        confirmMessage: '是否【恢复】该任务？',
        successMessage: '已恢复',
        failMessage: '恢复失败',
        api: resumeTaskRecord
      })
    },
    handleTerminateTask(item) {
      return this.requestTaskRecordAction({
        item,
        action: 'terminate',
        confirmMessage: '是否【终止】该任务？',
        successMessage: '已终止',
        failMessage: '终止失败',
        api: terminateTaskRecord
      })
    },
    async handleExecuteTask(item) {
      const planId = this.getTaskPlanId(item)
      if (planId == null) {
        this.$message.error('缺少任务标识，无法执行')
        return
      }
      if (this.isStartingTask(item)) return
      try {
        await this.$primaryConfirm({
          title: '提示',
          message: '是否【立即执行】该任务？',
          confirmText: '确定',
          cancelText: '取消',
          onConfirm: async () => {
            this.startingTaskIds = this.startingTaskIds.concat(planId)
            try {
              const preview = this.unwrap(await startTaskPreview(planId, {}))
              if (preview && preview.valid === false) {
                this.$message.warning((preview && preview.message) || '任务预检未通过，无法启动')
                const rejected = new Error((preview && preview.message) || '任务预检未通过，无法启动')
                rejected.handled = true
                throw rejected
              }
              const data = this.unwrap(await startTask(planId, {}))
              if (data && data.accepted === false) {
                this.$message.warning((data && data.message) || '任务未能启动')
                const rejected = new Error((data && data.message) || '任务未能启动')
                rejected.handled = true
                throw rejected
              }
              this.$message.success((data && data.message) || '任务已启动')
            } catch (error) {
              if (!(error && error.handled)) {
                this.$message.error((error && error.message) || '执行失败')
              }
              throw error
            } finally {
              this.startingTaskIds = this.startingTaskIds.filter(id => id !== planId)
            }
          }
        })
      } catch (error) {
        // 用户取消
      }
    },
    handleClickAlert(item) {
      // 无参：查看全部；有参：仅展示当前告警，隐藏右侧列表与搜索
      this.$refs.warningBatchRef?.open({
        item: item || null,
        simple: !!item
      })
    }
  },
  created() {
    this.$root.$on('bi-panorama-focus-task', this.focusTaskFromPopup)
  },
  beforeDestroy() {
    this.$root.$off('bi-panorama-focus-task', this.focusTaskFromPopup)
  },
  watch: {
    // 切换 GIS/SLAM 或 SLAM 地图时，关闭任务装备弹窗
    globalMapId() {
      this.clearTaskRobotView()
    },
    // robots: {
    //   handler(newVal, oldVal) {
    //     if (newVal?.length && !this.taskList[0]?.robots?.length) {
    //       this.$set(this.taskList[0], 'robots', newVal)
    //     }
    //   },
    //   immediate: true
    // },
    alarmsData: {
      handler(newVal) {
        if (newVal?.high?.items?.length && !this.updated) {
          this.$set(this.alertCollapseArr, 0, false)
          this.updated = true
        }
      },
      immediate: true
    }
  },
}
</script>

<style lang="scss" scoped>
.left-div {
  backdrop-filter: unset !important;
  background: transparent !important;
  .container {
    overflow-y: auto;
    &::-webkit-scrollbar {
      width: 2px;               /* 垂直滚动条宽度 */
      height: 2px;              /* 水平滚动条高度 */
    }
    &::-webkit-scrollbar-thumb {
      border: 1px solid #42536F; /* 创建内边距效果,会覆盖背景值 */
    }
    .box {
      width: 334px;
      /* border: 2px solid rgba(0, 0, 0, 0.00); */
      &:not(.no_data) {
        backdrop-filter: blur(0px);
      }
      &.no_data {
        overflow: hidden;
        min-height: 41px;
        border: 1px solid #2497FC;
        background: linear-gradient(90deg, #003D7C 9.09%, rgba(0, 23, 47, 0.00) 100%);
        .title {
          color: #D5EDFF;
          text-shadow: none;
        }
      }
      .title {
        border-radius: 4px 6px 0 0;
        /* opacity: 0.3;  */
        background: linear-gradient(90deg, rgba(0, 84, 171, 0.60) 9.09%, rgba(0, 60, 106, 0.00) 69.39%);
        .desc {
          color: #EFF8FF;
          text-shadow: 0 2px 30px #0279B8;
          font-family: YouSheBiaoTiHei;
          font-size: 18px;
          background-image: url("../../../../assets/images/new-bi/title-bg.png");
        }
        .more {
          color: #3BA5E7;
          font-family: "Microsoft YaHei";
          font-size: 14px;
          line-height: 18px;
        }
      }
      .notice {
        border-radius: 4px;
        background: #BF000C;
        color: #FFF;
        font-family: "Microsoft YaHei";
        font-size: 12px;
        line-height: 20px;
      }
      .count {
        .item {
          border-radius: 4px;
          border: 1px solid #0B5CA8;
          background: linear-gradient(163deg, rgba(4, 89, 163, 0.50) 15.18%, rgba(0, 56, 114, 0.50) 64.72%);
          .desc, .value, .svg-icon {
            color: #00E1FF;
            font-family: "Microsoft YaHei";
          }
          .desc {
            font-size: 12px;
            font-weight: 600;
            line-height: 16px; /* 133.333% */
          }
          .value {
            font-size: 18px;
            font-weight: 400;
            line-height: 22px;
          }
          .svg-icon {
            font-size: 16px;
            height: 16px;
            line-height: 16px;
          }
          &:nth-child(2) {
            border: 1px solid #096A2B;
            background: #031F27;
            .desc, .value, .svg-icon {
              color: #00FF50;
            }
          }
          &:nth-child(3) {
            border: 1px solid #752700;
            background: #1B191F;
            .desc, .value, .svg-icon {
              color: #FF6E00;
            }
          }
          &:nth-child(4) {
            border: 1px solid rgba(255, 255, 255, 0.20);
            background: #13223A;
            .desc, .value, .svg-icon {
              color: #fff;
            }
          }
        }
      }
      .t2 {
        color: #D5EDFF;
        font-family: YouSheBiaoTiHei;
        font-size: 18px;
        line-height: 23px;
      }
      .device_types {
        .item {
          border-radius: 4px;
          /* border: 1px solid #041B3E;
          background: rgba(0, 49, 98, 0.50); */
          background: #012851;
          &.is-long-name {
            padding: 9px 2px;
            .desc {
              white-space: nowrap;
            }
          }
          .desc {
            color: #BEE1FF;
            font-family: "Microsoft YaHei";
            font-size: 12px;
            line-height: 16px; /* 133.333% */
          }
          .value {
            color: #FFF;
            font-family: Bahnschrift;
            font-size: 18px;
          }
        }
      }
      &.alert {
        .type {
          border-radius: 6px;
          border: 1px solid;
          &.collapse {
            /* overflow: auto; */
            overflow-y: hidden;
            background: #001331;
          }
          .type_name {
            font-family: "Microsoft YaHei";
            font-size: 14px;
            font-weight: 600;
            line-height: 20px;
            .symbol {
              width: 20px;
              height: 20px;
              text-align: center;
              line-height: 20px;
              border-radius: 50%;
              .svg-icon {
                color: #fff !important;
                font-size: 11.6px;
              }
            }
          }
          .list {
            .item {
              position: relative;
              color: #FFF;
              font-family: "Alibaba PuHuiTi";
              font-size: 12px;
              letter-spacing: 0.802px;
              line-height: 16px;
              .event {
                font-weight: 600;
                font-family: "Microsoft YaHei";
              }
              .img {
                position: relative;
                /* background: #ccc; */
                /* background: var(--img) lightgray -4.267px -11.862px / 104% 118.678% no-repeat; */
                .alert_type {
                  position: absolute;
                  top: 0;
                  left: 0;
                  padding: 4px 6px;
                  color: #FFF;
                  font-size: 12px;
                  line-height: 16px; /* 133.333% */
                  border-radius: 0 0 9px 0;
                }
              }
              & + .item {
                &::before {
                  position: absolute;
                  top: -20px;
                  left: 0;
                  width: 248px;
                  height: 1px;
                  content: '';
                }
              }
            }
          }
          &.danger {
            border-color: #AC1515;
            box-shadow: 0 0 30px 0 rgba(255, 0, 0, 0.30) inset;
            .type_name {
              color: #FF0600;
              .symbol {
                background: linear-gradient(168deg, #FC8D8C 16.86%, #EA2532 63.24%);
              }
              .svg-icon {
                color: #FF0004;
              }
            }
            .list .item {
              & + .item::before {
                background: #5B0000;
              }
              .alert_type {
                background: #CE0101;
              }
              .event {
                color: #FF0600;
              }
            }
          }
          &.warning {
            border-color: #8A4600;
            box-shadow: 0 0 20px 0 rgba(255, 130, 0, 0.30) inset;
            .type_name {
              color: #FF8200;
              .symbol {
                background: linear-gradient(168deg, #FF9240 16.86%, #DE6300 63.24%);
              }
              .svg-icon {
                color: FF8200;
              }
            }
            .list .item {
              & + .item::before {
                background: #4D2200;
              }
              .alert_type {
                background: #D05C00 ;
              }
              .event {
                color: #FF7100;
              }
            }
          }
          &.green {
            border-color: #006810;
            box-shadow: 0 0 30px 0 rgba(0, 255, 38, 0.30) inset;
            .type_name {
              color: #00C91E;
              .symbol {
                background: linear-gradient(168deg, #11CD60 16.86%, #047447 63.24%);
              }
              .svg-icon {
                color: #00FF26;
              }
            }
            .list .item {
              & + .item::before {
                background: #00410A;
              }
              .alert_type {
                background: #009D18;
              }
              .event {
                color: #00C91E;
              }
            }
          }
        }
      }
      &.task {
        &:not(.no_data) {
          background: #021328;
        }
        .list {
          position: relative;
          overflow-anchor: none;
        }
        .item {
          position: relative;
          padding-bottom: 10px;
          border-radius: 6px;
          border: 1px solid #11203F;
          background: #11203F;
          &.is-active {
            border-color: #1D7EEC;
            background: #071939;
            box-shadow: 0 0 30px 0 rgba(0, 86, 207, 0.30) inset;
            .header {
              border-radius: 5px 5px 0 0;
              background: #0A224D;
            }
            .action-btn {
              border-color: #D2EBFF;
              background: #0B2348;
              box-shadow: inset 0 0 20px 0 #008CFF;
            }
          }
          .header {
            color: #FFF;
            font-family: "Microsoft YaHei";
            font-size: 14px;
            font-weight: 600;
            line-height: 18px;
            .svg-icon {
              color: #63D9EF;
              font-size: 18px;
            }
            .status {
              flex-shrink: 0;
              color: #FFF;
              border-radius: 4px;
              font-family: "Microsoft YaHei";
              font-size: 12px;
              line-height: 16px;
              white-space: nowrap;
              .svg-icon {
                color: #FFF !important;
                font-size: 12px !important;
              }
              &.blue {
                background: #225CA4;
              }
              &.orange {
                background: #E18000;
              }
              &.gray {
                background: #616161;
              }
              &.green {
                background: #00B61B;
              }
              &.red {
                background: #A42222;
              }
            }
          }
          .desc {
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin-top: 11px;
            padding: 0 10px;
            color: #FFF;
            font-family: "Alibaba PuHuiTi";
            font-size: 12px;
            line-height: 16px;
            letter-spacing: 0.802px;
          }
          .task-actions {
            display: flex;
            align-items: center;
            gap: 10px;
            margin-top: 10px;
            padding: 0 10px;
          }
          .action-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            height: 28px;
            padding: 0;
            border: 1px solid #174D91;
            border-radius: 2px;
            background: linear-gradient(180deg, #083B8B 25%, #0B2348 100%);
            color: #FFF;
            font-family: "Microsoft YaHei";
            font-size: 12px;
            line-height: 16px;
            cursor: pointer;
            .svg-icon {
              color: #FFF;
            }
            &:disabled,
            &.is-disabled {
              opacity: 0.55;
              cursor: not-allowed;
            }
            /* &:not(.is-disabled) {
              &:active {
                color: #0BF9FE;
                box-shadow: 0 0 10px 3px #0BF9FE inset;
              }
            } */
          }
          .action-detail {
            width: 146px;
            .svg-icon {
              font-size: 14px;
            }
          }
          .action-icon {
            width: 28px;
            .svg-icon {
              font-size: 14px;
            }
          }
          .action-video {
            width: 36px;
            .svg-icon {
              font-size: 18px;
            }
          }
          .action-execute {
            width: 100%;
            .svg-icon {
              font-size: 14px;
            }
          }
        }
      }
    }
  }
}
</style>
