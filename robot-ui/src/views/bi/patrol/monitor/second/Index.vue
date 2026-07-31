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
            v-else
            :map="slamMapPayload"
            :show-labels="true"
            :enable-add-point="false"
            :enable-robot-click="false"
          />
        </div>
      </div>
    </div>
    <div class="ml38">
      <LeftVideo :prefixId="prefixId" ref="leftVideoRef" style="width: 1134px;" card-title-class="title-1127-37" />
      <div class="mt21">
        <div class="card-title hp30 title-1132-30 pr30">
          <div class="text" style="line-height: 30px;">
            基本信息
          </div>
        </div>
        <div class="mt21 hp178 d-flex" style="background: #011223; border: 1px solid #123F8C;">
          <div class="flex1 flx-align-center pl37 robot basic">
            <div>
              <div class="name ">{{ selectedRobot.name }}</div>
              <img v-if="selectedRobot.typeCode === 'ROBOT_DOG'" src="@/assets/images/new-bi/dog.png" alt="" srcset="" width="96" height="96">
              <img v-if="selectedRobot.typeCode === 'WHEELED_ROBOT'" src="@/assets/images/new-bi/car.png" alt="" srcset="" width="96" height="96">
            </div>
            <div class="ml54">
              <div class="desc">当前速度：{{ currenRobot.speed }}m/s</div>
              <div class="desc">当前电量：{{ currenRobot.battery }}%</div>
              <div class="desc">当前任务：{{ currenRobot?.runningTask?.name || '-' }}</div>
              <div class="desc">控制模型：{{ currenRobot?.controlMode === 'MANUAL' ? '手动控制' : currenRobot?.controlMode === 'NAVIGATION' ? '自动控制' : '-' }}</div>
            </div>
          </div>
          <div class="flex1 flx-align-center">
            <SelfRobotDogControl v-if="selectedRobot.type === '四足机器狗'" />
            <SelfRobotCarControl v-if="selectedRobot.type === '轮式机器人'" />
          </div>
        </div>
      </div>
    </div>
    <div class="ml62 flex1">
      <div class="h100 pr14">
        <div class="card-title title-344-37">
          <div class="text">
            双光云台
          </div>
        </div>
        <div class="common-scroll mt10 pr14" style="height: calc(100% - 47px); min-height: 923px; overflow-y: auto; margin-right: -14px;">
          <Yuntai />
          <div v-if="multiFunctionDevice" class="mt20">
            <div class="card-title title-344-37">
              <div class="text">
                多合一
              </div>
            </div>
            <MultiInOne />
          </div>
          <div class="mt20" v-if="audioDevice">
            <div class="card-title title-344-37">
              <div class="text">
                双向对讲机
              </div>
            </div>
            <div class="box p20 mt10 flx-center flex-column">
              <Talk />
            </div>
          </div>
          <div class="mt20" v-if="netGunDevice">
            <div class="card-title title-344-37">
              <div class="text">
                捕网器
              </div>
            </div>
            <Catcher />
          </div>
          <div class="mt20" v-if="launcherDevice">
            <div class="card-title title-344-37">
              <div class="text">
                发射器
              </div>
            </div>
            <Launcher />
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
import { mapState } from 'vuex'
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
      if (data.key) {
        await this.$refs.leftVideoRef.test({ data })
      } else {
        for (const item of data) {
          await this.$refs.leftVideoRef.test({ data: item })
        }
      }
    }
  }
}
</script>

<style scoped lang="scss">
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
