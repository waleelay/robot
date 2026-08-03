<template>
  <div
    class="map-preview-box w100 h100 flx-center"
    :class="{ 'fit-visible-area': !!visibleLayout }"
    :style="visibleAreaStyle"
  >
    <template v-if="hasPreview">
      <div
        ref="viewportRef"
        class="map-preview-viewport flx-center w100 h100"
        :class="{ 'is-map-loading': mapLoading }"
        @wheel="handleWheel"
        style="background: #112B4D;"
      >
        <div class="map-preview-stage" :style="stageStyle" @mousedown="handleMouseDown">
          <!-- <img v-if="imageUrl" ref="imageRef" class="map-preview-image" :src="imageUrl" alt="地图预览" style="width: 100%; height: 100%;" /> -->
          <template v-if="imageUrl">
            <canvas
              ref="canvas"
              :title="enableAddPoint ? '右键点击可设置临时点位' : undefined"
              @contextmenu.prevent="onCanvasContextMenu"
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
              <!-- Robot1「显示路径」：polyline + 路径点；MapTool「点位」：地图点位，互不影响 -->
              <polyline v-if="showPolyline && polylinePoints" :points="polylinePoints" class="map-preview-path" />
              <!-- MapTool「点位」：逻辑不变 -->
              <template v-if="showPath">
                <g
                  v-for="point in drawablePoints"
                  :key="point.id"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-map-tool"
                  :class="{
                    selected: point.id === selectedPointId,
                    inPath: isPointInPath(point),
                    hovered: point.id === hoveredPointId
                  }"
                  @mouseenter="hoveredPointId = point.id"
                  @mouseleave="hoveredPointId = null"
                  @click.stop="handlePointClick(point)"
                >
                  <rect x="-14" y="-28" width="28" height="50" fill="transparent" />
                  <image
                    :href="mapPointMarker"
                    x="-10"
                    y="-26"
                    width="20"
                    height="26"
                    class="map-point-marker"
                  />
                  <foreignObject
                    class="map-point-name-fo"
                    :x="-point.nameWidth / 2"
                    y="4"
                    :width="point.nameWidth"
                    height="20"
                  >
                    <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name">{{ point.pointName }}</div>
                  </foreignObject>
                  <title>{{ point.pointName }} / {{ point.pointCode || point.id }} / 任务路径点</title>
                </g>
              </template>
              <!-- 装备任务路径点：按顺序标注 Figma 右上角序号，相对点位图标偏移 -->
              <template v-if="showPolyline">
                <g
                  v-for="point in drawablePathPoints"
                  :key="`path-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-path-point"
                  :class="{
                    selected: point.id === selectedPointId,
                    hovered: point.id === hoveredPointId
                  }"
                  @mouseenter="hoveredPointId = point.id"
                  @mouseleave="hoveredPointId = null"
                  @click.stop="handlePointClick(point)"
                >
                  <!-- MapTool 未开点位时，路径点仍展示图标+名称 -->
                  <template v-if="!showPath">
                    <rect x="-14" y="-28" width="28" height="50" fill="transparent" />
                    <image
                      :href="mapPointMarker"
                      x="-10"
                      y="-26"
                      width="20"
                      height="26"
                      class="map-point-marker"
                    />
                    <foreignObject
                      class="map-point-name-fo"
                      :x="-point.nameWidth / 2"
                      y="4"
                      :width="point.nameWidth"
                      height="20"
                    >
                      <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name">{{ point.pointName }}</div>
                    </foreignObject>
                  </template>
                  <!-- Figma：20×20 序号，相对点位图标右上角 -->
                  <g class="path-seq-badge" transform="translate(6, -40)" pointer-events="none">
                    <image :href="pathPointBadge" x="0" y="0" width="20" height="20" />
                    <text x="10" y="14.5" text-anchor="middle" class="path-seq-text">{{ point.pathIndex }}</text>
                  </g>
                  <title>{{ point.pointName }} / 路径序号 {{ point.pathIndex }}</title>
                </g>
              </template>
              <g
                v-for="robot in drawableRobots"
                :key="robot.robotId"
                :transform="`translate(${robot.pixel.x}, ${robot.pixel.y})${showSmall ? '' : ` scale(${1 / zoom})`}`"
                class="map-preview-robot custom-point"
                :class="[robot.statusClass, { 'show-icon': isRobotHighlighted(robot.robotId), 'is-static': !enableRobotClick }]"
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
                <span class="oper ml4" :class="robot.customStatusName === '空闲中' ? 'blue' : 'orange '" @click="handleSelectRobot(robot)">
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
        <transition name="slam-map-loading-fade">
          <div v-if="mapLoading" class="slam-map-loading flx-center flex-column" @wheel.prevent @mousedown.stop>
            <div class="slam-map-loading__spinner" aria-hidden="true"></div>
            <p class="slam-map-loading__text">{{ previewImageStatus || '地图加载中' }}</p>
          </div>
        </transition>
      </div>
    </template>
    <Empty v-else width="126px" :opacity="0.7" textColor="#BEE1FF" text="当前地图暂无预览" />
    <RobotControlPart ref="robotControlPartRef" />
    <RobotCarControlPart ref="robotCarControlPartRef" />
    <Robot1 :showAnimate="showAnimate" :style="popupStyle" ref="robot1Ref" @showControlPart="showControlPart" @showPath="showPathArea" @showSlam="showSlam" @showArea="showDashedArea" @clear="clear" />
    <!-- <Slam ref="slamRef" /> -->
  </div>
</template>

<script>
import Empty from '../../../components/Empty.vue'
import { mapActions, mapState } from 'vuex';
import addPointTask from './add-point-task.js'
import Robot1 from '../popup/Robot1.vue'
import RobotControlPart from '../popup/RobotControlPart.vue'
import RobotCarControlPart from '../popup/RobotCarControlPart.vue'
// import Slam from '../../gis/globalMap/popup/Slam.vue'
import { ROBOT_TYPE_INFO } from '@/constants/robot.js'
import { addTaskByPoint, previewImageBlob } from '@/api/new-bi.js';

const ROBOT_BG = require('@/assets/images/new-bi/robot-bg.svg')
const ROBOT_SELECTED_HALO = require('@/assets/images/new-bi/robot-selected-halo.svg')
const ROBOT_SELECTED_CORNERS = require('@/assets/images/new-bi/robot-selected-corners.svg')
// MapTool 点位图标：Figma 20×26，底部尖端为锚点
const MAP_POINT_MARKER = require('@/assets/images/new-bi/map-point-marker.svg')
// 任务路径序号底图：Figma 20×20，#456393 / #8EBAFF
const PATH_POINT_BADGE = require('@/assets/images/new-bi/path-point-badge.svg')
// Figma 机器狗图标展示 23.017×16.525，对应源图 38×28
const ROBOT_ICON_SCALE_X = 23.017 / 38
const ROBOT_ICON_SCALE_Y = 16.525 / 28

export default {
  name: 'BiPatrolSlam',
  mixins: [addPointTask],
  components: { Robot1, RobotControlPart, RobotCarControlPart, Empty },
  props: {
    map: { type: Object, default: null },
    // points: { type: Array, default: () => [] },
    selectedPointId: { type: Number, default: null },
    pathPointIds: { type: Array, default: () => [] },
    showLabels: { type: Boolean, default: false },
    // 是否允许右键添加临时点位（实时监控等场景关闭）
    enableAddPoint: { type: Boolean, default: true },
    // 是否允许点击装备弹窗/切换选中（second 监控页关闭，避免回到 first）
    enableRobotClick: { type: Boolean, default: true },
    // 侧栏是否收缩；与 visibleLayout 配合，将地图限制在未遮挡区域
    collapse: { type: Boolean, default: false },
    // 'home' 指挥中心（顶栏+左右侧）| 'panorama' 全景（顶栏+左侧）| '' 不限制
    visibleLayout: { type: String, default: '' }
  },
  data() {
    return {
      zoom: 1,
      minZoomValue: 0.25,
      maxZoomValue: 3,
      // 适应当前视口的默认比例（侧栏展开时小于最大）
      defaultZoomValue: 1,
      resizeObserver: null,
      robotBg: ROBOT_BG,
      robotSelectedHalo: ROBOT_SELECTED_HALO,
      robotSelectedCorners: ROBOT_SELECTED_CORNERS,
      mapPointMarker: MAP_POINT_MARKER,
      pathPointBadge: PATH_POINT_BADGE,
      imageUrl: '',
      previewImageStatus: '地图预览加载中',
      mapLoading: false,
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
      // Robot1「显示路径」：仅控制任务路径 polyline，与 MapTool 点位无关
      showPolyline: false,
      showArea: false,
      showContextMenu: false,
      locationLabel: '临时点',
      collapseZoomTimer: null,
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotLocation', 'slamOfRobot', 'showRobotIds', 'taskPathPoints']),
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    currenRouteName() {
      return this.$route.name
    },
    // biPatrolMonitor：精简图标，不展示状态信息（与 GIS 一致）
    showSmall() {
      return this.currenRouteName === 'biPatrolMonitor'
    },
    showAnimate() {
      return this.currenRouteName !== 'biIndex'
    },
    popupStyle() {
      return this.currenRouteName === 'biIndex' ? {
        left: this.popupOffset.x + 'px',
        top: this.popupOffset.y + 'px',
        display: this.popupVisible ? 'block' : 'none'
      } : {};
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
      const mapW = Number(this.map?.previewWidth || 0)
      const mapH = Number(this.map?.previewHeight || 0)
      // 宽高共用同一 zoom，保证等比、不变形
      return {
        width: `${mapW * this.zoom}px`,
        height: `${mapH * this.zoom}px`,
        transform: `translate(${this.offsetX}px, ${this.offsetY}px)`
      }
    },
    selectedShowRobotId() {
      const ids = Array.isArray(this.showRobotIds) ? this.showRobotIds : []
      return ids.length === 1 ? ids[0] : null
    },
    // 选中单个装备时：按 runningTaskId + 当前 map.id 匹配任务路径（供 polyline 使用）
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
    // MapTool「点位」是否可切换：仅看当前地图是否有点位
    canTogglePath() {
      return Array.isArray(this.map?.points) && this.map.points.length > 0
    },
    // MapTool 点位始终使用当前地图点位，不受装备选中影响
    activePoints() {
      return this.map?.points || []
    },
    activePathPointIds() {
      if (this.showPolyline && this.activeTaskPathData) {
        return this.activeTaskPathData.pathPoints
          .map(point => point.id ?? point.mapPointId)
          .filter(id => id !== undefined && id !== null)
      }
      if (Array.isArray(this.pathPointIds) && this.pathPointIds.length) return this.pathPointIds
      return (this.map?.points || [])
        .map(point => point.id ?? point.mapPointId)
        .filter(id => id !== undefined && id !== null)
    },
    drawablePoints() {
      return this.toDrawablePoints(this.activePoints)
    },
    // Robot1 路径点：仅来自装备关联的任务路径数据，带路径顺序号
    drawablePathPoints() {
      if (!this.activeTaskPathData) return []
      return this.toDrawablePoints(this.activeTaskPathData.pathPoints)
        .map((point, index) => ({ ...point, pathIndex: index + 1 }))
    },
    // MapTool 开点位 → 全量地图点；仅 Robot1 开路径 → 任务路径点（模板已拆分，此计算属性保留兼容）
    overlayPoints() {
      if (this.showPath) return this.drawablePoints
      if (this.showPolyline) return this.drawablePathPoints
      return []
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
    // Robot1 路径线：仅来自装备关联的任务路径数据
    polylinePoints() {
      if (!this.showPolyline || !this.activeTaskPathData) return ''
      const points = this.drawablePathPoints
      if (points.length < 2) return ''
      return points.map((point) => `${point.pixel.x},${point.pixel.y}`).join(' ')
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
    // 侧栏/顶栏遮挡下的可见区域：默认铺满未遮挡区域
    // 设计稿 1920：侧栏约 401px（与 map-operation right:395 对齐）；收缩后留操作边距
    visibleAreaStyle() {
      if (!this.visibleLayout) return {}
      // 与左右面板宽度（334+外边距）及 map-operation 定位保持一致
      const sideExpanded = 401
      const sideCollapsed = 20
      if (this.visibleLayout === 'home') {
        // 指挥中心：除去顶部导航与两侧；收缩则占满，否则居中于两侧之间
        // map-div 有 margin-top:-55px，top:55 对齐顶栏下方可见区
        return {
          left: `${this.collapse ? sideCollapsed : sideExpanded}px`,
          right: `${this.collapse ? 38 : sideExpanded}px`,
          top: '55px',
          bottom: '20px',
          width: 'auto',
          height: 'auto'
        }
      }
      if (this.visibleLayout === 'panorama') {
        // 全景地图：仅考虑顶部（外层 pt80）与左侧收缩；右侧留地图工具栏空间
        return {
          left: `${this.collapse ? sideCollapsed : sideExpanded}px`,
          right: '70px',
          top: '0',
          bottom: '0',
          width: 'auto',
          height: 'auto'
        }
      }
      return {}
    },
    // 两侧收缩时的边距（用于计算最大缩放）
    collapsedVisibleInsets() {
      if (!this.visibleLayout) return { left: 0, right: 0, top: 0, bottom: 0 }
      if (this.visibleLayout === 'home') {
        return { left: 20, right: 38, top: 55, bottom: 20 }
      }
      if (this.visibleLayout === 'panorama') {
        return { left: 20, right: 70, top: 0, bottom: 0 }
      }
      return { left: 0, right: 0, top: 0, bottom: 0 }
    },
  },
  watch: {
    collapse() {
      // 侧栏动画结束后按新可见区域重算缩放
      if (this.collapseZoomTimer) clearTimeout(this.collapseZoomTimer)
      this.$nextTick(() => {
        this.collapseZoomTimer = setTimeout(() => {
          this.collapseZoomTimer = null
          this.updateZoomBounds(true)
        }, 520)
      })
    },
    previewSource: {
      immediate: true,
      async handler({ id, cacheKey, hasPreview }, oldVal) {
        // 父组件常因心跳展开新 map 对象；id/cacheKey/hasPreview 未变时跳过，避免周期性 loadMap 闪屏
        const samePreview = !!(oldVal
          && String(oldVal.id) === String(id)
          && String(oldVal.cacheKey ?? '') === String(cacheKey ?? '')
          && !!oldVal.hasPreview === !!hasPreview)
        if (samePreview) return

        const switched = !!(oldVal && String(oldVal.id) !== String(id))
        // 切换 SLAM 地图：清空装备选中/操作框、路径画线，并先对齐缩放
        if (switched) {
          this.clearRobotSelectionUI()
          this.resetSlamDrawState()
          this.invalidateMapBitmap()
        }
        this.previewImageStatus = '地图加载中...'
        if (!hasPreview || id === undefined || id === null) {
          this.mapLoading = false
          this.revokeImageUrl()
          return
        }
        this.coloredCanvas = null
        // 切换或首次/预览更新时展示加载态
        if (switched || !oldVal) {
          this.mapLoading = true
          this.updateZoomBounds(true)
        } else {
          this.mapLoading = true
        }

        // 预览图接口需 Bearer；用 axios 拉取 blob，再交给 Image 渲染
        const loadSeq = (this.imageLoadSeq = (this.imageLoadSeq || 0) + 1)
        try {
          const res = await previewImageBlob(id, cacheKey)
          if (loadSeq !== this.imageLoadSeq) return
          const blob = res && res.data instanceof Blob ? res.data : res
          if (!(blob instanceof Blob)) {
            throw new Error('地图预览响应无效')
          }
          const nextUrl = URL.createObjectURL(blob)
          if (loadSeq !== this.imageLoadSeq) {
            URL.revokeObjectURL(nextUrl)
            return
          }
          if (this.imageObjectUrl) {
            URL.revokeObjectURL(this.imageObjectUrl)
          }
          this.imageObjectUrl = nextUrl
          this.imageUrl = nextUrl
          this.$nextTick(() => {
            if (loadSeq !== this.imageLoadSeq) return
            // 首屏 viewport 可能尚未就绪，再对齐一次；切换时保持重置
            this.updateZoomBounds(switched || !oldVal)
            this.observeViewport()
            if (this.$refs.canvas) {
              this.canvas = this.$refs.canvas
              this.ctx = this.canvas.getContext('2d')
              this.loadMap()
            } else {
              this.mapLoading = false
            }
          })
        } catch (error) {
          if (loadSeq !== this.imageLoadSeq) return
          this.mapLoading = false
          this.previewImageStatus = '地图加载失败'
          console.error('加载 SLAM 地图预览失败', error)
        }
      },
    },
    // 选中/打开装备不再关闭 MapTool 点位；无任务路径时仅关闭 polyline
    showRobotIds: {
      deep: true,
      handler() {
        if (this.showPolyline && !this.activeTaskPathData) {
          this.showPolyline = false
        }
      }
    },
    activeTaskPathData(val) {
      if (!val && this.showPolyline) this.showPolyline = false
    }
  },
  async created() {
    this.setShowRobotIds([])
  },
  mounted() {
    this.$nextTick(() => {
      this.updateZoomBounds(true)
      this.observeViewport()
    })
  },
  methods: {
    ...mapActions('websocketExtraData', ['setShowRobotIds']),
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    /** 切换地图时清空选中装备及 Robot1 / 遥控等操作框 */
    clearRobotSelectionUI() {
      // 先关遥控：selectedRobot 清空后 showControlPart 会选错面板
      this.$refs.robotControlPartRef?.show?.(false)
      this.$refs.robotCarControlPartRef?.show?.(false)
      // Robot1.show(null) 会关弹窗、清选中并发 clear([])
      if (this.$refs.robot1Ref?.show) {
        this.$refs.robot1Ref.show(null)
      } else {
        this.setSelectedRobotId('')
        this.clear([])
      }
      this.showSlam(false)
      this.activeRobotId = null
      this.closePopup()
      this.closeContextMenu()
      this.$emit('clear-selection')
    },
    toDrawablePoints(points) {
      return (Array.isArray(points) ? points : [])
        .map((point) => {
          const id = point.id ?? point.mapPointId
          const coordinateX = point.coordinateX ?? point.x
          const coordinateY = point.coordinateY ?? point.y
          const pixel = this.mapPointToPixel({
            ...point,
            coordinateX,
            coordinateY
          }, this.map)
          if (!pixel) return null
          const pointName = point.pointName || point.name || point.pointCode || String(id)
          // Figma 点位名 14px，左右各留 4px
          const nameWidth = Math.max(28, Math.ceil(String(pointName).length * 14) + 8)
          return { ...point, id, pixel, pointName, nameWidth }
        })
        .filter(Boolean)
    },
    isPointInPath(point) {
      if (!point) return false
      const ids = this.activePathPointIds || []
      if (!ids.length) return false
      return ids.some((id) =>
        String(id) === String(point.id) || String(id) === String(point.mapPointId)
      )
    },
    isRobotHighlighted(robotId) {
      return (this.showRobotIds || []).some(id => String(id) === String(robotId))
    },
    onCanvasContextMenu(event) {
      if (!this.enableAddPoint) return
      this.onCanvasClick(event)
    },
    handleRobotClick(event, robot) {
      if (!this.enableRobotClick) return
      // 点击装备时仅还原临时打点/派遣状态，保留 MapTool 点位渲染
      this.resetSlamDrawState({ keepMapToolPath: true })
      if (this.currenRouteName === 'biIndex') {
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
    resetSlamDrawState({ keepMapToolPath = false } = {}) {
      // MapTool 点位由 MapTool 控制；打开/关闭装备时保持不变
      if (!keepMapToolPath) this.showPath = false
      this.showPolyline = false
      this.locationPoint = null
      this.showContextMenu = false
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
    // 切换地图时丢弃旧位图，避免 stage 尺寸变化时旧图被拉伸/压缩
    invalidateMapBitmap() {
      this.imageLoadSeq += 1
      this.img = null
      this.W = 0
      this.H = 0
      this.coloredCanvas = null
      this.isLoaded = false
      this.grid = null
      if (this.canvas) {
        this.canvas.width = 1
        this.canvas.height = 1
      }
    },
    togglePath(visible) {
      if (!this.canTogglePath) {
        this.showPath = false
        return
      }
      // MapTool 只控制点位，不联动 polyline
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
      if (this.currenRouteName !== 'biIndex') return
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
      // 关闭时两侧都关，避免选中态已清空时关错面板
      if (visible === false) {
        this.$refs.robotControlPartRef?.show?.(false)
        this.$refs.robotCarControlPartRef?.show?.(false)
        return
      }
      const type = this.selectedRobot?.type
      const isDog = type === '四足机器狗' || type === '四足机器人' || type === 'ROBOT_DOG'
      const controlRef = isDog ? this.$refs.robotControlPartRef : this.$refs.robotCarControlPartRef
      const nextVisible = typeof visible === 'boolean' ? visible : !controlRef?.visible
      controlRef?.show(nextVisible)
    },
    showPathArea(visible) {
      // Robot1「显示/隐藏路径」同时控制 polyline 与路径点位
      this.showPolyline = typeof visible === 'boolean' ? visible : !this.showPolyline
    },
    showDashedArea() {
      // SLAM 地图暂无区域图层，保留接口以兼容 Robot1
      this.showArea = !this.showArea
    },
    showSlam(visible) {
      // this.$refs.slamRef?.show(visible)
    },
    clear(robotId) {
      this.setShowRobotIds(robotId || [])
      this.showControlPart(false)
      this.showSlam(false)
      if (!robotId || !robotId.length) {
        // 关闭 Robot1：关掉路径线，保留 MapTool 点位
        this.showPolyline = false
        this.closePopup()
      }
    },
    async handleSelectRobot(robot) {
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
        this.closeContextMenu()
        try {
          await this.$primaryConfirm({
            title: '提示',
            message: '当前选择装备正在【任务中】，是否终止任务？进行新任务',
            confirmText: '确定',
            cancelText: '取消',
            onConfirm: async () => {
              await this.addTask(startPoint)
            }
          })
        } catch (error) {
          // 用户取消
        }
        return
      }
      this.addTask(startPoint)
    },
    closeContextMenu() {
      this.showContextMenu = false
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
      const curW = viewport.clientWidth
      const curH = viewport.clientHeight
      if (!curW || !curH) return

      const wasAtDefault = Math.abs(this.zoom - this.defaultZoomValue) < 0.01
      const wasAtMax = Math.abs(this.zoom - this.maxZoomValue) < 0.01

      // 当前视口：默认等比适配（不变形）
      const defaultZoom = Math.max(0.1, Math.min(curW / mapWidth, curH / mapHeight))

      // 最大比例：按两侧收缩后的可视宽高计算（更宽），仍取 min 保证等比不超出该边界
      const collapsedSize = this.getCollapsedViewportSize()
      const maxZoom = Math.max(
        0.1,
        Math.min(collapsedSize.width / mapWidth, collapsedSize.height / mapHeight)
      )

      this.defaultZoomValue = defaultZoom
      this.maxZoomValue = Math.max(defaultZoom, maxZoom)
      this.minZoomValue = Math.max(0.1, defaultZoom * 0.25)

      if (reset || wasAtDefault) {
        this.zoom = this.defaultZoomValue
      } else if (wasAtMax) {
        this.zoom = this.maxZoomValue
      } else {
        this.zoom = Math.max(this.minZoomValue, Math.min(this.maxZoomValue, this.zoom))
      }
      this.zoom = Number(this.zoom.toFixed(3))
      this.offsetX = 0
      this.offsetY = 0
      this.$nextTick(() => this.syncCanvasResolution())
    },
    // 两侧收缩时的可视区域尺寸（最大缩放基准）
    getCollapsedViewportSize() {
      const viewport = this.$refs.viewportRef
      if (!this.visibleLayout) {
        return {
          width: viewport?.clientWidth || 0,
          height: viewport?.clientHeight || 0
        }
      }
      const parent = this.$el?.parentElement
      const parentW = parent?.clientWidth || viewport?.clientWidth || 0
      const parentH = parent?.clientHeight || viewport?.clientHeight || 0
      const insets = this.collapsedVisibleInsets
      return {
        width: Math.max(1, parentW - insets.left - insets.right),
        height: Math.max(1, parentH - insets.top - insets.bottom)
      }
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
      // 放大：宽高等比，上限为两侧收缩时的视口比例
      const step = Math.max(0.1, this.defaultZoomValue * 0.05)
      this.zoom = Math.min(this.maxZoomValue, Number((this.zoom + step).toFixed(3)))
      this.$nextTick(() => this.syncCanvasResolution())
    },
    zoomOut() {
      const step = Math.max(0.1, this.defaultZoomValue * 0.05)
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - step).toFixed(3)))
      this.$nextTick(() => this.syncCanvasResolution())
    },
    resetView() {
      this.zoom = this.defaultZoomValue
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
    if (this.collapseZoomTimer) clearTimeout(this.collapseZoomTimer)
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
  &.fit-visible-area {
    position: absolute;
    width: auto !important;
    height: auto !important;
    z-index: 0;
    background: #112B4D;
    transition: left 0.5s cubic-bezier(0.23, 0.9, 0.35, 1),
      right 0.5s cubic-bezier(0.23, 0.9, 0.35, 1),
      top 0.5s cubic-bezier(0.23, 0.9, 0.35, 1),
      bottom 0.5s cubic-bezier(0.23, 0.9, 0.35, 1);
  }
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
  &.is-map-loading {
    cursor: wait;
    .map-preview-stage {
      opacity: 0.35;
      pointer-events: none;
      transition: opacity 0.2s ease;
    }
  }
  .slam-map-loading {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    background: rgba(8, 22, 40, 0.55);
    backdrop-filter: blur(2px);
    pointer-events: all;
  }
  .slam-map-loading__spinner {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 2px solid rgba(11, 249, 254, 0.2);
    border-top-color: #0BF9FE;
    border-right-color: rgba(0, 203, 253, 0.75);
    animation: slam-map-spin 0.75s linear infinite;
    box-shadow: 0 0 16px rgba(11, 249, 254, 0.25);
  }
  .slam-map-loading__text {
    margin: 14px 0 0;
    color: #D7EDFF;
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: 20px;
    letter-spacing: 0.5px;
  }
  .map-preview-stage {
    position: relative;
    top: 0;
    left: 0;
    // 禁止 flex 压缩舞台，避免宽高被非等比挤压变形
    flex-shrink: 0;
    flex-grow: 0;
    cursor: grab;
    // will-change: left, top, width, height;
    will-change: transform;
    transform-origin: center center;
    transition: opacity 0.2s ease;
    &:active {
      cursor: grabbing;
    }
    .map-preview-image {
      display: block;
      width: 100%;
      height: 100%;
      // 跟随舞台等比尺寸，禁止拉伸变形
      object-fit: fill;
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
          font-size: 14px;
          paint-order: stroke;
          // stroke: #fff;
          stroke: #497da4;
          stroke-width: 1px;
          // fill: #0f172a;
          fill: #081018;
        }
        &.inPath circle {
          fill: #2563eb;
          filter: drop-shadow(0 2px 4px rgba(37, 99, 235, .4));
        }
        &.hovered circle {
          stroke: #0f172a;
          stroke-width: 3;
        }
        // MapTool 点位：Figma 黄点 + 名称，底部尖端为锚点
        &.is-map-tool,
        &.is-path-point {
          .map-point-marker {
            filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
          }
          .map-point-name-fo {
            overflow: visible;
          }
          .map-point-name {
            box-sizing: border-box;
            width: 100%;
            height: 18px;
            color: #D7EDFF;
            font-family: "Microsoft YaHei", sans-serif;
            font-size: 14px;
            font-weight: 400;
            line-height: 17.517px;
            text-align: center;
            white-space: nowrap;
            text-shadow:
              1px 0 rgba(0, 19, 48, 0.85),
              -1px 0 rgba(0, 19, 48, 0.85),
              0 1px rgba(0, 19, 48, 0.85),
              0 -1px rgba(0, 19, 48, 0.85);
          }
          &.hovered .map-point-marker {
            filter: drop-shadow(0 0 4px rgba(255, 246, 69, 0.65));
          }
        }
        // 任务路径序号：Figma 右上角蓝底白字
        .path-seq-badge {
          .path-seq-text {
            fill: #FFF;
            stroke: none;
            font-family: "Microsoft YaHei", sans-serif;
            font-size: 14px;
            font-weight: 400;
            line-height: 17.517px;
            paint-order: normal;
          }
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
        &.is-static {
          pointer-events: none;
          cursor: default;
        }
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

@keyframes slam-map-spin {
  to {
    transform: rotate(360deg);
  }
}

.slam-map-loading-fade-enter-active,
.slam-map-loading-fade-leave-active {
  transition: opacity 0.22s ease;
}
.slam-map-loading-fade-enter,
.slam-map-loading-fade-leave-to {
  opacity: 0;
}
</style>
