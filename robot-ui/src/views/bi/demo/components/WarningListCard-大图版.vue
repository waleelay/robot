<template>
  <div class="custom-warning-list-div wp348 pr4 hp190 flex1 common-scroll" style="overflow: hidden auto;">
    <div class="list wp340">
      <el-popover
        v-for="(item, index) in listData"
        :key="item.name + index"
        placement="left"
        trigger="hover"
        popper-class="custom-popover warning-layer-popover">
        <template slot="reference">
          <div
            class="item"
            :class="{'empty flx-center flex-column': item.status === 1, 'mt10': index !== 0}">
            <template v-if="item.status === 1">
              <svg-icon icon-class="unlink1" />
              <span class="text">离线</span>
            </template>
            <template v-else>
              <div class="top flx-justify-between w100 pr10 pl10">
                <div class="title">数据源：{{ item.name }}</div>
                <div class="flx-center">
                  <div class="status" :class="{'success': item.type === 0, 'primary': item.type === 1, 'primary-light': item.type === 2}">
                    {{ item.type === 0 ? '业务告警' : item.type === 1 ? '任务告警' : '设备告警' }}
                  </div>
                  <!-- <div class="status error ml10">{{ item.status === 0 ? '待处理' : '已处理' }}</div> -->
                </div>
              </div>
              <div class="bottom flx-justify-between w100 pr10 pl10">
                <div class="title">告警信息：{{ item.info }}</div>
                <div class="flx-center">
                  <div class="status error">立即处置</div>
                </div>
              </div>
            </template>
          </div>
        </template>
        <warning-s v-if="item.status === 0"></warning-s>
        <!-- <Warning /> -->
      </el-popover>
    </div>
  </div>
</template>

<script>
// 新版小框
import WarningS from './modal/warning/WarningS.vue'
// 旧版
// import Warning from './modal/warning/WarnInfo-s.vue'
export default {
  name: 'WarningListCard',
  components: {
    WarningS,
    // Warning
  },
  data() {
    return {
      listData: [
        {
          name: '周界巡逻机器狗上装设备-视频监控',
          type: 0,
          info: '车辆识别',
          status: 0,
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控1',
          type: 2,
          info: '车辆识别',
          status: 0,
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控2',
          type: 2,
          info: '车辆识别',
          status: 1,
        },
        {
          name: '周界巡逻机器狗上装设备-视频监控3',
          type: 1,
          info: '车辆识别',
          status: 0,
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