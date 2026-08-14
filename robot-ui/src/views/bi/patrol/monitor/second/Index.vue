<template>
  <div class="d-flex h100 pr20 pl10 pb10 pt20">
    <div class="left flex-column h100 wp284" style="min-width: 0; overflow: hidden;">
      <EquipmentListTree @updateVideo="updateVideo" class="flex1" style="min-height: 0;" />
      <div class="mt20">
        <div class="card-title title-284-37 flx-justify-between flx-align-center pr4">
          <div class="text">多媒体记录</div>
          <div class="flx-align-center mt2">
            <div class="custom-tab-button1 flex">
              <div
                v-for="item in mediaTabList"
                :key="item.value"
                class="tab-button-item"
                :class="{ 'is-active': mediaTabIndex === item.value }"
                @click="mediaTabIndex = item.value"
              >{{ item.label }}</div>
            </div>
            <div class="multimedia-more ml10 curp" @click="openMultimediaMore" :title="`查看更多${mediaTabIndex ? '视频' : '图片'}记录`">
              <svg-icon icon-class="right" style="font-size: 14px; color: #fff" />
            </div>
          </div>
        </div>
        <MultimediaRecord
          ref="multimediaRecordRef"
          :hide-header="true"
          :tab-index.sync="mediaTabIndex"
          @open-detail="openMultimediaDetail"
          @deleted="handleMultimediaDeleted"
        />
        <MultimediaDetail ref="multimediaDetailRef" @deleted="handleMultimediaDeleted" />
      </div>
      <div class="mt20 hp296 flex-column" style="min-width: 0; max-width: 100%; overflow: hidden;">
        <div class="card-title title-284-37">
          <div class="text">
            实时地图
          </div>
        </div>
        <div class="flex1 mt10 h100 slam-map-wrap">
          <GlobalGisMap v-if="globalMapId === 'gis'" />
          <GlobalSlamMap
            v-if="globalMapId && globalMapId !== 'gis'"
            :map="slamMapPayload"
            :show-labels="true"
            :enable-add-point="false"
            :enable-robot-click="false"
          />
        </div>
      </div>
    </div>
    <div class="ml38">
      <div class="page-tab flx-align-center mb15">
        <span class="page-tab__item is-link ml10" @click="backToMonitor">实时监控</span>
        <svg-icon icon-class="right" class="ml10" style="color: #fff; font-size: 14px;"></svg-icon>
        <span class="page-tab__item is-current ml10">深度控制</span>
      </div>
      <LeftVideo :prefixId="prefixId" ref="leftVideoRef" style="width: 1134px;" card-title-class="title-1127-37" />
      <div class="mt21">
        <div class="card-title hp30 title-1132-30 pr30">
          <div class="text" style="line-height: 30px;">
            基本信息
          </div>
        </div>
        <div class="mt10 hp162 d-flex" style="background: #011223; border: 1px solid #123F8C;">
          <div class="flex1 flx-align-center pl37 robot basic">
            <div class="flex-column flx-align-center">
              <div class="name text-ellipsis">{{ selectedRobot.name }}</div>
              <img v-if="selectedRobot.typeCode === 'ROBOT_DOG'" src="@/assets/images/new-bi/dog.png" alt="" srcset="" width="96" height="96">
              <img v-if="selectedRobot.typeCode === 'WHEELED_ROBOT'" src="@/assets/images/new-bi/car.png" alt="" srcset="" width="96" height="96">
            </div>
            <div class="ml54">
              <div class="desc">当前速度：{{ Number(currenRobot.speed || 0).toFixed(2) }}m/s</div>
              <div class="desc">当前电量：{{ currenRobot.battery }}%</div>
              <div v-if="currenRobot?.runningTask?.name" class="desc">
                当前任务：{{ currenRobot?.runningTask?.name || '-' }}
                <span v-if="currenRobot?.runningTask" class="task-status ml8" :class="activeTaskStatusClass">{{ activeTaskStatusLabel }}</span>
              </div>
              <div class="desc">控制模型：{{ currenRobot?.controlMode || '-' }}</div>
            </div>
          </div>
          <div class="flex1 flx-align-center">
            <SelfRobotDogControl v-if="showDogControl" />
            <SelfRobotCarControl v-if="showCarControl" />
          </div>
        </div>
      </div>
    </div>
    <div class="ml62 flex1">
      <div class="h100 pr14">
        <div class="page-back-row flx-align-center mb15">
          <div class="page-back" title="返回" @click="backToMonitor">
            <svg-icon icon-class="back1" />
          </div>
        </div>
        <div class="card-title title-344-37">
          <div class="text">
            上装控制区域
          </div>
        </div>
        <div class="common-scroll mt10 pr14" style="height: calc(100% - 94px); min-height: 876px; overflow-y: auto; margin-right: -14px;">
          <div class="box p20 mt10">
            <div class="card-title-t2">
              <div class="text pb12">
                双光云台
              </div>
            </div>
            <Yuntai class="mt20 pt10 pb10" />
          </div>
          <div v-if="multiFunctionDevice" class="mt20">
            <div class="box p20">
              <div class="card-title-t2">
                <div class="text pb12">
                  多合一
                </div>
              </div>
              <MultiInOne class="mt20" />
            </div>
          </div>
          <div class="mt20" v-if="audioDevice">
            <div class="box p20">
              <div class="card-title-t2">
                <div class="text pb12">
                  双向对讲机
                </div>
              </div>
              <Talk class="mt20 flx-center flex-column" />
            </div>
          </div>
          <div class="mt20" v-if="netGunDevice">
            <div class="box p20">
              <div class="card-title-t2">
                <div class="text pb12">
                  捕网器
                </div>
              </div>
              <Catcher class="mt20" />
            </div>
          </div>
          <div class="mt20" v-if="launcherDevice">
            <div class="box p20">
              <div class="card-title-t2">
                <div class="text pb12">
                  发射器
                </div>
              </div>
              <Launcher class="mt20" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import EquipmentListTree from './EquipmentListTree.vue'
import LeftVideo from './../first/LeftVideo.vue'
import SelfRobotDogControl from './components/SelfRobotDogControl.vue'
import SelfRobotCarControl from './components/SelfRobotCarControl.vue'
import Talk from './components/Talk.vue'
import Yuntai from './components/Yuntai.vue'
import MultiInOne from './components/MultiInOne.vue'
import Catcher from './components/Catcher.vue'
import Launcher from './components/Launcher.vue'
import MultimediaRecord from './components/MultimediaRecord.vue'
import MultimediaDetail from './components/MultimediaDetail.vue'
import GlobalGisMap from '../../../gis/globalMap/GlobalGisMap.vue'
import GlobalSlamMap from '../../../gis/globalMap/slam/GlobalSlamMap.vue'
import yuntai from './components/yuntai'
import { mapActions, mapState } from 'vuex'
import { isRobotDog } from '@/constants/robot.js'
export default {
  name: 'BiPatrolMonitorSecondScreen',
  components: {
    EquipmentListTree,
    LeftVideo,
    SelfRobotDogControl,
    SelfRobotCarControl,
    Yuntai,
    MultiInOne,
    Talk,
    Catcher,
    Launcher,
    MultimediaRecord,
    MultimediaDetail,
    GlobalGisMap,
    GlobalSlamMap
  },
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    ...mapState('websocketExtraData', [
      'robotBaseInfo',
      'globalMapId',
      'slamMapList',
      'slamOfRobot',
      'taskPathPoints',
      'taskData'
    ]),
    currenRobot() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    },
    showDogControl() {
      if (this.baseDevice?.deviceType === 'QUADRUPED_BASE') return true
      return isRobotDog(this.selectedRobot) || isRobotDog(this.currenRobot)
    },
    showCarControl() {
      if (this.baseDevice?.deviceType === 'WHEELED_BASE') return true
      const type = this.selectedRobot.typeCode || this.selectedRobot.type
      return ['WHEELED_ROBOT', '轮式机器人'].includes(type)
    },
    // second 界面以当前选中装备确定 SLAM 地图
    targetRobotId() {
      return this.selectedRobotId || null
    },
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
      if (!this.currentSlamMap) {
        this._slamMapPayloadCache = null
        return null
      }
      const group = this.slamOfRobot?.[String(this.currentSlamMapId)]
      const points = group?.points?.length ? group.points : (this.currentSlamMap.points || [])
      const cache = this._slamMapPayloadCache
      // robotBaseInfo 心跳会使本 computed 重算；map/points 未变时复用对象，避免 GlobalSlamMap 误判换图
      if (cache && cache.mapRef === this.currentSlamMap && cache.points === points) {
        return cache.payload
      }
      const payload = { ...this.currentSlamMap, points }
      this._slamMapPayloadCache = { mapRef: this.currentSlamMap, points, payload }
      return payload
    }
  },
  data() {
    return {
      mediaTabList: [
        { label: '图片', value: 0 },
        { label: '视频', value: 1 }
      ],
      mediaTabIndex: 0
    }
  },
  watch: {
    warningLightQueryKey: {
      immediate: true,
      handler(key) {
        if (!key) {
          this.lastWarningLightQueryKey = ''
          return
        }
        if (key === this.lastWarningLightQueryKey) return
        this.lastWarningLightQueryKey = key
        this.queryWarningLightState()
      }
    }
  },
  mixins: [yuntai],
  methods: {
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    // 与 Header.back 一致：清空选中装备，回到一级实时监控
    backToMonitor() {
      this.setSelectedRobotId('')
    },
    openMultimediaMore() {
      const recordRef = this.$refs.multimediaRecordRef
      this.$refs.multimediaDetailRef?.open({
        tabIndex: this.mediaTabIndex,
        list: recordRef?.displayList || []
      })
    },
    openMultimediaDetail(payload = {}) {
      this.$refs.multimediaDetailRef?.open(payload)
    },
    handleMultimediaDeleted() {
      this.$refs.multimediaRecordRef?.refreshList?.()
    },
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
    async updateVideo(data) {
      await this.$nextTick()
      const leftVideoRef = this.$refs.leftVideoRef
      if (!leftVideoRef || typeof leftVideoRef.test !== 'function') return

      // 进入控制中心默认加载全部视频源：按空槽顺序依次播放，避免并发全部落到第一个窗口
      const cameraList = Array.isArray(data) ? data : (data ? [data] : [])
      for (const item of cameraList) {
        if (!item) continue
        const emptyKey = leftVideoRef.findEmptySlotKey ? leftVideoRef.findEmptySlotKey() : null
        await leftVideoRef.test({ data: item, index: emptyKey })
      }
    }
  }
}
</script>

<style scoped lang="scss">
.page-tab {
  font-family: "Microsoft YaHei";
  font-size: 16px;
  line-height: 21px;
  .page-tab__item {
    white-space: nowrap;
    &.is-link {
      color: #fff;
      cursor: pointer;
      &:hover {
        color: #0BF9FE;
      }
    }
    &.is-current {
      color: #0BF9FE;
    }
  }
}
.page-back-row {
  justify-content: flex-end;
  min-height: 32px;
}
.page-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 32px;
  border-radius: 100px;
  border: 1px solid #5AA0FF;
  background: linear-gradient(180deg, #011C39 0%, #0073C1 100%);
  cursor: pointer;
  .svg-icon {
    font-size: 20px;
    color: #AED1FF;
  }
  &:hover {
    filter: brightness(1.08);
  }
}
.custom-tab-button1 {
  width: fit-content;
  border: 1px solid #334465;
  .tab-button-item {
    padding: 2px 10px;
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
.multimedia-more {
  color: #3BA5E7;
  font-family: "Alibaba PuHuiTi";
  font-size: 14px;
  line-height: 19px;
  letter-spacing: 0.857px;
  white-space: nowrap;
  &:hover {
    color: #4AB8FF;
  }
}
.slam-map-wrap {
  min-width: 0;
  max-width: 100%;
  min-height: 250px;
  overflow: hidden;
  background: #1c121c;
}
.basic {
  .name {
    max-width: 160px;
    font-family: YouSheBiaoTiHei;
    font-size: 20px;
    line-height: 26px;
    letter-spacing: 0.4px;
    background: linear-gradient(180deg, #FFF 7.28%, #95CFF7 62.98%);
    background-clip: text;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .desc {
    color: rgba($color: #FFF, $alpha: .8);
    font-family: Inter;
    font-size: 14px;
    line-height: 17px;
    & + .desc {
      margin-top: 10px;
    }
  }
  .task-status {
    font-size: 12px;
    &.green { color: #25FF6E; }
    &.orange { color: #FF7734; }
    &.blue { color: #159AFF; }
    &.red { color: #FF0404; }
    &.gray { color: #8897AB; }
  }
}
.snapshot {
  position: relative;
  &::before {
    position: absolute;
    top: 14px;
    left: 0;
    width: 1px;
    height: calc(100% - 28px);
    background: #123F8C;
    content: '';
  }
}
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.30) inset;
}
</style>
