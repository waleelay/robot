<template>
  <div class="machine-container robot-container new" :class="{ visible }" :style="positionStyle" ref="containerRef">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">{{currenRobot?.name  || '-'}}</div>
          <div class="status ml10" :class="currenRobot?.statusClass || ''">{{ currenRobot?.customStatusName || currenRobot?.status || '-' }}</div>
        </div>
        <div class="close mr10" @click="onClose()">
          <svg-icon icon-class="close"></svg-icon>
        </div>
      </div>
      <div class="info-content pr10 pl10 flex flex-wrap mt15">
        <div class="item wp156">
          装备类型：<span class="value">{{ currenRobot?.type || '-' }}</span>
        </div>
        <div class="item wp149 ml26">
          当前电量：<span class="value">{{ currenRobot?.battery || '-' }}%</span>
        </div>
        <div class="item wp156 mt10">
          装备型号：<span class="value">{{ currenRobot?.model || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          是否告警：<span class="value">{{ currenRobot?.alarmLevel === 'none' ? '否' : '是' }}</span>
        </div>
        <div class="item wp156 mt10">
          控制模型：<span class="value">{{ currenRobot?.controlMode || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          上装设备：<span class="value">{{ currenRobot?.mountedDeviceCount || 0 }}台</span>
        </div>
        <div class="item wp156 mt10">
          当前速度：<span class="value">{{ currenRobot?.speed || 0 }}m/s</span>
        </div>
        <div class="mt10 with-divider w100"></div>
        <div v-for="(task, index) in taskList" :key="task.taskId" class="mt10 task flex" :class="task.status" :title="task?.statusName">
          <div class="item wp156 text-ellipsis" :title="task?.name || ''">
            <span class="wp60 tar">任务{{index + 1}}：</span><span class="value">{{ task?.name || '-' }}</span>
          </div>
          <div class="item wp149 ml26">
            任务时段：<span class="value">{{ task?.timeRange || '-' }}</span>
          </div>
        </div>
      </div>
      <div class="btns mt10 mb20 ml0 flx-align-center flex-wrap wp360" style="margin-top: -10px !important">
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="$emit('showControlPart')">远程控制</el-button>
        <!-- <el-button type="primary" class="mt20" @click="$emit('showSlam', true)">SLAM地图</el-button> -->
        <el-button v-if="showAnimate && showControl && currenRobot?.runningTaskId" type="primary" class="mt20" @click="$emit('showSlam', true)">SLAM地图</el-button>
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="onShutdown()">一键返航</el-button>
        <el-button v-if="showAnimate && showControl" type="primary" class="mt20" @click="onStartup()">退出充电桩</el-button>
        <!-- <el-button type="primary" @click="onAddTask()">添加任务</el-button> -->
        <el-button v-if="hasTaskPath || globalMapId === 'gis'" type="primary" class="mt20" @click="togglePath()">显示路径</el-button>
        <el-button v-if="globalMapId === 'gis'" type="primary" class="mt20" @click="$emit('showArea', true)">显示区域</el-button>
      </div>
    </div>
    <!-- <div class="guideline wp157 hp29 mt9 ml161">
      <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
    </div> -->
    <img v-if="!showAnimate" width="197" height="47" style="position: absolute; bottom: -47px; left: 0" src="@/assets/images/new-bi/guideline.png" alt="">
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import gsap from './gsap.js';
import { getDescArr } from '../../../../../utils/index.js';
export default {
  name: 'Modal',
  mixins: [gsap],
  data() {
    return {
      className: '',
      pathVisible: false,
    }
  },
  computed: {
    showControl() {
      return this.selectedRobot?.status === 'online'
    },
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    ...mapState('websocketExtraData', ['robotBaseInfo', 'taskData', 'taskPathPoints', 'globalMapId']),
    currenRobot() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    },
    taskList() {
      const { task = [] } = this.currenRobot || {}
      return getDescArr(task?.map(item => this.taskData?.[item.taskId] || item) || [], 'timestamp')
    },
    // 装备关联任务路径有点位时才显示「显示路径」按钮
    hasTaskPath() {
      const taskId = this.currenRobot?.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return false
      const pathData = this.taskPathPoints?.[taskId]
      if (!pathData || !Array.isArray(pathData.pathPoints) || !pathData.pathPoints.length) return false
      const mapId = this.globalMapId
      if (mapId && mapId !== 'gis' && pathData.mapId != null && pathData.mapId !== '' &&
        String(pathData.mapId) !== String(mapId)) {
        return false
      }
      return true
    },
    // getRunningTask() {
    //   return this.taskList?.find(item => item.status === 'running') || null
    // },
    // getStatus() {
    //   return this.currenRobot?.status === 'online' ? this.getRunningTask ? '任务中' : '空闲中' : this.currenRobot?.status === 'offline' ? '离线' : '故障'
    // },
    // getStatusClass() {
    //   return this.currenRobot?.status === 'online' ? this.getRunningTask ? 'blue' : 'green' : this.currenRobot?.status === 'offline' ? '' : 'orange'
    // }
  },
  watch: {
    hasTaskPath(val) {
      if (!val && this.pathVisible) {
        this.pathVisible = false
        this.$emit('showPath', false)
      }
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    onShutdown() {
      // this.$emit('shutdown')
    },
    onStartup() {
      // this.$emit('startup')
    },
    onClose() {
      this.visible = false
      this.pathVisible = false
      this.$emit('showPath', false)
      this.handleGlobalClick(null, false)
      this.setSelectedRobotId('')
      this.$emit('clear')
    },
    togglePath() {
      this.pathVisible = !this.pathVisible
      this.$emit('showPath', this.pathVisible)
    },
    show(e, robot) {
      this.$emit('showControlPart', false)
      if (this.selectedRobotId === robot?.robotId || !e) {
        this.pathVisible = false
        this.$emit('showPath', false)
        this.setSelectedRobotId('')
        this.handleGlobalClick(e, false)
        this.$emit('clear', [])
      } else {
        // 切换装备时关闭路径线，不影响 MapTool 点位
        this.pathVisible = false
        this.$emit('showPath', false)
        this.visible = true
        this.setSelectedRobotId(robot?.robotId)
        this.handleGlobalClick(e, true)
        this.$emit('clear', [robot?.robotId])
      }
    }
  },
}
</script>

<style lang="scss" scoped>
.btns {
  .el-button:first-child {
    margin-left: 10px;
  }
}
.machine-container.robot-container.new {
  position: fixed;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  will-change: left, top, transform, scale, opacity, backdrop-filter;
  &.visible {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    backdrop-filter: blur(15px);
  }
  .box {
    width: min-content;
    .info-content {
      .item {
        color: rgba(255, 255, 255, 0.80);
        font-family: "Microsoft YaHei";
        line-height: 18px;
        .value {
          color: #FFF;
        }
      }
      .with-divider {
        border-top: 1px solid #5DA7FF;
      }
      .task {
        &.pending {
          .value {
            color: #FF7734;
          }
        }
        &.running {
          .value {
            color: #25FF6E;
          }
        }
      }
    }
  }
}
</style>