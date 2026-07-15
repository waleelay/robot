<template>
  <div class="map-preview-box w100 h100">
    <template v-if="hasPreview">
      <!-- <div class="map-preview-toolbar">
        <el-button-group>
          <el-button @click="zoomOut">缩小</el-button>
          <el-button @click="resetView">重置视图</el-button>
          <el-button @click="zoomIn">放大</el-button>
          <el-button @click="backCenter">居中point4</el-button>
        </el-button-group>
        <span class="map-preview-zoom">{{ Math.round(zoom * 100) }}%</span>
      </div> -->
      <div ref="viewportRef" class="map-preview-viewport flx-center w100 h100" @wheel="handleWheel" style="background: #112B4D;">
        <div class="map-preview-stage" :style="stageStyle" @mousedown="handleMouseDown">
          <!-- <img v-if="imageUrl" ref="imageRef" class="map-preview-image" :src="imageUrl" alt="地图预览" style="width: 100%; height: 100%;" /> -->
          <template v-if="imageUrl">
            <canvas ref="canvas" @contextmenu.prevent="onCanvasClick" class="map-preview-image" style="width: 100%; height: 100%;" />
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
                  :class="{ selected: point.id === selectedPointId, inPath: pathPointIds.includes(point.id), hovered: point.id === hoveredPointId }"
                  @mouseenter="hoveredPointId = point.id"
                  @mouseleave="hoveredPointId = null"
                  @click.stop="handlePointClick(point)"
                >
                  <circle r="6" />
                  <text v-if="showLabels || point.id === selectedPointId" x="9" y="-9">{{ point.pointName }}</text>
                  <title>{{ point.pointName }} / {{ point.pointCode }}</title>
                </g>
              </template>
              <g
                v-for="robot in drawableRobots"
                :key="robot.robotId"
                :transform="`translate(${robot.pixel.x}, ${robot.pixel.y}) scale(${1 / zoom})`"
                class="map-preview-robot"
                :class="robot.statusClass"
              >
                <image :href="robotIcon" x="-18" y="-42" width="36" height="36" />
                <text class="robot-name" x="0" y="8">{{ robot.name }}</text>
                <rect class="robot-status-bg" x="-28" y="13" width="56" height="18" rx="3" />
                <text class="robot-status" x="0" y="26">{{ robot.statusText }}</text>
              </g>
            </svg>
          </template>
          <el-empty v-else :description="previewImageStatus" />
          <!-- <span class="start-point" :style="{ opacity: locationPoint ? 1 : 0, zIndex: locationPoint ? 1 : 0, left: `${startPoint?.[0] || 0}px`, top: `${startPoint?.[1] || 0}px` }">起始地</span> -->
          <div :style="{ opacity: locationPoint ? 1 : 0, zIndex: locationPoint ? 1 : 0, ...locationStyle }" class="location flx-center flex-column"  ref="pointLocationRef">
            <img src="./../../../../assets/images/new-bi/address1.png" alt="位置" class="wp40 hp46" />
            <span class="margin-top: -2px">临时点</span>
          </div>
          <div class="context-menu d-flex" :style="{ display: locationPoint ? 'flex' : 'none', top: `${((locationPoint?.y || 0) - 27)}px`, left: `${((locationPoint?.x || 0) + 65)}px` }">
            <div class="flx-center div1 p10">
              <span>派遣设备前往该点</span>
              <svg-icon icon-class="right" class="ml4" />
            </div>
            <div v-if="normalRobots.length" class="div2 ml10 p10">
              <div v-for="robot in normalRobots" :key="robot.robotId" class="item flx-justify-between p6" :class="robot.statusClass">
                <span class="name pl14" :class="robot.statusClass">{{ robot.name }}</span>
                <span class="oper ml4" :class="robot.statusClass">
                  {{robot.customStatusName === '空闲中' ? '立即派遣' : '终止任务'}}
                </span>
              </div>
            </div>
          </div>
        </div>
        <div class="map-operation">
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
  </div>
</template>

<script>
import { mapState } from 'vuex';
import addPointTask from './add-point-task.js'
export default {
  name: 'BiPatrolSlam',
  mixins: [addPointTask],
  props: {
    map: { type: Object, default: null },
    points: { type: Array, default: () => [] },
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
      robotIcon: require('@/assets/images/new-bi/robot-normal.png'),
      imageUrl: '',
      previewImageStatus: '地图预览加载中',
      imageObjectUrl: '',
      imageLoadSeq: 0,
      hoveredPointId: null,
      // 拖拽相关
      offsetX: 0,
      offsetY: 0,
      isDragging: false,
      startX: 0,
      startY: 0,
      startOffsetX: 0,
      startOffsetY: 0,
      operList: [
        {
          icon: 'map-path',
          name: '路径',
          key: 'path',
          action: 'renderPath'
        },
        {
          icon: 'map-location',
          name: '定位',
          key: 'location',
          action: 'backCenter'
        },
        {
          icon: 'map-zoom-in',
          name: '放大',
          key: 'zoomIn',
          action: 'zoomIn'
        },
        {
          icon: 'map-zoom-out',
          name: '缩小',
          key: 'zoomOut',
          action: 'zoomOut'
        },
      ],
      showPath: false,
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotLocation', 'slamOfRobot']),
    normalRobots() {
      return Object.values(this.robotBaseInfo || {}).filter(item => item.status === 'online') || []
    },
    hasPreview() {  
      return !!this.map?.previewFileId &&
        !!this.map?.previewWidth &&
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
        // transform: `translate(${this.offsetX}px, ${this.offsetY}px)`
      }
    },
    drawablePoints() {
      return this.points
        .map((point) => ({ ...point, pixel: this.mapPointToPixel(point, this.map) }))
        .filter((point) => point.pixel)
    },
    drawableRobots() {
      const robots = this.slamOfRobot?.[String(this.map?.id)]?.robots || []
      return robots.map(baseRobot => {
        const robot = { ...baseRobot, ...(this.robotBaseInfo?.[baseRobot.robotId] || {}) }
        const location = { ...(baseRobot.location || {}), ...(this.robotLocation?.[baseRobot.robotId] || {}) }
        const coordinateX = location.x ?? location.coordinateX
        const coordinateY = location.y ?? location.coordinateY
        if (coordinateX === undefined || coordinateX === null || coordinateY === undefined || coordinateY === null) return null
        const pixel = this.mapPointToPixel({ coordinateX, coordinateY }, this.map)
        if (!pixel) return null
        return {
          ...robot,
          name: robot.name || robot.deviceName || robot.robotId,
          pixel,
          statusClass: robot.statusClass || (robot.status === 'offline' ? 'gray' : robot.status === 'online' ? 'blue' : 'orange'),
          statusText: robot.customStatusName || robot.statusName || (robot.status === 'online' ? '在线' : robot.status === 'offline' ? '离线' : '故障')
        }
      }).filter(Boolean)
    },
    polylinePoints() {
      return this.pathPointIds
        .map((id) => this.drawablePoints.find((point) => point.id === id)?.pixel)
        .filter(Boolean)
        .map((pixel) => `${pixel.x},${pixel.y}`)
        .join(" ")
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
      handler({ id, cacheKey, hasPreview }) {
        this.revokeImageUrl()
        this.previewImageStatus = '地图预览加载中'
        if (!hasPreview || id === undefined || id === null) return
        const preUrl = (process.env.VUE_APP_BASE_ORIGIN || window.location.origin || '').replace(/\/$/, '')
        this.imageUrl = `${preUrl}/api/v1/management/maps/${id}/preview-image?cacheKey=${encodeURIComponent(cacheKey || '')}`
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
  },
  mounted() {
    this.$nextTick(() => {
      this.updateZoomBounds(true)
      this.observeViewport()
    })
  },
  methods: {
    changeMapZoom({ method } = {}) {
      if (typeof this[method] === 'function') {
        this[method]()
      }
    },
    handleClickTool(item) {
      this[item.action]();
    },
    renderPath() {
      this.showPath = !this.showPath;
      console.log(11, this.pathPointIds
        .map((id) => this.drawablePoints.find((point) => point.id === id)?.pixel).filter(Boolean)
        .map((pixel) => `${pixel.x},${pixel.y}`)
        .join(" "))

        console.log(2, this.polylinePoints);
        
      // 渲染路径及点
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
    },
    zoomOut() {
      const step = Math.max(0.1, this.maxZoomValue * 0.05)
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - step).toFixed(3)))
    },
    resetView() {
      this.zoom = this.maxZoomValue
      this.offsetX = 0
      this.offsetY = 0
    },
    backCenter() {
      // 获取第4个点（索引为3）
      const point4 = this.drawablePoints[3];
      if (!point4 || !point4.pixel) return;
      
      const viewportWidth = 247;
      const viewportHeight = 169;
      const stageWidth = Number(this.map?.previewWidth || 0) * this.zoom;
      const stageHeight = Number(this.map?.previewHeight || 0) * this.zoom;
      
      // 计算该点在stage上的位置（已缩放）
      const pointX = point4.pixel.x * this.zoom;
      const pointY = point4.pixel.y * this.zoom;
      
      // 计算使该点居中所需的偏移
      // viewport中心位置
      const viewportCenterX = viewportWidth / 2;
      const viewportCenterY = viewportHeight / 2;
      
      // 目标偏移 = viewport中心 - 点在stage上的位置
      let targetOffsetX = viewportCenterX - pointX;
      let targetOffsetY = viewportCenterY - pointY;
      
      // 应用边界限制：确保top, left, right, bottom >= 0
      const diffX = stageWidth - viewportWidth;
      const diffY = stageHeight - viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX / 2;
        targetOffsetX = Math.max(-maxOffsetX, Math.min(0, targetOffsetX));
      } else {
        targetOffsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY / 2;
        targetOffsetY = Math.max(-maxOffsetY, Math.min(0, targetOffsetY));
      } else {
        targetOffsetY = 0;
      }
      
      this.offsetX = targetOffsetX;
      this.offsetY = targetOffsetY;
    },
    handleMouseDown(e) {
      if (e.target.closest('.map-preview-point')) return
      this.isDragging = true
      this.startX = e.clientX
      this.startY = e.clientY
      this.startOffsetX = this.offsetX
      this.startOffsetY = this.offsetY
      document.addEventListener('mousemove', this.handleMouseMove)
      document.addEventListener('mouseup', this.handleMouseUp)
    },
    handleMouseMove(e) {
      if (!this.isDragging) return
      
      const viewportWidth = 247;
      const viewportHeight = 169;
      const stageWidth = Number(this.map?.previewWidth || 0) * this.zoom;
      const stageHeight = Number(this.map?.previewHeight || 0) * this.zoom;
      
      // 计算当前偏移
      let newOffsetX = this.startOffsetX + e.clientX - this.startX
      let newOffsetY = this.startOffsetY + e.clientY - this.startY
      
      // 边界限制：确保stage始终完全覆盖viewport，边界距离不为正数
      const diffX = stageWidth - viewportWidth;
      const diffY = stageHeight - viewportHeight;
      
      if (diffX > 0) {
        // stage比viewport宽，可拖动范围: [-(diffX), 0]
        // 向左拖动可以看到右侧内容，确保left<=0, right<=0
        const maxOffsetX = diffX;
        newOffsetX = Math.max(-maxOffsetX, Math.min(0, newOffsetX));
      } else {
        // stage小于等于viewport宽，保持居中
        newOffsetX = 0;
      }
      
      if (diffY > 0) {
        // stage比viewport高，可拖动范围: [-(diffY), 0]
        // 向上拖动可以看到下侧内容，确保top<=0, bottom<=0
        const maxOffsetY = diffY;
        newOffsetY = Math.max(-maxOffsetY, Math.min(0, newOffsetY));
      } else {
        // stage小于等于viewport高，保持居中
        newOffsetY = 0;
      }
      
      this.offsetX = newOffsetX
      this.offsetY = newOffsetY
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
    handleMapClick(event) {
      const pixel = this.eventToPixel(event);
      const point = this.pixelToMapPoint(pixel, this.map);
      console.log('point', point);
      
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
.map-preview-viewport {
  position: relative;
  width: 100%;
  height: 100%;
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
      // cursor: crosshair;
      .map-preview-point {
        cursor: pointer;
         circle {
          fill: #10b981;
          stroke: #fff;
          stroke-width: 2;
          filter: drop-shadow(0 2px 4px rgba(16, 185, 129, .4));
        }
        text {
          font-size: 12px;
          paint-order: stroke;
          stroke: #fff;
          stroke-width: 3px;
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
        filter: drop-shadow(0 1px 2px rgba(37, 99, 235, .4));
      }
      .map-preview-robot {
        .robot-name, .robot-status {
          fill: #fff;
          font-family: "Microsoft YaHei";
          text-anchor: middle;
          paint-order: stroke;
          stroke: rgba(0, 19, 48, .9);
          stroke-width: 3px;
        }
        .robot-name {
          font-size: 13px;
          font-weight: 600;
        }
        .robot-status {
          font-size: 11px;
          stroke-width: 2px;
        }
        .robot-status-bg {
          fill: #0062ae;
          stroke: #2a86f3;
          stroke-width: 1px;
        }
        &.green .robot-status-bg {
          fill: #0d7f2a;
          stroke: #23ab08;
        }
        &.orange .robot-status-bg {
          fill: #a75400;
          stroke: #ff9000;
        }
        &.gray .robot-status-bg {
          fill: #515a67;
          stroke: #8897ab;
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
  span {
    padding: 2px 4px;
    color: #FFF;
    border-radius: 2px;
    background: #0062AE;
    font-size: 14px;
    line-height: 17.517px; /* 125.119% */
  }
}
.start-point {
  position: absolute;
  padding: 2px 4px;
  border-radius: 2px;
  background: #0D9F31;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 14px;
  line-height: 17.517px; /* 125.119% */
}
.context-menu {
  position: absolute;
  z-index: 1;
  .div1, .div2 {
    border-radius: 4px;
    border: 2px solid rgba(0, 0, 0, 0.00);
    background: rgba(0, 19, 48, 0.90);
    box-shadow: 0 0 20px 0 rgba(1, 80, 170, 0.80) inset;
    backdrop-filter: blur(5px);
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
      padding: 6px 10px;
      border-radius: 100px;
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      line-height: 12px; /* 100% */
      letter-spacing: 0.857px;
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
</style>
