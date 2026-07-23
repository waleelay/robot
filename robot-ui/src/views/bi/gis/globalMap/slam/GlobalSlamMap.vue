<template>
  <div class="map-preview-box w100 h100 flx-center">
    <template v-if="hasPreview">
      <div ref="viewportRef" class="map-preview-viewport flx-center w100 h100" @wheel="handleWheel" style="background: #112B4D;">
        <div class="map-preview-stage" :style="stageStyle" @mousedown="handleMouseDown">
          <!-- <img v-if="imageUrl" ref="imageRef" class="map-preview-image" :src="imageUrl" alt="地图预览" style="width: 100%; height: 100%;" /> -->
          <template v-if="imageUrl">
            <canvas
              ref="canvas"
              title="右键点击可设置临时点位"
              @contextmenu.prevent="onCanvasClick"
              @click="handleCanvasBlankClick"
              class="map-preview-image"
              style="width: 100%; height: 100%;"
            />
            <svg
              v-if="imageUrl"
              ref="overlayRef"
              class="map-preview-overlay"
              :viewBox="`0 0 ${map.previewWidth} ${map.previewHeight}`"
              @click="handleMapClick"
            >
              <template v-if="showPath">
                <polyline v-if="polylinePoints" :points="polylinePoints" class="map-preview-path" />
                <g
                  v-for="point in drawablePoints"
                  :key="point.id"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y})`"
                  class="map-preview-point"
                  :class="{ selected: point.id === selectedPointId, inPath: activePathPointIds.includes(point.id), hovered: point.id === hoveredPointId }"
                  @mouseenter="hoveredPointId = point.id"
                  @mouseleave="hoveredPointId = null"
                  @click.stop="handlePointClick(point)"
                >
                  <circle r="2" />
                  <text v-if="showLabels || point.id === selectedPointId" x="9" y="-9">{{ point.pointName }}</text>
                  <title>{{ point.pointName }} / {{ point.pointCode || point.id }} / 任务路径点</title>
                </g>
              </template>
              <g
                v-for="robot in drawableRobots"
                :key="robot.robotId"
                :transform="`translate(${robot.pixel.x}, ${robot.pixel.y})${showSmall ? '' : ` scale(${1 / zoom})`}`"
                class="map-preview-robot custom-point"
                :class="[robot.statusClass, { 'show-icon': isRobotHighlighted(robot.robotId) }]"
                @click.stop="handleRobotClick($event, robot)"
              >
                <!-- 选中光圈：Figma 50×54，相对 tip 偏移 (-25, -47) -->
                <image
                  v-if="isRobotHighlighted(robot.robotId)"
                  :href="robotSelectedHalo"
                  x="-25"
                  y="-47"
                  width="50"
                  height="54"
                  class="robot-selected-halo"
                  pointer-events="none"
                />
                <!-- 默认/选中底图：Figma 39.68×42.97 ≈ 40×43 -->
                <image :href="robotBg" x="-20" y="-43" width="40" height="43" class="robot-bg" />
                <!-- 装备图标：Figma 机器狗展示尺寸 23.017×16.525 -->
                <image
                  :href="robot.iconUrl"
                  :x="-robot.displayIconWidth / 2"
                  :y="-(23 + robot.displayIconHeight / 2)"
                  :width="robot.displayIconWidth"
                  :height="robot.displayIconHeight"
                  class="robot-type-icon"
                />
                <!-- 选中四角：Figma 66×6（含发光 viewBox 72×12），上下各一，底边垂直翻转 -->
                <template v-if="isRobotHighlighted(robot.robotId)">
                  <image
                    :href="robotSelectedCorners"
                    x="-36"
                    y="-56"
                    width="72"
                    height="12"
                    class="robot-selected-corners"
                    pointer-events="none"
                  />
                  <g transform="matrix(1 0 0 -1 0 20)" pointer-events="none">
                    <image
                      :href="robotSelectedCorners"
                      x="-36"
                      y="4"
                      width="72"
                      height="12"
                      class="robot-selected-corners"
                    />
                  </g>
                </template>
                <foreignObject
                  class="robot-name-fo"
                  :x="-robot.nameWidth / 2"
                  y="0"
                  :width="robot.nameWidth"
                  height="20"
                >
                  <div
                    xmlns="http://www.w3.org/1999/xhtml"
                    class="robot-name-pill"
                  >{{ robot.name }}</div>
                </foreignObject>
                <foreignObject
                  v-if="!showSmall"
                  class="robot-status-fo"
                  :x="robot.statusBgX"
                  y="22"
                  :width="robot.statusBgWidth"
                  height="20"
                >
                  <div
                    xmlns="http://www.w3.org/1999/xhtml"
                    class="robot-status-pill"
                    :class="robot.statusClass"
                  >{{ robot.customStatusName }}</div>
                </foreignObject>
              </g>
            </svg>
          </template>
          <el-empty v-else :description="previewImageStatus" />
          <span class="start-point" :style="startPointStyle">起始地</span>
          <div
            v-show="locationPoint"
            :style="locationStyle"
            class="location flx-center flex-column"
            ref="pointLocationRef"
          >
            <img src="./../../../../../assets/images/new-bi/address1.png" alt="位置" class="wp40 hp46" />
            <input
              ref="locationLabelInput"
              v-model="locationLabel"
              class="location-label-input"
              type="text"
              :style="{ width: locationLabelWidth }"
              @blur="saveLocationLabel"
              @keydown.enter.prevent="blurLocationLabel"
            />
          </div>
          <!-- 放在 stage 内，随 zoom / translate 同步，避免缩放后 getBoundingClientRect 错位 -->
          <div v-show="showContextMenu" class="context-menu d-flex" style="width: inherit;" :style="contextMenuStyle">
            <div class="flx-center div1">
              <span>派遣设备前往该点</span>
              <svg-icon icon-class="right" class="ml4" />
            </div>
            <div v-if="normalRobots.length" class="div2 ml10">
              <div v-for="robot in normalRobots" :key="robot.robotId" class="item flx-justify-between p6" :class="robot.statusClass">
                <span class="name pl14" :class="robot.statusClass">{{ robot.name }}</span>
                <span class="oper ml4" :class="robot.statusClass" @click="handleSelectRobot(robot)">
                  {{robot.customStatusName === '空闲中' ? '立即派遣' : '终止任务'}}
                </span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="operList.length" class="map-operation">
          <div class="operation">
            <div
              v-for="(item, index) in operList"
              :key="item.key"
              @click="handleClickTool(item)"
              class="operation-item flx-center flex-column"
              :class="{ 'is-active': showPath && item.key === 'path' }"
            >
              <template v-if="index === 10">
                <el-popover placement="left" trigger="hover" popper-class="custom-popover map-layer-popover">
                  <template slot="reference">
                    <svg-icon :icon-class="item.icon" />
                    <span>{{ item.name }}</span>
                  </template>
                  <el-radio-group v-model="tabIndex" class="custom-radio-group flex with-border vertical">
                    <el-radio v-for="item in tabList" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
                  </el-radio-group>
                </el-popover>
              </template>
              <template v-else>
                <svg-icon :icon-class="item.icon" />
                <span class="mt4">{{ item.name }}</span>
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>
    <el-empty v-else description="当前地图暂无预览，请先生成地图预览" />
    <div v-if="showNotice" class="notice-modal flx-center">
      <div class="notice-modal__mask" @click="closeNotice"></div>
      <div class="notice-modal__dialog">
        <p class="notice-modal__text">当前选择装备正在【任务中】，是否终止任务？进行新任务</p>
        <div class="notice-modal__btns">
          <button type="button" class="notice-modal__btn" @click="closeNotice">取消</button>
          <button type="button" class="notice-modal__btn is-primary" @click="addTask">确定</button>
        </div>
      </div>
    </div>
    <RobotControlPart ref="robotControlPartRef" />
    <RobotCarControlPart ref="robotCarControlPartRef" />
    <Robot1 :showAnimate="true" ref="robot1Ref" @showControlPart="showControlPart" @showPath="showPathArea" @showSlam="showSlam" @showArea="showDashedArea" @clear="clear" />
    <!-- <Slam ref="slamRef" /> -->
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import addPointTask from './add-point-task.js'
import Robot1 from '../popup/Robot1.vue'
import RobotControlPart from '../popup/RobotControlPart.vue'
import RobotCarControlPart from '../popup/RobotCarControlPart.vue'
// import Slam from '../../gis/globalMap/popup/Slam.vue'
import { ROBOT_TYPE_INFO } from '@/constants/robot.js'
import { addTaskByPoint } from '@/api/new-bi.js';

const ROBOT_BG = require('@/assets/images/new-bi/robot-bg.svg')
const ROBOT_SELECTED_HALO = require('@/assets/images/new-bi/robot-selected-halo.svg')
const ROBOT_SELECTED_CORNERS = require('@/assets/images/new-bi/robot-selected-corners.svg')
// Figma 机器狗图标展示 23.017×16.525，对应源图 38×28
const ROBOT_ICON_SCALE_X = 23.017 / 38
const ROBOT_ICON_SCALE_Y = 16.525 / 28

export default {
  name: 'BiPatrolSlam',
  mixins: [addPointTask],
  components: { Robot1, RobotControlPart, RobotCarControlPart },
  props: {
    map: { type: Object, default: null },
    // points: { type: Array, default: () => [] },
    selectedPointId: { type: Number, default: null },
    pathPointIds: { type: Array, default: () => [] },
    showLabels: { type: Boolean, default: false }
  },
  data() {
    return {
      zoom: 3,
      minZoomValue: 0.5,
      maxZoomValue: 3,
      resizeObserver: null,
      robotBg: ROBOT_BG,
      robotSelectedHalo: ROBOT_SELECTED_HALO,
      robotSelectedCorners: ROBOT_SELECTED_CORNERS,
      imageUrl: '',
      previewImageStatus: '地图预览加载中',
      imageObjectUrl: '',
      imageLoadSeq: 0,
      hoveredPointId: null,
      // 拖拽相关
      offsetX: 0,
      offsetY: 0,
      isDragging: false,
      dragMoved: false,
      startX: 0,
      startY: 0,
      startOffsetX: 0,
      startOffsetY: 0,
      popupVisible: false,
      popupOffset: { x: 0, y: 0 },
      activeRobotId: null,
      operList: [
        // {
        //   icon: 'map-path',
        //   name: '路径',
        //   key: 'path',
        //   action: 'renderPath'
        // },
        // {
        //   icon: 'map-location',
        //   name: '定位',
        //   key: 'location',
        //   action: 'backCenter'
        // },
        // {
        //   icon: 'map-zoom-in',
        //   name: '放大',
        //   key: 'zoomIn',
        //   action: 'zoomIn'
        // },
        // {
        //   icon: 'map-zoom-out',
        //   name: '缩小',
        //   key: 'zoomOut',
        //   action: 'zoomOut'
        // },
      ],
      showPath: false,
      showNotice: false,
      showContextMenu: false,
      locationLabel: '临时点',
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotLocation', 'slamOfRobot', 'showRobotIds', 'taskPathPoints']),
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    // biPatrolMonitor：精简图标，不展示状态信息（与 GIS 一致）
    showSmall() {
      return this.$route.name === 'biPatrolMonitor'
    },
    popupStyle() {
      return this.$route.name === 'biIndex' ? {
        left: this.popupOffset.x + 'px',
        top: this.popupOffset.y + 'px',
        display: this.popupVisible ? 'block' : 'none'
      } : {}
    },
    normalRobots() {
      return Object.values(this.robotBaseInfo || {}).filter(item => item.status === 'online') || []
    },
    hasPreview() {
      return !!this.map?.previewWidth &&
        !!this.map?.previewHeight &&
        this.map?.resolution !== undefined &&
        this.map?.originX !== undefined &&
        this.map?.originY !== undefined &&
        this.map?.originYaw !== undefined
    },
    stageStyle() {
      return {
        width: `${Number(this.map?.previewWidth || 0) * this.zoom}px`,
        height: `${Number(this.map?.previewHeight || 0) * this.zoom}px`,
        transform: `translate(${this.offsetX}px, ${this.offsetY}px)`
      }
    },
    selectedShowRobotId() {
      const ids = Array.isArray(this.showRobotIds) ? this.showRobotIds : []
      return ids.length === 1 ? ids[0] : null
    },
    // 选中单个装备时：按 runningTaskId + 当前 map.id 匹配任务路径
    activeTaskPathData() {
      if (!this.selectedShowRobotId) return null
      const robot = this.robotBaseInfo?.[this.selectedShowRobotId] || {}
      const taskId = robot.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return null
      const pathData = this.taskPathPoints?.[taskId]
      if (!pathData || !Array.isArray(pathData.pathPoints) || !pathData.pathPoints.length) return null
      if (String(pathData.mapId) !== String(this.map?.id)) return null
      return pathData
    },
    canTogglePath() {
      if (this.selectedShowRobotId) return !!this.activeTaskPathData
      return Array.isArray(this.map?.points) && this.map.points.length > 0
    },
    activePoints() {
      if (this.selectedShowRobotId && this.activeTaskPathData) {
        return this.activeTaskPathData.pathPoints
      }
      return this.map?.points || []
    },
    activePathPointIds() {
      if (this.selectedShowRobotId && this.activeTaskPathData) {
        return this.activeTaskPathData.pathPoints
          .map(point => point.id ?? point.mapPointId)
          .filter(id => id !== undefined && id !== null)
      }
      if (Array.isArray(this.pathPointIds) && this.pathPointIds.length) return this.pathPointIds
      return (this.map?.points || []).map(point => point.id).filter(id => id !== undefined && id !== null)
    },
    drawablePoints() {
      return this.activePoints
        .map((point) => {
          const id = point.id ?? point.mapPointId
          const coordinateX = point.coordinateX ?? point.x
          const coordinateY = point.coordinateY ?? point.y
          const pixel = this.mapPointToPixel({
            ...point,
            coordinateX,
            coordinateY
          }, this.map)
          return pixel ? { ...point, id, pixel } : null
        })
        .filter(Boolean)
    },
    drawableRobots() {
      // const robots = this.robotBaseInfo?.['test111'] ? [this.robotBaseInfo?.['test111']] : []
      // biPatrolMonitor：按当前地图关联装备展示；其他场景同样以 slamOfRobot 为准
      const robots = this.slamOfRobot?.[String(this.map?.id)]?.robots || []
      return robots.map(baseRobot => {
        const robot = { ...baseRobot, ...(this.robotBaseInfo?.[baseRobot.robotId] || {}) }
        let location = { ...(baseRobot.location || {}), ...(this.robotLocation?.[baseRobot.robotId] || {}) }
        // if (this.startPoint) {
        //   location = { ...location, x: -2.627, y: 3.787 }
        // }
        const coordinateX = location.x ?? location.coordinateX
        const coordinateY = location.y ?? location.coordinateY
        if (coordinateX === undefined || coordinateX === null || coordinateY === undefined || coordinateY === null) return null
        const pixel = this.mapPointToPixel({ coordinateX, coordinateY }, this.map)
        if (!pixel) return null
        const typeInfo = ROBOT_TYPE_INFO[robot.type] || ROBOT_TYPE_INFO.default
        const statusText = robot.customStatusName || robot.statusName
        const statusClass = robot.statusClass
        const isCamouflage = statusClass === 'blue' || statusClass === 'green'
        const name = robot.name || robot.deviceName || robot.robotId
        const nameWidth = Math.max(44, Math.ceil(String(name).length * 11) + 8)
        const statusBgWidth = Math.max(56, String(statusText).length * 12 + (isCamouflage ? 36 : 28))
        const statusBgX = -statusBgWidth / 2
        const displayIconWidth = typeInfo.width * ROBOT_ICON_SCALE_X
        const displayIconHeight = typeInfo.height * ROBOT_ICON_SCALE_Y
        return {
          ...robot,
          name,
          pixel,
          nameWidth,
          iconUrl: require(`@/assets/images/new-bi/${typeInfo.img}.png`),
          iconWidth: typeInfo.width,
          iconHeight: typeInfo.height,
          displayIconWidth,
          displayIconHeight,
          statusBgWidth,
          statusBgX
        }
      }).filter(Boolean)
    },
    polylinePoints() {
      return this.activePathPointIds
        .map((id) => this.drawablePoints.find((point) => String(point.id) === String(id))?.pixel)
        .filter(Boolean)
        .map((pixel) => `${pixel.x},${pixel.y}`)
        .join(" ")
    },
    contextMenuStyle() {
      // 菜单在 stage 内：直接用地图像素 * zoom，与临时点同一坐标系，缩放/平移自动同步
      if (!this.showContextMenu || !this.locationPoint) {
        return { display: 'none' }
      }
      const zoom = Number(this.zoom) || 1
      const pixelX = Number(this.locationPoint.pixelX)
      const pixelY = Number(this.locationPoint.pixelY)
      const pointX = Number.isFinite(pixelX) ? pixelX * zoom : 0
      const pointY = Number.isFinite(pixelY) ? pixelY * zoom : 0
      return {
        display: 'flex',
        left: `${pointX + 65}px`,
        top: `${pointY - 27}px`
      }
    },
    noticeDialogStyle() {
      const { offsetX, offsetY, zoom, showNotice } = this
      if (!showNotice) return {}
      void offsetX
      void offsetY
      void zoom
      if (!this.locationPoint || !this.$refs.canvas) {
        return {
          left: '50%',
          top: '50%',
          transform: 'translate(-50%, -50%)'
        }
      }
      const canvasRect = this.$refs.canvas.getBoundingClientRect()
      return {
        left: `${canvasRect.left + (this.locationPoint.x || 0) + 40}px`,
        top: `${canvasRect.top + (this.locationPoint.y || 0) - 20}px`,
        transform: 'none'
      }
    },
    locationLabelWidth() {
      const len = String(this.locationLabel || '').length || 3
      return `${Math.max(48, len * 14 + 8)}px`
    },
    // 返回需要监听的对象，依赖 map 和 hasPreview
    previewSource() {
      const id = this.map && this.map.id;
      const cacheKey = (this.map && (this.map.previewGeneratedAt || this.map.previewFileId));
      return {
        id,
        cacheKey,
        hasPreview: this.hasPreview,
      };
    },
  },
  watch: {
    previewSource: {
      immediate: true,
      handler({ id, cacheKey, hasPreview }, oldVal) {
        // 切换 SLAM 地图：清空路径/画线/打点，重新加载底图与装备
        if (oldVal && String(oldVal.id) !== String(id)) {
          this.resetSlamDrawState()
        }
        this.revokeImageUrl()
        this.previewImageStatus = '地图预览加载中'
        if (!hasPreview || id === undefined || id === null) return
        const preUrl = (process.env.VUE_APP_BASE_ORIGIN || window.location.origin || '').replace(/\/$/, '')
        this.imageUrl = `${preUrl}/api/v1/management/maps/${id}/preview-image?t=${encodeURIComponent(cacheKey || '')}`
        this.$nextTick(() => {
          this.updateZoomBounds(true)
          this.observeViewport()
          if (this.$refs.canvas) {
            this.canvas = this.$refs.canvas
            this.ctx = this.canvas.getContext('2d')
            this.loadMap()
          }
        })
      },
    },
    showRobotIds: {
      deep: true,
      handler() {
        if (this.showPath && !this.canTogglePath) {
          this.showPath = false
        }
      }
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.updateZoomBounds(true)
      this.observeViewport()
    })
  },
  methods: {
    ...mapActions('websocketExtraData', ['setShowRobotIds']),
    isRobotHighlighted(robotId) {
      return (this.showRobotIds || []).some(id => String(id) === String(robotId))
    },
    handleRobotClick(event, robot) {
      // 点击装备时还原 SLAM 绘制状态（临时点、路径线），仅保留装备信息
      this.resetSlamDrawState()
      if (this.$route.name === 'biIndex') {
        if (this.activeRobotId === robot.robotId) {
          this.closePopup()
        } else {
          this.activeRobotId = robot.robotId
          this.popupVisible = true
          this.$nextTick(() => this.updatePopupPosition(robot))
        }
      } else if (this.activeRobotId === robot.robotId) {
        this.activeRobotId = ''
      } else {
        this.activeRobotId = robot.robotId
      }
      // Robot1.show -> clear([robotId]) -> setShowRobotIds，与 GIS 一致
      this.$refs.robot1Ref?.show(event, robot)
    },
    resetSlamDrawState() {
      this.showPath = false
      this.locationPoint = null
      this.showContextMenu = false
      this.showNotice = false
      this.locationLabel = '临时点'
      this.endPoint = null
      this.startPoint = null
      this.unloadedPath = []
      this.loadedPath = []
      this.lastDrawnPaths = null
      if (typeof this.reset === 'function') {
        this.reset()
      }
    },
    togglePath(visible) {
      if (!this.canTogglePath) {
        this.showPath = false
        return
      }
      this.showPath = typeof visible === 'boolean' ? visible : !this.showPath
    },
    closePopup() {
      this.popupVisible = false
      this.activeRobotId = null
    },
    getScaleWrapper() {
      return this.$el && this.$el.closest && this.$el.closest('.screen-wrapper')
    },
    getScaleContext() {
      const wrapper = this.getScaleWrapper()
      if (!wrapper) {
        return {
          left: 0,
          top: 0,
          scaleX: 1,
          scaleY: 1,
          width: window.innerWidth,
          height: window.innerHeight
        }
      }
      const rect = wrapper.getBoundingClientRect()
      const scaleX = rect.width && wrapper.offsetWidth ? rect.width / wrapper.offsetWidth : 1
      const scaleY = rect.height && wrapper.offsetHeight ? rect.height / wrapper.offsetHeight : 1
      return {
        left: rect.left,
        top: rect.top,
        scaleX: scaleX || 1,
        scaleY: scaleY || 1,
        width: wrapper.offsetWidth || window.innerWidth,
        height: wrapper.offsetHeight || window.innerHeight
      }
    },
    viewportRectToScaleRect(rect) {
      const context = this.getScaleContext()
      return {
        left: (rect.left - context.left) / context.scaleX,
        top: (rect.top - context.top) / context.scaleY,
        width: rect.width / context.scaleX,
        height: rect.height / context.scaleY
      }
    },
    getElementSizeInScaleWrapper(el) {
      if (!el) return { width: 0, height: 0 }
      const rect = this.viewportRectToScaleRect(el.getBoundingClientRect())
      return {
        width: el.offsetWidth || rect.width,
        height: el.offsetHeight || rect.height
      }
    },
    updatePopupPosition(robot) {
      if (this.$route.name !== 'biIndex') return
      const target = robot || this.drawableRobots.find(item => item.robotId === this.activeRobotId)
      if (!this.popupVisible || !target?.pixel) return
      const stage = this.$el?.querySelector?.('.map-preview-stage')
      if (!stage) return
      const stageRect = this.viewportRectToScaleRect(stage.getBoundingClientRect())
      const robotEl = this.$refs.robot1Ref && this.$refs.robot1Ref.$el
      const robotSize = this.getElementSizeInScaleWrapper(robotEl)
      const context = this.getScaleContext()
      const maxLeft = Math.max(0, context.width - robotSize.width)
      const maxTop = Math.max(0, context.height - robotSize.height)
      const left = stageRect.left + target.pixel.x * this.zoom + 29
      const top = stageRect.top + target.pixel.y * this.zoom - robotSize.height - 28
      this.popupOffset = {
        x: Math.min(Math.max(0, left), maxLeft),
        y: Math.min(Math.max(0, top), maxTop)
      }
    },
    showControlPart(visible) {
      const type = this.selectedRobot?.type
      const isDog = type === '四足机器狗' || type === '四足机器人' || type === 'ROBOT_DOG'
      const controlRef = isDog ? this.$refs.robotControlPartRef : this.$refs.robotCarControlPartRef
      const nextVisible = typeof visible === 'boolean' ? visible : !controlRef?.visible
      controlRef?.show(nextVisible)
    },
    showPathArea() {
      this.showPath = !this.showPath      
    },
    showDashedArea() {
      // SLAM 地图暂无区域图层，保留接口以兼容 Robot1
    },
    showSlam(visible) {
      // this.$refs.slamRef?.show(visible)
    },
    clear(robotId) {
      this.setShowRobotIds(robotId || [])
      this.showControlPart(false)
      this.showSlam(false)
      if (!robotId || !robotId.length) {
        this.closePopup()
      }
    },
    handleSelectRobot(robot) {
      this.robotId = robot.robotId
      const pixel = this.getPixelByRobotId(robot.robotId)
      if (!pixel) return null
      const startPoint = [parseInt(pixel.x), parseInt(pixel.y)]
      if (!this.endPoint) return
      // 开启安全区域判断时：先校验是否存在可通行自定义路径
      if (this.enableSafetyAreaCheck) {
        const path = this.getPaths(startPoint, this.endPoint)
        if (!path) return
      }
      if (robot.customStatusName !== '空闲中') {
        this.showNotice = true
        this.closeContextMenu()
        return
      }
      this.addTask(startPoint)
    },
    closeContextMenu() {
      this.showContextMenu = false
    },
    closeNotice() {
      this.showNotice = false
    },
    async addTask(startPoint) {
      const pixel = { x: this.endPoint?.[0] || 0, y: this.endPoint?.[1] || 0 }
      const { coordinateX, coordinateY, coordinateZ } = this.pixelToMapPoint(pixel, this.map)
      const data = {
        robotId: this.robotId,
        x: coordinateX,
        y: coordinateY,
        yaw: coordinateZ,
      }
      const res = await addTaskByPoint(data)
      console.log('派遣任务结果', res)
      this.showNotice = false
      this.setStartPoint(startPoint)
      this.closeContextMenu()
      // if (res.code === 0) {
      //   this.$message.success('任务派遣成功')
      //   this.closeContextMenu()
      // } else {
      //   this.$message.error(res.msg || '任务派遣失败')
      // }
    },
    saveLocationLabel() {
      const next = String(this.locationLabel || '').trim()
      this.locationLabel = next || '临时点'
      if (this.locationPoint) {
        this.locationPoint = {
          ...this.locationPoint,
          label: this.locationLabel
        }
      }
    },
    blurLocationLabel() {
      this.$refs.locationLabelInput?.blur?.()
    },
    changeMapZoom({ method } = {}) {
      if (typeof this[method] === 'function') {
        this[method]()
      }
    },
    handleClickTool(item) {
      this[item.action]();
    },
    renderPath() {
      this.togglePath()
    },
    updateZoomBounds(reset = false) {
      const viewport = this.$refs.viewportRef
      const mapWidth = Number(this.map?.previewWidth || 0)
      const mapHeight = Number(this.map?.previewHeight || 0)
      if (!viewport || !mapWidth || !mapHeight) return
      const wasAtMax = Math.abs(this.zoom - this.maxZoomValue) < 0.01
      const widthZoom = viewport.clientWidth / mapWidth
      const heightZoom = viewport.clientHeight / mapHeight
      this.maxZoomValue = Math.max(0.1, Math.min(widthZoom, heightZoom))
      this.minZoomValue = Math.max(0.1, this.maxZoomValue * 0.25)
      this.zoom = reset || wasAtMax
        ? this.maxZoomValue
        : Math.max(this.minZoomValue, Math.min(this.maxZoomValue, this.zoom))
      this.zoom = Number(this.zoom.toFixed(3))
      this.offsetX = 0
      this.offsetY = 0
      this.$nextTick(() => this.syncCanvasResolution())
    },
    observeViewport() {
      if (this.resizeObserver || typeof ResizeObserver === 'undefined' || !this.$refs.viewportRef) return
      this.resizeObserver = new ResizeObserver(() => this.updateZoomBounds())
      this.resizeObserver.observe(this.$refs.viewportRef)
    },
    minZoom() {
      return this.minZoomValue
    },
    zoomIn() {
      const step = Math.max(0.1, this.maxZoomValue * 0.05)
      this.zoom = Math.min(this.maxZoomValue, Number((this.zoom + step).toFixed(3)))
      this.$nextTick(() => this.syncCanvasResolution())
    },
    zoomOut() {
      const step = Math.max(0.1, this.maxZoomValue * 0.05)
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - step).toFixed(3)))
      this.$nextTick(() => this.syncCanvasResolution())
    },
    resetView() {
      this.zoom = this.maxZoomValue
      this.offsetX = 0
      this.offsetY = 0
      this.$nextTick(() => this.syncCanvasResolution())
    },
    backCenter() {
      this.offsetX = 0
      this.offsetY = 0
    },
    handleMouseDown(e) {
      if (e.target.closest('.map-preview-point') || e.target.closest('.map-preview-robot') || e.target.closest('.map-operation') || e.target.closest('.context-menu') || e.target.closest('.location')) return
      this.isDragging = true
      this.dragMoved = false
      this.startX = e.clientX
      this.startY = e.clientY
      this.startOffsetX = this.offsetX
      this.startOffsetY = this.offsetY
      document.addEventListener('mousemove', this.handleMouseMove)
      document.addEventListener('mouseup', this.handleMouseUp)
    },
    handleMouseMove(e) {
      if (!this.isDragging) return
      if (Math.abs(e.clientX - this.startX) > 3 || Math.abs(e.clientY - this.startY) > 3) {
        this.dragMoved = true
      }
      // 当前视图内自由拖拽
      this.offsetX = this.startOffsetX + e.clientX - this.startX
      this.offsetY = this.startOffsetY + e.clientY - this.startY
    },
    handleMouseUp() {
      this.isDragging = false
      document.removeEventListener('mousemove', this.handleMouseMove)
      document.removeEventListener('mouseup', this.handleMouseUp)
    },
    handleWheel(e) {
      e.preventDefault()
      if (e.deltaY > 0) this.zoomOut()
      else this.zoomIn()
    },
    revokeImageUrl() {
      if (this.imageObjectUrl) {
        URL.revokeObjectURL(this.imageObjectUrl);
        this.imageObjectUrl = null;
      }
      this.imageUrl = "";
      this.coloredCanvas = null;
    },
    mapPointToPixel(point, map) {
      if (!this.hasPreview || !map) return null;
      const width = Number(map.previewWidth);
      const height = Number(map.previewHeight);
      const resolution = Number(map.resolution);
      const originX = Number(map.originX);
      const originY = Number(map.originY);
      const originYaw = Number(map.originYaw);
      if (!width || !height || !resolution) return null;
      const dx = Number(point.coordinateX) - originX;
      const dy = Number(point.coordinateY) - originY;
      const cos = Math.cos(originYaw);
      const sin = Math.sin(originYaw);
      const localX = dx * cos + dy * sin;
      const localY = -dx * sin + dy * cos;
      return { x: localX / resolution, y: height - localY / resolution };
    },
    pixelToMapPoint(pixel, map) {
      if (!this.hasPreview || !map) return null;
      const width = Number(map.previewWidth);
      const height = Number(map.previewHeight);
      const resolution = Number(map.resolution);
      const originX = Number(map.originX);
      const originY = Number(map.originY);
      const originYaw = Number(map.originYaw);
      if (!width || !height || !resolution) return null;
      const cos = Math.cos(originYaw);
      const sin = Math.sin(originYaw);
      const localX = pixel.x * resolution;
      const localY = (height - pixel.y) * resolution;
      const dx = localX * cos - localY * sin;
      const dy = localX * sin + localY * cos;
      return {
        coordinateX: Number((originX + dx).toFixed(3)),
        coordinateY: Number((originY + dy).toFixed(3)),
        coordinateZ: 0,
        pixelX: Number(pixel.x.toFixed(1)),
        pixelY: Number(pixel.y.toFixed(1))
      };
    },
    eventToPixel(event) {
      // const rect = this.$refs?.overlayRef?.getBoundingClientRect();
      const rect = this.$refs?.canvas?.getBoundingClientRect();
      if (!rect || !this.map?.previewWidth || !this.map?.previewHeight) return null;
      return {
        x: ((event.clientX - rect.left) / rect.width) * Number(this.map.previewWidth),
        y: ((event.clientY - rect.top) / rect.height) * Number(this.map.previewHeight)
      };
    },
    // 有临时点但尚未生成临时路径时，点击空白处隐藏临时点与右键菜单
    clearTempPointIfNoPath() {
      if (!this.locationPoint) return
      const hasTempPath = !!this.startPoint ||
        (this.unloadedPath && this.unloadedPath.length > 0) ||
        (this.loadedPath && this.loadedPath.length > 0)
      if (hasTempPath) return
      this.locationPoint = null
      this.showContextMenu = false
      this.endPoint = null
    },
    handleCanvasBlankClick() {
      // 拖拽平移地图后不触发清除
      if (this.dragMoved) return
      this.clearTempPointIfNoPath()
    },
    handleMapClick(event) {
      this.clearTempPointIfNoPath()
      const pixel = this.eventToPixel(event);
      const point = this.pixelToMapPoint(pixel, this.map);
      // if (point) this.$emit("map-click", point);
    },
    handlePointClick(point) {
      const nearby = this.drawablePoints.filter((item) => {
        const dx = item.pixel.x - point.pixel.x;
        const dy = item.pixel.y - point.pixel.y;
        return Math.sqrt(dx * dx + dy * dy) <= 8;
      });
      if (nearby.length <= 1) {
        this.$emit("point-click", point);
        return;
      }
      const currentIndex = nearby.findIndex((item) => item.id === this.selectedPointId);
      this.$emit("point-click", nearby[(currentIndex + 1 + nearby.length) % nearby.length]);
    }
  },
  beforeDestroy() {
    this.imageLoadSeq += 1
    this.resizeObserver?.disconnect()
    this.revokeImageUrl();
  }
}
</script>

<style lang="scss">
.map-preview-box {
  position: relative;
  width: 100%;
  height: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}
.map-preview-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  max-width: 100%;
  min-width: 0;
  // background: rgb(243, 240, 210);
  background: #cdcdcd;
  overflow: hidden;
  cursor: grab;
  will-change: transform;
  &:active {
    cursor: grabbing;
  }
  .map-preview-stage {
    position: relative;
    top: 0;
    left: 0;
    cursor: grab;
    // will-change: left, top, width, height;
    will-change: transform;
    transform-origin: center center;
    &:active {
      cursor: grabbing;
    }
    & > svg {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      overflow: visible;
      // cursor: crosshair;
      .map-preview-point {
        pointer-events: auto;
        cursor: pointer;
         circle {
          fill: #10b981;
          stroke: #fff;
          stroke-width: 0.5;
          filter: drop-shadow(0 2px 4px rgba(16, 185, 129, .4));
        }
        text {
          font-size: 12px;
          paint-order: stroke;
          stroke: #fff;
          stroke-width: 1px;
          fill: #0f172a;
        }
        &.inPath circle {
          fill: #2563eb;
          filter: drop-shadow(0 2px 4px rgba(37, 99, 235, .4));
        }
        &.hovered circle {
          stroke: #0f172a;
          stroke-width: 3;
        }
      }
      .map-preview-path {
        fill: none;
        stroke: #2563eb;
        stroke-width: 3;
        stroke-linecap: round;
        stroke-linejoin: round;
        vector-effect: non-scaling-stroke;
        filter: drop-shadow(0 1px 2px rgba(37, 99, 235, .4));
      }
      .map-preview-robot {
        pointer-events: auto;
        cursor: pointer;
        .robot-selected-halo,
        .robot-selected-corners {
          pointer-events: none;
        }
        .robot-name-fo {
          overflow: visible;
        }
        .robot-name-pill {
          box-sizing: border-box;
          width: 100%;
          height: 20px;
          padding: 2px 4px;
          color: #FFF;
          font-family: "Microsoft YaHei";
          font-size: 11px;
          font-weight: 600;
          line-height: 16px;
          text-align: center;
          white-space: nowrap;
          text-shadow:
            1px 0 rgba(0, 19, 48, 0.9),
            -1px 0 rgba(0, 19, 48, 0.9),
            0 1px rgba(0, 19, 48, 0.9),
            0 -1px rgba(0, 19, 48, 0.9);
        }
        .robot-status-fo {
          overflow: visible;
        }
        .robot-status-pill {
          box-sizing: border-box;
          position: relative;
          width: 100%;
          height: 20px;
          padding: 0 6px;
          color: #FFF;
          font-family: "Microsoft YaHei";
          font-size: 11px;
          line-height: 20px;
          // text-align: center;
          border-radius: 4px;
          white-space: nowrap;
          padding-left: 22px;
          // 默认原点（orange/gray）
          &::before {
            position: absolute;
            top: 7px;
            left: 10px;
            width: 6px;
            height: 6px;
            border-radius: 50%;
            content: '';
            background: #FFF;
          }
          &.blue {
            background: #0070C6;
          }
          &.green {
            background: #187905;
          }
          // blue/green：隐藏圆点，显示伪装盾牌 ::before
          &.blue,
          &.green {
            &::before {
              top: 4px;
              left: 6px;
              width: 12px;
              height: 12px;
              border-radius: 0;
              background: #FFF;
              -webkit-mask: url("data:image/svg+xml,%3Csvg width='16' height='16' viewBox='0 0 16 16' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M13.0448 2.73493C12.925 2.73927 12.807 2.74094 12.6915 2.74094H12.691C9.65333 2.74094 8.31513 1.34872 8.30398 1.33672L8.00044 1.00439L7.69647 1.33671C7.68322 1.35127 6.27116 2.84788 2.95516 2.73493L2.5293 2.72039V9.03171C2.5293 10.6841 3.08097 13.0942 7.85145 14.9382L8.00003 14.9957L8.14853 14.9382C12.919 13.0942 13.4707 10.6841 13.4707 9.03171V2.72039L13.0448 2.73493ZM7.45605 10.9028L4.27145 8.08097L5.11759 7.39532L6.81084 8.64464C6.81084 8.64464 9.14881 6.22593 11.4061 5.09724L11.7285 5.46044C11.7285 5.46044 8.90714 7.7985 7.45605 10.9028Z' fill='currentColor'/%3E%3C/svg%3E") center/contain no-repeat;
              mask: url("data:image/svg+xml,%3Csvg width='16' height='16' viewBox='0 0 16 16' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M13.0448 2.73493C12.925 2.73927 12.807 2.74094 12.6915 2.74094H12.691C9.65333 2.74094 8.31513 1.34872 8.30398 1.33672L8.00044 1.00439L7.69647 1.33671C7.68322 1.35127 6.27116 2.84788 2.95516 2.73493L2.5293 2.72039V9.03171C2.5293 10.6841 3.08097 13.0942 7.85145 14.9382L8.00003 14.9957L8.14853 14.9382C12.919 13.0942 13.4707 10.6841 13.4707 9.03171V2.72039L13.0448 2.73493ZM7.45605 10.9028L4.27145 8.08097L5.11759 7.39532L6.81084 8.64464C6.81084 8.64464 9.14881 6.22593 11.4061 5.09724L11.7285 5.46044C11.7285 5.46044 8.90714 7.7985 7.45605 10.9028Z' fill='currentColor'/%3E%3C/svg%3E") center/contain no-repeat;
            }
          }
          &.orange {
            background: #D85A00;
          }
          &.gray {
            border: 1px solid #0061B1;
            background: #272727;
          }
        }
      }
    }
  }
  
  // 地图工具
  .map-operation {
    position: absolute;
    top: 8px !important;
    right: 8px !important;
    width: 34px;
    .operation {
      // width: 32px;
      padding: 10px 6px;
      border-radius: 4px;
      border: 1px solid #3479BE;
      background: #00000080;
      .operation-item {
        color: #EEF7FF;
        font-family: "Microsoft YaHei";
        font-size: 10px;
        cursor: pointer;
        .svg-icon {
          font-size: 16px;
        }
        span {
          font-size: 10px;
          height: 18px;
          line-height: 18px;
        }
        &:hover, &.is-active {
          color: #00CBFD;
        }
        & + .operation-item {
          position: relative;
          margin-top: 12px;
          &::before {
            position: absolute;
            top: -5.5px;
            left: 0;
            display: block;
            width: 20px;
            height: 1px;
            background: rgba(255, 255, 255, 0.30);
            content: '';
          }
        }
      }
    }
  }
}
.location {
  position: absolute;
  z-index: 1;
  pointer-events: auto;

  .location-label-input {
    box-sizing: border-box;
    min-width: 48px;
    max-width: 160px;
    padding: 2px 4px;
    border: none;
    border-radius: 2px;
    outline: none;
    background: #0062AE;
    color: #FFF;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 14px;
    line-height: 17.517px;
    text-align: center;

    &:focus {
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55);
    }
  }
}
.start-point {
  position: absolute;
  padding: 0 4px;
  border-radius: 2px;
  background: #0D9F31;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 14px;
  line-height: 22px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 1;
}
.context-menu {
  position: absolute;
  z-index: 20;
  pointer-events: auto;
  .div1, .div2 {
    position: relative;
    padding: 9px 10px;
    border-radius: 4px;
    border: 2px solid #000;
    background: rgba(0, 19, 48, 0.9);
    box-shadow: inset 0 0 20px 0 rgba(1, 80, 170, 0.8);
    backdrop-filter: blur(5px);
    &::before {
      background: linear-gradient(90deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) left top no-repeat,
        linear-gradient(180deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) left top no-repeat,
        linear-gradient(270deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) right bottom no-repeat,
        linear-gradient(0deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) right bottom no-repeat;
      background-size: 80px 2.5px, 2.5px 80px, 80px 2.5px, 2.5px 80px;
      background-repeat: no-repeat;
      border-radius: 4px;
    }
    &::after {
      background: linear-gradient(270deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) right top no-repeat,
        linear-gradient(180deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) right top no-repeat,
        linear-gradient(90deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) left bottom no-repeat,
        linear-gradient(0deg, #038EFF 0%, rgba(36, 151, 252, 0) 100%) left bottom no-repeat;
      background-size: 80px 2.5px, 2.5px 80px, 80px 2.5px, 2.5px 80px;
      background-repeat: no-repeat;
      border-radius: 4px;
    }
  }
  .div1 {
    height: fit-content;
    color: #FFF;
    font-family: "Alibaba PuHuiTi";
    font-size: 16px;
    line-height: 18px; /* 75% */
    letter-spacing: 0.857px;
    .svg-icon {
      font-size: 14px
    }
  }
  .div2 {
    .name {
      position: relative;
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 16px;
      line-height: 24px; /* 75% */
      letter-spacing: 0.857px;
      &::before {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        position: absolute;
        top: 7px;
        left: 0;
        content: '';
      }
      &.blue::before {
        background: #0062AE;
      }
      &.green::before {
        background: #23AB08;
      }
    }
    .oper {
      padding: 5px 10px;
      border-radius: 100px;
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      line-height: 12px; /* 100% */
      letter-spacing: 0.857px;
      cursor: default;
      &.blue {
        border: 1px solid #2A86F3;
        background: rgba(9, 45, 72, 0.50);
        box-shadow: 0 0 10px 0 #2A86F3 inset;
      }
      &.orange {
        border: 1px solid #FF9000;
        background: #1B1A18;
        box-shadow: 0 0 10px 0 #F3452A inset;
      }
    }
  }
}

.notice-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  pointer-events: none;

  &__mask {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    pointer-events: auto;
  }

  &__dialog {
    position: fixed;
    // width: 473px;
    min-height: 108px;
    padding: 20px 20px 24px;
    border: 1px solid #2A86F3;
    background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 6.03%, rgba(4, 23, 62, 0.32) 56.39%, rgba(7, 45, 94, 0.31) 101.39%, rgba(4, 62, 151, 0.40) 109.49%);
    backdrop-filter: blur(15px);
    box-shadow: inset 0 0 20px 0 rgba(42, 134, 243, 0.35);
    pointer-events: auto;
  }

  &__text {
    margin: 0 0 24px;
    color: #FFF;
    text-align: center;
    font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
    font-size: 16px;
    line-height: 22px;
    letter-spacing: 0.857px;
  }

  &__btns {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 20px;
  }

  &__btn {
    min-width: 70px;
    padding: 6px 10px;
    border: 1px solid #2A86F3;
    border-radius: 2px;
    background: rgba(9, 45, 72, 0.50);
    box-shadow: inset 0 0 10px 0 #2A86F3;
    color: #FFF;
    font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
    font-size: 12px;
    line-height: 12px;
    letter-spacing: 0.857px;
    cursor: pointer;

    &:hover {
      opacity: 0.9;
    }
    &:not(.is-disabled) {
      &:active {
        color: #0BF9FE;
        box-shadow: 0 0 10px 3px #0BF9FE inset;
      }
    }
  }
}

.slam-map-error-message.el-message {
  top: 100px !important;
  min-width: auto;
  padding: 20px;
  border: 1px solid #FF0202;
  border-radius: 2px;
  background: rgba(72, 9, 9, 0.5) !important;
  backdrop-filter: blur(5px);
  box-shadow: inset 0 0 20px 0 #B30000;

  .el-message__content {
    padding: 0;
    color: #FFF;
    font-family: "Microsoft YaHei", sans-serif;
    font-size: 16px;
    line-height: 17.517px;
  }

  .el-message__icon {
    display: none;
  }

  .slam-error-content {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .slam-error-icon {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20' fill='none'%3E%3Cpath d='M10 2L18 16H2L10 2Z' fill='%23FFC107' stroke='%23FFC107' stroke-width='1'/%3E%3Cpath d='M10 7V11' stroke='%23000' stroke-width='1.5' stroke-linecap='round'/%3E%3Ccircle cx='10' cy='14' r='1' fill='%23000'/%3E%3C/svg%3E") center/contain no-repeat;
  }
}
</style>
