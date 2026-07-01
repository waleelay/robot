<template>
  <div class="map-preview-box w100 h100">
    <template v-if="hasPreview">
      <div class="map-preview-viewport flx-center w100 h100" @wheel="handleWheel" style="background: #CDCDCD;">
        <div class="map-preview-stage" :style="stageStyle" @mousedown="handleMouseDown">
          <img v-if="imageUrl" class="map-preview-image" :src="imageUrl" alt="地图预览" style="width: 100%; height: 100%;" />
          <el-empty v-else :description="previewImageStatus" />
          <svg
            v-if="imageUrl"
            ref="pathRef"
            class="map-preview-overlay"
            :viewBox="`0 0 ${map.previewWidth} ${map.previewHeight}`"
          >
            <polyline v-if="showReply && replyPolylinePoints" :points="replyPolylinePoints" class="map-preview-path" />
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
              >
                <circle r="6" />
                <text v-if="showLabels || point.id === selectedPointId" x="9" y="-9">{{ point.pointName }}---{{ polylinePoints.length }}</text>
                <title>{{ point.pointName }} / {{ point.pointCode }}</title>
              </g>
            </template>
          </svg>
          <svg-icon icon-class="address" class="wp20 hp20" :style="currentRobotPosition" style="position: absolute; font-size: 20px; color: #f0f" />
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
import mapInfo from '../../slam/mapInfo.json'
import pathInfo from '../../slam/pathInfo.json'
import mapPoints from '../../slam/map-points.json'
import slamMouse from './slam-mouse.js'

export default {
  name: 'BiReplySlam',
  mixins: [slamMouse],
  data() {
    return {
      pathInfo: pathInfo.data,
      map: mapInfo.data,
      points: mapPoints.data,
      showLabels: true,
      selectedPointId: null,
      showPath: false,
      currentRobotPosition: { top: 0, left: 0 },
      moveTimer: null,
      movePoints: [],
      showReply: true
    }
  },
  methods: {
    startMove() {
      if (this.moveTimer) {
        clearInterval(this.moveTimer)
        this.moveTimer = null
        // this.movePoints = []
        return
      }
      this.movePoints = []
      const points = this.drawablePoints
      let count = 0
      this.moveTimer = setInterval(() => {
        const top = (points[count].pixel.y) * this.zoom - 10
        const left = (points[count].pixel.x) * this.zoom - 10
        this.currentRobotPosition = { top, left }
        this.movePoints.push(`${points[count].pixel.x},${points[count].pixel.y}`)
        count ++
        if (count >= points.length) {
          count = 0
          this.movePoints = []
        }
      }, 1000)
    }
  },
  beforeDestroy() {
    if (this.moveTimer) {
      clearInterval(this.moveTimer)
      this.moveTimer = null
    }
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