<template>
  <div class="custom-warning-list-div-new wp348 pr4 hp190 flex1 common-scroll" style="overflow: hidden auto;">
    <div class="list wp340">
      <el-popover
        v-for="(item, index) in listData"
        :key="item.name + index"
        placement="left"
        trigger="hover"
        popper-class="custom-popover warning-layer-popover">
        <template slot="reference">
          <div
            class="item flx-align-center"
            :class="{'empty': item.status === 1, 'mt10': index !== 0}">
            <div v-if="item.status === 1" class="empty-img flx-center">
              <svg-icon icon-class="unlink1" />
              <span class="text">离线</span>
            </div>
            <template v-else>
              <div class="img">
                <img src="@/assets/images/new-bi/video-bg.png" alt="" class="w100 h100">
                <div class="status" :class="{'red': item.type === 0, 'orange': item.type === 1, 'green': item.type === 2}">
                  {{ item.type === 0 ? '高风险' : item.type === 1 ? '中风险' : '低风险' }}
                </div>
              </div>
            </template>
            <div class="info ml12">
              <div class="name">{{ item.info }}</div>
              <div class="desc mt9">告警类型：{{ item.type === 0 ? '业务告警' : item.type === 1 ? '任务告警' : '设备告警' }}</div>
              <div class="time mt9">{{ item.time }}</div>
            </div>
          </div>
        </template>
        <warning-s v-if="item.status === 0 && index === 0" :details="item"></warning-s>
        <!-- 自定义dialog -->
        <!-- <Warning-s-dialog v-if="index === 1" :details="item" /> -->
        <!-- 最初版本告警处置，中间显示 -->
        <!-- <warn-info v-if="index === 2" :details="item"></warn-info> -->
        <!-- 最初版本告警处置，随popover显示 -->
        <!-- <warn-info-s v-if="index === 3" :details="item"></warn-info-s> -->
      </el-popover>
    </div>
  </div>
</template>

<script>
// 新版小框
import WarningS from './modal/warning/WarningS.vue'
// import WarningSDialog from './modal/warning/WarningSDialog.vue'
// 旧版
// import WarnInfo from './modal/warning/WarnInfo.vue'
// import WarnInfoS from './modal/warning/WarnInfoS.vue'
export default {
  name: 'WarningListCard',
  components: {
    WarningS,
    // WarningSDialog,
    // WarnInfo,
    // WarnInfoS,
  },
  data() {
    return {
      listData: [
        {
          name: '周界巡逻机器狗上装设备-视频监控',
          type: 0,
          info: '车辆识别',
          status: 0,
          time: '2026-03-05 18:03:19'
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控1',
          type: 2,
          info: '车辆识别',
          status: 0,
          time: '2026-03-05 18:03:19'
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控2',
          type: 2,
          info: '车辆识别',
          status: 1,
          time: '2026-03-05 18:03:19'
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控3',
          type: 1,
          info: '车辆识别',
          status: 0,
          time: '2026-03-05 18:03:19'
        }
      ]
    }
  },
  methods: {
  },
  mounted() {
  },
  beforeDestroy() {
  }
}
</script>