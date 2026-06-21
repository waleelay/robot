<template>
  <div class="left-div ml20 pr20 h100" :style="{ 'pointer-events': selectedRobotId ? 'none' : 'auto' }">
    <div class="container flex-column w100 h100 flx-center" style="flex-wrap: nowrap;">
      <div class="box hp386">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">告警概览</span> 
          <span class="flx-center more curp" @click="getMoreRobotInfo">
            <span>更多</span>
            <svg-icon icon-class="right" class="ml4" />
          </span>
        </div>
        <div class="pt20 pr18 pb20 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 hp70 flx-center flex-column red">
              <div class="desc">今日告警</div>
              <div class="value mt4">{{ deviceStats?.total || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 green">
              <div class="desc">已处理</div>
              <div class="value mt4">{{ deviceStats?.online || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 gray">
              <div class="desc">未处理</div>
              <div class="value mt4">{{ deviceStats?.fault || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 ">
              <div class="desc">处置率</div>
              <div class="value mt4">{{ deviceStats?.offline || 0  }}%</div>
            </div>
          </div>
          <div class="mt20">
            <div class="t2">告警分布</div>
            <div class="mt20 hp150">
              <PieChart :items="[
                { name: '高风险', value: 2, color: '#FF2424' },
                { name: '中风险', value: 2, color: '#FFA024' },
                { name: '低风险', value: 50, color: '#24CBFF' },
              ]" />
            </div>
          </div>
        </div>
      </div>
      <div class="box mt20 task pb20" :class="{ 'no_data hp41': collapseArr[1] }">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">巡逻概览</span>
        </div>
        <div class="xlgl mt20 d-flex pr20 pl20">
          <div class="item hp62 flex1 flx-center flex-column">
            <div class="desc">今日巡逻时长</div>
            <div class="value mt4"><span class="mr4">100</span>小时</div>
          </div>
          <div class="item hp62 flex1 flx-center flex-column ml10">
            <div class="desc">今日巡逻里程</div>
            <div class="value mt4"><span class="mr4">36.2</span>KM</div>
          </div>
        </div>
        <div class="mt20">
          <div class="t2 ml10">巡逻画面</div>
          <div class="flex flex-wrap pl10">
            <div class="wp142 hp80 mt20 ml10" style="border: 1px solid #eef;"></div>
            <div class="wp142 hp80 mt20 ml10" style="border: 1px solid #eef;"></div>
            <div class="wp142 hp80 mt20 ml10" style="border: 1px solid #eef;"></div>
            <div class="wp142 hp80 mt20 ml10" style="border: 1px solid #eef;"></div>
          </div>
        </div>
      </div>
    </div>
    <div class="collapse-left flx-center" @click="$emit('changeCollapse')">
      <div class="flx-center">
        <svg-icon :icon-class="collapse ? 'right-s' : 'left-s'" />
      </div>
    </div>
    <!-- <TaskRobotView ref="taskRobotViewRef" @handleClickTask="handleClickTask" /> -->
    <!-- <WarningBatch ref="warningBatchRef" /> -->
  </div>
</template>

<script>
import { mapState } from 'vuex';
// import TaskRobotView from '../components/modal/TaskRobotView.vue';
// import WarningBatch from './WarningBatch.vue'
import { mapActions } from 'vuex/dist/vuex.common.js';
import PieChart from './PieChart.vue';
export default {
  name: 'BiIndexLeft',
  components: { PieChart },
  props: {
    collapse: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    ...mapState('websocketExtraData', ['taskData', 'alarmsData', 'deviceTypeStats', 'deviceStats']),
  },
  data() {
    return {
      tabList: [
        {
          label: '今日',
          value: 0
        },
        {
          label: '本月',
          value: 1
        },
        {
          label: '当年',
          value: 2
        }
      ],
      tabIndex: 0,
      collapseArr: [false, false, true],
      alertCollapseArr: [true, true, true],
      alertList: [1],
      activeTaskId: null,
      overviewInfo: {},
      alarms: {
        high: {
          name: '高风险',
          class: 'danger'
        },
        medium: {
          name: '中风险',
          class: 'warning'
        },
        low: {
          name: '低风险',
          class: 'green'
        },
      }
    }
  },
  async mounted() {
    if (this.alarmsData?.high?.items?.length) {
      this.collapseArr[2] = false
    }

    
    // 更新告警信息
    // setTimeout(() => {
    //   this.setRobotAlarmInfo({ robotId: this.alarmsData?.high?.items?.[1]?.robotId, alarmInfo: this.alarmsData?.high?.items?.[1], close: true });
    // }, 5000);
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotAlarmInfo']),
    getMoreRobotInfo() {
  
    },
    toggleCollapse(type, typeIndex) {
      this.$set(this[type], typeIndex, !this[type][typeIndex])
    },
    handleClickTask(taskId) {      
      if (this.activeTaskId === taskId) {
        this.$refs.taskRobotViewRef.dialogVisible = false
        // 清空录像
        this.activeTaskId = null
        return
      }
      this.activeTaskId = taskId
      this.$refs.taskRobotViewRef.showModal({
        taskInfo: { ...this.taskData[taskId]},
        robotIds: this.taskData[taskId].equipmentList.map(robot => robot.robotId)
      })
    },
    handleClickAlert() {
      this.$refs.warningBatchRef.open(this.alarmsData || {})
    }
  },
  watch: {
    // robots: {
    //   handler(newVal, oldVal) {
    //     if (newVal?.length && !this.taskList[0]?.robots?.length) {
    //       this.$set(this.taskList[0], 'robots', newVal)
    //     }
    //   },
    //   immediate: true
    // },
  },
}
</script>

<style scoped>
.left-div {
  backdrop-filter: unset !important;
  background: transparent !important;
  .container {
    overflow-y: auto;
    &::-webkit-scrollbar {
      width: 2px;               /* 垂直滚动条宽度 */
      height: 2px;              /* 水平滚动条高度 */
    }
    &::-webkit-scrollbar-thumb {
      border: 1px solid #42536F; /* 创建内边距效果,会覆盖背景值 */
    }
    .box {
      width: 334px;
      border-radius: 6px;
      border: 2px solid rgba(0, 0, 0, 0.00);
      background: rgba(0, 19, 48, 0.60);
      box-shadow: 0 0 20px 0 rgba(1, 80, 170, 0.80) inset;
      &:not(.no_data) {
        position: relative;
        /* border: 2px solid transparent; */
        border: none;
        background: rgba(0, 19, 48, 0.60);
        box-shadow: 0 0 20px 0 rgba(1, 80, 170, 0.80) inset, 0 12px 28px -8px rgba(0, 0, 0, 0.5);
        isolation: isolate;
        transition: all 0.25s ease;
        backdrop-filter: blur(0px);
        z-index: 1;
        &::before, &::after {
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          content: '';
          pointer-events: none;
          z-index: 2;
        }
        &::before {
          /* 1. 左上角 水平线 (从左向右 #2497FC → 透明) */
          /* 2. 左上角 垂直线 (从上向下 #2497FC → 透明) */
          /* 3. 右下角 水平线 (从右向左 #2497FC → 透明，因右下角从右下端点向左淡化) */
          /* 4. 右下角 垂直线 (从下向上 #2497FC → 透明) */
          background: linear-gradient(90deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) left top no-repeat,
            linear-gradient(180deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) left top no-repeat,
            linear-gradient(270deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) right bottom no-repeat,
            linear-gradient(0deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) right bottom no-repeat;
          background-size: 80px 2.5px, 2.5px 80px, 80px 2.5px, 2.5px 80px;
          background-repeat: no-repeat;
          border-radius: 6px;
        }
        &::after {
          /* 1. 右上角 水平线 (从右向左 #2497FC → 透明) */
          /* 3. 左下角 水平线 (从左向右 #2497FC → 透明) */
          /* 2. 右上角 垂直线 (从上向下 #2497FC → 透明) */
          /* 4. 左下角 垂直线 (从下向上 #2497FC → 透明) */
          background: linear-gradient(270deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) right top no-repeat,
            linear-gradient(180deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) right top no-repeat,
            linear-gradient(90deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) left bottom no-repeat,
            linear-gradient(0deg, #2497FC 0%, rgba(36, 151, 252, 0) 100%) left bottom no-repeat;
          background-size: 80px 2.5px, 2.5px 80px, 80px 2.5px, 2.5px 80px;
          background-repeat: no-repeat;
          border-radius: 6px;
        }
      }
      &.no_data {
        overflow: hidden;
        min-height: 41px;
        border: 1px solid #2497FC;
        background: linear-gradient(90deg, #003D7C 9.09%, rgba(0, 23, 47, 0.00) 100%);
        .title {
          color: #D5EDFF;
          text-shadow: none;
        }
      }
      .title {
        border-radius: 4px 6px 0 0;
        /* opacity: 0.3;  */
        background: linear-gradient(90deg, rgba(0, 84, 171, 0.60) 9.09%, rgba(0, 60, 106, 0.00) 69.39%);
        .desc {
          color: #EFF8FF;
          text-shadow: 0 2px 30px #0279B8;
          font-family: YouSheBiaoTiHei;
          font-size: 18px;
          background-image: url("../../../assets/images/new-bi/title-bg.png");
        }
        .more {
          color: #3BA5E7;
          font-family: "Microsoft YaHei";
          font-size: 14px;
          line-height: 18px;
        }
      }
      .notice {
        border-radius: 4px;
        background: #BF000C;
        color: #FFF;
        font-family: "Microsoft YaHei";
        font-size: 12px;
        line-height: 20px;
      }
      .count {
        .item {
          border-radius: 4px;
          border: 1px solid #0B5CA8;
          background: linear-gradient(163deg, rgba(4, 89, 163, 0.50) 15.18%, rgba(0, 56, 114, 0.50) 64.72%);
          .desc, .value, .svg-icon {
            color: #00E1FF;
            font-family: "Microsoft YaHei";
          }
          .desc {
            font-size: 12px;
            font-weight: 600;
            line-height: 16px; /* 133.333% */
          }
          .value {
            font-size: 18px;
            font-weight: 400;
            line-height: 22px;
          }
          .svg-icon {
            font-size: 16px;
            height: 16px;
            line-height: 16px;
          }
          &.green {
            border: 1px solid #096A2B;
            background: #031F27;
            .desc, .value, .svg-icon {
              color: #00FF50;
            }
          }
          &.orange {
            border: 1px solid #752700;
            background: #1B191F;
            .desc, .value, .svg-icon {
              color: #FF6E00;
            }
          }
          &.gray {
            border: 1px solid rgba(255, 255, 255, 0.20);
            background: #13223A;
            .desc, .value, .svg-icon {
              color: #fff;
            }
          }
          &.red {
            border: 1px solid #F00;
            background: linear-gradient(163deg, rgba(127, 0, 0, 0.50) 15.18%, rgba(85, 0, 0, 0.50) 64.72%);
            .desc, .value, .svg-icon {
              color: #f00;
            }
          }
        }
      }
      .t2 {
        color: #D5EDFF;
        font-family: YouSheBiaoTiHei;
        font-size: 18px;
        line-height: 23px;
      }
      .xlgl {
        .item {
          border-radius: 4px;
          background: #012851;
          .desc {
            color: #BEE1FF;
            font-family: "Microsoft YaHei";
            font-size: 12px;
            line-height: 16px; /* 133.333% */
          }
          .value {
            color: #BEE1FF;
            font-family: "Microsoft YaHei";
            font-size: 12px;
            line-height: 16px; /* 133.333% */
            span {
              color: #FFF;
              font-family: Bahnschrift;
              font-size: 18px;
              font-style: normal;
              font-weight: 400;
              line-height: 22px;
            }
          }
        }
      }
      .device_types {
        .item {
          border-radius: 4px;
          /* border: 1px solid #041B3E;
          background: rgba(0, 49, 98, 0.50); */
          background: #012851;
          .desc {
            color: #BEE1FF;
            font-family: "Microsoft YaHei";
            font-size: 12px;
            line-height: 16px; /* 133.333% */
          }
          .value {
            color: #FFF;
            font-family: Bahnschrift;
            font-size: 18px;
          }
        }
      }
      &.alert {
        .type {
          border-radius: 6px;
          border: 1px solid;
          &.collapse {
            /* overflow: auto; */
            overflow-y: hidden;
            background: #001331;
          }
          .type_name {
            font-family: "Microsoft YaHei";
            font-size: 14px;
            font-weight: 600;
            line-height: 20px;
            .symbol {
              width: 20px;
              height: 20px;
              text-align: center;
              line-height: 20px;
              border-radius: 50%;
              .svg-icon {
                color: #fff !important;
                font-size: 11.6px;
              }
            }
          }
          .list {
            .item {
              position: relative;
              color: #FFF;
              font-family: "Alibaba PuHuiTi";
              font-size: 12px;
              letter-spacing: 0.802px;
              line-height: 16px;
              .event {
                font-weight: 600;
                font-family: "Microsoft YaHei";
              }
              .img {
                /* background: #ccc; */
                background: url(<path-to-image>) lightgray -4.267px -11.862px / 104% 118.678% no-repeat;
                .alert_type {
                  padding: 4px 6px;
                  color: #FFF;
                  font-size: 12px;
                  line-height: 16px; /* 133.333% */
                  border-radius: 0 0 9px 0;
                }
              }
              & + .item {
                &::before {
                  position: absolute;
                  top: -20px;
                  left: 0;
                  width: 248px;
                  height: 1px;
                  content: '';
                }
              }
            }
          }
          &.danger {
            border-color: #AC1515;
            box-shadow: 0 0 30px 0 rgba(255, 0, 0, 0.30) inset;
            .type_name {
              color: #FF0600;
              .symbol {
                background: linear-gradient(168deg, #FC8D8C 16.86%, #EA2532 63.24%);
              }
              .svg-icon {
                color: #FF0004;
              }
            }
            .list .item {
              & + .item::before {
                background: #5B0000;
              }
              .alert_type {
                background: #CE0101;
              }
              .event {
                color: #FF0600;
              }
            }
          }
          &.warning {
            border-color: #8A4600;
            box-shadow: 0 0 20px 0 rgba(255, 130, 0, 0.30) inset;
            .type_name {
              color: #FF8200;
              .symbol {
                background: linear-gradient(168deg, #FF9240 16.86%, #DE6300 63.24%);
              }
              .svg-icon {
                color: FF8200;
              }
            }
            .list .item {
              & + .item::before {
                background: #4D2200;
              }
              .alert_type {
                background: #D05C00 ;
              }
              .event {
                color: #FF7100;
              }
            }
          }
          &.green {
            border-color: #006810;
            box-shadow: 0 0 30px 0 rgba(0, 255, 38, 0.30) inset;
            .type_name {
              color: #00C91E;
              .symbol {
                background: linear-gradient(168deg, #11CD60 16.86%, #047447 63.24%);
              }
              .svg-icon {
                color: #00FF26;
              }
            }
            .list .item {
              & + .item::before {
                background: #00410A;
              }
              .alert_type {
                background: #009D18;
              }
              .event {
                color: #00C91E;
              }
            }
          }
        }
      }
      &.task {
        &:not(.no_data) {
          background: #021328;
        }
        .item {
          position: relative;
          border-radius: 6px;
          border: 1px solid #11203F;
          background: #11203F;
          &.is-active {
            border-color: #1D7EEC;
            background: #071939;
            box-shadow: 0 0 30px 0 rgba(0, 86, 207, 0.30) inset;
            .header {
              border-radius: 5px 5px 0 0;
              background: #0A224D;
            }
          }
          .symbol {
            position: absolute;
            bottom: 10px;
            right: 10px;
          }
          .header {
            color: #FFF;
            font-family: "Microsoft YaHei";
            font-size: 14px;
            font-weight: 600;
            line-height: 18px;
            .svg-icon {
              color: #63D9EF;
              font-size: 18px;
            }
            .status {
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
          }
          .desc {
            padding-left: 20px;
            color: #FFF;
            font-family: "Alibaba PuHuiTi";
            font-size: 12px;
            line-height: 16px;
            letter-spacing: 0.802px;
          }
        }
      }
    }
  }
}
</style>
