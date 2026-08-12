<template>
  <div
    class="left-div pr28 mb20 no-w-scroll mt105"
    :class="{ 'ml20': !collapse, 'ml10': collapse }"
    :style="{
      'pointer-events': sidebarPointerEvents,
      height: 'calc(100% - 154px)',
      overflowY: selectVisible ? 'visible' : 'auto',
      overflowX: selectVisible ? 'visible' : 'hidden'
    }"
  >
    <div
      class="container flex-column w100"
      :class="{ 'ovh-visible': selectVisible }"
      style="flex-wrap: nowrap;"
    >
      <div class="box bi-corner-box hp386">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">告警概览</span> 
          <!-- <span class="flx-center more curp" @click="getMoreRobotInfo">
            <span>更多</span>
            <svg-icon icon-class="right" class="ml4" />
          </span> -->
        </div>
        <div class="pt20 pr18 pb20 pl18">
          <div class="count flx-justify-between">
            <div class="item wp66 hp70 flx-center flex-column red">
              <div class="desc">今日告警</div>
              <div class="value mt4">{{ alarmSummary.totalToday || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 green">
              <div class="desc">已处理</div>
              <div class="value mt4">{{ alarmSummary.handled || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 gray">
              <div class="desc">未处理</div>
              <div class="value mt4">{{ alarmSummary.unhandled || 0 }}</div>
            </div>
            <div class="item wp66 hp70 flx-center flex-column ml10 ">
              <div class="desc">处置率</div>
              <div class="value mt4">{{ alarmSummary.handleRateText || 0 }}</div>
            </div>
          </div>
          <div class="mt20">
            <div class="t2">告警分布</div>
            <!-- <div class="wp142 hp80">
              <video class="w100 h100" src="./111.mp4" controls></video>
            </div> -->
            <div class="mt20 hp150">
              <PieChart :items="alarmPieChart" />
            </div>
          </div>
        </div>
      </div>
      <div class="box bi-corner-box hp520 mt20 task pb18 posr" :class="{ 'no_data hp41': collapseArr[1] }">
        <div class="pt9 pr20 pb9 pl20 flx-justify-between title">
          <span class="desc">巡逻概览</span>
        </div>
        <div class="xlgl mt20 d-flex pr20 pl20">
          <div class="item hp62 flex1 flx-center flex-column">
            <div class="desc1">今日巡逻时长</div>
            <div class="value mt4"><span class="mr4">{{ patrolOverview.durationToday || 0 }}</span>{{ patrolOverview.durationUnit || '小时' }}</div>
          </div>
          <div class="item hp62 flex1 flx-center flex-column ml10">
            <div class="desc1">今日巡逻里程</div>
            <div class="value mt4"><span class="mr4">{{ patrolOverview.mileageToday || 0 }}</span>{{ patrolOverview.mileageUnit || 'KM' }}</div>
          </div>
        </div>
        <div class="mt18 patrol-videos posr">
          <div class="t2 ml20">巡逻画面</div>
          <div class="flex flex-wrap pl10">
            <div
              v-for="(slot, index) in videoSlots"
              :key="`patrol-slot-${index}`"
              class="patrol-video-slot wp296 hp159 mt10 ml10 posr ovh curp"
              :class="{ active: selectVisible && activeSlotIndex === index, empty: !slot }"
              @click="slot ? toggleEquipmentSelect(index) : openEquipmentSelect(index)"
            >
              <template v-if="slot">
                <VideoBox
                  :videoIndex="slot.camera.key"
                  :prefixId="prefixId"
                  :ZQL_videosInfos="getSlotVideosInfos(slot)"
                />
                <div class="video-name text-ellipsis posa">{{ getSlotLabel(slot) }}</div>
                <div class="video-close curp flx-center posa" @click.stop="clearSlot(index)">
                  <svg-icon icon-class="close" />
                </div>
                <div class="video-switch curp posa" @click.stop="openEquipmentSelect(index)">切换画面</div>
              </template>
              <div v-else class="empty-slot w100 h100 flx-center flex-column">
                <img class="empty-img" src="@/assets/images/new-bi/empty.png" alt="">
                <div class="empty-text flx-align-center mt10">
                  <svg-icon icon-class="plus" class="add-icon" />
                  <span>请添加视频画面</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <EquipmentScreenSelect
          :visible="selectVisible"
          :title="selectTitle"
          :placeholder="selectPlaceholder"
          :options="selectOptions"
          :selected-id="currentSelectedId"
          @close="closeEquipmentSelect"
          @select="handleSelectOption"
        />
      </div>
    </div>
    <div class="collapse-left flx-center" @click="$emit('changeCollapse')">
      <div class="flx-center">
        <svg-icon :icon-class="collapse ? 'right-s' : 'left-s'" />
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import PieChart from './PieChart.vue';
import VideoBox from '../components/modal/VideoBox.vue';
import EquipmentScreenSelect from './EquipmentScreenSelect.vue';
export default {
  name: 'BiIndexLeft',
  components: { PieChart, VideoBox, EquipmentScreenSelect },
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
    ...mapState('websocketRobot', ['robots', 'cameras']),
    ...mapState('websocketExtraData', ['taskData', 'alarmsData', 'deviceTypeStats', 'deviceStats', 'robotBaseInfo', 'alarmSummary', 'patrolOverview']),
    /** 选中固定摄像头时不禁用侧边栏 */
    isSelectedFixedCamera() {
      if (!this.selectedRobotId) return false
      const robot = this.robotBaseInfo?.[this.selectedRobotId]
        || this.$store.getters['websocketRobot/getSelectedRobot']
        || {}
      return robot.sourceType === 'FIXED_CAMERA'
        || robot.typeCode === 'FIXED_CAMERA'
        || robot.equipmentType === 'FIXED_CAMERA'
        || robot.type === 'FIXED_CAMERA'
        || robot.type === '固定摄像头'
    },
    sidebarPointerEvents() {
      return (this.selectedRobotId && !this.isSelectedFixedCamera) ? 'none' : 'auto'
    },
    alarmPieChart() {
      return [
        { name: '高风险', value: 0, color: '#FF2424' },
        { name: '中风险', value: 0, color: '#FFA024' },
        { name: '低风险', value: 0, color: '#00D8A4' },
      ]
    },
    onlineRobots() {
      return (this.robots || []).filter(robot => {
        const status = this.robotBaseInfo?.[robot.robotId]?.status || robot.status
        return status === 'online'
      })
    },
    // 仅 1 个在线装备时，选择框展示该装备下全部摄像头（本体优先）
    isSingleEquipmentCameraMode() {
      return this.onlineRobots.length === 1
    },
    selectTitle() {
      if (this.isSingleEquipmentCameraMode) {
        return this.onlineRobots[0]?.name || '装备画面选择'
      }
      return '装备画面选择'
    },
    selectPlaceholder() {
      return this.isSingleEquipmentCameraMode ? '请输入摄像头名称' : '请输入装备名称'
    },
    currentSelectedId() {
      const slot = this.videoSlots[this.activeSlotIndex]
      if (!slot) return ''
      return this.isSingleEquipmentCameraMode ? slot.camera?.key : slot.robotId
    },
    selectOptions() {
      const otherSlot = this.videoSlots[this.activeSlotIndex === 0 ? 1 : 0]
      if (this.isSingleEquipmentCameraMode) {
        const robot = this.onlineRobots[0]
        const cameras = [...(robot?.cameras || [])].sort((a, b) => {
          if (a.groupType === 'body') return -1
          if (b.groupType === 'body') return 1
          return 0
        })
        return cameras.map(camera => {
          const occupied = otherSlot?.camera?.key === camera.key
          return {
            id: camera.key,
            label: camera.name || camera.groupTypeName || camera.key,
            robotId: robot.robotId,
            cameraKey: camera.key,
            disabled: occupied,
            occupied
          }
        })
      }
      return this.onlineRobots.map(robot => {
        const occupied = otherSlot?.robotId === robot.robotId
        return {
          id: robot.robotId,
          label: robot.name,
          robotId: robot.robotId,
          disabled: occupied,
          occupied
        }
      })
    }
  },
  data() {
    return {
      tabList: [
        { label: '今日', value: 0 },
        { label: '本月', value: 1 },
        { label: '当年', value: 2 }
      ],
      tabIndex: 0,
      collapseArr: [false, false, true],
      alertCollapseArr: [true, true, true],
      alertList: [1],
      activeTaskId: null,
      overviewInfo: {},
      alarms: {
        high: { name: '高风险', class: 'danger' },
        medium: { name: '中风险', class: 'warning' },
        low: { name: '低风险', class: 'green' },
      },
      prefixId: 'home-video',
      videoSlots: [null, null],
      selectVisible: false,
      activeSlotIndex: 0
    }
  },
  async mounted() {
    this.setPrefixId(this.prefixId)
    if (this.alarmsData?.high?.items?.length) {
      this.collapseArr[2] = false
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['startCamera', 'stopCamera', 'setPrefixId']),
    getMoreRobotInfo() {

    },
    getSlotVideosInfos(slot) {
      if (!slot?.camera) return {}
      const camera = this.cameras?.[slot.camera.key] || slot.camera
      return {
        [camera.key]: {
          ...camera,
          robot: slot.robot
        }
      }
    },
    getSlotLabel(slot) {
      if (!slot) return ''
      const equipmentName = slot.robot?.name || ''
      const cameraName = slot.camera?.name || slot.camera?.groupTypeName || ''
      return cameraName ? `${equipmentName}-${cameraName}` : equipmentName
    },
    openEquipmentSelect(index) {
      this.activeSlotIndex = index
      this.selectVisible = true
      this.$emit('patrol-select-change', true)
    },
    toggleEquipmentSelect(index) {
      if (this.selectVisible && this.activeSlotIndex === index) {
        this.closeEquipmentSelect()
        return
      }
      this.openEquipmentSelect(index)
    },
    closeEquipmentSelect() {
      this.selectVisible = false
      this.$emit('patrol-select-change', false)
    },
    async clearSlot(index) {
      const slot = this.videoSlots[index]
      if (!slot?.camera) {
        this.$set(this.videoSlots, index, null)
        return
      }
      const camera = this.cameras?.[slot.camera.key] || slot.camera
      await this.stopCamera(camera)
      this.$set(this.videoSlots, index, null)
      if (this.selectVisible && this.activeSlotIndex === index) {
        // 保持选择框打开，便于继续选择
      }
    },
    async handleSelectOption(item) {
      const index = this.activeSlotIndex
      const current = this.videoSlots[index]
      const currentId = this.isSingleEquipmentCameraMode ? current?.camera?.key : current?.robotId
      // 再次点击已勾选项：关闭当前画面
      if (item.id === currentId) {
        await this.clearSlot(index)
        return
      }
      await this.assignSlot(index, item)
    },
    async assignSlot(index, item) {
      const robot = this.onlineRobots.find(r => r.robotId === item.robotId)
        || this.robots.find(r => r.robotId === item.robotId)
      if (!robot) return
      const robotInfo = Object.assign({}, this.robotBaseInfo?.[robot.robotId] || robot)
      let cameraMeta = null
      if (this.isSingleEquipmentCameraMode) {
        cameraMeta = (robot.cameras || []).find(c => c.key === item.cameraKey || c.key === item.id)
      } else {
        cameraMeta = (robot.cameras || []).find(c => c.groupType === 'body') || (robot.cameras || [])[0]
      }
      if (!cameraMeta) return
      const camera = Object.assign({}, this.cameras?.[cameraMeta.key] || cameraMeta)
      const prev = this.videoSlots[index]
      if (prev?.camera?.key && prev.camera.key !== camera.key) {
        await this.stopCamera(this.cameras?.[prev.camera.key] || prev.camera)
      }
      // 先挂载视频 DOM，再拉流，保证 LiveKit 能附着到 video 元素
      this.$set(this.videoSlots, index, {
        robotId: robot.robotId,
        robot: { ...robot, ...robotInfo },
        camera
      })
      this.setPrefixId(this.prefixId)
      await this.$nextTick()
      await this.startCamera({ robot: robotInfo, camera })
      const latest = this.cameras?.[camera.key] || camera
      this.$set(this.videoSlots, index, {
        robotId: robot.robotId,
        robot: { ...robot, ...robotInfo },
        camera: latest
      })
    },
    async clearAllSlots() {
      this.closeEquipmentSelect()
      for (let i = 0; i < this.videoSlots.length; i++) {
        await this.clearSlot(i)
      }
    },
    toggleCollapse(type, typeIndex) {
      this.$set(this[type], typeIndex, !this[type][typeIndex])
    },
    handleClickTask(taskId) {
      if (this.activeTaskId === taskId) {
        this.$refs.taskRobotViewRef.dialogVisible = false
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
    collapse(val) {
      if (val && this.selectVisible) {
        this.closeEquipmentSelect()
      }
    },
    cameras: {
      handler() {
        this.videoSlots.forEach((slot, index) => {
          if (!slot?.camera?.key) return
          const latest = this.cameras?.[slot.camera.key]
          if (latest) {
            this.$set(this.videoSlots, index, { ...slot, camera: latest })
          }
        })
      },
      deep: true
    }
  },
  async beforeDestroy() {
    await this.clearAllSlots()
  }
}
</script>

<style lang="scss" scoped>
.patrol-videos {
  .patrol-video-slot {
    background: #092144;
    border: 1px solid #3877F2;
    box-sizing: border-box;
    &.active {
      box-shadow: 0 0 12px 0 rgba(56, 119, 242, 0.55);
    }
    ::v-deep .item {
      box-shadow: none !important;
    }
    .video-name {
      top: 3px;
      left: 4px;
      z-index: 2;
      padding: 4px;
      color: #FFF;
      background: rgba(0, 0, 0, 0.50);
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-weight: 600;
      line-height: 19px;
      max-width: calc(100% - 40px);
      pointer-events: none;
    }
    .video-close,
    .video-switch {
      z-index: 3;
      opacity: 0;
      transition: opacity 0.2s;
      pointer-events: none;
    }
    .video-close {
      top: 5px;
      right: 5px;
      width: 24px;
      height: 24px;
      color: #FFF;
      .svg-icon {
        width: 14px;
        height: 14px;
        font-size: 14px;
      }
    }
    .video-switch {
      right: 4px;
      bottom: 4px;
      padding: 4px;
      color: #FFF;
      background: rgba(0, 0, 0, 0.50);
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-weight: 600;
      line-height: 19px;
    }
    &:hover {
      .video-close,
      .video-switch {
        opacity: 1;
        pointer-events: auto;
      }
    }
    .empty-slot {
      .empty-img {
        width: 101px;
        height: 52px;
        object-fit: contain;
        opacity: 0.7;
      }
      .empty-text {
        gap: 3px;
        color: #BEE1FF;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 28px;
        text-decoration: underline;
        .add-icon {
          width: 18px;
          height: 18px;
          color: #BEE1FF;
          font-size: 18px;
        }
      }
    }
  }
}
.left-div {
  backdrop-filter: unset !important;
  background: transparent !important;
  .container {
    overflow-y: auto;
    &.ovh-visible {
      overflow: visible;
    }
    &::-webkit-scrollbar {
      width: 2px;               /* 垂直滚动条宽度 */
      height: 2px;              /* 水平滚动条高度 */
    }
    &::-webkit-scrollbar-thumb {
      border: 1px solid #42536F; /* 创建内边距效果,会覆盖背景值 */
    }
    .box {
      width: 334px;
      // border: 2px solid rgba(0, 0, 0, 0.00) !important;
      &:not(.no_data) {
        backdrop-filter: blur(0px);
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
          .desc1 {
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
        overflow: visible;
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
