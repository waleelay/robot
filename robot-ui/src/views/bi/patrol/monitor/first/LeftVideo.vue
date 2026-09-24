<template>
<div :id="prefixId" class="custom-video-div" :class="[prefixId, { 'is-page-fullscreen': isPageFullscreen }]">
  <!-- 页面全屏：右上角浮层退出（不占布局行） -->
  <button
    v-if="isPageFullscreen"
    type="button"
    class="page-fullscreen-exit"
    title="退出全屏"
    @click="toggleFullscreen1"
  >
    <svg-icon icon-class="close-fullscreen" />
  </button>

  <!-- 页面级 chrome：全屏时隐藏 -->
  <div class="page-video-chrome flx-justify-between">
    <div class="card-title hp36 flx-justify-between pr26" :class="cardTitleClass" style="line-height: 36px;">
      <div class="text">
        多设备实时画面
      </div>
      <div class="split-screen flx-align-center">
        <span @click="onSplitChange(1)" :class="{ 'is-active': splitType === 1 }">
          <svg-icon icon-class="screen-split-1" />
        </span>
        <span @click="onSplitChange(4)" class="ml10" :class="{ 'is-active': splitType === 4 }">
          <svg-icon icon-class="screen-split-4" />
        </span>
        <span @click="onSplitChange(6)" class="ml10" :class="{ 'is-active': splitType === 6 }">
          <svg-icon icon-class="screen-split-6" />
        </span>
        <span @click="onSplitChange(9)" class="ml10" :class="{ 'is-active': splitType === 9 }">
          <svg-icon icon-class="screen-split-9" />
        </span>
        <span class="ml10" @click="toggleFullscreen1">
          <svg-icon :icon-class="isPageFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
        </span>
      </div>
    </div>
  </div>

  <!-- 仅缩放视频列表区域，不含标题/分屏条 -->
  <div ref="videoScaleStage" class="video-scale-stage" :style="fullscreenStageStyle">
    <div
      class="list hp759 flx-justify-between flex-wrap w100"
      :class="[
        { mt9: !isPageFullscreen },
        listPaddingClass,
        isPageFullscreen ? `split-${splitType}` : ''
      ]"
      :style="isPageFullscreen ? undefined : { width: '1364px' }"
    >
      <div v-show="!isPageFullscreen" class="horn top-left"></div>
      <div v-show="!isPageFullscreen" class="horn top-right"></div>
      <div v-show="!isPageFullscreen" class="horn bottom-left"></div>
      <div v-show="!isPageFullscreen" class="horn bottom-right"></div>
      <!-- style="width: calc(100% - 42px); height: calc(100% - 30px);" 1312 738 -->
      <div v-if="splitType === 6" class="split-6-wrap" :class="{ 'pr5 pl5': !isPageFullscreen }">
        <div class="d-flex">
          <div
            :draggable="!!ZQL_videosInfos['slot_1']"
            @dragstart="onSlotDragStart($event, 'slot_1')"
            @dragend="onDragEnd"
            :style="{ cursor: ZQL_videosInfos['slot_1'] ? 'grab' : 'default' }"
          >
            <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_1')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" @openControlCenter="openControlCenter" :videoIndex="1" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" :slotCloseHints="slotCloseHints" :isPageFullscreen="isPageFullscreen" className="six-1" />
          </div>
          <!-- <VideoBox :videoIndex="0" :prefixId="prefixId" :splitType="splitType" :slotDevices="slotDevices" @updateSlot="updateSlot" className="six-1" /> -->
          <div class="ml26">
            <div
              :draggable="!!ZQL_videosInfos['slot_2']"
              @dragstart="onSlotDragStart($event, 'slot_2')"
              @dragend="onDragEnd"
              :style="{ cursor: ZQL_videosInfos['slot_2'] ? 'grab' : 'default' }"
            >
              <VideoBox
                @toggleFullscreen="toggleFullscreen"
                @onAlgoChange="onAlgoChange"
                @playPauseVideo="playPauseVideo('slot_2')"
                @test="test"
                @removeVideo="handleRemoveVideo"
                @refreshVideo="handleRefreshVideo"
                @openControlCenter="openControlCenter"
                :videoIndex="2"
                :prefixId="prefixId"
                :splitType="splitType"
                :ZQL_videosInfos="ZQL_videosInfos"
                :slotCloseHints="slotCloseHints"
                :isPageFullscreen="isPageFullscreen"
                className="six-2"
              />
            </div>
            <div
              :draggable="!!ZQL_videosInfos['slot_3']"
              @dragstart="onSlotDragStart($event, 'slot_3')"
              @dragend="onDragEnd"
              :style="{ cursor: ZQL_videosInfos['slot_3'] ? 'grab' : 'default' }"
            >
              <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_3')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" @openControlCenter="openControlCenter" :videoIndex="3" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" :slotCloseHints="slotCloseHints" :isPageFullscreen="isPageFullscreen" className="mt16 six-3" />
            </div>
          </div>
        </div>
        <div class="d-flex mt20">
          <div
            :draggable="!!ZQL_videosInfos['slot_4']"
            @dragstart="onSlotDragStart($event, 'slot_4')"
            @dragend="onDragEnd"
            :style="{ cursor: ZQL_videosInfos['slot_4'] ? 'grab' : 'default' }"
          >
            <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_4')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" @openControlCenter="openControlCenter" :videoIndex="4" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" :slotCloseHints="slotCloseHints" :isPageFullscreen="isPageFullscreen" className="six-4" />
          </div>
          <div
            :draggable="!!ZQL_videosInfos['slot_5']"
            @dragstart="onSlotDragStart($event, 'slot_5')"
            @dragend="onDragEnd"
            :style="{ cursor: ZQL_videosInfos['slot_5'] ? 'grab' : 'default' }"
          >
            <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_5')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" @openControlCenter="openControlCenter" :videoIndex="5" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" :slotCloseHints="slotCloseHints" :isPageFullscreen="isPageFullscreen" className="ml28 six-5" />
          </div>
          <div
            :draggable="!!ZQL_videosInfos['slot_6']"
            @dragstart="onSlotDragStart($event, 'slot_6')"
            @dragend="onDragEnd"
            :style="{ cursor: ZQL_videosInfos['slot_6'] ? 'grab' : 'default' }"
            >
            <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_6')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" @openControlCenter="openControlCenter" :videoIndex="6" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" :slotCloseHints="slotCloseHints" :isPageFullscreen="isPageFullscreen" className="ml26 six-6" />
          </div>
        </div>
      </div>
      <template v-else-if="splitType === 4 || splitType === 9">
        <div
          v-for="index in splitType"
          :key="index"
          :draggable="!!ZQL_videosInfos[`slot_${index}`]"
          @dragstart="onSlotDragStart($event, `slot_${index}`)"
          @dragend="onDragEnd"
          :style="{ cursor: ZQL_videosInfos[`slot_${index}`] ? 'grab' : 'default' }"
        >
          <VideoBox
            @toggleFullscreen="toggleFullscreen"
            @onAlgoChange="onAlgoChange"
            @playPauseVideo="playPauseVideo(`slot_${index}`)"
            @test="test"
            @removeVideo="handleRemoveVideo"
            @refreshVideo="handleRefreshVideo"
            @openControlCenter="openControlCenter"
            :videoIndex="index"
            :prefixId="prefixId"
            :splitType="splitType"
            :ZQL_videosInfos="ZQL_videosInfos"
            :slotCloseHints="slotCloseHints"
            :isPageFullscreen="isPageFullscreen"
          />
        </div>
      </template>
      <template v-else>
        <VideoBox
          @toggleFullscreen="toggleFullscreen"
          @onAlgoChange="onAlgoChange"
          @playPauseVideo="playPauseVideo(`slot_${index}`)"
          @test="test"
          @removeVideo="handleRemoveVideo"
          @refreshVideo="handleRefreshVideo"
          @openControlCenter="openControlCenter"
          v-for="index in splitType"
          :key="index"
          :videoIndex="index"
          :prefixId="prefixId"
          :splitType="splitType"
          :ZQL_videosInfos="ZQL_videosInfos"
          :slotCloseHints="slotCloseHints"
          :isPageFullscreen="isPageFullscreen"
        />
      </template>
    </div>
  </div>
</div>
</template>

<script>
// import video from '../../../js/mixins/video.js'
import canvasUtil from '../../../js/mixins/box-canvas.js'
import VideoBox from './VideoBox.vue';
import { mapActions, mapState } from 'vuex';
import { onDragStart, onDragEnd } from '@/store/modules/dragVideo.js';
import { events as fullscreenEvents, enterFullscreen, exitFullscreen, isElementFullscreen } from '@/utils/fullscreen.js';
import { pickDefaultCamera, isBodyCamera, isFixedCameraRobot } from '../../../js/utils/pick-default-camera';
export default {
  name: 'LeftVideo',
  mixins: [canvasUtil],
  components: { VideoBox },
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    },
    cardTitleClass: {
      type: String,
      default: 'title-1364-37'
    }
  },
  data() {
    return {
      algInfoList: [
        // {
        //   // currentAlg: 0,
        //   // currentAlgLabel: '人脸识别',
        //   currentAlg: '',
        //   currentAlgLabel: '',
        //   algTypes: [
        //     // {
        //     //   label: '人脸识别',
        //     //   value: 0
        //     // },
        //     // {
        //     //   label: '车辆识别',
        //     //   value: 1
        //     // }
        //   ]
        // }
      ],
      singleId: null,         // 一分屏选中的设备id，初始无默认填充
      checkedIds: [],          // 多分屏选中的设备id数组，初始为空
      slotDevices: [],                // 长度等于splitType，存储每个格子的设备信息或null
      lastCheckedIds: [],            // 记录上一次的多选值，用于对比变化
      fullscreenIndex: null,            // 当前全屏的格子索引，null表示无全屏
      isPageFullscreen: false,

      // ===================================================================================
      // 修改为动态键值对形式，不再限制为4个
      ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
      ZQL_playingSource: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的摄像头ID  { 'slot_1': 1, 'slot_2': 2 }
      ZQL_sources: {},
      statusArr: {}, // 改为对象形式，键名为'slot_1'...
      sourceceList: [],
      manualChange: false,
      fixedCameraPlayableStates: {},
      /** 任务结束后空槽提示：slot_1 → 文案 */
      slotCloseHints: {},
      _slotCloseHintTimers: {}
    }
  },
  computed: {
    ...mapState('dragVideo', ['dropResult', 'splitType']),
    ...mapState('websocketRobot', ['robots', 'cameras']),
    isSecondScreen() {
      return this.prefixId === 'test-video-div-second'
    },
    isFirstScreen() {
      return this.prefixId === 'test-video-div-first'
    },
    listPaddingClass() {
      // 页面全屏时去掉装饰性内边距，便于视频区贴边铺满
      if (this.isPageFullscreen) return ''
      if (this.isFirstScreen) return 'pr51 pl51'
      if (this.isSecondScreen) {
        if (this.splitType === 4) return 'pr12 pl12 pt10 pb10'
        if (this.splitType === 6) return 'pr14 pl14 pt8 pb8'
        if (this.splitType === 9) return 'pr10 pl10 pt8 pb8'
        return 'pr18 pl18 pt10 pb10'
      }
      return 'pr26 pl26'
    },
    fullscreenStageStyle() {
      if (!this.isPageFullscreen) return {}
      // 与 VideoTool 一致：元素全屏后用 CSS 铺满，不整体 scale，避免文字/图标被放大
      return {
        width: '100%',
        height: '100%'
      }
    }
  },
  async mounted() {
    // 初始化：不默认填充任何设备
    this.singleId = null;
    this.checkedIds = [];
    this.lastCheckedIds = [];
    this.slotDevices = new Array(this.splitType).fill(null);
    // 进入控制中心时分屏数可能未变化，必须主动初始化槽位，否则空槽查找会全部落到 slot_1
    this.initSlots(this.splitType)
    // 从 store 加载机器人列表

    this.setPrefixId(this.prefixId)
    fullscreenEvents.forEach(event => {
      document.addEventListener(event, this.handleFullscreenChange)
    })
    document.addEventListener('keydown', this.handleFullscreenKeydown)
  },
  beforeDestroy() {
    fullscreenEvents.forEach(event => {
      document.removeEventListener(event, this.handleFullscreenChange)
    })
    document.removeEventListener('keydown', this.handleFullscreenKeydown)
    Object.keys(this._slotCloseHintTimers || {}).forEach(key => {
      clearTimeout(this._slotCloseHintTimers[key])
    })
    this._slotCloseHintTimers = {}
    this.releaseWallConsumers()
  },
  methods: {
    ...mapActions('dragVideo', ['resetDrag', 'setSplitType']),
    ...mapActions('websocketRobot', ['startCamera', 'stopCamera', 'restartCamera', 'recoverCameraPlayback', 'setPrefixId', 'setSelectedRobotId', 'setControlCenterReturnTo']),
    onDragStart,
    onDragEnd,
    showSlotCloseHint(slotKey, text = '任务已结束，视频关闭', durationMs = 3000) {
      if (!slotKey) return
      const prevTimer = this._slotCloseHintTimers?.[slotKey]
      if (prevTimer) clearTimeout(prevTimer)
      this.$set(this.slotCloseHints, slotKey, text)
      this._slotCloseHintTimers[slotKey] = setTimeout(() => {
        this.$delete(this.slotCloseHints, slotKey)
        this.$delete(this._slotCloseHintTimers, slotKey)
      }, durationMs)
    },
    /**
     * 任务结束/终止：仅关闭传入装备对应槽位，并在空槽显示 3 秒提示。
     * 不匹配的槽位保持播放。
     */
    async closeEndedTaskVideos(robotIds = []) {
      const targetSet = new Set((robotIds || []).map(id => String(id)).filter(Boolean))
      if (!targetSet.size) return

      const takenCameras = []
      for (let i = 1; i <= this.splitType; i++) {
        const slotKey = `slot_${i}`
        const info = this.ZQL_videosInfos[slotKey]
        const playingKey = this.ZQL_playingSource[slotKey]
        if (!info && !playingKey) continue
        const robotId = String(info?.robotId || info?.robot?.robotId || '')
        if (!robotId || !targetSet.has(robotId)) continue

        takenCameras.push(this.takeSlotCamera(slotKey))
        this.showSlotCloseHint(slotKey)
      }
      for (const taken of takenCameras) {
        try {
          await this.stopTakenCamera(taken)
        } catch (e) {}
      }
    },
    // 六分屏窗口拖拽：未播放不可拖
    onSlotDragStart(event, slotKey) {
      const videoInfo = this.ZQL_videosInfos[slotKey]
      if (!videoInfo || !this.ZQL_playingSource[slotKey]) {
        event.preventDefault()
        return
      }
      onDragStart(event, videoInfo.robot || videoInfo, 'smallVideo', slotKey)
    },
    updateSlot(data) {
      this.slotDevices[data.index] = data.data
    },
    getRef(refName) {
      return this.$refs?.[refName]?.[0] || {}
    },
    wallConsumerId() {
      // Vue 实例 uid 用于隔离同一业务墙快速销毁/重建时的迟到 stop，
      // 避免旧实例释放新实例刚建立的消费者。
      return `patrol-monitor-wall:${this.prefixId}:${this._uid}`
    },
    resolvePlaybackRobot(videoInfo, fallback) {
      if (fallback) return fallback
      if (videoInfo?.robot) return videoInfo.robot
      const robotId = videoInfo?.robotId
      return (this.robots || []).find(item => String(item.robotId) === String(robotId))
    },
    startCameraPayload(robot, camera) {
      return {
        robot,
        camera,
        throwOnError: true,
        consumerId: this.wallConsumerId(),
        prefixId: this.prefixId
      }
    },
    stopCameraPayload(camera) {
      return {
        ...camera,
        consumerId: this.wallConsumerId(),
        prefixId: this.prefixId
      }
    },
    syncSlotSelections() {
      this.checkedIds = [...new Set(Object.values(this.ZQL_playingSource).filter(Boolean))]
      this.lastCheckedIds = this.checkedIds.slice()
      if (typeof this.$emit === 'function') {
        const robotIds = typeof this.getPlayingRobotIds === 'function'
          ? this.getPlayingRobotIds()
          : []
        this.$emit('selection-change', {
          cameraKeys: this.checkedIds.slice(),
          robotIds
        })
      }
    },
    assignSlotCamera(slotKey, robot, camera) {
      this.$set(this.ZQL_playingSource, slotKey, camera.key)
      this.$set(this.ZQL_videosInfos, slotKey, { robot, ...camera, robotId: robot.robotId })
      this.syncSlotSelections()
    },
    takeSlotCamera(slotKey) {
      const videoInfo = this.ZQL_videosInfos[slotKey]
      const cameraKey = this.ZQL_playingSource[slotKey] || videoInfo?.key
      const camera = this.cameras?.[cameraKey] || videoInfo
      this.$set(this.ZQL_videosInfos, slotKey, null)
      this.$set(this.ZQL_playingSource, slotKey, null)
      this.syncSlotSelections()
      return cameraKey ? { camera, videoInfo, cameraKey } : null
    },
    isSlotCameraIntended(slotKey, cameraKey) {
      return this.ZQL_playingSource[slotKey] === cameraKey
    },
    isCameraIntended(cameraKey) {
      return Object.values(this.ZQL_playingSource).includes(cameraKey)
    },
    async stopTakenCamera(taken) {
      if (!taken?.camera?.key) return
      await this.stopCamera(this.stopCameraPayload(taken.camera, taken.videoInfo))
    },
    async closeSlotCamera(slotKey) {
      const taken = this.takeSlotCamera(slotKey)
      await this.stopTakenCamera(taken)
      return taken
    },
    async startAssignedCamera(slotKey, robot, camera) {
      try {
        await this.startCamera(this.startCameraPayload(robot, camera))
        if (this.isFixedCameraRobot(robot) && this.isSlotCameraIntended(slotKey, camera.key)) {
          this.$set(this.ZQL_videosInfos, slotKey, {
            ...this.ZQL_videosInfos[slotKey],
            sourceStartFailed: false
          })
        }
        return this.isSlotCameraIntended(slotKey, camera.key)
      } catch (_) {
        // 固定摄像头保留用户播放意图，等待 Gateway/RTSP 恢复或人工重启；
        // 机器人继续沿用原有的启动失败清理行为。
        if (this.isFixedCameraRobot(robot) && this.isSlotCameraIntended(slotKey, camera.key)) {
          this.$set(this.ZQL_videosInfos, slotKey, {
            ...this.ZQL_videosInfos[slotKey],
            loading: false,
            sourceStartFailed: true
          })
        } else if (this.isSlotCameraIntended(slotKey, camera.key)) {
          this.takeSlotCamera(slotKey)
        }
        return false
      }
    },
    async replaceSlotCamera(slotKey, robot, camera) {
      const previousKey = this.ZQL_playingSource[slotKey]
      if (previousKey === camera.key) {
        await this.closeSlotCamera(slotKey)
        return false
      }
      const previous = this.takeSlotCamera(slotKey)
      this.assignSlotCamera(slotKey, robot, camera)
      try {
        await this.stopTakenCamera(previous)
      } catch (_) {
        // 旧画面释放失败不能覆盖最新播放意图，服务端会话由后续状态同步继续回收。
      }
      if (!this.isSlotCameraIntended(slotKey, camera.key)) return false
      return this.startAssignedCamera(slotKey, robot, camera)
    },
    async releaseAllSlots() {
      const takenCameras = []
      const slotKeys = new Set([
        ...Object.keys(this.ZQL_videosInfos || {}),
        ...Object.keys(this.ZQL_playingSource || {})
      ])
      slotKeys.forEach(slotKey => {
        const videoInfo = this.ZQL_videosInfos[slotKey]
        if (videoInfo || this.ZQL_playingSource[slotKey]) takenCameras.push(this.takeSlotCamera(slotKey))
      })
      await Promise.all(takenCameras.filter(Boolean).map(taken => this.stopTakenCamera(taken).catch(() => {})))
    },
    releaseWallConsumers() {
      this.releaseAllSlots().catch(() => {})
    },
    async openControlCenter(robotId) {
      await this.releaseAllSlots()
      this.setControlCenterReturnTo(null)
      this.setSelectedRobotId(robotId)
    },
    async start(robot, data) {
      const emptyIndex = data.index
      if (!robot) return false
      const camera = data.data
      this.assignSlotCamera(emptyIndex, robot, camera)
      return this.startAssignedCamera(emptyIndex, robot, camera)
    },
    isFixedCameraRobot,
    async syncVideoSlots() {
      const recoveredCameraKeys = new Set()
      const previousPlayableStates = this.fixedCameraPlayableStates
      const nextPlayableStates = {}
      this.robots.forEach(robot => {
        if (!this.isFixedCameraRobot(robot)) return
        const playable = robot.status === 'online' && robot.enabled && robot.configReady && robot.playable !== false
        const cameras = robot.cameras || []
        cameras.forEach(camera => {
          const previous = previousPlayableStates[camera.key]
          nextPlayableStates[camera.key] = playable
          if (previous === false && playable) recoveredCameraKeys.add(camera.key)
        })
      })
      this.fixedCameraPlayableStates = nextPlayableStates
      for (const slotKey of Object.keys(this.ZQL_videosInfos)) {
        const videoInfo = this.ZQL_videosInfos[slotKey]
        if (!videoInfo?.robotId || !this.ZQL_playingSource[slotKey]) continue
        const robot = this.robots.find(item => String(item.robotId) === String(videoInfo.robotId))
        if (!robot) continue
        const camera = (robot.cameras || []).find(item => item.key === videoInfo.key)
        if (!camera) continue
        this.$set(this.ZQL_videosInfos, slotKey, {
          robot,
          ...videoInfo,
          ...camera,
          isPaused: videoInfo.isPaused
        })
        if (!recoveredCameraKeys.has(camera.key)) continue
        const current = this.cameras?.[camera.key] || camera
        const sessionActive = current.session && current.session.status !== 'CLOSED'
        if (sessionActive || current.room || current.loading || current.connecting) continue
        await this.startAssignedCamera(slotKey, robot, current)
        if (!this.isCameraIntended(camera.key)) {
          await this.stopCamera(this.stopCameraPayload(current, videoInfo))
        }
      }
    },
    rebindCameraTracks(cameras) {
      this.$nextTick(() => {
        const cameraList = cameras || []
        cameraList.forEach(camera => {
          if (!camera) return
          const video = document.getElementById(this.prefixId + camera.key)
          const audio = document.getElementById(this.prefixId + camera.key + '-audio')
          if (video) {
            if (!camera.remoteVideoTrack) {
              video.srcObject = null
              video.load()
            } else {
              camera.remoteVideoTrack.attach(video)
            }
          }
          if (audio) {
            if (!camera.remoteAudioTrack) {
              audio.srcObject = null
            } else {
              camera.remoteAudioTrack.attach(audio)
            }
          }
        })
      })
    },
    async test(data) {
      // console.log('-----------test------------', data, this.ZQL_videosInfos);
      let emptyKey = data.index

      // 四/六/九分屏：窗口间拖拽互换（未播放窗口不可作为源）
      if ([4, 6, 9].includes(this.splitType) && data.componentId === 'smallVideo') {
        if (emptyKey === data.slotKey) return
        const existObj = this.ZQL_videosInfos[emptyKey] ? Object.assign({}, this.ZQL_videosInfos[emptyKey]) : null
        const sourceObj = this.ZQL_videosInfos[data.slotKey] ? Object.assign({}, this.ZQL_videosInfos[data.slotKey]) : null
        if (!sourceObj || !sourceObj.key || !this.ZQL_playingSource[data.slotKey]) return
        if (this.ZQL_playingSource[emptyKey] && existObj) {
          this.$set(this.ZQL_videosInfos, data.slotKey, existObj)
          this.$set(this.ZQL_playingSource, data.slotKey, existObj.key)
        } else {
          this.$set(this.ZQL_videosInfos, data.slotKey, null)
          this.$set(this.ZQL_playingSource, data.slotKey, null)
        }
        this.$set(this.ZQL_videosInfos, emptyKey, sourceObj)
        this.$set(this.ZQL_playingSource, emptyKey, sourceObj.key)
        const key1 = this.ZQL_playingSource[emptyKey]
        const key2 = this.ZQL_playingSource[data.slotKey]
        this.syncSlotSelections()
        this.rebindCameraTracks([this.cameras?.[key1], this.cameras?.[key2]])
        return
      }

      // 填充 放入设备
      const robot = (this.robots || []).find(d => String(d.robotId) === String(data.data.robotId)) || data.data
      // 拖拽指定摄像头优先；否则默认本体相机，没有本体则取装备第一个数据源
      const cameraObj = (data?.data?.key && !data.data.cameras)
        ? data.data
        : pickDefaultCamera(robot, this.cameras)
      if (!cameraObj) return
      const camera = this.cameras?.[cameraObj.key] || cameraObj
      if (this.splitType === 1) {
        if (!this.ZQL_playingSource['slot_1']) {
          await this.start(robot, { index: 'slot_1', data: camera })
        } else {
          await this.replaceSlotCamera('slot_1', robot, camera)
        }
      } else {
        const playingSlot = Object.keys(this.ZQL_playingSource)
          .find(key => this.ZQL_playingSource[key] === camera.key)
        if (playingSlot) {
          await this.closeSlotCamera(playingSlot)
          return
        }
        emptyKey = emptyKey || this.findEmptySlotKey()
        if (emptyKey) {
          if (this.ZQL_playingSource[emptyKey]) {
            await this.replaceSlotCamera(emptyKey, robot, camera)
          } else {
            await this.start(robot, { index: emptyKey, data: camera })
          }
        }
      }
    },
    playPauseVideo(key) {
      const camera = this.ZQL_videosInfos[key]
      if (!camera) return
      const videoElement = document.getElementById(`${this.prefixId}${camera.key}`)
      if (!videoElement) return
      const pausing = !camera.isPaused
      this.$set(this.ZQL_videosInfos[key], 'isPaused', pausing)
      if (pausing) {
        this.pauseVideo(videoElement)
      } else {
        this.resumeVideo(videoElement, this.ZQL_videosInfos[key])
      }
    },
    pauseVideo(videoElement) {
      if (!videoElement) return
      videoElement.dataset.userPaused = '1'
      videoElement.pause()
    },
    resumeVideo(videoElement, camera) {
      if (!camera) return
      const current = this.cameras?.[camera.key] || camera
      this.rebindCameraTracks([current])
      if (!videoElement || !current.remoteVideoTrack) {
        this.recoverCameraPlayback(current).catch(() => {})
        return
      }
      delete videoElement.dataset.userPaused
      videoElement.play().catch(err => {
        // 播放策略或 DOM 异常只重新绑定当前 Track，不重启共享推流。
        console.error('播放视频失败:', err)
        this.rebindCameraTracks([this.cameras?.[camera.key] || current])
      })
    },
    // 刷新视频
    async refreshVideo(key) {
      const videoInfo = this.ZQL_videosInfos[key]
      if (!videoInfo) return
      const camera = this.cameras?.[videoInfo.key] || videoInfo
      if (!camera.session) {
        const robot = this.resolvePlaybackRobot(videoInfo)
        if (this.isFixedCameraRobot(robot)) {
          await this.startAssignedCamera(key, robot, camera)
          return
        }
      }
      this.$set(this.ZQL_videosInfos, key, { ...videoInfo, loading: true })
      try {
        await this.recoverCameraPlayback(camera)
        this.rebindCameraTracks([this.cameras?.[camera.key] || camera])
      } finally {
        const latest = this.cameras?.[camera.key] || camera
        this.$set(this.ZQL_videosInfos, key, { ...videoInfo, ...latest, loading: false })
      }
    },
    // 处理视频删除
    async handleRemoveVideo(key) {
      await this.closeSlotCamera(key)
    },
    // 处理刷新视频
    handleRefreshVideo(key) {
      this.refreshVideo(key);
    },
    updateData(robots) {
      Object.keys(this.ZQL_videosInfos).forEach(key => {
        const videoInfo = this.ZQL_videosInfos[key];
        if (videoInfo && videoInfo.robotId) {
          // 找到对应的机器人
          const robot = robots.find(r => r.robotId === videoInfo.robotId);
          if (robot) {
            // 找到对应的摄像头
            const camera = robot.cameras.find(c => c.key === videoInfo.key);
            if (camera) {
              // console.log('camera--------------------------------', camera.status, camera);
              // 更新视频信息，保持与原数据同步
              this.$set(this.ZQL_videosInfos, key, { robot, ...videoInfo, ...camera });
            }
          }
        }
      });
    },
    // 分屏切换：由 applySplitVideoChannels 统一保留/停流，避免与 watch 重复 stop
    async onSplitChange(val) {
      if (this.splitType === val) {
        this.rebindCameraTracks(this.currentVisibleCameras())
        return
      }
      const playingBeforeChange = this.orderedPlayingVideoInfos()
      this.manualChange = true
      this.setSplitType(val)
      this.fullscreenIndex = null
      try {
        await this.applySplitVideoChannels(playingBeforeChange, val)
      } finally {
        this.manualChange = false
      }
    },
    revertObjToArr(obj) {
      return Object.values(obj)
    },
    // 算法切换
    onAlgoChange(data) {
      const { deviceItem, alg } = data
      deviceItem.currentAlg = alg.alg_type;
      deviceItem.currentAlgName = alg.reserved_args.ch_name;
      // console.log(`设备 ${deviceItem.desc} 算法 -> ${alg?.type || '未选择'}`);
    },
    toggleFullscreen(data) {},
    // 仅当本视频区处于元素全屏时退出，不碰网页 Header / F11 全屏
    async exitPanelFullscreen() {
      if (!isElementFullscreen(this.prefixId)) return
      await exitFullscreen()
    },
    // 视频区全屏：与 VideoTool 相同，对指定元素 requestFullscreen，与网页全屏相互独立
    async toggleFullscreen1() {
      if (!isElementFullscreen(this.prefixId)) {
        await enterFullscreen(this.prefixId)
      } else {
        await this.exitPanelFullscreen()
      }
      await new Promise(resolve => requestAnimationFrame(resolve))
      this.syncPanelFullscreen()
    },
    handleFullscreenKeydown(event) {
      if (event.key === 'Escape' && this.isPageFullscreen) {
        this.exitPanelFullscreen()
      }
    },
    handleFullscreenChange() {
      this.syncPanelFullscreen()
    },
    syncPanelFullscreen() {
      this.isPageFullscreen = isElementFullscreen(this.prefixId)
    },
    orderedPlayingVideoInfos() {
      // 按槽位序号收集正在播放的画面（拖拽后 slot_1 可能为空，不能只看 slot_1）
      return Object.keys(this.ZQL_videosInfos)
        .sort((a, b) => Number(a.replace('slot_', '')) - Number(b.replace('slot_', '')))
        .map(key => {
          const info = this.ZQL_videosInfos[key]
          if (!info || !this.ZQL_playingSource[key]) return null
          return info
        })
        .filter(Boolean)
    },
    /** 视频框中实际展示的装备 ID（含离线占位，不以 activeCameras 为准） */
    getPlayingRobotIds() {
      return [...new Set(
        this.orderedPlayingVideoInfos()
          .map(info => info.robotId || info.robot?.robotId)
          .filter(Boolean)
          .map(id => String(id))
      )]
    },
    /** 按装备取视频框中正在展示的摄像头信息 */
    getPlayingCameraByRobotId(robotId) {
      const targetId = String(robotId)
      for (const slotKey of Object.keys(this.ZQL_videosInfos || {})) {
        const info = this.ZQL_videosInfos[slotKey]
        if (!info || !this.ZQL_playingSource[slotKey]) continue
        const id = info.robotId || info.robot?.robotId
        if (String(id) !== targetId) continue
        return {
          ...info,
          key: info.key || this.ZQL_playingSource[slotKey]
        }
      }
      return null
    },
    /**
     * 按任务装备列表同步视频框：
     * - 目标列表中已有的装备：复用，不关不重启
     * - 不在目标列表中的装备：关闭
     * - 目标列表中尚未展示的装备：打开
     * - 传入空数组：关闭全部
     */
    async syncTaskRobots(robotIds = []) {
      const targetIds = [...new Set((robotIds || []).map(id => String(id)).filter(Boolean))]
      const targetSet = new Set(targetIds)

      // 1) 先一次性撤销不在目标列表中的槽位意图，再异步停流，避免状态更新误触发固定摄像头恢复
      const takenCameras = []
      for (let i = 1; i <= this.splitType; i++) {
        const slotKey = `slot_${i}`
        const info = this.ZQL_videosInfos[slotKey]
        const playingKey = this.ZQL_playingSource[slotKey]
        if (!info || !playingKey) continue
        const robotId = String(info.robotId || info.robot?.robotId || '')
        if (robotId && targetSet.has(robotId)) continue
        takenCameras.push(this.takeSlotCamera(slotKey))
      }
      for (const taken of takenCameras) {
        try {
          await this.stopTakenCamera(taken)
        } catch (e) {}
      }

      // 2) 打开尚未展示的目标装备（已展示的直接复用）
      const playingIds = new Set(this.getPlayingRobotIds())
      for (const robotId of targetIds) {
        if (playingIds.has(robotId)) continue
        const robot = (this.robots || []).find(item => String(item.robotId) === robotId)
        if (!robot) continue
        const cameraObj = pickDefaultCamera(robot, this.cameras)
        if (!cameraObj) continue
        const camera = this.cameras?.[cameraObj.key] || cameraObj
        const emptyKey = this.findEmptySlotKey() || (this.splitType === 1 ? 'slot_1' : null)
        if (!emptyKey) break
        if (await this.start(robot, { index: emptyKey, data: camera })) {
          playingIds.add(robotId)
        }
      }

      this.syncSlotSelections()
    },
    currentVisibleCameras() {
      return Object.values(this.ZQL_videosInfos)
        .filter(Boolean)
        .map(camera => this.cameras?.[camera.key] || camera)
    },
    async applySplitVideoChannels(playingItems, splitType) {
      const nextVideosInfos = {}
      const nextPlayingSource = {}
      const retainedKeys = new Set()
      let items = (playingItems || []).filter(Boolean)

      // 切到一分屏：优先保留正在播放的 body，否则保留当前第一个播放中的流
      if (splitType === 1 && items.length > 1) {
        const bodyItem = items.find(item => isBodyCamera(item))
        if (bodyItem) {
          items = [bodyItem, ...items.filter(item => item.key !== bodyItem.key)]
        }
      }

      for (let i = 1; i <= splitType; i++) {
        const key = `slot_${i}`
        const item = items[i - 1]
        if (!item) {
          nextVideosInfos[key] = null
          nextPlayingSource[key] = null
          continue
        }
        const camera = this.cameras?.[item.key] || item
        const robot = item.robot || this.robots.find(robotItem => robotItem.robotId === item.robotId)
        nextVideosInfos[key] = { robot, ...item, ...camera }
        nextPlayingSource[key] = camera.key
        retainedKeys.add(camera.key)
      }

      // 先收缩播放意图，再异步停掉多余流；状态监听只能看到新宫格，
      // 不会把用户主动移除的固定摄像头误判为断流并重新拉起。
      this.ZQL_videosInfos = nextVideosInfos
      this.ZQL_playingSource = nextPlayingSource
      this.syncSlotSelections()
      this.slotDevices = new Array(splitType).fill(null)

      // 只停多余路，保留的视频流不 stop、不重启
      const removedItems = items.slice(splitType)
      for (const item of removedItems) {
        const camera = this.cameras?.[item.key] || item
        if (camera && camera.key && !retainedKeys.has(camera.key)) {
          await this.stopCamera(this.stopCameraPayload(camera, item))
        }
      }

      // 等待分屏 DOM 重建后再挂载 track
      await this.$nextTick()
      await this.$nextTick()
      this.rebindCameraTracks(this.currentVisibleCameras())
    },
    initSlots(splitType) {
      console.log('initSlots splitType', splitType);
      
      const newVideosInfos = {};
      const newPlayingSource = {};
      const newStatusArr = {};
      
      for (let i = 1; i <= splitType; i++) {
        const key = 'slot_' + i;
        newVideosInfos[key] = this.ZQL_videosInfos?.[key] || null;
        newPlayingSource[key] = this.ZQL_playingSource?.[key] || null;
        newStatusArr[key] = 'off';
        this.clearSlot(key);
      }
      
      this.ZQL_videosInfos = newVideosInfos;
      this.ZQL_playingSource = newPlayingSource;
      this.statusArr = newStatusArr;
      
      // 清空全屏状态
      this.fullscreenKey = null;
      this.isFullscreen = false;
      document.body.style.overflow = '';
    },
    clearSlot(key) {
      this.$set(this.ZQL_videosInfos, key, null);
      this.$set(this.ZQL_playingSource, key, null);
      this.syncSlotSelections()
    },
    // 按分屏顺序查找第一个空槽，避免 Object.keys 未初始化或乱序导致全部落到 slot_1
    findEmptySlotKey() {
      for (let i = 1; i <= this.splitType; i++) {
        const key = `slot_${i}`
        if (!this.ZQL_playingSource[key]) return key
      }
      return null
    },
  },

  watch: {
    // 分屏变化
    splitType: {
      async handler(newVal, oldVal) {
        console.log('=====================splitType=============================', oldVal, newVal);
        if (oldVal === newVal) return

        this.fullscreenIndex = null;
        this.singleId = null;
        this.slotDevices = new Array(newVal).fill(null);

        // 界面点击切换分屏：由 onSplitChange → applySplitVideoChannels 统一处理保留/停流，避免重复 stop
        if (this.manualChange) {
          return
        }

        // 外部切换也复用同一槽位收缩流程，只释放本视频区移除的画面，不扫描全局 activeCameras。
        const playingBeforeChange = this.orderedPlayingVideoInfos()
        await this.applySplitVideoChannels(playingBeforeChange, newVal)
      },
      // immediate: true
    },
    robots: {
      async handler() {
        await this.syncVideoSlots()
      },
      deep: true,
      immediate: true
    },
  }
}
</script>

<style lang="scss" scoped>
.card-title {
  width: 1364px;
}

.video-scale-stage {
  transform-origin: center center;
}

/* 页面全屏退出浮层：根节点右上角，不占布局行 */
.page-fullscreen-exit {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 20;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  .svg-icon {
    font-size: 22px;
  }

  &:hover {
    background: rgba(0, 0, 0, 0.65);
  }
}

.custom-video-div.is-page-fullscreen {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #061a33;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: none !important;

  .page-video-chrome {
    display: none;
  }

  .video-scale-stage {
    flex: 1 1 auto;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  /* 格子 CSS 铺满，文字/图标保持 VideoTool 原 px，不随画面放大 */
  ::v-deep .list {
    margin: 0 !important;
    padding: 0 !important;
    max-width: 100%;
    max-height: 100%;
    width: min(100%, calc(100vh * 16 / 9)) !important;
    height: min(100%, calc(100vw * 9 / 16)) !important;

    .item.one,
    .item.four,
    .item.nine,
    .item.six,
    .item.six-1 {
      width: 100% !important;
      height: 100% !important;
    }

    > * {
      min-width: 0;
      min-height: 0;
      height: 100%;
    }

    &.split-1 {
      display: flex !important;
      align-items: stretch;
      justify-content: stretch;

      > * {
        flex: 1 1 auto;
        width: 100%;
        height: 100%;
      }
    }

    &.split-4 {
      display: grid !important;
      grid-template-columns: 1fr 1fr;
      grid-template-rows: 1fr 1fr;
      gap: 8px;
    }

    &.split-9 {
      display: grid !important;
      grid-template-columns: 1fr 1fr 1fr;
      grid-template-rows: 1fr 1fr 1fr;
      gap: 8px;
    }

    &.split-6 {
      display: grid !important;
      grid-template-columns: 1fr 1fr 1fr;
      grid-template-rows: 1fr 1fr 1fr;
      gap: 8px;
      align-items: stretch;
      justify-items: stretch;

      .split-6-wrap,
      .split-6-wrap > .d-flex,
      .split-6-wrap > .d-flex:first-child > .ml26 {
        display: contents;
      }

      .split-6-wrap > .d-flex:first-child > div:first-child {
        grid-column: 1 / 3;
        grid-row: 1 / 3;
      }

      /* 无视频流时格子仍占满网格轨道，避免高度塌成 0 */
      .split-6-wrap > .d-flex > div,
      .split-6-wrap > .d-flex > .ml26 > div {
        width: 100%;
        height: 100%;
        min-height: 0;
        align-self: stretch;
      }

      .item {
        width: 100% !important;
        height: 100% !important;
        min-height: 100% !important;
      }

      .ml26,
      .ml28,
      .mt16,
      .mt20 {
        margin: 0 !important;
      }
    }
  }
}
</style>
