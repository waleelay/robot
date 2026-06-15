<template>
  <div class="machine-container robot-container new" :class="{ visible }" :style="positionStyle" ref="containerRef">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box">
      <div class="top m4 flx-justify-between">
        <div class="flx-align-center">
          <div class="title ml10">{{selectedRobot.name  || '监区点名机器人'}}</div>
          <div class="status ml10">{{ selectedRobot.status || '空闲中' }}</div>
        </div>
        <div class="close mr10" @click="onClose()">
          <svg-icon icon-class="close"></svg-icon>
        </div>
      </div>
      <div class="info-content pr10 pl10 flex flex-wrap mt15">
        <div class="item wp156">
          装备类型：<span class="value">{{ selectedRobot.type || '-' }}</span>
        </div>
        <div class="item wp149 ml26">
          当前电量：<span class="value">{{ selectedRobot.battery || '-' }}%</span>
        </div>
        <div class="item wp156 mt10">
          装备型号：<span class="value">{{ selectedRobot.model || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          是否告警：<span class="value">{{ selectedRobot.isWarning || '-' }}</span>
        </div>
        <div class="item wp156 mt10">
          控制模型：<span class="value">{{ selectedRobot.controlMode || '-' }}</span>
        </div>
        <div class="item wp149 ml26 mt10">
          上装设备：<span class="value">{{ '3' }}台</span>
        </div>
        <div class="item wp156 mt10">
          当前速度：<span class="value">{{ selectedRobot.speed || '-' }}m/s</span>
        </div>
        <div class="mt10 with-divider w100"></div>
        <div class="item wp156 mt10">
          任务名称：<span class="value">A区-夜间巡逻</span>
        </div>
        <div class="item wp149 ml26 mt10">
          任务时段：<span class="value">20:00-22:00</span>
        </div>
      </div>
      <div class="btns m10 mb20 flx-align-center">
        <el-button type="primary" @click="$emit('showControlPart', true)">远程控制</el-button>
        <el-button type="primary" @click="$emit('showSlam', true)">SLAM地图</el-button>
        <el-button type="primary" @click="onShutdown()">一键返航</el-button>
        <el-button type="primary" @click="onStartup()">退出充电桩</el-button>
        <!-- <el-button type="primary" @click="onAddTask()">添加任务</el-button> -->
      </div>
      <div class="btns m10 mb20 flx-align-center">
        <el-button type="primary" @click="$emit('showPath', true)">显示路径</el-button>
        <el-button type="primary" @click="$emit('showArea', true)">显示区域</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import gsap from './gsap.js';
export default {
  name: 'Modal',
  mixins: [gsap],
  data() {
    return {
      className: ''
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
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
      
      console.log('show=========', this.selectedRobotId, 22, robot.robotId);
      if (this.selectedRobotId === robot.robotId) {
        this.setSelectedRobotId('')
        this.handleGlobalClick(e, false)
      } else {
        this.visible = true
        this.setSelectedRobotId(robot.robotId)
        this.handleGlobalClick(e, true)
      }
      this.$emit('showControlPart', false)
    }
  },
}
</script>

<style lang="scss" scoped>
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