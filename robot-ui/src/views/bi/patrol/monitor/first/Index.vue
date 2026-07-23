<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-08 15:32:24
 * @Description: TaskListTree
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\first\Index.vue
 * @Version: 
-->
<template>
  <div class="flex h100 pr20 pl20 pb10 pt20">
    <div class="right flex1 flex-column h100" style="min-width: 0; max-width: 100%; overflow: hidden;">
      <TaskListTree ref="taskListRef" @select-task="selectTask" :update-video-handler="updateVideo" />
      <div class="mt20 flex1 flex-column" style="min-width: 0; max-width: 100%; overflow: hidden;">
        <div class="card-title">
          <div class="text">
            实时地图{{ globalMapId }}
          </div>
        </div>
        <div class="flex1 mt10 h100 slam-map-wrap">
          <GlobalGisMap v-if="globalMapId === 'gis'" />
          <GlobalSlamMap v-else :map="slamMapPayload" :show-labels="true" />
        </div>
      </div>
    </div>
    <div class="left h100 no-w-scroll ml30">
      <!-- 1364px -->
      <LeftVideo :prefixId="prefixId" ref="leftVideoRef" style="width: 1432px;" />
      <!-- <el-radio-group @change="onSingleDeviceChange" v-if="splitType === 1" v-model="singleId" class="custom-radio-group flex with-border vertical ml20">
        <el-radio v-for="dev in deviceList" :key="dev.id" :label="dev.id" class="flx-align-center">{{ dev.desc }}</el-radio>
      </el-radio-group>
      <el-checkbox-group @change="onMultiDeviceChange" v-else v-model="checkedIds" class="custom-check-group flex vertical ml20">
        <el-checkbox v-for="dev in deviceList" :key="dev.id" :label="dev.id" class="flx-align-center">{{ dev.desc }}</el-checkbox>
      </el-checkbox-group> -->
      <div class="mt9" style="width: 1432px">
        <div class="card-title flx-justify-between title-1364-30 mb9 w100 hp30 pl30" style="line-height: 30px;">
          <div class="text">
            多媒体记录
          </div>
          <div class="custom-tab-button1 flex mr10 mb5">
            <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value">{{ item.label }}</div>
          </div>
        </div>
        <Snapshot :tabIndex="tabIndex" />
      </div>
    </div>
  </div>
</template>

<script>
import LeftVideo from './LeftVideo.vue'
import Snapshot from '../../../components/Snapshot.vue'
import TaskListTree from './TaskListTree.vue';
import { mapActions, mapState } from 'vuex'
import GlobalGisMap from '../../../gis/globalMap/GlobalGisMap.vue';
import GlobalSlamMap from '../../../gis/globalMap/slam/GlobalSlamMap.vue';
export default {
  name: 'BiPatrolMonitor',
  components: { TaskListTree, LeftVideo, Snapshot, GlobalGisMap, GlobalSlamMap },
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    }
  },
  data() {
    return {
      tabList: [
        {
          label: '图片',
          value: 0
        },
        {
          label: '视频',
          value: 1
        }
      ],
      tabIndex: 0
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['globalMapId', 'slamMapList', 'slamOfRobot', 'robotBaseInfo', 'taskPathPoints', 'taskData']),
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
    robots() {
      return this.$store.getters['websocketRobot/getRobots'] || []
    },
    // 当前界面播放中的装备 id（按激活摄像头顺序去重）
    checkedRobotIds() {
      return [...new Set(Object.values(this.activeCameras || {}).map(item => item.robot?.robotId).filter(Boolean))]
    },
    // 播放中的第一个装备
    firstSelectedRobotId() {
      return this.checkedRobotIds[0] || null
    },
    // 第一个在线装备
    firstOnlineRobotId() {
      for (const item of this.robots) {
        const robotId = item?.robotId
        if (!robotId) continue
        if (this.robotBaseInfo?.[robotId]?.status === 'online') return robotId
      }
      return null
    },
    // 优先播放中的第一个装备，无播放设备时回退到第一个在线装备
    targetRobotId() {
      return this.firstSelectedRobotId || this.firstOnlineRobotId
    },
    // 目标装备对应的 SLAM 地图 id
    currentSlamMapId() {
      if (!this.targetRobotId) return null
      return this.resolveRobotSlamMapId(this.targetRobotId)
    },
    currentSlamMap() {
      const id = this.currentSlamMapId
      if (id === undefined || id === null || id === '') return null
      const group = this.slamOfRobot?.[String(id)]
      return group?.mapInfo || this.slamMapList.find(item => String(item.id) === String(id)) || null
    },
    slamMapPayload() {
      if (!this.currentSlamMap) return null
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      const points = group?.points?.length ? group.points : (this.currentSlamMap.points || [])
      return { ...this.currentSlamMap, points }
    }
  },
  async mounted() {
    this.setSelectedRobotId('')
    // for (const [index, key] of Object.keys(this.activeCameras).entries()) {
    //   // console.log('++++++++++++++++++++++++++++++');
    //   const res = await this.stopCamera(this.activeCameras[key].camera);
    //   // console.log('++++++++++++++++++++++++');
    // }
  },
  methods: {
    ...mapActions('dragVideo', ['setSplitType']),
    ...mapActions('websocketRobot', ['stopCamera', 'setSelectedRobotId']),
    // 解析装备关联的 SLAM 地图：直接 mapId > 任务 mapId > slamOfRobot 归属
    resolveRobotSlamMapId(robotId) {
      if (robotId === undefined || robotId === null || robotId === '') return null
      const robot = this.robotBaseInfo?.[robotId] || {}
      const directMapId = robot.mapId ?? robot.location?.mapId
      if (directMapId !== undefined && directMapId !== null && directMapId !== '') return directMapId

      const taskId = robot.runningTaskId
      if (taskId !== undefined && taskId !== null && taskId !== '') {
        const taskMapId = this.taskPathPoints?.[taskId]?.mapId ?? this.taskData?.[taskId]?.mapId
        if (taskMapId !== undefined && taskMapId !== null && taskMapId !== '') return taskMapId
      }

      const targetId = String(robotId)
      for (const [mapId, group] of Object.entries(this.slamOfRobot || {})) {
        if (group?.robots?.some(item => String(item.robotId) === targetId)) {
          return mapId
        }
      }
      return null
    },
    selectTask(selectRows) {
      this.$refs.leftVideoRef.slotDevices = this.$refs.leftVideoRef.slotDevices.map((item, index) => selectRows[index] || null)
      
    },
    async updateVideo(data) {
      // this.$nextTick(async () => {
      //   console.log('updateVideo');
        
      //   await this.$refs?.leftVideoRef?.test({ data })
      // });

      await new Promise(resolve => this.$nextTick(resolve))
      console.log('======updateVideo===')

      const leftVideoRef = this.$refs?.leftVideoRef
      if (!leftVideoRef || typeof leftVideoRef.test !== 'function') return
      await leftVideoRef.test({ data })
      
    },
    // async updateVideo(data) {
    //   if (data.key) {
    //     await this.$refs.leftVideoRef.test({ data })
    //   } else {
    //     console.log('更新视频', data);
    //     // for (const item of data) {
    //       await this.$refs.leftVideoRef.test({ data })
    //     // }
    //   }
    // },
  }
}
</script>

<style lang="scss" scoped>
.slam-map-wrap {
  min-width: 0;
  max-width: 100%;
  min-height: 403px;
  overflow: hidden;
  background: #1c121c;
}
.custom-tab-button1 {
  width: fit-content;
  border: 1px solid #334465;
  .tab-button-item {
    padding: 2px 20px;
    color: #ADBDD1;
    text-align: center;
    font-family: "Alibaba PuHuiTi";
    font-size: 14px;
    line-height: 19px;
    letter-spacing: 0.857px;
    background: transparent;
    border-radius: 0 !important;
    cursor: pointer;
    & + .tab-button-item {
      border-left: 1px solid #334465;
    }
    &.is-active {
      border: 1px solid #2E85C4;
      background: #003264;
      color: #4AB8FF;
    }
  }
}
</style>
