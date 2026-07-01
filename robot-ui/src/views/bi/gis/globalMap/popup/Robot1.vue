<template>
  <div class="machine-container robot-container new" :class="{ visible }" :style="positionStyle" ref="containerRef">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">{{currenRobot?.name  || '监区点名机器人'}}</div>
          <div class="status ml10">{{ currenRobot?.status || '空闲中' }}</div>
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
        <div class="item wp156 mt10">
          任务名称：<span class="value">{{ currenRobot?.task?.name || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          任务时段：<span class="value">{{ currenRobot?.task?.timeRange || '-' }}</span>
        </div>
      </div>
      <div class="btns mt10 mb20 ml0 flx-align-center flex-wrap wp360" style="margin-top: -10px !important">
        <el-button v-if="showOper && showControl" type="primary" class="mt20" @click="$emit('showControlPart', true)">远程控制</el-button>
        <el-button v-if="showOper && showControl" type="primary" class="mt20" @click="$emit('showSlam', true)">SLAM地图</el-button>
        <el-button v-if="showOper && showControl" type="primary" class="mt20" @click="onShutdown()">一键返航</el-button>
        <el-button v-if="showOper && showControl" type="primary" class="mt20" @click="onStartup()">退出充电桩</el-button>
        <!-- <el-button type="primary" @click="onAddTask()">添加任务</el-button> -->
        <el-button type="primary" class="mt20" @click="$emit('showPath', true)">显示路径</el-button>
        <el-button type="primary" class="mt20" @click="$emit('showArea', true)">显示区域</el-button>
      </div>
    </div>
    <!-- <div class="guideline wp157 hp29 mt9 ml161">
      <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
    </div> -->
    <img v-if="!showOper" width="197" height="47" style="position: absolute; bottom: -47px; left: 0" src="@/assets/images/new-bi/guideline.png" alt="">
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import gsap from './gsap.js';
export default {
  name: 'Modal',
  mixins: [gsap],
  data() {
    return {
      className: '',
    }
  },
  computed: {
    showOper() {
      return this.$route.name !== 'biIndex'
    },
    showControl() {
      return this.selectedRobot?.status === 'online'
    },
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    currenRobot() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    onClose() {
      this.visible = false
      this.handleGlobalClick(null, false)
      this.setSelectedRobotId('')
      this.$emit('clear')
    },
    show(e, robot) {
      this.$emit('showControlPart', false)
      if (this.selectedRobotId === robot.robotId) {
        this.setSelectedRobotId('')
        this.handleGlobalClick(e, false)
      } else {
        this.visible = true
        this.setSelectedRobotId(robot.robotId)
        this.handleGlobalClick(e, true)
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
    visibility: visible;
    pointer-events: auto;
  }
  .box {
    width: min-content;
    background: linear-gradient(180deg, rgba(4, 91, 149, 0.50) 0.01%, rgba(4, 51, 68, 0.37) 5.51%, rgba(4, 42, 62, 0.40) 51.52%, rgba(7, 56, 94, 0.38) 92.62%, rgba(4, 78, 151, 0.50) 100.03%);
    border-color: #2A86F3;
    .top {
      background: linear-gradient(90deg, #2C8EFF -0.18%, rgba(0, 13, 59, 0.20) 94.39%);
    }
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
    }
  }
}
</style>