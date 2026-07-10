<template>
  <div class="left-div pr28 h100 mt25 no-w-scroll" :class="{ 'ml20': !collapse, 'ml10': collapse }" :style="{ 'pointer-events': selectedRobotId ? 'none' : 'auto', maxHeight: 'calc(100% - 50px)', overflowY: 'auto' }">
    <div class="container flex-column w100 h100 common-scroll" style="flex-wrap: nowrap;">
      <div class="box" :class="{'hp264': deviceTypeStats?.length, 'hp155': !deviceTypeStats?.length}">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="getMoreRobotInfo">
          <span class="desc">装备类型</span>
          <!-- <span class="flx-center more curp">
            <span>更多</span>
            <svg-icon :icon-class="collapseArr[0] ? 'right' : 'down'" class="ml4" />
          </span> -->
        </div>
        <div class="pt20 pr18 pb20 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 flex1 pt9 pr5 pb5 pl9">
              <div class="desc">总数</div>
              <div class="value mt4">{{ deviceStats?.total ? String(deviceStats?.total).padStart(2, '0') : '-' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">在线</div>
              <div class="value mt4">{{ deviceStats?.online ? String(deviceStats?.online).padStart(2, '0') : '-' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">故障</div>
              <div class="value mt4">{{ deviceStats?.fault ? String(deviceStats?.fault).padStart(2, '0') : '-' }}</div>
              <div class="tar hp16" style="margin-top: -4px;">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
            <div class="item wp66 flex1 ml10 pt9 pr5 pb5 pl9">
              <div class="desc">离线</div>
              <div class="value mt4">{{ deviceStats?.offline ? String(deviceStats?.offline).padStart(2, '0') : '-' }}</div>
              <div class="tar hp16" style="margin-top: -4px">
                <svg-icon icon-class="robot"></svg-icon>
              </div>
            </div>
          </div>
          <div class="mt20">
            <div v-if="deviceTypeStats?.length" class="t2">设备类型</div>
            <div class="device_types mt10 flx-justify-between">
              <div v-for="(item, index) in deviceTypeStats || []" :key="item.type" class="item flex1 p9" :class="{'ml10': index !== 0}">
                <div class="desc">{{ item.name }}</div>
                <div class="value mt4">{{ item.count ? String(item.count).padStart(2, '0') : '-' }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="box mt20 task" :class="{ 'no_data hp41': collapseArr[1], 'hp323': !collapseArr[1] }">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="toggleCollapse('collapseArr', 1)">
          <div class="flx-center">
            <span class="desc">任务列表</span>
            <div v-if="Object.keys(taskData || [])?.length" class="ml4 notice pr10 pl10">{{ Object.keys(taskData || []).length ? Object.keys(taskData || []).length > 99 ? '99+' : Object.keys(taskData || []).length : '-'  }}</div>
          </div>
          <!-- <span class="flx-center more curp">
            <span>更多</span>
            <svg-icon :icon-class="collapseArr[1] ? 'right' : 'down'" class="ml4" />
          </span> -->
        </div>
        <div class="list pt10 pr20 pl20 mb20 common-scroll ovya" :style="{ maxHeight: collapseArr[2] ? '300px' : '262px' }">
           <div v-for="(item, key, index) of taskData || []" class="item wp288 pb10" :style="{ 'pointer-events': item.status === 'running' ? 'auto' : 'none' }" :class="{ 'is-active': activeTaskId == item.taskId, 'mb10': index !== Object.keys(taskData || {}).length - 1 }" @click="handleClickTask(key)">
            <div class="header flx-justify-between p10">
              <div class="flx-align-center flex1" style="min-width: 0">
                <svg-icon icon-class="d-right"></svg-icon>
                <span class="ml4 text-ellipsis" :title="item.name">{{ item.name }}</span>
              </div>
              <span class="status flx-center pt2 pr6 pb2 pl6 wp64 ml10" :class="getTaskStatusName(item.status)">
                <svg-icon icon-class="security"></svg-icon>
                <span class="ml4">{{ item.statusName || '---' }}</span>
              </span>
            </div>
            <div class="desc mt10">
              <div>任务时间：{{ item.timeRange }}</div>
              <div class="mt10 text-ellipsis" style="max-width: calc(100% - 45px)">当前位置：{{ item.currentLocation }}</div>
              <div class="mt10">执行装备：{{ item.equipmentList?.length }}台</div>
            </div>
            <div v-if="item.status === 'running'" class="symbol wp36 hp28">
              <img :src="require(`../../../../assets/images/new-bi/camera-${activeTaskId == item.taskId ? 'active' : 'off1'}.png`)" class="w100 h100" alt="" srcset="">
            </div>
           </div>
        </div>
      </div>
      <div class="box mt20 alert" :class="{ 'no_data hp41': collapseArr[2], 'hp323': !collapseArr[2] }" style="max-height: 446px;">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title" @click="toggleCollapse('collapseArr', 2)">
          <span class="desc">告警中心</span>
          <!-- <span class="flx-center more curp">
            <span>更多</span>
            <svg-icon :icon-class="collapseArr[2] ? 'right' : 'down'" class="ml4" />
          </span> -->
        </div>
        <div v-if="alarmsData" class="mt10 ml20 common-scroll ovya mb10" :style="{ maxHeight: collapseArr[1] ? '360px' : '262px', minHeight: '146px' }">
          <div v-for="(alarm, key, alarmIndex) in alarms" :key="key" class="type wp288 pt10 pr20 pb10 pl20" :class="[alarm.class, { 'hp42 ovyh': alertCollapseArr[alarmIndex], 'mt10': alarmIndex !== 0 }]">
            <div class="type_name flx-justify-between" @click="toggleCollapse('alertCollapseArr', alarmIndex)">
              <div class="flx-center">
                <span class="symbol flx-center">
                  <svg-icon icon-class="notice1"></svg-icon>
                </span>
                <span class="ml10">{{ alarm.name || '-' }}（{{ alarmsData?.[key]?.items?.length || 0 }}）</span>
              </div>
              <span class="flx-center curp">
                <svg-icon :icon-class="alertCollapseArr[alarmIndex] ? 'down' : 'up'" style="font-size: 14px;"></svg-icon>
              </span>
            </div>
            <div class="mt20 list">
              <div v-for="(item, index) in alarmsData?.[key]?.items || []" :key="item.alarmId" class="item flx-center" :class="{ 'mt40 mb10': index !== 0 }" @click="handleClickAlert(item)">
                <div class="img wp120 hp72 flx-center"
                >
                <!-- :style="{ background: `url(${getImageUrl(item.snapshotUrl?.visible) || (item.title.includes('火灾') ? img1 : img2)}) lightgray -4.267px -11.862px / 104% 118.678% no-repeat` }" -->
                  <img :src="getImageUrl(item.snapshotUrl?.visible)" alt="" srcset="" style="width: 100%; height: 100%; object-fit: cover;">
                  <span class="alert_type wp64 text-ellipsis">{{ item.categoryName }}</span>
                </div>
                <div class="ml6 flex1" style="min-width: 0;">
                  <div class="event text-ellipsis" :title="item.title">事件：{{ item.title }}</div>
                  <div class="time mt2 flx-align-start flex">
                    <span>时间：</span>
                    <div class="flex-column">
                      <span>{{ item.eventTime.split(' ')[0] }}</span>
                      <span>{{ item.eventTime.split(' ')[1] }}</span>
                    </div>
                  </div>
                  <div class="area mt5">位置：{{ item?.location?.address || '-' }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="collapse-left flx-center" @click="$emit('changeCollapse')">
      <div class="flx-center">
        <svg-icon :icon-class="collapse ? 'right-s' : 'left-s'" />
      </div>
    </div>
    <TaskRobotView ref="taskRobotViewRef" @handleClickTask="handleClickTask" />
    <WarningBatch ref="warningBatchRef" />
    <WarnInfo ref="WarnInfoRef" />
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex';
import { getPatrolPanoramaOverview } from '../../../../api/new-bi.js';
import TaskRobotView from '../../components/modal/TaskRobotView.vue';
import WarningBatch from './warning/WarningBatch.vue'
import WarnInfo from './warning/WarnInfo.vue'
export default {
  name: 'BiPatrolPanoramaLeft',
  components: { TaskRobotView, WarningBatch, WarnInfo },
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
      collapseArr: [false, false, false],
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
      },
      updated: false,
      img1: require('@/assets/images/new-bi/test.png'),
      img2: require('@/assets/images/new-bi/warning1.png'),
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotAlarmInfo', 'setShowRobotIds']),
    getImageUrl(url) {
      const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
      return `${preUrl}${url}`
    },
    getTaskStatusName(status) {
      switch (status) {
        case 'running':
          return 'green'
        case 'pending':
          return 'orange'
        case 'completed':
          return 'blue'
        case 'failed':
          return 'red'
        default:
          return 'gray'
      }
    },
    getMoreRobotInfo() {
  
    },
    toggleCollapse(type, typeIndex) {
      this.$set(this[type], typeIndex, !this[type][typeIndex])
    },
    handleClickTask(taskId) {
      console.log(111, taskId, this.activeTaskId);
            
      if (this.activeTaskId == taskId) {
        this.$refs.taskRobotViewRef.dialogVisible = false
        // 清空录像
        this.activeTaskId = null
        this.setShowRobotIds([])
        return
      }
      this.activeTaskId = taskId
      console.log(222, taskId, this.activeTaskId, taskId === this.activeTaskId);
      const robotIds = this.taskData[taskId].equipmentList.map(robot => robot.robotId)
      this.setShowRobotIds(robotIds)
      this.$refs.taskRobotViewRef.showModal({
        taskInfo: { ...this.taskData[taskId]},
        robotIds
      })
    },
    handleClickAlert(item) {
      this.$refs.warningBatchRef.open(this.alarmsData || {})
      // this.$refs.WarnInfoRef.open(item)
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
    alarmsData: {
      handler(newVal, oldVal) {
        if (newVal?.high?.items?.length && !this.updated) {
          this.alertCollapseArr[0] = false
          this.updated = true
        }
      },
      immediate: true
    }
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
          background-image: url("../../../../assets/images/new-bi/title-bg.png");
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
          &:nth-child(2) {
            border: 1px solid #096A2B;
            background: #031F27;
            .desc, .value, .svg-icon {
              color: #00FF50;
            }
          }
          &:nth-child(3) {
            border: 1px solid #752700;
            background: #1B191F;
            .desc, .value, .svg-icon {
              color: #FF6E00;
            }
          }
          &:nth-child(4) {
            border: 1px solid rgba(255, 255, 255, 0.20);
            background: #13223A;
            .desc, .value, .svg-icon {
              color: #fff;
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
                position: relative;
                /* background: #ccc; */
                /* background: var(--img) lightgray -4.267px -11.862px / 104% 118.678% no-repeat; */
                .alert_type {
                  position: absolute;
                  top: 0;
                  left: 0;
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
              font-family: "Microsoft YaHei";
              font-size: 12px;
              line-height: 16px;
              .svg-icon {
                color: #FFF;
                font-size: 12px;
              }
              &.blue {
                background: #225CA4;;
              }
              &.orange {
                background: #E18000;
              }
              &.gray {
                background: #616161;
              }
              &.green {
                background: #00B61B;
              }
              &.red {
                background: #A42222;
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
