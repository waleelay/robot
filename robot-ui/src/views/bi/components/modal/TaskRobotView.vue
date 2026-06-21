<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-02 14:10:40
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\modal\RobotControlPart.vue
 * @Version: 
-->
<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :modal="false"
    append-to-body
    title="异常报告"
  >
    <template slot="footer"></template>
    <div class="flex mb20">
      <div class="custom-modal-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">{{ taskInfo.taskName }}</div>
              <!-- <span class="status flx-center ml10 pt2 pr6 pb2 pl6">
                <svg-icon icon-class="security"></svg-icon>
                <span class="ml4">{{ taskInfo.status }}</span>
              </span>  -->
            </div>
            <div class="flx-center">
              <div class="setting flx-center curp" @click="goTask">
                <svg-icon icon-class="setting"></svg-icon>
                <span class="ml4">深度控制</span>
              </div>
              <div class="close mr10 ml10" @click="closeModal">
                <svg-icon icon-class="close"></svg-icon>
              </div>
            </div>
          </div>
          <div class="info-content flex robot-list p10" style="align-items: flex-start" :class="{ 'show-page': robotList?.length > 2 }">
            <div class="page page-pre flx-center">
              <svg-icon icon-class="d-left"></svg-icon>
            </div>
            <div class="page page-next flx-center">
              <svg-icon icon-class="d-right"></svg-icon>
            </div>
            <div v-for="(robot, index) in robotList || []" :key="robot.robotId" class="item p10" :class="{ 'ml10': index !== 0 }">
              <div class="d-flex hp145">
                <div>
                  <div class="flx-justify-between">
                    <div class="title">{{ robot.name }}</div>
                    <div class="status pl10">LIVE</div>
                  </div>
                  <div class="d-flex hp122 mt10">
                    <div class="wp216 h100 main">
                      <VideoBox
                        @toggleFullscreen="toggleFullscreen"
                        :videoIndex="`${robot.robotId}_${index}_0`"
                        :prefixId="prefixId"
                        :ZQL_videosInfos="ZQL_videosInfos"
                        className="six-1" />
                    </div>
                  </div>
                </div>
                <div v-if="robot?.cameras?.length > 1" class="mt4 ml10 p5 side-list common-scroll ovya">
                  <div
                    v-for="(camera, cameraIndex) in robot.cameras.slice(1)"
                    :key="camera.key"
                    class="wp108 hp62 main curp"
                    :class="{ 'mt4': cameraIndex !== 0 }">
                    <VideoBox
                      @toggleFullscreen="toggleFullscreen"
                      @select="swapWithMain(robot.robotId, index, cameraIndex + 1)"
                      :videoIndex="`${robot.robotId}_${index}_${cameraIndex + 1}`"
                      :prefixId="prefixId"
                      :ZQL_videosInfos="ZQL_videosInfos"
                      className="six-1" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import VideoBox from './VideoBox.vue';
import common from './common.js';
import { toggleFullscreen } from '../../../../utils/fullscreen.js';
import { mapActions } from 'vuex';

export default {
  name: 'TaskRobotView',
  components: { VideoBox },
  mixins: [common],
  data() {
    return {
      dialogVisible: false,
      taskInfo: {},
      robotIds: ['robot-001'],
      robotList: [],
      taskIndex: '',
      prefixId: 'task-robot-video-div',
      cameraOrderByRobot: {},
    }
  },
  mounted() {
    // setTimeout(() => {
    //   this.started = true
    // }, 3000);
  },
  computed: {
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    cameras() {
      return this.$store.getters['websocketRobot/getCameras'];
    },
  },
  methods: {
    ...mapActions('websocketRobot', ['setPrefixId']),
    async showModal(data) {
      this.dialogVisible = true;
      this.robotIds = data.robotIds
      this.taskInfo = { ...data.taskInfo }
      this.started = true
      await this.$nextTick()
      await this.syncRobotList()
    },
    closeModal() {
      this.dialogVisible = false;
      this.$emit('handleClickTask', this.taskInfo.taskId)
    },
    goTask() {
      this.$router.push({ path: '/bi/patrol/monitor', query: { taskId: this.taskInfo.taskId || 0 } })
    },
    orderedCameras(robot) {
      const cameras = (robot.cameras || [])
        .map(camera => this.cameras[camera.key] || camera)
        .sort((a, b) => {
          if (a.groupType === 'body') return -1
          if (b.groupType === 'body') return 1
          return 0
        })
      const availableKeys = cameras.map(camera => camera.key)
      const previousOrder = this.cameraOrderByRobot[robot.robotId] || []
      const order = previousOrder
        .filter(key => availableKeys.includes(key))
        .concat(availableKeys.filter(key => !previousOrder.includes(key)))
      this.$set(this.cameraOrderByRobot, robot.robotId, order)
      return order.map(key => this.cameras[key] || cameras.find(camera => camera.key === key))
    },
    async syncRobotList() {
      if (!this.robots?.length || !this.robotIds?.length || !this.dialogVisible) return
      this.setPrefixId(this.prefixId)
      const robotList = this.robots
        .filter(robot => this.robotIds.includes(robot.robotId))
        .map(robot => ({ ...robot, cameras: this.orderedCameras(robot) }))
      this.$set(this, 'robotList', robotList)
      await this.updateInfo()
    },
    async swapWithMain(robotId, robotIndex, cameraIndex) {
      const robot = this.robotList[robotIndex]
      if (!robot || cameraIndex <= 0 || cameraIndex >= robot.cameras.length) return
      const cameras = [...robot.cameras]
      const mainCamera = cameras[0]
      cameras[0] = cameras[cameraIndex]
      cameras[cameraIndex] = mainCamera
      this.$set(this.robotList, robotIndex, { ...robot, cameras })
      this.$set(this.cameraOrderByRobot, robotId, cameras.map(camera => camera.key))
      await this.updateInfo()
      this.rebindCameraTracks([cameras[0], cameras[cameraIndex]])
    },
  },
  watch: {
    cameras: {
      async handler() {
        await this.syncRobotList()
      },
      deep: false,
      immediate: true
    },
    dialogVisible: {
      async handler(newVal) {
        if (!newVal) {
          await this.stopAll()
          this.started = false
        }
      },
      immediate: true
    }
  }
}
</script>

<style scoped lang="scss">
.top .status {
  color: #FFF;
  border-radius: 4px;
  background: #225CA4;
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 16px;
  .svg-icon {
    color: #FFF;
    font-size: 12px;
  }
}
.box {
  width: auto !important;
  max-width: 1300px;
}
.robot-list {
  position: relative;
  .item {
    border-radius: 4px;
    border: 1px solid #005FCF;
    background: rgba(4, 24, 65, 0.20);
    .title {
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 10px;
      line-height: 13px;
    }
    .status {
      position: relative;
      color: #27ED00;
      font-family: "Microsoft YaHei";
      font-size: 10px;
      line-height: 13px;
      &::before {
        content: '';
        position: absolute;
        top: 4.5px;
        left: 0;
        width: 4px;
        height: 4px;
        border-radius: 50%;
        background: #27ED00;
      }
    }
    .main {
      background: #6E6E6E;
    }
    .side-list {
      border-radius: 4px;
      border: 1px solid #0053B5;
    }
  }
  .page {
    position: absolute;
    top: 10px;
    width: 118;
    height: calc(100% - 20px);
    border-radius: 4px;
    background: #002859;
    border: 1px solid rgba(0, 95, 207, 0.50);
    filter: drop-shadow(-12px 0 10px #192238);
    cursor: pointer;
    opacity: 0;
    transition: all 0.3s ease-in-out;
    .svg-icon {
      color: #005FCF;
      font-size: 16px;
    }
    &.page-pre {
      left: 10px;
      border-top-right-radius: 0px;
      border-bottom-right-radius: 0px;
    }
    &.page-next {
      right: 10px;
      border-top-left-radius: 0px;
      border-bottom-left-radius: 0px;
    }
  }
  &.show-page:hover {
    .page {
      opacity: 1;
    }
  }
}
.setting {
  padding: 4px 6px;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 10px;
  line-height: 13px;
  border-radius: 2px;
  background: rgba(38, 84, 152, 0.50);
}
</style>
