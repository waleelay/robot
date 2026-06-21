<template>
  <div class="d-flex h100 pr20 pl10 pb10 pt20">
    <div class="left flex-column h100 wp284">
      <EquipmentListTree @updateVideo="updateVideo" />
      <div class="mt20 flex1 flex-column">
        <div class="card-title title-284-37">
          <div class="text">
            实时地图
          </div>
        </div>
        <div class="flex1 mt10 h100" style="min-height: 250px;">
          <SmallMap />
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
          <div class="flex1 flx-align-center pl37 robot">
            <img v-if="selectedRobot.type === '四足机器狗'"src="@/assets/images/new-bi/dog.png" alt="" srcset="" width="161" height="104">
            <img v-else src="@/assets/images/new-bi/car.png" alt="" srcset="" width="143" height="143">
            <div class="basic ml20">
              <div class="desc">当前速度：1.5m/s</div>
              <div class="desc">当前电量：98%</div>
              <div class="desc">当前任务：周期性训练</div>
              <div class="desc">控制模型：自动控制</div>
            </div>
          </div>
          <div class="flex1 flx-align-center">
            <SelfRobotDogControl v-if="selectedRobot.type === '四足机器狗'" />
            <SelfRobotCarControl v-if="selectedRobot.type === '轮式机器人'" />
          </div>
          <!-- <div class="flex1 flx-align-center pl34 snapshot">
            <img src="@/assets/images/new-bi/video-bg.png" alt="" srcset="" width="264" height="148">
            <div class="basic ml20">
              <div class="desc">告警类型：设备告警</div>
              <div class="desc">告警信息：设备故障</div>
              <div class="desc">告警地点：A区机房</div>
              <div class="desc">抓拍时间：2026.03.26 12:00:00</div>
            </div>
          </div> -->
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
          <div class="mt20" v-if="launcherDevice">
            <div class="card-title title-344-37">
              <div class="text">
                发射器
              </div>
            </div>
            <Launcher />
          </div>
          <div class="mt20" v-if="netGunDevice">
            <div class="card-title title-344-37">
              <div class="text">
                捕网器
              </div>
            </div>
            <Catcher />
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
import Catcher from './components/Catcher.vue'
import Launcher from './components/Launcher.vue'
import { motionControl } from '@/api/login'
import SmallMap from '../../../gis/globalMap/SmallMap..vue'
import yuntai from './components/yuntai'
export default {
  name: 'BiPatrolMonitorSecondScreen',
  components: {EquipmentListTree, LeftVideo, SelfRobotDogControl, SelfRobotCarControl, Yuntai, Talk, Catcher, Launcher, SmallMap},
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    }
  },
  computed: {
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
  },
  data() {
    return {
    }
  },
  mixins: [yuntai],
  methods: {
    async updateVideo(data) {
      if (data.key) {
        await this.$refs.leftVideoRef.test({ data })
      } else {
        for (const item of data) {
          await this.$refs.leftVideoRef.test({ data: item })
        }
      }
    },
    // updateCheckId(ids) {
    //   this.checkedIds = ids
    // }
  }
}
</script>

<style scoped lang="scss">
.basic {
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
</style>
