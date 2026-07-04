<template>
  <div class="right-div ml20 pr20 no-w-scroll mb20" :style="{ 'pointer-events': selectedRobotId ? 'none' : 'auto', height: 'calc(100% - 20px)', overflowY: 'auto' }">
    <div class="container flex-column w100 mt105" style="flex-wrap: nowrap;">
      <div class="box zbgl">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">装备概览</span> 
          <span class="flx-center more curp" @click="getMoreRobotInfo">
            <span>更多</span>
            <svg-icon icon-class="right" class="ml4" />
          </span>
        </div>
        <div class="pt20 pr18 pb10 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 hp70 flx-center flex-column">
              <div class="desc">总数</div>
              <div class="value mt4">{{ deviceStats?.total ? String(deviceStats?.total).padStart(2, '0') : '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 green">
              <div class="desc">在线</div>
              <div class="value mt4">{{ deviceStats?.online ? String(deviceStats?.online).padStart(2, '0') : '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 orange">
              <div class="desc">故障</div>
              <div class="value mt4">{{ deviceStats?.fault ? String(deviceStats?.fault).padStart(2, '0') : '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 gray">
              <div class="desc">离线</div>
              <div class="value mt4">{{ deviceStats?.offline ? String(deviceStats?.offline).padStart(2, '0') : '-' }}</div>
            </div>
          </div>
          <div class="mt20">
            <div class="t2">装备类型</div>
            <div class="mt12"> 
              <div class="flx-center top pr10 pl10">
                <div class="tal" style="width: 40%;">装备类型</div>
                <div class="flex1 tac ml10">总数</div>
                <div class="flex1 tac ml10">故障</div>
                <div class="flex1 tac ml10">离线</div>
              </div>
              <div class="common-scroll ovya" style="min-height: 144px; max-height: 216px;">
                <template v-if="deviceTypeStats.length">
                  <div v-for="item in deviceTypeStats" class="devices flx-center pr10 pl10">
                    <div class="tal" style="width: 40%;">{{ item.name }}</div>
                    <div class="flex1 tac ml10">{{ item.count || 0 }}</div>
                    <div class="flex1 tac ml10">{{ item.fault || 0 }}</div>
                    <div class="flex1 tac ml10">{{ item.offline || 0  }}</div>
                  </div>
                  <div class="devices flx-center pr10 pl10">
                    <div class="tal" style="width: 40%;">摄像头</div>
                    <div class="flex1 tac ml10">6</div>
                    <div class="flex1 tac ml10">0</div>
                    <div class="flex1 tac ml10">0</div>
                  </div>
                </template>
                <div v-else style="color: #165e8c; font-family: 'Microsoft YaHei'; font-size: 14px; line-height: 108px; text-align: center;">暂无数据</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="box mt20 rwgl">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">任务概览</span> 
          <span class="flx-center more curp" @click="getMoreRobotInfo">
            <span>更多</span>
            <svg-icon icon-class="right" class="ml4" />
          </span>
        </div>
        <div class="pt20 pr18 pb20 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 hp70 flx-center flex-column">
              <div class="desc">今日任务</div>
              <div class="value mt4">{{ taskOverview?.totalToday || '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10">
              <div class="desc">完成率</div>
              <div class="value mt4">{{ taskOverview?.completedRateText || '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 green">
              <div class="desc">执行中</div>
              <div class="value mt4">{{ taskOverview?.running || '-' }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 orange">
              <div class="desc">待执行</div>
              <div class="value mt4">{{ taskOverview?.pending || '-' }}</div>
            </div>
          </div>
          <div class="mt20">
            <div class="t2">任务详情</div>
            <div class="mt12">
              <div class="flx-justify-between top pr10 pl10">
                <div style="width: 43%;">任务名称</div>
                <div class="ml10 mr10 wp50">状态</div>
                <div class="ml10" style="width: 35%;">执行时间</div>
              </div>
              <div class="common-scroll ovya" style="min-height: 144px; max-height: 252px;">
                <template v-if="tasks.length">
                  <div v-for="item in tasks" class="tasks flx-justify-between pr10 pl10">
                    <div style="width: 43%;" class="text-ellipsis" :title="item.name">{{ item.name }}</div>
                    <div class="ml10 mr10 status wp50" :class="item.statusName === '执行中' ? 'green' : item.status === '待执行' ? 'orange' : 'gray'">{{ item.statusName }}</div>
                    <div class="ml10" style="width: 35%;">{{ item.timeRange }}</div>
                  </div>
                </template>
                <div v-else style="color: #165e8c; font-family: 'Microsoft YaHei'; font-size: 14px; line-height: 108px; text-align: center;">暂无数据</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="collapse-right flx-center" @click="$emit('changeCollapse')">
      <div class="flx-center">
        <svg-icon :icon-class="collapse ? 'left-s' : 'right-s'" />
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
export default {
  name: 'BiIndexLeft',
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
    ...mapState('websocketExtraData', ['taskData', 'alarmsData', 'deviceTypeStats', 'deviceStats', 'taskOverview']),
    tasks() {
      return Object.keys(this.taskData || {}).map(key => this.taskData[key])
    },
  },
  data() {
    return {
      collapseArr: [false, false, true],
      alertCollapseArr: [true, true, true],
      alertList: [1],
      activeTaskId: null,
      overviewInfo: {},
      devices: [
        { name: '机器狗', total: 10, fault: 0, offline: 3 },
        { name: '机器人', total: 0, fault: 0, offline: 0 },
        { name: '无人机', total: 0, fault: 0, offline: 0 },
        { name: '无人车', total: 0, fault: 0, offline: 0 },
      ],
      // tasks: [
      //   { name: '任务1', status: '执行中', period: '2-4小时' },
      //   { name: '任务2', status: '待执行', period: '2-4小时' },
      //   { name: '任务3', status: '执行中', period: '2-4小时' },
      //   { name: '任务4', status: '执行中', period: '2-4小时' },
      // ]
    }
  },
  async mounted() {
    if (this.alarmsData?.high?.items?.length) {
      this.collapseArr[2] = false
    }
  },
  methods: {
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

<style lang="scss" scoped>
.right-div {
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

    .zbgl {
      .top {
        color: #8CF;
        background: #053260;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 36px;
        letter-spacing: 0.857px;
      }
      .devices {
        color: #D0DEEE;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 36px;
        letter-spacing: 0.857px;
      }
    }
    .rwgl {
      .top {
        color: #8CF;
        background: #053260;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 36px;
        letter-spacing: 0.857px;
      }
      .tasks {
        color: #D0DEEE;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 36px;
        letter-spacing: 0.857px;
        .status {
          padding: 0 6px;
          font-family: "Microsoft YaHei";
          font-size: 10px;
          line-height: 18px; /* 180% */
          border-radius: 2px;
          background: #131B2D;
          text-align: center;
          &.green {
            color: #49DC7F;
            border: 1px solid #49DC7F;
          }
          &.orange {
            color: #FF7734;
            border: 1px solid #FF7734;
          }
        }
      }
    }
  }
}
</style>
