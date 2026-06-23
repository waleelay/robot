<template>
  <div class="map-preview-box w100 h100">
    <template v-if="hasPreview">
      <div class="map-preview-viewport flx-center w100 h100" @wheel="handleWheel" style="background: #CDCDCD;">
        <div class="map-preview-stage" :style="stageStyle" @mousedown="handleMouseDown">
          <img v-if="imageUrl" class="map-preview-image" :src="imageUrl" alt="地图预览" style="width: 100%; height: 100%;" />
          <el-empty v-else :description="previewImageStatus" />
          <svg
            v-if="imageUrl && showPath"
            ref="overlayRef"
            class="map-preview-overlay"
            :viewBox="`0 0 ${map.previewWidth} ${map.previewHeight}`"
            @click="handleMapClick"
          >
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
          </svg>
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
export default {
  name: 'BiPatrolSlam',
  props: {
    map: { type: Object, default: null },
    points: { type: Array, default: () => [] },
    selectedPointId: { type: Number, default: null },
    pathPointIds: { type: Array, default: () => [] },
    showLabels: { type: Boolean, default: false }
  },
  data() {
    return {
      zoom: 1,
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
      showPath: false
    }
  },
  computed: {
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
        transform: `translate(${this.offsetX}px, ${this.offsetY}px)`
      }
    },
    drawablePoints() {
      return this.points
        .map((point) => ({ ...point, pixel: this.mapPointToPixel(point, this.map) }))
        .filter((point) => point.pixel)
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
      async handler(newVal) {
        this.imageObjectUrl = require('./preview-image.png');
        this.imageUrl = require('./preview-image.png');
        
        // const { id, cacheKey, hasPreview } = newVal;
        // const seq = ++this.imageLoadSeq;
        // this.revokeImageUrl();
        // this.previewImageStatus = '地图预览加载中';
        // if (!hasPreview || !id) return;
        // try {
        //   const blob = await mapApi.previewImageBlob(id, cacheKey);
        //   const nextUrl = URL.createObjectURL(blob);
        //   if (seq !== this.imageLoadSeq) {
        //     URL.revokeObjectURL(nextUrl);
        //     return;
        //   }
        //   this.imageObjectUrl = nextUrl;
        //   this.imageUrl = nextUrl;
        // } catch (error) {
        //   if (seq === this.imageLoadSeq) {
        //     this.previewImageStatus = '地图预览图片加载失败，请检查接口授权或后端服务';
        //   }
        // }
      },
    },
  },
  methods: {
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
    // 最小缩放比例：确保stage不会小于viewport的宽高
    minZoom() {
      const viewportWidth = 247; // viewport固定宽度
      const viewportHeight = 169; // viewport固定高度
      const stageWidth = Number(this.map?.previewWidth || 0);
      const stageHeight = Number(this.map?.previewHeight || 0);
      
      if (!stageWidth || !stageHeight) return 0.5;
      
      // 计算最小缩放比例，确保stage宽高不小于viewport
      const minZoomByWidth = viewportWidth / stageWidth;
      const minZoomByHeight = viewportHeight / stageHeight;
      
      return Math.max(minZoomByWidth, minZoomByHeight, 0.5);
    },
    zoomIn() {
      this.zoom = Math.min(3, Number((this.zoom + 0.25).toFixed(2)));
    },
    zoomOut() {
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - 0.25).toFixed(2)));
      
      // 缩小后检查边界，确保top, left, right, bottom >= 0
      const viewportWidth = 247;
      const viewportHeight = 169;
      const stageWidth = Number(this.map?.previewWidth || 0) * this.zoom;
      const stageHeight = Number(this.map?.previewHeight || 0) * this.zoom;
      const diffX = stageWidth - viewportWidth;
      const diffY = stageHeight - viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX / 2;
        this.offsetX = Math.max(-maxOffsetX, Math.min(0, this.offsetX));
      } else {
        this.offsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY / 2;
        this.offsetY = Math.max(-maxOffsetY, Math.min(0, this.offsetY));
      } else {
        this.offsetY = 0;
      }
    },
    resetView() {
      this.zoom = 1
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
      const viewport = this.$el.querySelector('.map-preview-viewport')
      if (!viewport) return
      
      const rect = viewport.getBoundingClientRect()
      const mouseX = e.clientX - rect.left
      const mouseY = e.clientY - rect.top
      
      // 计算鼠标相对于stage中心的位置
      const stageWidth = Number(this.map?.previewWidth || 0) * this.zoom
      const stageHeight = Number(this.map?.previewHeight || 0) * this.zoom
      
      // 计算鼠标在stage上的原始位置（考虑当前偏移）
      const stageMouseX = mouseX - this.offsetX
      const stageMouseY = mouseY - this.offsetY
      
      // 计算缩放因子
      const delta = e.deltaY > 0 ? -0.1 : 0.1
      const newZoom = Math.max(this.minZoom(), Math.min(3, this.zoom + delta))
      
      // 计算新的偏移，使鼠标位置保持在原地
      const scale = newZoom / this.zoom
      this.offsetX = mouseX - stageMouseX * scale
      this.offsetY = mouseY - stageMouseY * scale
      
      this.zoom = Number(newZoom.toFixed(2))
      // 缩放后检查边界，确保stage始终覆盖viewport
      const newStageWidth = Number(this.map?.previewWidth || 0) * this.zoom;
      const newStageHeight = Number(this.map?.previewHeight || 0) * this.zoom;
      const diffX = newStageWidth - viewportWidth;
      const diffY = newStageHeight - viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX;
        this.offsetX = Math.max(-maxOffsetX, Math.min(0, this.offsetX));
      } else {
        this.offsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY;
        this.offsetY = Math.max(-maxOffsetY, Math.min(0, this.offsetY));
      } else {
        this.offsetY = 0;
      }
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
      const rect = this.$refs?.overlayRef?.getBoundingClientRect();
      if (!rect || !this.map?.previewWidth || !this.map?.previewHeight) return null;
      return {
        x: ((event.clientX - rect.left) / rect.width) * Number(this.map.previewWidth),
        y: ((event.clientY - rect.top) / rect.height) * Number(this.map.previewHeight)
      };
    },
    handleMapClick(event) {
      const pixel = this.eventToPixel(event);
      const point = this.pixelToMapPoint(pixel, this.map);
      if (point) this.$emit("map-click", point);
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
    this.revokeImageUrl();
  }
}
</script>

<style lang="scss">
.map-preview-viewport {
  position: relative;
  width: 247px;
  height: 169px;
  // background: rgb(243, 240, 210);
  background: #cdcdcd;
  overflow: hidden;
  cursor: grab;
  will-change: transform;
  &:active {
    cursor: grabbing;
  }
  .map-preview-stage {
    position: absolute;
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
</style>