<template>
  <div class="slam-map-root w100 h100">
    <div
      class="map-preview-box w100 h100 flx-center"
      :class="{ 'fit-visible-area': !!visibleLayout }"
      :style="visibleAreaStyle"
    >
      <template v-if="hasPreview && !mapLoadFailed">
        <div
          ref="viewportRef"
          class="map-preview-viewport flx-center w100 h100"
          :class="{ 'is-map-loading': mapLoading, 'is-measuring': measureActive }"
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
                @dblclick.prevent="handleMeasureDblClick"
                class="map-preview-image"
                style="width: 100%; height: 100%;"
              />
              <svg
                v-if="imageUrl"
                ref="overlayRef"
                class="map-preview-overlay"
                :viewBox="`0 0 ${map.previewWidth} ${map.previewHeight}`"
                @click="handleMapClick"
                @dblclick.prevent="handleMeasureDblClick"
              >
              <!-- 底层：未置顶的任务路径 -->
              <g
                v-for="path in baseDisplayTaskPaths"
                :key="`task-path-${path.taskId}`"
                class="map-task-path-layer"
                @mouseenter="raiseTaskPath(path.taskId)"
                @mouseleave="clearRaisedTaskPath"
              >
                <polyline
                  v-if="path.polylinePoints"
                  :points="path.polylinePoints"
                  class="map-preview-path-hit"
                />
                <polyline
                  v-if="path.polylinePoints"
                  :points="path.polylinePoints"
                  class="map-preview-path"
                  :style="{ stroke: path.color }"
                />
                <title>{{ path.taskName }}</title>
                <g
                  v-for="point in path.points"
                  :key="`task-path-${path.taskId}-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-path-point"
                >
                  <template v-if="shouldShowPathPointMarker(point)">
                    <rect
                      :x="point.icon.iconX - 4"
                      :y="point.icon.iconY - 4"
                      :width="point.icon.width + 8"
                      :height="point.icon.height + 28"
                      fill="transparent"
                    />
                    <image
                      :href="point.icon.marker"
                      :x="point.icon.iconX"
                      :y="point.icon.iconY"
                      :width="point.icon.width"
                      :height="point.icon.height"
                      class="map-point-marker"
                    />
                    <foreignObject
                      v-if="!activeRaisedTaskPathId"
                      class="map-point-name-fo"
                      :x="-(point.nameWidth || mapPointNameMaxWidth) / 2"
                      :y="point.icon.nameY"
                      :width="point.nameWidth || mapPointNameMaxWidth"
                      :height="point.icon.useSpecialNameStyle ? 20 : 24"
                    >
                      <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name-wrap">
                        <div
                          class="map-point-name"
                          :class="{ 'is-equip-name': point.icon.useSpecialNameStyle }"
                          :title="point.pointName"
                        >{{ point.pointName }}</div>
                      </div>
                    </foreignObject>
                  </template>
                  <g
                    v-if="shouldShowPathSeq(path.taskId)"
                    class="path-seq-badge"
                    transform="translate(6, -40)"
                    pointer-events="none"
                  >
                    <image :href="pathPointBadge" x="0" y="0" width="20" height="20" />
                    <text x="10" y="14.5" text-anchor="middle" class="path-seq-text">{{ point.pathIndex }}</text>
                  </g>
                </g>
              </g>
              <!-- MapTool「点位」：未置顶 -->
              <template v-if="showPath && !pointsRaised">
                <g
                  v-for="point in drawablePoints"
                  :key="`map-point-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-map-tool"
                  :class="{
                    selected: point.id === selectedPointId,
                    inPath: isPointInPath(point),
                    hovered: point.id === hoveredPointId
                  }"
                  @mouseenter="onMapPointEnter(point)"
                  @mouseleave="onMapPointLeave"
                  @click.stop="handlePointClick(point)"
                >
                  <rect
                    :x="point.icon.iconX - 4"
                    :y="point.icon.iconY - 4"
                    :width="point.icon.width + 8"
                    :height="point.icon.height + 28"
                    fill="transparent"
                  />
                  <image
                    :href="point.icon.marker"
                    :x="point.icon.iconX"
                    :y="point.icon.iconY"
                    :width="point.icon.width"
                    :height="point.icon.height"
                    class="map-point-marker"
                  />
                  <foreignObject
                    class="map-point-name-fo"
                    :x="-(point.nameWidth || mapPointNameMaxWidth) / 2"
                    :y="point.icon.nameY"
                    :width="point.nameWidth || mapPointNameMaxWidth"
                    :height="point.icon.useSpecialNameStyle ? 20 : 24"
                  >
                    <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name-wrap">
                      <div
                        class="map-point-name"
                        :class="{ 'is-equip-name': point.icon.useSpecialNameStyle }"
                        :title="point.pointName"
                      >{{ point.pointName }}</div>
                    </div>
                  </foreignObject>
                  <title>{{ point.pointName }} / {{ point.pointCode || point.id }}</title>
                </g>
              </template>
              <!-- 模拟执行：仅画避障后的已走路径；未走仍由 canvas 两点虚线负责 -->
              <g v-if="!showSmall && mockExecutionPathLayer" class="mock-exec-path-layer" pointer-events="none">
                <polyline
                  v-if="mockExecutionPathLayer.traveledPoints"
                  :points="mockExecutionPathLayer.traveledPoints"
                  class="mock-exec-path-traveled"
                />
                <polyline
                  v-if="mockExecutionPathLayer.traveledPoints"
                  :points="mockExecutionPathLayer.traveledPoints"
                  class="mock-exec-path-traveled-core"
                />
                <g
                  v-for="(arrow, index) in mockExecutionPathLayer.arrows"
                  :key="`mock-path-arrow-${index}`"
                  :transform="`translate(${arrow.x}, ${arrow.y}) rotate(${arrow.deg}) scale(${1 / zoom})`"
                >
                  <image
                    :href="pathDirectionArrow"
                    :x="-pathArrowWidth / 2"
                    :y="-pathArrowHeight / 2"
                    :width="pathArrowWidth"
                    :height="pathArrowHeight"
                  />
                </g>
              </g>
              <!-- 真实环境：本页会话内记录的已走路径（刷新后不恢复）；小窗口实时地图不展示 -->
              <template v-if="!showSmall">
              <g
                v-for="layer in sessionTraveledPathLayers"
                :key="`session-traveled-${layer.robotId}`"
                class="mock-exec-path-layer"
                pointer-events="none"
              >
                <polyline :points="layer.traveledPoints" class="mock-exec-path-traveled" />
                <polyline :points="layer.traveledPoints" class="mock-exec-path-traveled-core" />
                <g
                  v-for="(arrow, index) in layer.arrows"
                  :key="`session-path-arrow-${layer.robotId}-${index}`"
                  :transform="`translate(${arrow.x}, ${arrow.y}) rotate(${arrow.deg}) scale(${1 / zoom})`"
                >
                  <image
                    :href="pathDirectionArrow"
                    :x="-pathArrowWidth / 2"
                    :y="-pathArrowHeight / 2"
                    :width="pathArrowWidth"
                    :height="pathArrowHeight"
                  />
                </g>
              </g>
              </template>
              <!-- 装备 -->
              <!-- 图标随地图缩放而变化 -->
              <!-- :transform="`translate(${robot.pixel.x}, ${robot.pixel.y})${showSmall ? '' : ` scale(${1 / zoom})`}`" -->
              <g
                v-for="robot in drawableRobots"
                :key="robot.robotId"
                :transform="`translate(${robot.pixel.x}, ${robot.pixel.y}) scale(${1 / zoom})`"
                class="map-preview-robot custom-point"
                :class="[robot.statusClass, { 'show-icon': isRobotHighlighted(robot.robotId), 'is-static': !isRobotClickable(robot) }]"
                @click.stop="handleRobotClick($event, robot)"
                @mouseenter="onRobotPathHover(robot)"
                @mouseleave="clearRaisedTaskPath"
              >
                <!-- 选中光圈：固定摄像头不展示 -->
                <image
                  v-if="isRobotHighlighted(robot.robotId) && !robot.isFixedCamera"
                  :href="robotSelectedHalo"
                  x="-25"
                  y="-47"
                  width="50"
                  height="54"
                  class="robot-selected-halo"
                  pointer-events="none"
                />
                <!-- 默认/选中底图：固定摄像头不展示 -->
                <image
                  v-if="!robot.isFixedCamera"
                  :href="robotBg"
                  x="-20"
                  y="-43"
                  width="40"
                  height="43"
                  class="robot-bg"
                />
                <!-- 装备图标：普通装备按 Figma 缩放；固定摄像头 44×62，锚点距底部 4px -->
                <image
                  :href="robot.iconUrl"
                  :x="robot.iconX"
                  :y="robot.iconY"
                  :width="robot.displayIconWidth"
                  :height="robot.displayIconHeight"
                  class="robot-type-icon"
                />
                <!-- 选中四角：固定摄像头不展示 -->
                <g
                  v-if="isRobotHighlighted(robot.robotId) && !robot.isFixedCamera"
                  pointer-events="none"
                >
                  <image
                    :href="robotSelectedCorners"
                    x="-36"
                    y="-56"
                    width="72"
                    height="12"
                    class="robot-selected-corners"
                  />
                  <g transform="matrix(1 0 0 -1 0 20)">
                    <image
                      :href="robotSelectedCorners"
                      x="-36"
                      y="4"
                      width="72"
                      height="12"
                      class="robot-selected-corners"
                    />
                  </g>
                </g>
                <foreignObject
                  class="robot-name-fo"
                  :x="-robot.nameWidth / 2"
                  :y="isRobotHighlighted(robot.robotId) ? robot.nameYSelected : robot.nameY"
                  :width="robot.nameWidth"
                  height="20"
                >
                  <div
                    xmlns="http://www.w3.org/1999/xhtml"
                    class="robot-name-pill"
                  >{{ robot.name }}</div>
                </foreignObject>
                <foreignObject
                  v-if="!showSmall && !robot.isFixedCamera"
                  class="robot-status-fo"
                  :x="robot.statusBgX"
                  :y="isRobotHighlighted(robot.robotId) ? robot.statusYSelected : robot.statusY"
                  :width="robot.statusBgWidth"
                  height="20"
                >
                  <div
                    xmlns="http://www.w3.org/1999/xhtml"
                    class="robot-status-pill"
                    :class="robot.statusClass"
                  >{{ robot.customStatusName }}</div>
                </foreignObject>
                <!-- 暂时不显示告警事件
                <foreignObject
                  v-if="robot.alarmText"
                  class="robot-warning-fo"
                  :x="robot.alarmX"
                  :y="robot.alarmY"
                  :width="robot.alarmWidth"
                  :height="robot.alarmHeight"
                  pointer-events="none"
                >
                  <div xmlns="http://www.w3.org/1999/xhtml" class="robot-warning flx-center" style="height: fit-content;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
                      <path d="M15.6585 13.1145L9.38682 2.25252C8.6197 0.924516 7.36601 0.924516 6.59909 2.25252L0.327353 13.1145C-0.439564 14.4438 0.187856 15.5282 1.72062 15.5282H14.2652C15.798 15.5282 16.4248 14.4437 15.6585 13.1145ZM7.13152 5.33448C7.35689 5.09081 7.64342 4.96896 7.99287 4.96896C8.3425 4.96896 8.62877 5.08953 8.85438 5.32961C9.07852 5.57023 9.19055 5.87115 9.19055 6.233C9.19055 6.54434 8.7227 8.8337 8.56664 10.4992H7.43975C7.30289 8.83368 6.79524 6.54434 6.79524 6.233C6.79527 5.87664 6.90748 5.57696 7.13152 5.33448ZM8.8386 13.254C8.60154 13.4849 8.31945 13.6 7.99295 13.6C7.66653 13.6 7.38436 13.4849 7.14735 13.254C6.91098 13.0237 6.7935 12.7447 6.7935 12.4171C6.7935 12.0911 6.91098 11.8091 7.14735 11.5727C7.38436 11.3363 7.66653 11.2181 7.99295 11.2181C8.31945 11.2181 8.60154 11.3363 8.8386 11.5727C9.0748 11.8091 9.19256 12.0911 9.19256 12.4171C9.19256 12.7447 9.0748 13.0237 8.8386 13.254Z" fill="#FFDD00"/>
                    </svg>
                    <span class="ml5">{{ robot.alarmText }}</span>
                  </div>
                </foreignObject>
                -->
              </g>
              <!-- 置顶：地图点位 -->
              <template v-if="showPath && pointsRaised">
                <g
                  v-for="point in drawablePoints"
                  :key="`map-point-raised-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-map-tool is-raised"
                  :class="{
                    selected: point.id === selectedPointId,
                    inPath: isPointInPath(point),
                    hovered: point.id === hoveredPointId
                  }"
                  @mouseenter="onMapPointEnter(point)"
                  @mouseleave="onMapPointLeave"
                  @click.stop="handlePointClick(point)"
                >
                  <rect
                    :x="point.icon.iconX - 4"
                    :y="point.icon.iconY - 4"
                    :width="point.icon.width + 8"
                    :height="point.icon.height + 28"
                    fill="transparent"
                  />
                  <image
                    :href="point.icon.marker"
                    :x="point.icon.iconX"
                    :y="point.icon.iconY"
                    :width="point.icon.width"
                    :height="point.icon.height"
                    class="map-point-marker"
                  />
                  <foreignObject
                    class="map-point-name-fo"
                    :x="-(point.nameWidth || mapPointNameMaxWidth) / 2"
                    :y="point.icon.nameY"
                    :width="point.nameWidth || mapPointNameMaxWidth"
                    :height="point.icon.useSpecialNameStyle ? 20 : 24"
                  >
                    <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name-wrap">
                      <div
                        class="map-point-name"
                        :class="{ 'is-equip-name': point.icon.useSpecialNameStyle }"
                        :title="point.pointName"
                      >{{ point.pointName }}</div>
                    </div>
                  </foreignObject>
                  <title>{{ point.pointName }} / {{ point.pointCode || point.id }}</title>
                </g>
              </template>
              <!-- 置顶：任务路径（线条 + 点位） -->
              <g
                v-if="raisedDisplayTaskPath"
                :key="`task-path-raised-${raisedDisplayTaskPath.taskId}`"
                class="map-task-path-layer is-raised"
                @mouseenter="raiseTaskPath(raisedDisplayTaskPath.taskId)"
                @mouseleave="clearRaisedTaskPath"
              >
                <polyline
                  v-if="raisedDisplayTaskPath.polylinePoints"
                  :points="raisedDisplayTaskPath.polylinePoints"
                  class="map-preview-path-hit"
                />
                <polyline
                  v-if="raisedDisplayTaskPath.polylinePoints"
                  :points="raisedDisplayTaskPath.polylinePoints"
                  class="map-preview-path"
                  :style="{ stroke: raisedDisplayTaskPath.color }"
                />
                <title>{{ raisedDisplayTaskPath.taskName }}</title>
                <g
                  v-for="point in raisedDisplayTaskPath.points"
                  :key="`task-path-raised-${raisedDisplayTaskPath.taskId}-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                  class="map-preview-point is-path-point"
                >
                  <template v-if="shouldShowPathPointMarker(point)">
                    <rect
                      :x="point.icon.iconX - 4"
                      :y="point.icon.iconY - 4"
                      :width="point.icon.width + 8"
                      :height="point.icon.height + 28"
                      fill="transparent"
                    />
                    <image
                      :href="point.icon.marker"
                      :x="point.icon.iconX"
                      :y="point.icon.iconY"
                      :width="point.icon.width"
                      :height="point.icon.height"
                      class="map-point-marker"
                    />
                    <foreignObject
                      class="map-point-name-fo"
                      :x="-(point.nameWidth || mapPointNameMaxWidth) / 2"
                      :y="point.icon.nameY"
                      :width="point.nameWidth || mapPointNameMaxWidth"
                      :height="point.icon.useSpecialNameStyle ? 20 : 24"
                    >
                      <div xmlns="http://www.w3.org/1999/xhtml" class="map-point-name-wrap">
                        <div
                          class="map-point-name"
                          :class="{ 'is-equip-name': point.icon.useSpecialNameStyle }"
                          :title="point.pointName"
                        >{{ point.pointName }}</div>
                      </div>
                    </foreignObject>
                  </template>
                </g>
              </g>
              <!-- 置顶：路径点编号（独立图层，压过其它路径名） -->
              <g
                v-if="raisedDisplayTaskPath && shouldShowPathSeq(raisedDisplayTaskPath.taskId)"
                class="path-seq-raised-layer"
                pointer-events="none"
              >
                <g
                  v-for="point in raisedDisplayTaskPath.points"
                  :key="`task-path-seq-raised-${raisedDisplayTaskPath.taskId}-${point.id}`"
                  :transform="`translate(${point.pixel.x}, ${point.pixel.y}) scale(${1 / zoom})`"
                >
                  <g class="path-seq-badge" transform="translate(6, -40)">
                    <image :href="pathPointBadge" x="0" y="0" width="20" height="20" />
                    <text x="10" y="14.5" text-anchor="middle" class="path-seq-text">{{ point.pathIndex }}</text>
                  </g>
                </g>
              </g>
              <!-- 测距层 -->
              <g v-if="measurePoints.length" class="map-measure-layer" pointer-events="none">
                <polyline
                  v-if="measurePolylinePoints"
                  :points="measurePolylinePoints"
                  class="map-measure-line"
                  :class="{ 'is-finished': measureFinished }"
                />
                <g
                  v-for="(seg, index) in measureSegments"
                  :key="`measure-seg-${index}`"
                  :transform="`translate(${seg.midX}, ${seg.midY}) scale(${1 / zoom})`"
                >
                  <foreignObject x="-48" y="-12" width="96" height="24">
                    <div xmlns="http://www.w3.org/1999/xhtml" class="map-measure-label">{{ seg.label }}</div>
                  </foreignObject>
                </g>
                <g
                  v-for="(pt, index) in measurePoints"
                  :key="`measure-pt-${index}`"
                  :transform="`translate(${pt.pixel.x}, ${pt.pixel.y}) scale(${1 / zoom})`"
                >
                  <circle r="5" class="map-measure-dot" :class="{ 'is-finished': measureFinished }" />
                </g>
                <g
                  v-if="measureTotalLabel"
                  :transform="`translate(${measurePoints[measurePoints.length - 1].pixel.x}, ${measurePoints[measurePoints.length - 1].pixel.y}) scale(${1 / zoom})`"
                >
                  <foreignObject x="8" y="-28" width="120" height="24">
                    <div xmlns="http://www.w3.org/1999/xhtml" class="map-measure-total">总长 {{ measureTotalLabel }}</div>
                  </foreignObject>
                </g>
              </g>
            </svg>
          </template>
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
            <div class="flx-center div1 bi-corner-box">
              <span>派遣设备前往该点</span>
              <svg-icon icon-class="right" class="ml4" />
            </div>
            <div v-if="normalRobots.length" class="div2 ml10 bi-corner-box">
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
    <Empty
      v-else
      width="126px"
      :opacity="0.7"
      textColor="#BEE1FF"
      :text="mapLoadFailed ? '地图加载失败' : '当前地图暂无预览'"
    />
    </div>
    <!-- 弹层放在地图裁剪容器外，避免 fit-visible-area 的 overflow/z-index 裁切或压住 -->
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
import mockTaskExecution from './mock-task-execution.js'
import sessionTraveledPath from './session-traveled-path.js'
import Robot1 from '../popup/Robot1.vue'
import RobotControlPart from '../popup/RobotControlPart.vue'
import RobotCarControlPart from '../popup/RobotCarControlPart.vue'
// import Slam from '../../gis/globalMap/popup/Slam.vue'
import { ROBOT_TYPE_INFO, isRobotDog, isFixedCamera } from '@/constants/robot.js'
import { addTaskByPoint, previewImageBlob } from '@/api/new-bi.js'
import { ENABLE_LIANTONG_SLAM_MOCK, ENABLE_LIANTONG_TASK_EXECUTION_MOCK, getMapPointIconMeta, isMapToolSpecialPoint, isPointToolRequireCharge } from '../../../js/constants/gisMapPoints.js'
import { PATH_ARROW_WIDTH, PATH_ARROW_HEIGHT } from './path-direction-arrows.js'

const ROBOT_BG = require('@/assets/images/new-bi/robot-bg.svg')
const ROBOT_SELECTED_HALO = require('@/assets/images/new-bi/robot-selected-halo.svg')
const ROBOT_SELECTED_CORNERS = require('@/assets/images/new-bi/robot-selected-corners.svg')
// 普通点 / 充电点图标（布局见 getMapPointIconMeta）
const MAP_POINT_MARKER = require('@/assets/images/new-bi/map_point2.png')
const MAP_CHARGE_MARKER = require('@/assets/images/new-bi/map_battery2.png')
const MAP_POINT_NAME_MAX_WIDTH = 160
// 任务路径序号底图：Figma 20×20，#456393 / #8EBAFF
const PATH_POINT_BADGE = require('@/assets/images/new-bi/path-point-badge.svg')
const PATH_DIRECTION_ARROW = require('@/assets/images/new-bi/path-direction-arrow.svg')
// Figma 机器狗图标展示 23.017×16.525，对应源图 38×28
const ROBOT_ICON_SCALE_X = 23.017 / 38
const ROBOT_ICON_SCALE_Y = 16.525 / 28
// MapTool「路径」多任务折线配色
const TASK_PATH_COLORS = ['#2563EB', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#06B6D4', '#EF4444']

export default {
  name: 'BiPatrolSlam',
  mixins: [addPointTask, mockTaskExecution, sessionTraveledPath],
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
    visibleLayout: { type: String, default: '' },
    // 全景任务列表选中的任务：只展示该任务路径；空则按 MapTool「路径」状态
    listFilterTaskId: { type: [String, Number], default: null }
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
      mapPointNameMaxWidth: MAP_POINT_NAME_MAX_WIDTH,
      pathPointBadge: PATH_POINT_BADGE,
      pathDirectionArrow: PATH_DIRECTION_ARROW,
      pathArrowWidth: PATH_ARROW_WIDTH,
      pathArrowHeight: PATH_ARROW_HEIGHT,
      imageUrl: '',
      previewImageStatus: '地图预览加载中',
      mapLoading: false,
      mapLoadFailed: false,
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
      // Robot1「显示路径」：仅控制当前选中装备任务路径
      showPolyline: false,
      // MapTool「路径」：当前 SLAM 地图全部任务路径
      showAllTaskPaths: false,
      // 悬停置顶：地图点位 / 某条任务路径
      pointsRaised: false,
      raisedTaskPathId: null,
      // Robot1「显示路径」在全量路径已开时钉住的任务路径（等同悬停效果，不受 mouseleave 清除）
      pinnedTaskPathId: null,
      showArea: false,
      showContextMenu: false,
      locationLabel: '临时点',
      collapseZoomTimer: null,
      measureActive: false,
      measureFinished: false,
      measurePoints: [],
      measureClickTimer: null,
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotLocation', 'slamOfRobot', 'showRobotIds', 'taskPathPoints', 'taskData' /* , 'robotAlarmObj' */]),
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras'] || {}
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
    measurePolylinePoints() {
      if (this.measurePoints.length < 2) return ''
      return this.measurePoints.map(p => `${p.pixel.x},${p.pixel.y}`).join(' ')
    },
    measureSegments() {
      const segs = []
      for (let i = 1; i < this.measurePoints.length; i++) {
        const a = this.measurePoints[i - 1]
        const b = this.measurePoints[i]
        const meters = this.slamPointDistance(a.mapPoint, b.mapPoint)
        segs.push({
          midX: (a.pixel.x + b.pixel.x) / 2,
          midY: (a.pixel.y + b.pixel.y) / 2,
          label: this.formatMeasureDistance(meters),
          meters
        })
      }
      return segs
    },
    measureTotalMeters() {
      return this.measureSegments.reduce((sum, seg) => sum + (seg.meters || 0), 0)
    },
    measureTotalLabel() {
      if (this.measurePoints.length < 2) return ''
      return this.formatMeasureDistance(this.measureTotalMeters)
    },
    popupStyle() {
      return this.currenRouteName === 'biIndex' ? {
        left: this.popupOffset.x + 'px',
        top: this.popupOffset.y + 'px',
        display: this.popupVisible ? 'block' : 'none'
      } : {};
    },
    normalRobots() {
      return Object.values(this.robotBaseInfo || {}).filter(item => {
        if (item.status !== 'online') return false
        if (isFixedCamera(item) || item.isFixedCamera) return false
        return true
      })
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
    // MapTool「点位」是否可切换：由 map-config.disablePointWithoutCharge 控制
    canTogglePath() {
      const points = this.map?.points || []
      if (!Array.isArray(points) || !points.length) return false
      if (isPointToolRequireCharge()) return points.some(isMapToolSpecialPoint)
      return true
    },
    // MapTool「点位」仅展示充电点 / 巡检点 / 门禁点
    activePoints() {
      return (this.map?.points || []).filter(isMapToolSpecialPoint)
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
    // MapTool「路径」：当前地图全部任务路径（含配色与名称）
    mapTaskPaths() {
      const mapId = this.map?.id
      if (mapId === undefined || mapId === null) return []
      const entries = Object.entries(this.taskPathPoints || {})
      const list = []
      entries.forEach(([taskId, data]) => {
        if (this.mockExecutionTaskId && String(taskId) === String(this.mockExecutionTaskId)) return
        if (!data || String(data.mapId) !== String(mapId)) return
        if (!Array.isArray(data.pathPoints) || !data.pathPoints.length) return
        const points = this.toDrawablePoints(data.pathPoints)
          .map((point, pathIndex) => ({ ...point, pathIndex: pathIndex + 1 }))
        if (points.length < 1) return
        const polylinePoints = points.length >= 2
          ? points.map(point => `${point.pixel.x},${point.pixel.y}`).join(' ')
          : ''
        const taskInfo = this.taskData?.[taskId] || {}
        list.push({
          taskId,
          taskName: taskInfo.name || taskInfo.taskName || `任务 ${taskId}`,
          points,
          polylinePoints
        })
      })
      const unifiedColor = TASK_PATH_COLORS[0]
      list.forEach((item, index) => {
        item.color = list.length > 1
          ? TASK_PATH_COLORS[index % TASK_PATH_COLORS.length]
          : unifiedColor
      })
      return list
    },
    // Robot1 单装备路径层（与 MapTool 全量路径合并展示）
    robot1TaskPathLayer() {
      if (!this.showPolyline || !this.activeTaskPathData) return null
      const robot = this.robotBaseInfo?.[this.selectedShowRobotId] || {}
      const taskId = robot.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return null
      if (this.mockExecutionTaskId && String(taskId) === String(this.mockExecutionTaskId)) return null
      const points = this.drawablePathPoints
      if (!points.length) return null
      const polylinePoints = points.length >= 2
        ? points.map(point => `${point.pixel.x},${point.pixel.y}`).join(' ')
        : ''
      const taskInfo = this.taskData?.[taskId] || this.activeTaskPathData || {}
      const existing = this.mapTaskPaths.find(item => String(item.taskId) === String(taskId))
      return {
        taskId,
        taskName: taskInfo.name || taskInfo.taskName || existing?.taskName || `任务 ${taskId}`,
        color: existing?.color || '#2563EB',
        points,
        polylinePoints
      }
    },
    // 当前应展示的全部任务路径层
    // 任务卡片过滤仅在 MapTool「路径」高亮（showAllTaskPaths）时生效；关闭路径则清空全部任务路径
    allDisplayTaskPaths() {
      const list = []
      const filterId = this.listFilterTaskId
      const hasListFilter = filterId !== undefined && filterId !== null && filterId !== ''
      if (this.showAllTaskPaths) {
        if (hasListFilter) {
          const hit = this.mapTaskPaths.find(item => String(item.taskId) === String(filterId))
          if (hit) list.push(hit)
        } else {
          list.push(...this.mapTaskPaths)
        }
      }
      const robot1Path = this.robot1TaskPathLayer
      if (robot1Path && (!hasListFilter || String(robot1Path.taskId) === String(filterId))
        && !list.some(item => String(item.taskId) === String(robot1Path.taskId))) {
        list.push(robot1Path)
      }
      const unifiedColor = TASK_PATH_COLORS[0]
      return list.map((item, index) => ({
        ...item,
        color: list.length > 1
          ? TASK_PATH_COLORS[index % TASK_PATH_COLORS.length]
          : unifiedColor
      }))
    },
    baseDisplayTaskPaths() {
      const raisedId = this.activeRaisedTaskPathId
      if (raisedId === undefined || raisedId === null || raisedId === '') return this.allDisplayTaskPaths
      return this.allDisplayTaskPaths.filter(item => String(item.taskId) !== String(raisedId))
    },
    raisedDisplayTaskPath() {
      const raisedId = this.activeRaisedTaskPathId
      if (raisedId === undefined || raisedId === null || raisedId === '') return null
      return this.allDisplayTaskPaths.find(item => String(item.taskId) === String(raisedId)) || null
    },
    // 悬停优先于钉住
    activeRaisedTaskPathId() {
      return this.raisedTaskPathId || this.pinnedTaskPathId
    },
    // MapTool 开点位 → 充电点/巡检点/门禁点；仅 Robot1 开路径 → 任务路径点（模板已拆分，此计算属性保留兼容）
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
        const zoom = Number(this.zoom) || 1
        pixel.x = Math.round(pixel.x * zoom) / zoom
        pixel.y = Math.round(pixel.y * zoom) / zoom
        const typeInfo = ROBOT_TYPE_INFO[robot.type] || ROBOT_TYPE_INFO.default
        const statusText = robot.customStatusName || robot.statusName
        const statusClass = robot.statusClass
        const isCamouflage = statusClass === 'blue' || statusClass === 'green'
        const name = robot.name || robot.deviceName || robot.robotId
        const nameWidth = Math.max(44, Math.ceil(String(name).length * 11) + 8)
        const statusBgWidth = Math.max(56, String(statusText).length * 12 + (isCamouflage ? 36 : 28))
        const statusBgX = -statusBgWidth / 2
        // 固定摄像头：原图 44×62，不走机器狗缩放，且不展示 robot-bg；选中用 active 图
        const isFixedCamera = typeInfo.img === 'robot-camera-normal'
          || robot.type === '固定摄像头'
          || robot.type === 'FIXED_CAMERA'
        const displayIconWidth = isFixedCamera ? typeInfo.width : typeInfo.width * ROBOT_ICON_SCALE_X
        const displayIconHeight = isFixedCamera ? typeInfo.height : typeInfo.height * ROBOT_ICON_SCALE_Y
        // 组原点为地图锚点；摄像头锚点距图标底部 4px
        const iconX = -displayIconWidth / 2
        const iconY = isFixedCamera
          ? -(displayIconHeight - 4)
          : -(23 + displayIconHeight / 2)
        const nameY = isFixedCamera ? 6 : 0
        const statusY = isFixedCamera ? 28 : 22
        // 选中四角默认框：y=-56 ~ 16，中心 -20；摄像头不展示四角，名称保持默认位置
        const defaultCornersTopY = -56
        const defaultCornersBottomY = 16
        const defaultFrameCenterY = (defaultCornersTopY + defaultCornersBottomY) / 2
        const iconCenterY = iconY + displayIconHeight / 2
        const cornersOffsetY = isFixedCamera ? iconCenterY - defaultFrameCenterY : 0
        // 选中时：普通装备名称距第二个 selected-corners 底部 2px；摄像头无四角，沿用 nameY
        const nameYSelected = isFixedCamera
          ? nameY
          : (defaultCornersBottomY + 2 + cornersOffsetY)
        const statusYSelected = nameYSelected + 22
        const cameraSelected = isFixedCamera && this.isRobotHighlighted(robot.robotId)
        const iconFile = cameraSelected ? 'robot-camera-active' : typeInfo.img
        // 暂时不显示告警事件
        // const alarmInfo = this.robotAlarmObj?.[robot.robotId]
        // let alarmText = ''
        // let alarmWidth = 0
        // let alarmX = 0
        // let alarmY = 0
        // const alarmHeight = 50
        // if (alarmInfo) {
        //   alarmText = `告警事件：${alarmInfo.categoryName || ''}：${alarmInfo.title || ''}`
        //   let textWidth = 0
        //   for (let i = 0; i < alarmText.length; i++) {
        //     textWidth += alarmText.charCodeAt(i) > 255 ? 16 : 9
        //   }
        //   alarmWidth = Math.ceil(textWidth) + 16 + 5 + 24 + 6
        //   alarmX = -(alarmWidth / 2)
        //   const visualTopY = isFixedCamera
        //     ? iconY
        //     : (this.isRobotHighlighted(robot.robotId) ? -56 : -43)
        //   alarmY = visualTopY - 53
        // }
        return {
          ...robot,
          name,
          pixel,
          nameWidth,
          isFixedCamera,
          iconUrl: require(`@/assets/images/new-bi/${iconFile}.png`),
          iconWidth: typeInfo.width,
          iconHeight: typeInfo.height,
          displayIconWidth,
          displayIconHeight,
          iconX,
          iconY,
          cornersOffsetY,
          nameY,
          statusY,
          nameYSelected,
          statusYSelected,
          statusBgWidth,
          statusBgX
          // alarmText,
          // alarmWidth,
          // alarmHeight,
          // alarmX,
          // alarmY
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
    canToggleTaskPaths() {
      return this.mapTaskPaths.length > 0
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
    zoom() {
      this.schedulePopupPositionUpdate()
    },
    offsetX() {
      this.schedulePopupPositionUpdate()
    },
    offsetY() {
      this.schedulePopupPositionUpdate()
    },
    // 装备位置推送时同步弹窗
    drawableRobots: {
      deep: true,
      handler() {
        this.schedulePopupPositionUpdate()
      }
    },
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
          this.resetSlamDrawState({ keepTempTaskPath: false })
          this.invalidateMapBitmap()
        }
        this.previewImageStatus = '地图加载中...'
        this.mapLoadFailed = false
        if (!hasPreview || id === undefined || id === null) {
          this.mapLoading = false
          this.mapLoadFailed = false
          this.revokeImageUrl()
          this.emitPreviewUnavailable(false)
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
          this.mapLoadFailed = false
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
          this.mapLoadFailed = true
          this.previewImageStatus = '地图加载失败'
          this.revokeImageUrl()
          console.error('加载 SLAM 地图预览失败', error)
          this.emitPreviewUnavailable(true)
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
    },
    // 实时监控小窗：选中任务后展示该任务路径（全景仍由 MapTool「路径」控制）
    listFilterTaskId(taskId) {
      if (this.enableAddPoint) return
      const hasTask = taskId !== undefined && taskId !== null && taskId !== ''
      this.showAllTaskPaths = hasTask
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
    /** 预览不可用：通知父级在 MapTool 地图选择处提示 */
    emitPreviewUnavailable(failed = false) {
      this.$emit('preview-unavailable', {
        failed: !!failed,
        text: failed ? '地图加载失败' : '当前地图暂无预览'
      })
    },
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
          // 路径点常缺 pointType：用地图点位数据补齐，以便应用充电/巡检/门禁图标规则
          const merged = this.mergeMapPointMeta(point)
          const id = merged.id ?? merged.mapPointId
          const coordinateX = merged.coordinateX ?? merged.x
          const coordinateY = merged.coordinateY ?? merged.y
          const pixel = this.mapPointToPixel({
            ...merged,
            coordinateX,
            coordinateY
          }, this.map)
          if (!pixel) return null
          const pointName = merged.pointName || merged.name || merged.pointCode || String(id)
          const iconMeta = getMapPointIconMeta(merged)
          const icon = {
            ...iconMeta,
            marker: iconMeta.isCharge ? MAP_CHARGE_MARKER : MAP_POINT_MARKER
          }
          // 充电点/巡检点/门禁点名称宽度对齐装备名称
          const nameWidth = iconMeta.useSpecialNameStyle
            ? Math.max(44, Math.ceil(String(pointName).length * 11) + 8)
            : MAP_POINT_NAME_MAX_WIDTH
          return { ...merged, id, pixel, pointName, icon, nameWidth }
        })
        .filter(Boolean)
    },
    /** 用当前地图 points 补齐路径点的 pointType / 名称等 */
    mergeMapPointMeta(point) {
      if (!point) return point
      const id = point.id ?? point.mapPointId
      if (id === undefined || id === null || id === '') return point
      const matched = (this.map?.points || []).find(item =>
        String(item.id ?? item.mapPointId) === String(id)
      )
      if (!matched) return point
      return {
        ...matched,
        ...point,
        pointType: point.pointType || matched.pointType,
        pointName: point.pointName || matched.pointName || matched.name,
        pointCode: point.pointCode || matched.pointCode
      }
    },
    /**
     * 路径上是否渲染点位图标
     * - 地图「点位」已开启时，已展示的特殊点位不再重复渲染
     */
    shouldShowPathPointMarker(point) {
      if (!this.showPath) return true
      const id = point?.id ?? point?.mapPointId
      if (id === undefined || id === null || id === '') return true
      return !this.drawablePoints.some(item => String(item.id) === String(id))
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
      if ((this.showRobotIds || []).some(id => String(id) === String(robotId))) return true
      // 监控等非指挥中心页：开视频后同步选中地图装备（与 GlobalGisMap.getSelectedStatus 一致）
      if (this.currenRouteName !== 'biIndex' && this.hasActiveVideo(robotId)) return true
      return false
    },
    onCanvasContextMenu(event) {
      if (!this.enableAddPoint) return
      this.onCanvasClick(event)
    },
    // first 监控页：固定摄像头不可点；未播放视频的装备不可点
    isRobotClickable(robot) {
      if (!this.enableRobotClick) return false
      if (this.showSmall) {
        if (robot?.isFixedCamera) return false
        if (!this.hasActiveVideo(robot?.robotId)) return false
      }
      return true
    },
    hasActiveVideo(robotId) {
      if (robotId === undefined || robotId === null || robotId === '') return false
      return Object.values(this.activeCameras || {}).some(
        item => String(item?.robot?.robotId) === String(robotId)
      )
    },
    handleRobotClick(event, robot) {
      if (!this.isRobotClickable(robot)) return
      // 点击装备时仅还原临时打点/派遣状态，保留 MapTool 点位与全量任务路径
      this.resetSlamDrawState({ keepMapToolPath: true, keepAllTaskPaths: true })
      if (this.currenRouteName === 'biIndex') {
        if (this.activeRobotId === robot.robotId) {
          this.closePopup()
          this.$refs.robot1Ref?.show(event, robot)
          return
        }
        this.activeRobotId = robot.robotId
        this.popupVisible = true
        // 先切换选中装备（更新弹窗内容高度），再定位，避免沿用上一台装备的高度导致错位
        this.$refs.robot1Ref?.show(event, robot)
        this.schedulePopupPositionUpdate(robot)
        return
      }
      if (this.activeRobotId === robot.robotId) {
        this.activeRobotId = ''
      } else {
        this.activeRobotId = robot.robotId
      }
      // Robot1.show -> clear([robotId]) -> setShowRobotIds，与 GIS 一致
      this.$refs.robot1Ref?.show(event, robot)
    },
    shouldKeepTempTaskDashedLine() {
      return this.shouldKeepTempTaskOverlay()
    },
    shouldKeepTempTaskOverlay() {
      if (this.mockExecBindTaskId && !this.mockExecDone) return true
      if (this.mockExecutionPathLayer?.traveledPoints) return true
      if (this.sessionTraveledPathLayers && this.sessionTraveledPathLayers.length) return true
      return !!(this.lastDrawnPaths && this.startPoint && this.endPoint)
    },
    hideTempTaskDestination() {
      this.locationPoint = null
      this.showContextMenu = false
    },
    clearTempTaskOverlay() {
      this.locationPoint = null
      this.showContextMenu = false
      this.locationLabel = '临时点'
      this.endPoint = null
      this.startPoint = null
      this.unloadedPath = []
      this.loadedPath = []
      this.lastDrawnPaths = null
      if (typeof this.reset === 'function') this.reset()
    },
    resetSlamDrawState({ keepMapToolPath = false, keepAllTaskPaths = false, keepTempTaskPath } = {}) {
      // MapTool 点位由 MapTool 控制；打开/关闭装备时保持不变
      if (!keepMapToolPath) this.showPath = false
      if (!keepAllTaskPaths) this.showAllTaskPaths = false
      this.showPolyline = false
      this.pointsRaised = false
      this.raisedTaskPathId = null
      this.pinnedTaskPathId = null
      this.showContextMenu = false
      const keepPath = keepTempTaskPath === undefined
        ? this.shouldKeepTempTaskOverlay()
        : !!keepTempTaskPath
      if (keepPath) return
      this.clearTempTaskOverlay()
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
      this.clearMeasure()
      if (typeof this.clearMockTaskExecution === 'function') {
        this.clearMockTaskExecution()
      }
      if (this.canvas) {
        this.canvas.width = 1
        this.canvas.height = 1
      }
    },
    togglePath(visible) {
      if (!this.canTogglePath) {
        this.showPath = false
        this.pointsRaised = false
        return
      }
      // MapTool「点位」
      this.showPath = typeof visible === 'boolean' ? visible : !this.showPath
      if (!this.showPath) this.pointsRaised = false
    },
    toggleTaskPaths(visible) {
      if (!this.canToggleTaskPaths) {
        this.showAllTaskPaths = false
        this.raisedTaskPathId = null
        this.pinnedTaskPathId = null
        return
      }
      this.showAllTaskPaths = typeof visible === 'boolean' ? visible : !this.showAllTaskPaths
      if (!this.showAllTaskPaths) {
        this.raisedTaskPathId = null
        this.pinnedTaskPathId = null
      }
    },
    raiseTaskPath(taskId) {
      if (!this.showAllTaskPaths && !this.showPolyline) return
      this.raisedTaskPathId = taskId
    },
    clearRaisedTaskPath() {
      // 仅清除悬停置顶，保留 Robot1 钉住的路径
      this.raisedTaskPathId = null
    },
    // 多路径时默认隐藏序号，仅悬停/钉住对应路径时显示；单路径始终显示
    shouldShowPathSeq(taskId) {
      if (this.allDisplayTaskPaths.length <= 1) return true
      return String(this.activeRaisedTaskPathId) === String(taskId)
    },
    onMapPointEnter(point) {
      this.hoveredPointId = point?.id ?? null
      if (this.showPath) this.pointsRaised = true
    },
    onMapPointLeave() {
      this.hoveredPointId = null
      this.pointsRaised = false
    },
    onRobotPathHover(robot) {
      if (!this.showAllTaskPaths && !this.showPolyline) return
      const taskId = robot?.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return
      const visible = this.allDisplayTaskPaths.some(item => String(item.taskId) === String(taskId))
      if (visible) this.raisedTaskPathId = taskId
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
      if (!robotSize.height) {
        if (!this._popupPosRetry) this._popupPosRetry = 0
        if (this._popupPosRetry < 8) {
          this._popupPosRetry += 1
          this.$nextTick(() => this.updatePopupPosition(target))
        }
        return
      }
      this._popupPosRetry = 0
      // 普通装备：选中四角 x=-36、宽 72、中点本地 y=-20
      // 固定摄像头：无四角，按图标半宽 / 图标垂直中心对齐
      const isFixedCamera = !!target.isFixedCamera
      const alignHalfWidth = isFixedCamera
        ? Math.max(22, (Number(target.displayIconWidth) || 44) / 2)
        : 36
      const alignMidOffsetY = isFixedCamera
        ? ((Number(target.iconY) || 0) + (Number(target.displayIconHeight) || 62) / 2)
        : (-20 + (Number(target.cornersOffsetY) || 0))
      const gap = 1
      // guideline.png（高 47、bottom:-47px）最底边作为模态底部对齐点
      const guidelineNaturalH = 47
      const anchorX = stageRect.left + target.pixel.x * this.zoom
      const anchorY = stageRect.top + target.pixel.y * this.zoom
      const alignRightX = anchorX + alignHalfWidth
      const alignMidY = anchorY + alignMidOffsetY
      // 初值：左缘贴对齐参考右缘；guideline 最底边对齐参考中点
      this.popupOffset = {
        x: alignRightX + gap,
        y: alignMidY - robotSize.height - guidelineNaturalH
      }
      // 内容切换后高度可能变化，下一帧按实测左缘/guideline 底边再校正，避免切换装备错位
      const token = (this._popupPosToken = (this._popupPosToken || 0) + 1)
      this.$nextTick(() => {
        requestAnimationFrame(() => {
          if (token !== this._popupPosToken || !this.popupVisible) return
          const el = this.$refs.robot1Ref && this.$refs.robot1Ref.$el
          const tipEl = this.$refs.robot1Ref && this.$refs.robot1Ref.$refs && this.$refs.robot1Ref.$refs.guidelineRef
          if (!el) return
          const modalRect = this.viewportRectToScaleRect(el.getBoundingClientRect())
          const dx = modalRect.left - (alignRightX + gap)
          let dy = 0
          if (tipEl) {
            const tipRect = this.viewportRectToScaleRect(tipEl.getBoundingClientRect())
            const guidelineBottomY = tipRect.top + tipRect.height
            dy = guidelineBottomY - alignMidY
          } else {
            const h = this.getElementSizeInScaleWrapper(el).height || robotSize.height
            dy = (modalRect.top + h + guidelineNaturalH) - alignMidY
          }
          if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
            this.popupOffset = {
              x: this.popupOffset.x - dx,
              y: this.popupOffset.y - dy
            }
          }
        })
      })
    },
    schedulePopupPositionUpdate(robot) {
      if (this.currenRouteName !== 'biIndex' || !this.popupVisible) return
      // 双 nextTick：等选中装备内容（任务行数等）渲染完再量高
      this.$nextTick(() => {
        this.$nextTick(() => this.updatePopupPosition(robot))
      })
    },
    showControlPart(visible) {
      // 关闭时两侧都关，避免选中态已清空时关错面板
      if (visible === false) {
        this.$refs.robotControlPartRef?.show?.(false)
        this.$refs.robotCarControlPartRef?.show?.(false)
        return
      }
      const robot = {
        ...(this.selectedRobot || {}),
        ...(this.robotBaseInfo?.[this.selectedRobot?.robotId] || {})
      }
      const controlRef = isRobotDog(robot) ? this.$refs.robotControlPartRef : this.$refs.robotCarControlPartRef
      const nextVisible = typeof visible === 'boolean' ? visible : !controlRef?.visible
      controlRef?.show(nextVisible)
    },
    showPathArea(visible) {
      const next = typeof visible === 'boolean' ? visible : !this.showPolyline
      // SLAM 全量任务路径已显示时：点击「显示路径」= 钉住当前装备路径（等同鼠标移入效果）
      if (this.showAllTaskPaths) {
        const robot = this.robotBaseInfo?.[this.selectedShowRobotId] || {}
        const taskId = robot.runningTaskId
        if (next && taskId !== undefined && taskId !== null && taskId !== '') {
          const onMap = this.mapTaskPaths.some(item => String(item.taskId) === String(taskId))
          if (onMap) {
            this.pinnedTaskPathId = taskId
            this.raisedTaskPathId = null
            this.showPolyline = false
            return
          }
        }
        if (!next) {
          this.pinnedTaskPathId = null
          this.raisedTaskPathId = null
          this.showPolyline = false
          return
        }
      }
      // 常规：Robot1 单独控制当前装备任务路径
      this.showPolyline = next
      if (!next) this.pinnedTaskPathId = null
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
        // 关闭 Robot1：关掉路径线与钉住态，保留 MapTool 点位/全量路径
        this.showPolyline = false
        this.pinnedTaskPathId = null
        this.raisedTaskPathId = null
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
      const isIdle = robot.customStatusName === '空闲中'
      try {
        await this.$primaryConfirm({
          title: '提示',
          message: isIdle
            ? '是否【立即派遣】该装备前往该点？'
            : '当前选择装备正在【任务中】，是否终止任务？进行新任务',
          confirmText: '确定',
          cancelText: '取消',
          onConfirm: async () => {
            await this.addTask(startPoint)
          }
        })
      } catch (error) {
        // 用户取消：保留右键菜单，便于继续选择装备
      }
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
      const useMock = ENABLE_LIANTONG_SLAM_MOCK && String(this.robotId).startsWith('mock-')
      let walkPixels = null
      if (useMock && ENABLE_LIANTONG_TASK_EXECUTION_MOCK) {
        walkPixels = this.buildMockWalkPixels(startPoint, this.endPoint)
        if (!walkPixels) {
          this.showMockNoPathError()
          throw new Error('MOCK_TEMP_TASK_NO_PATH')
        }
      }
      let taskId = null
      if (useMock) {
        taskId = this.applyMockTemporaryTask(data)
      } else {
        const res = await addTaskByPoint(data)
        console.log('派遣任务结果', res)
      }
      this.setStartPoint(startPoint)
      this.closeContextMenu()
      this.$message.success('任务派遣成功')
      if (walkPixels) {
        this.startMockTaskExecution({
          robotId: this.robotId,
          taskId,
          walkPixels
        })
      }
    },
    applyMockTemporaryTask({ robotId, x, y, yaw }) {
      const robot = this.robotBaseInfo?.[robotId] || {}
      const location = this.robotLocation?.[robotId] || robot.location || {}
      const oldTaskId = robot.runningTaskId
      const mapId = this.map?.id
      if (oldTaskId != null && this.taskData?.[oldTaskId]) {
        this.$store.commit('websocketExtraData/SET_TASK_INFO', {
          ...this.taskData[oldTaskId],
          status: 'terminated',
          statusName: '已终止'
        })
        const oldPath = this.taskPathPoints?.[oldTaskId]
        this.$store.commit('websocketExtraData/SET_TASK_PATH_POINTS', {
          taskId: oldTaskId,
          data: { mapId: oldPath?.mapId || mapId, pathPoints: [] }
        })
      }
      const taskId = `mock-temp-nav-${robotId}-${Date.now()}`
      const startX = location.x ?? location.coordinateX
      const startY = location.y ?? location.coordinateY
      const pathPoints = [
        {
          id: `${taskId}-start`,
          mapId,
          pointName: '起始地',
          pointType: 'START',
          coordinateX: startX,
          coordinateY: startY,
          coordinateZ: location.yaw ?? 0
        },
        {
          id: `${taskId}-end`,
          mapId,
          pointName: this.locationLabel || '临时点',
          pointType: 'NORMAL',
          coordinateX: x,
          coordinateY: y,
          coordinateZ: yaw ?? 0
        }
      ]
      this.$store.commit('websocketExtraData/SET_TASK_INFO', {
        taskId,
        mapId,
        name: this.locationLabel || '临时任务',
        status: 'running',
        statusName: '执行中',
        timeRange: '临时',
        pathPoints,
        equipmentList: [{
          robotId,
          name: robot.name,
          type: robot.type,
          typeCode: robot.typeCode,
          status: 'online'
        }]
      })
      this.$store.commit('websocketExtraData/SET_TASK_PATH_POINTS', {
        taskId,
        data: { mapId, pathPoints }
      })
      this.$store.commit('websocketExtraData/SET_ROBOT_BASE_INFO', {
        robotId,
        robotInfo: {
          ...robot,
          controlMode: '导航模式'
        }
      })
      return taskId
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

      // 视口贴合上限（两侧收缩后可视区）；再放大 2.5 倍，仍用同一 zoom 保证宽高等比不变形
      const collapsedSize = this.getCollapsedViewportSize()
      const fitMaxZoom = Math.max(
        0.1,
        Math.min(collapsedSize.width / mapWidth, collapsedSize.height / mapHeight)
      )
      const maxZoom = fitMaxZoom * 2.5

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
      // 放大：宽高等比共用同一 zoom，上限为视口贴合比例 × 2.5
      const step = Math.max(0.1, this.defaultZoomValue * 0.05)
      this.zoom = Math.min(this.maxZoomValue, Number((this.zoom + step).toFixed(3)))
      this.$nextTick(() => {
        this.syncCanvasResolution()
        this.schedulePopupPositionUpdate()
      })
    },
    zoomOut() {
      const step = Math.max(0.1, this.defaultZoomValue * 0.05)
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - step).toFixed(3)))
      this.$nextTick(() => {
        this.syncCanvasResolution()
        this.schedulePopupPositionUpdate()
      })
    },
    resetView() {
      this.zoom = this.defaultZoomValue
      this.offsetX = 0
      this.offsetY = 0
      this.$nextTick(() => {
        this.syncCanvasResolution()
        this.schedulePopupPositionUpdate()
      })
    },
    /** 复位：恢复默认加载时的缩放与位置（与 resetView 一致） */
    backCenter() {
      this.resetView()
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
      this.schedulePopupPositionUpdate()
    },
    handleMouseUp() {
      this.isDragging = false
      document.removeEventListener('mousemove', this.handleMouseMove)
      document.removeEventListener('mouseup', this.handleMouseUp)
      this.schedulePopupPositionUpdate()
    },
    handleWheel(e) {
      e.preventDefault()
      if (e.deltaY > 0) this.zoomOut()
      else this.zoomIn()
      this.schedulePopupPositionUpdate()
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
    pixelToMapPoint(pixel, map, options = {}) {
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
      const coordinateX = originX + dx
      const coordinateY = originY + dy
      if (options.round === false) {
        return {
          coordinateX,
          coordinateY,
          coordinateZ: 0,
          pixelX: pixel.x,
          pixelY: pixel.y
        }
      }
      return {
        coordinateX: Number(coordinateX.toFixed(3)),
        coordinateY: Number(coordinateY.toFixed(3)),
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
    handleCanvasBlankClick(event) {
      // 拖拽平移地图后不触发清除
      if (this.dragMoved) return
      if (this.measureActive) {
        this.scheduleMeasurePoint(event)
        return
      }
      this.clearTempPointIfNoPath()
    },
    handleMapClick(event) {
      if (this.dragMoved) return
      if (this.measureActive) {
        this.scheduleMeasurePoint(event)
        return
      }
      this.clearTempPointIfNoPath()
      const pixel = this.eventToPixel(event);
      const point = this.pixelToMapPoint(pixel, this.map);
      // if (point) this.$emit("map-click", point);
    },
    // MapTool 测距：SLAM 地图坐标系距离（米）
    toggleRanging(visible) {
      const next = typeof visible === 'boolean' ? visible : !this.measureActive
      this.measureActive = next
      if (next) {
        this.measureFinished = false
        window.addEventListener('keydown', this.handleMeasureKeydown)
      } else {
        this.clearMeasure()
        window.removeEventListener('keydown', this.handleMeasureKeydown)
      }
    },
    // 延迟落点：双击产生的 click 在提交前被取消，避免多打点
    scheduleMeasurePoint(event) {
      const pixel = this.eventToPixel(event)
      if (!pixel) return
      const mapPoint = this.pixelToMapPoint(pixel, this.map)
      if (!mapPoint) return
      const payload = {
        pixel: { x: pixel.x, y: pixel.y },
        mapPoint
      }
      this.cancelPendingMeasurePoint()
      this.measureClickTimer = setTimeout(() => {
        this.measureClickTimer = null
        this.commitMeasurePoint(payload)
      }, 250)
    },
    cancelPendingMeasurePoint() {
      if (!this.measureClickTimer) return
      clearTimeout(this.measureClickTimer)
      this.measureClickTimer = null
    },
    handleMeasureDblClick(event) {
      if (!this.measureActive) return
      event.preventDefault()
      event.stopPropagation()
      // 取消双击触发的 click 落点，只结束测段
      this.cancelPendingMeasurePoint()
      if (this.measurePoints.length >= 2) this.measureFinished = true
    },
    handleMeasureKeydown(e) {
      if (!this.measureActive) return
      if (e.key === 'Escape') {
        this.clearMeasure()
      } else if (e.key === 'Backspace' || e.key === 'Delete') {
        e.preventDefault()
        this.undoMeasurePoint()
      }
    },
    commitMeasurePoint(payload) {
      if (!payload?.pixel || !payload?.mapPoint) return
      if (this.measureFinished) {
        this.measurePoints = []
        this.measureFinished = false
      }
      this.measurePoints.push(payload)
    },
    undoMeasurePoint() {
      this.cancelPendingMeasurePoint()
      if (!this.measurePoints.length) return
      this.measurePoints.pop()
      this.measureFinished = false
    },
    clearMeasure() {
      this.cancelPendingMeasurePoint()
      this.measurePoints = []
      this.measureFinished = false
    },
    slamPointDistance(a, b) {
      if (!a || !b) return 0
      const dx = Number(a.coordinateX) - Number(b.coordinateX)
      const dy = Number(a.coordinateY) - Number(b.coordinateY)
      if (!Number.isFinite(dx) || !Number.isFinite(dy)) return 0
      return Math.sqrt(dx * dx + dy * dy)
    },
    formatMeasureDistance(meters) {
      const m = Number(meters) || 0
      if (m >= 1000) return `${(m / 1000).toFixed(2)} km`
      return `${m.toFixed(1)} m`
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
    this.cancelPendingMeasurePoint()
    window.removeEventListener('keydown', this.handleMeasureKeydown)
    this.resizeObserver?.disconnect()
    this.revokeImageUrl();
  }
}
</script>

<style lang="scss">
.slam-map-root {
  position: relative;
  width: 100%;
  height: 100%;
  max-width: 100%;
  min-width: 0;
}
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
  &.is-measuring {
    cursor: crosshair !important;
    &:active {
      cursor: crosshair !important;
    }
    // 测距时 SVG 需接收空白点击；并屏蔽点位/装备/路径抢占事件
    .map-preview-stage {
      cursor: crosshair !important;
      &:active {
        cursor: crosshair !important;
      }
      > svg {
        pointer-events: auto !important;
        cursor: crosshair !important;
      }
      .map-preview-image {
        cursor: crosshair !important;
      }
    }
    .map-preview-point,
    .map-preview-robot,
    .map-task-path-layer {
      pointer-events: none !important;
      cursor: crosshair !important;
    }
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
        // MapTool 点位：普通点 map_point2；充电点 map_battery2（底部中心锚点，名称 mt4）
        &.is-map-tool,
        &.is-path-point {
          .map-point-marker {
            filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
          }
          .map-point-name-fo {
            overflow: visible;
          }
          .map-point-name-wrap {
            display: flex;
            justify-content: center;
            width: 100%;
            pointer-events: none;
          }
          .map-point-name {
            box-sizing: border-box;
            width: fit-content;
            max-width: 100%;
            padding: 2px 4px;
            border-radius: 2px;
            background: #009465;
            color: #FFF;
            font-family: "Microsoft YaHei", sans-serif;
            font-size: 14px;
            font-style: normal;
            font-weight: 400;
            line-height: 17.517px; /* 125.119% */
            text-align: center;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            // 充电点 / 巡检点 / 门禁点名称：与装备名称 robot-name-pill 一致
            &.is-equip-name {
              width: 100%;
              height: 20px;
              border-radius: 0;
              background: transparent;
              color: #000;
              font-size: 11px;
              font-weight: 600;
              line-height: 16px;
              overflow: visible;
              text-overflow: clip;
              text-shadow:
                -1px -1px 0 #FFF,
                 1px -1px 0 #FFF,
                -1px  1px 0 #FFF,
                 1px  1px 0 #FFF,
                 0   -1px 0 #FFF,
                 0    1px 0 #FFF,
                -1px  0   0 #FFF,
                 1px  0   0 #FFF;
            }
          }
          &.hovered .map-point-marker {
            filter: drop-shadow(0 0 4px rgba(255, 246, 69, 0.65));
          }
        }
      }
      // 任务路径序号：Figma 右上角蓝底白字（置顶层不在 map-preview-point 内，需与点位同级）
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
      .map-preview-path {
        fill: none;
        stroke: #2563eb;
        stroke-width: 3;
        stroke-linecap: round;
        stroke-linejoin: round;
        vector-effect: non-scaling-stroke;
        filter: drop-shadow(0 1px 2px rgba(0, 0, 0, .35));
        pointer-events: stroke;
      }
      .map-preview-path-hit {
        fill: none;
        stroke: transparent;
        stroke-width: 14;
        stroke-linecap: round;
        stroke-linejoin: round;
        vector-effect: non-scaling-stroke;
        pointer-events: stroke;
      }
      .mock-exec-path-traveled {
        fill: none;
        stroke: #18D0DD;
        stroke-width: 8;
        stroke-linecap: round;
        stroke-linejoin: round;
        vector-effect: non-scaling-stroke;
      }
      .mock-exec-path-traveled-core {
        fill: none;
        stroke: #fff;
        stroke-width: 2.5;
        stroke-linecap: round;
        stroke-linejoin: round;
        vector-effect: non-scaling-stroke;
      }
      .map-measure-line {
        fill: none;
        stroke: #21C8FF;
        stroke-width: 3;
        stroke-linecap: round;
        stroke-linejoin: round;
        stroke-dasharray: 6 4;
        vector-effect: non-scaling-stroke;
        &.is-finished {
          stroke-dasharray: none;
        }
      }
      .map-measure-dot {
        fill: #21C8FF;
        stroke: #fff;
        stroke-width: 2;
        &.is-finished {
          fill: #22C55E;
        }
      }
      .map-measure-label,
      .map-measure-total {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        padding: 0 6px;
        border-radius: 4px;
        box-sizing: border-box;
        font-family: "Alibaba PuHuiTi", "Microsoft YaHei", sans-serif;
        font-size: 12px;
        line-height: 16px;
        color: #E8F7FF;
        background: rgba(1, 28, 57, 0.88);
        border: 1px solid rgba(33, 200, 255, 0.65);
        white-space: nowrap;
        pointer-events: none;
      }
      .map-measure-total {
        color: #0BF9FE;
        font-weight: 600;
        justify-content: flex-start;
      }
      .map-task-path-layer {
        pointer-events: auto;
        &.is-raised .map-preview-path {
          stroke-width: 6;
        }
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
          pointer-events: none;
        }
        .robot-name-pill {
          box-sizing: border-box;
          width: 100%;
          height: 20px;
          padding: 2px 4px;
          color: #000;
          font-family: "Microsoft YaHei";
          font-size: 11px;
          font-weight: 600;
          line-height: 16px;
          text-align: center;
          white-space: nowrap;
          transform: translateZ(0);
          backface-visibility: hidden;
          // 1px 白色描边，保证地图上清晰可读
          text-shadow:
            -1px -1px 0 #FFF,
             1px -1px 0 #FFF,
            -1px  1px 0 #FFF,
             1px  1px 0 #FFF,
             0   -1px 0 #FFF,
             0    1px 0 #FFF,
            -1px  0   0 #FFF,
             1px  0   0 #FFF;
        }
        // 暂时不显示告警事件
        // .robot-warning-fo {
        //   overflow: visible;
        //   pointer-events: none;
        // }
        // .robot-warning {
        //   padding: 10px 12px;
        //   width: max-content;
        //   margin-left: -6px;
        //   color: #FFF;
        //   font-family: "Microsoft YaHei";
        //   font-size: 16px;
        //   line-height: 18px;
        //   background: #410912;
        //   border: 1px solid #FF0202;
        //   border-radius: 2px;
        //   white-space: nowrap;
        //   flex-wrap: nowrap;
        //   position: relative;
        //   &::after {
        //     position: absolute;
        //     right: 0;
        //     left: 0;
        //     bottom: -7px;
        //     margin: 0 auto;
        //     width: 10px;
        //     height: 10px;
        //     background: #410912;
        //     border: 1px solid #FF0202;
        //     border-top: none;
        //     border-right: none;
        //     transform: rotate(-45deg);
        //     transform-origin: top;
        //     content: "";
        //     z-index: 0;
        //   }
        //   svg {
        //     flex-shrink: 0;
        //     position: relative;
        //     z-index: 1;
        //   }
        //   .ml5 {
        //     margin-left: 5px;
        //     position: relative;
        //     z-index: 1;
        //   }
        // }
        .robot-status-fo {
          overflow: visible;
          pointer-events: none;
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
          transform: translateZ(0);
          backface-visibility: hidden;
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
    padding: 9px 10px;
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
      cursor: pointer;
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
