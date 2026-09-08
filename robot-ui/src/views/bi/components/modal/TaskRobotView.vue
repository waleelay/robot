<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-02 14:10:40
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\modal\RobotControlPart.vue
 * @Version: 
-->
<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :modal="false"
    :lock-scroll="false"
    append-to-body
    title="异常报告"
  >
    <template slot="footer"></template>
    <div class="flex mb50">
      <div class="custom-modal-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10 text-ellipsis" :title="taskInfo.name" :style="{ maxWidth }">{{ taskInfo.name }}</div>
              <!-- <span class="status flx-center ml10 pt2 pr6 pb2 pl6">
                <svg-icon icon-class="security"></svg-icon>
                <span class="ml4">{{ taskInfo.status }}</span>
              </span>  -->
            </div>
            <div class="flx-center">
              <div class="setting flx-center curp" @click="goTask">
                <svg-icon icon-class="setting"></svg-icon>
                <span class="ml4">控制中心</span>
              </div>
              <div class="close mr10 ml10" @click="closeModal">
                <svg-icon icon-class="close"></svg-icon>
              </div>
            </div>
          </div>
          <div
            class="info-content robot-list p10"
            :class="{ 'show-page': canPageScroll }"
          >
            <div
              class="page page-pre flx-center"
              :class="{ disabled: !canScrollPre }"
              @click="scrollRobotList(-1)"
            >
              <svg-icon icon-class="d-left"></svg-icon>
            </div>
            <div
              class="page page-next flx-center"
              :class="{ disabled: !canScrollNext }"
              @click="scrollRobotList(1)"
            >
              <svg-icon icon-class="d-right"></svg-icon>
            </div>
            <div
              ref="robotListViewport"
              class="robot-list-viewport"
              @scroll="updateRobotListScrollState"
            >
              <div ref="robotListTrack" class="robot-list-track">
                <div
                  v-for="(robot, index) in robotList || []"
                  :key="`${robot.sourceType || 'robot'}_${robot.robotId}`"
                  class="item p10"
                  :class="{ 'ml10': index !== 0 }"
                >
                  <div class="d-flex hp145">
                    <div>
                      <div class="flx-justify-between">
                        <div class="title">{{ robot.name }}</div>
                      </div>
                      <div class="d-flex hp122 mt10">
                        <div class="wp216 h100 main">
                          <VideoBox
                            @toggleFullscreen="toggleFullscreen"
                            :videoIndex="`${robot.robotId}_${index}_0`"
                            :prefixId="prefixId"
                            :ZQL_videosInfos="ZQL_videosInfos"
                            className="six-1" />
                        </div>
                      </div>
                    </div>
                    <div v-if="robot?.cameras?.length > 1" class="mt4 ml10 p5 side-list common-scroll ovya">
                      <div
                        v-for="(camera, cameraIndex) in robot.cameras.slice(1)"
                        :key="camera.key"
                        class="wp108 hp62 main curp"
                        :class="{ 'mt4': cameraIndex !== 0 }">
                        <VideoBox
                          @toggleFullscreen="toggleFullscreen"
                          @select="swapWithMain(robot.robotId, index, cameraIndex + 1)"
                          :videoIndex="`${robot.robotId}_${index}_${cameraIndex + 1}`"
                          :prefixId="prefixId"
                          :ZQL_videosInfos="ZQL_videosInfos"
                          className="six-1" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import VideoBox from './VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../utils/fullscreen.js';
import { mapActions, mapState } from 'vuex';

/** 本机联调可开；上线前改为 false */
const ENABLE_TASK_ROBOT_VIEW_MOCK = false

const MOCK_TASK_ID = 'mock-task-robot-view-1'

export default {
  name: 'TaskRobotView',
  components: { VideoBox },
  mixins: [common],
  data() {
    return {
      dialogVisible: false,
      taskInfo: {},
      robotIds: [],
      robotList: [],
      taskIndex: '',
      prefixId: 'task-robot-video-div',
      cameraOrderByRobot: {},
      /** mock 打开时允许合成占位装备/摄像头 */
      useMockPlaceholders: false,
      canPageScroll: false,
      canScrollPre: false,
      canScrollNext: false,
      /** 仅手动翻页/拖动更新；实时同步不得清零 */
      robotListScrollLeft: 0,
      robotListSignature: ''
    }
  },
  mounted() {
    if (ENABLE_TASK_ROBOT_VIEW_MOCK) {
      this.openMockTaskRobotView()
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['taskFixedCameraData', 'robotBaseInfo']),
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    cameras() {
      return this.$store.getters['websocketRobot/getCameras'];
    },
    // 无装备 / 无视频 / 单装备单视频时标题限宽，否则随弹窗变宽
    maxWidth() {
      const robots = this.robotList || []
      if (!robots.length) return '180px'
      const videoCount = robots.reduce((sum, robot) => sum + (robot.cameras?.length || 0), 0)
      if (!videoCount || (robots.length === 1 && videoCount === 1)) return '180px'
      return undefined
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId', 'setSelectedRobotId', 'setControlCenterReturnTo']),
    ...mapActions('websocketExtraData', ['loadTaskFixedCameras']),
    /** 关闭弹窗并等待视频流停妥（供互斥切换 / 换任务调用） */
    async closeAndStop() {
      const needStop = this.dialogVisible || this.started || (this.robotList && this.robotList.length)
      if (needStop) {
        await this.stopAll()
      }
      this.started = false
      this.useMockPlaceholders = false
      this.robotListScrollLeft = 0
      this.robotListSignature = ''
      this.robotList = []
      this.dialogVisible = false
    },
    async showModal(data) {
      // 已打开时换任务：必须先停旧任务流，再绑新任务起流
      if (this.dialogVisible || this.started) {
        await this.stopAll()
        this.started = false
      }
      this.dialogVisible = true
      this.robotIds = Array.isArray(data?.robotIds) ? data.robotIds : []
      this.taskInfo = { ...(data?.taskInfo || {}) }
      this.useMockPlaceholders = Boolean(data?.useMockPlaceholders)
      this.started = true
      await this.$nextTick()
      await this.ensureTaskFixedCamerasLoaded()
      await this.syncRobotList({ resetScroll: true })
    },
    async ensureTaskFixedCamerasLoaded() {
      const taskId = this.taskInfo?.taskId
      if (taskId === undefined || taskId === null || taskId === '') return
      if (this.useMockPlaceholders) return
      try {
        await this.loadTaskFixedCameras(taskId)
      } catch (error) {
        console.warning('固定摄像头列表暂不可用，请稍后重试')
      }
    },
    openMockTaskRobotView() {
      const taskId = MOCK_TASK_ID
      const fixedItems = [
        {
          cameraId: 'mock-fixed-cam-1',
          name: '模拟固定摄像头-东门',
          sourceType: 'FIXED_CAMERA',
          sourceId: 'mock-fixed-cam-1',
          defaultQuality: '720P'
        },
        {
          cameraId: 'mock-fixed-cam-2',
          name: '模拟固定摄像头-仓库',
          sourceType: 'FIXED_CAMERA',
          sourceId: 'mock-fixed-cam-2',
          defaultQuality: '720P'
        }
      ]
      this.$store.commit('websocketExtraData/SET_TASK_FIXED_CAMERAS', {
        taskId,
        items: fixedItems
      })
      this.showModal({
        taskInfo: {
          taskId,
          name: '【模拟】任务装备与固定摄像头'
        },
        robotIds: ['mock-equip-robot-1', 'mock-equip-robot-2'],
        useMockPlaceholders: true
      })
    },
    closeModal() {
      this.dialogVisible = false;
      // 关闭弹窗不取消任务卡片与地图装备选中
      this.$emit('close', this.taskInfo.taskId)
    },
    async goTask() {
      await this.stopAll()
      // 进入一级监控任务列表，避免残留的装备选中把页面带到二级控制台
      this.setControlCenterReturnTo(this.$route.fullPath)
      this.setSelectedRobotId('')
      const taskId = this.taskInfo.taskId
      const query = {
        taskId: taskId === undefined || taskId === null ? 0 : taskId
      }
      try {
        await this.$router.push({ path: '/bi/patrol/monitor', query })
      } catch (e) {
        // 已在实时监控且 query 相同时忽略重复导航
      }
    },
    orderedCameras(robot) {
      const cameras = (robot.cameras || [])
        .map(camera => {
          return this.cameras[camera.key] || camera
        })
        .sort((a, b) => {
          if (a.groupType === 'body') return -1
          if (b.groupType === 'body') return 1
          return 0
        })
      const availableKeys = cameras.map(camera => camera.key)
      const previousOrder = this.cameraOrderByRobot[robot.robotId] || []
      const order = previousOrder
        .filter(key => availableKeys.includes(key))
        .concat(availableKeys.filter(key => !previousOrder.includes(key)))
      this.$set(this.cameraOrderByRobot, robot.robotId, order)
      return order.map(key => this.cameras[key] || cameras.find(camera => camera.key === key)).filter(Boolean)
    },
    buildMockEquipmentRobot(robotId, index) {
      const id = String(robotId)
      const cameraKey = `${id}_body`
      return {
        robotId: id,
        name: `模拟装备-${index + 1}`,
        sourceType: 'ROBOT',
        status: 'online',
        cameras: [
          {
            key: cameraKey,
            robotId: id,
            name: '主摄像头',
            groupType: 'body',
            cameraId: cameraKey
          }
        ]
      }
    },
    /** 对齐 TaskListTree.getTaskFixedCameraRobot */
    resolveFixedCameraItem(camera) {
      const sourceId = camera?.sourceId || camera?.cameraId
      if (sourceId === undefined || sourceId === null || sourceId === '') return null
      const id = String(sourceId)
      const live = (this.robots || []).find(item => String(item.robotId) === id)
      const base = this.robotBaseInfo?.[sourceId] || this.robotBaseInfo?.[id]
      if (!live && !base) {
        const cameraKey = `${id}_main`
        return {
          robotId: id,
          name: camera?.name || id,
          type: 'FIXED_CAMERA',
          typeCode: 'FIXED_CAMERA',
          sourceType: 'FIXED_CAMERA',
          status: this.useMockPlaceholders ? 'online' : 'offline',
          cameras: this.useMockPlaceholders
            ? [{
              key: cameraKey,
              robotId: id,
              name: camera?.name || '固定摄像头',
              groupType: 'body',
              cameraId: id
            }]
            : []
        }
      }
      const merged = {
        ...camera,
        ...base,
        ...live,
        robotId: live?.robotId || base?.robotId || sourceId,
        name: live?.name || base?.name || camera?.name || id,
        sourceType: 'FIXED_CAMERA',
        cameras: live?.cameras?.length
          ? live.cameras
          : Object.values(this.cameras || {}).filter(item => String(item?.robotId) === id)
      }
      return {
        ...merged,
        cameras: this.orderedCameras(merged)
      }
    },
    taskFixedCameraItems(taskId = this.taskInfo?.taskId) {
      if (taskId === undefined || taskId === null || taskId === '') return []
      const items = this.taskFixedCameraData?.[taskId] || this.taskFixedCameraData?.[String(taskId)]
      return Array.isArray(items) ? items : []
    },
    buildEquipmentList() {
      const robots = this.robots || []
      return (this.robotIds || []).map((robotId, index) => {
        const id = String(robotId)
        const live = robots.find(item => String(item.robotId) === id)
        if (live) {
          return { ...live, cameras: this.orderedCameras(live) }
        }
        if (this.useMockPlaceholders) {
          return this.buildMockEquipmentRobot(id, index)
        }
        return null
      }).filter(Boolean)
    },
    buildFixedCameraList() {
      return this.taskFixedCameraItems()
        .map(item => this.resolveFixedCameraItem(item))
        .filter(Boolean)
    },
    buildRobotListSignature(list) {
      return (list || []).map(robot => {
        const cams = (robot.cameras || []).map(camera => camera?.key || camera?.cameraId || '').join(',')
        return `${robot.sourceType || 'robot'}:${robot.robotId}:${cams}`
      }).join('|')
    },
    restoreRobotListScroll() {
      const viewport = this.$refs.robotListViewport
      if (!viewport) return
      const left = this.robotListScrollLeft || 0
      viewport.scrollLeft = left
      // 布局/视频占位变化后再补一次，避免被浏览器钳制回 0
      requestAnimationFrame(() => {
        if (!this.$refs.robotListViewport) return
        this.$refs.robotListViewport.scrollLeft = this.robotListScrollLeft || 0
        this.updateRobotListScrollState()
      })
    },
    async syncRobotList(options = {}) {
      if (!this.dialogVisible) return
      const resetScroll = Boolean(options.resetScroll)
      this.setPrefixId(this.prefixId)
      const equipmentList = this.buildEquipmentList()
      const fixedCameraList = this.buildFixedCameraList()
      const robotList = [...equipmentList, ...fixedCameraList]
      const signature = this.buildRobotListSignature(robotList)

      // 结构未变时不重建列表，避免 VideoBox/DOM 刷新把横向滚动顶回开头
      if (!resetScroll && signature && signature === this.robotListSignature) {
        this.restoreRobotListScroll()
        return
      }

      if (resetScroll) this.robotListScrollLeft = 0
      this.robotListSignature = signature
      this.$set(this, 'robotList', robotList)
      await this.updateInfo()
      await this.$nextTick()
      this.restoreRobotListScroll()
    },
    updateRobotListScrollState() {
      const viewport = this.$refs.robotListViewport
      if (!viewport) {
        this.canPageScroll = false
        this.canScrollPre = false
        this.canScrollNext = false
        return
      }
      // 以用户滚动为准写回，防止同步逻辑读到中间态
      this.robotListScrollLeft = viewport.scrollLeft
      const maxScroll = Math.max(0, viewport.scrollWidth - viewport.clientWidth)
      const left = viewport.scrollLeft
      this.canPageScroll = maxScroll > 2
      this.canScrollPre = left > 2
      this.canScrollNext = left < maxScroll - 2
    },
    scrollRobotList(direction) {
      const viewport = this.$refs.robotListViewport
      if (!viewport || !this.canPageScroll) return
      if (direction < 0 && !this.canScrollPre) return
      if (direction > 0 && !this.canScrollNext) return
      const items = viewport.querySelectorAll('.item')
      const firstItem = items[0]
      const step = firstItem
        ? firstItem.getBoundingClientRect().width + 10
        : Math.max(viewport.clientWidth * 0.7, 240)
      const nextLeft = Math.max(0, Math.min(
        viewport.scrollWidth - viewport.clientWidth,
        viewport.scrollLeft + direction * step
      ))
      this.robotListScrollLeft = nextLeft
      viewport.scrollTo({ left: nextLeft, behavior: 'smooth' })
      window.setTimeout(() => this.updateRobotListScrollState(), 320)
    },
    async swapWithMain(robotId, robotIndex, cameraIndex) {
      const robot = this.robotList[robotIndex]
      if (!robot || cameraIndex <= 0 || cameraIndex >= robot.cameras.length) return
      const cameras = [...robot.cameras]
      const mainCamera = cameras[0]
      cameras[0] = cameras[cameraIndex]
      cameras[cameraIndex] = mainCamera
      this.$set(this.robotList, robotIndex, { ...robot, cameras })
      this.$set(this.cameraOrderByRobot, robotId, cameras.map(camera => camera.key))
      this.robotListSignature = this.buildRobotListSignature(this.robotList)
      await this.updateInfo()
      this.rebindCameraTracks([cameras[0], cameras[cameraIndex]])
      await this.$nextTick()
      this.restoreRobotListScroll()
    },
  },
  watch: {
    cameras: {
      async handler() {
        if (!this.dialogVisible) return
        await this.syncRobotList()
      },
      deep: false,
      immediate: false
    },
    robots: {
      async handler() {
        if (!this.dialogVisible) return
        await this.syncRobotList()
      },
      deep: false
    },
    taskFixedCameraData: {
      async handler() {
        if (!this.dialogVisible) return
        await this.syncRobotList()
      },
      deep: true
    },
    dialogVisible: {
      async handler(newVal, oldVal) {
        // closeAndStop / showModal 已先停流并把 started=false；此处兜底用户关弹窗
        if (!newVal && oldVal && this.started) {
          await this.stopAll()
          this.started = false
          this.useMockPlaceholders = false
          this.robotListScrollLeft = 0
          this.robotListSignature = ''
        }
      },
      immediate: true
    }
  }
}
</script>

<style scoped lang="scss">
.top .status {
  color: #FFF;
  border-radius: 4px;
  background: #225CA4;
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 16px;
  .svg-icon {
    color: #FFF;
    font-size: 12px;
  }
}
.box {
  width: auto !important;
  max-width: 1300px;
  min-width: 300px;
}
.robot-list {
  position: relative;
  display: block;
  max-width: 1280px;
  .robot-list-viewport {
    width: 100%;
    overflow-x: auto;
    overflow-y: hidden;
    scroll-behavior: smooth;
    // 隐藏滚动条，仅用左右按钮翻页
    scrollbar-width: none;
    -ms-overflow-style: none;
    &::-webkit-scrollbar {
      display: none;
    }
  }
  .robot-list-track {
    display: flex;
    flex-wrap: nowrap;
    align-items: flex-start;
    width: max-content;
  }
  .item {
    flex: 0 0 auto;
    border-radius: 4px;
    border: 1px solid #005FCF;
    background: rgba(4, 24, 65, 0.20);
    .title {
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 10px;
      line-height: 13px;
    }
    .status {
      position: relative;
      color: #27ED00;
      font-family: "Microsoft YaHei";
      font-size: 10px;
      line-height: 13px;
      &::before {
        content: '';
        position: absolute;
        top: 4.5px;
        left: 0;
        width: 4px;
        height: 4px;
        border-radius: 50%;
        background: #27ED00;
      }
    }
    .main {
      background: #6E6E6E;
    }
    .side-list {
      overflow-x: hidden;
      border-radius: 4px;
      border: 1px solid #0053B5;
    }
  }
  .page {
    position: absolute;
    top: 10px;
    z-index: 2;
    width: 18px;
    height: calc(100% - 20px);
    border-radius: 4px;
    background: #002859;
    border: 1px solid rgba(0, 95, 207, 0.50);
    filter: drop-shadow(-12px 0 10px #192238);
    cursor: pointer;
    opacity: 0;
    pointer-events: none;
    transition: all 0.3s ease-in-out;
    .svg-icon {
      color: #005FCF;
      font-size: 16px;
    }
    &.page-pre {
      left: 10px;
      border-top-right-radius: 0px;
      border-bottom-right-radius: 0px;
    }
    &.page-next {
      right: 10px;
      border-top-left-radius: 0px;
      border-bottom-left-radius: 0px;
    }
    &.disabled {
      opacity: 0.25 !important;
      cursor: not-allowed;
    }
  }
  &.show-page:hover {
    .page {
      opacity: 1;
      pointer-events: auto;
    }
  }
}
.setting {
  padding: 4px 6px;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 10px;
  line-height: 13px;
  border-radius: 2px;
  background: rgba(38, 84, 152, 0.50);
}
</style>
