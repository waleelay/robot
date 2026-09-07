<template>
  <el-dialog
    v-if="warningVisible"
    class="execute-dialog execute-dialog1 flx-align-center"
    :width="warningVisible ? '276px' : '0'"
    :height="warningVisible ? '136px' : '0'"
    :visible.sync="warningVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <template slot="title"></template>
    <div class="flx-center wp274 hp136 custom-warning" :class="riskThemeClass">
      <svg-icon icon-class="warning" style="font-size: 76px" :style="{ color: flashIconColor }"></svg-icon>
    </div>
    <template slot="footer"></template>
  </el-dialog>
  <el-dialog
    v-else
    class="custom-dialog__wrapper robot-dialog flx-align-center"
    v-dialogDrag
    :width="dialogVisible ? '1094px' : '0'"
    :height="dialogVisible ? '632px' : '0'"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="异常报告"
  >
    <template slot="footer"></template>
    <div
      class="custom-modal-container warning-batch-container"
      :class="[riskThemeClass, pulseBorderClass]"
    >
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box" style="width: 1096px">
        <!-- <div class="line-up"></div>
        <div class="line-right"></div>
        <div class="line-down"></div>
        <div class="line-left"></div> -->
        <div class="top m4 flx-justify-between" style="position: relative; z-index: 2">
          <div class="title ml10">告警信息</div>
          <div class="close mr10" @click="close">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content p20 flex" style="position: relative; z-index: 2">
          <div class="flex1">
            <div class="tuxiang">
              <div class="image-box">
                <div class="title flx-justify-between">
                  <div class="text">告警画面</div>
                  <div v-if="options.length > 1" class="select-list">
                    <el-select
                      v-model="selectedValue"
                      placeholder="请选择"
                      class="custom-select"
                      popper-class="custom-select-popper"
                      @change="handleChangeSelect"
                    >
                      <el-option
                        v-for="item in options"
                        :key="item.key"
                        :label="item.label"
                        :value="item.key">

                        <div class="flx-align-center">
                          <el-radio v-model="selectedValue" :label="item.key" class="custom-radio">{{ item.label }}</el-radio>
                        </div>
                      </el-option>
                    </el-select>
                  </div>
                </div>
                <div class="list-box mt10">
                  <!--  border: 0.5px solid #1665A2; background: #001D46; -->
                  <div class="mt10 flx-center" style="width: 640px; height: 355px;">
                    <!-- <img v-if="details?.title?.includes('火灾')" src="../../../../../assets/images/new-bi/test.png" class="w100" style="height: auto; max-height: 100%;" alt="">
                    <img v-else src="../../../../../assets/images/new-bi/warning1.png" class="w100" style="height: auto; max-height: 100%;" alt=""> -->
                    <el-carousel v-if="options.length" trigger="click" :autoplay="false" height="100%" ref="carouselRef" @change="handleChangeCarousel">
                      <el-carousel-item v-for="item in options" :key="item.key" :name="item.key">
                        <div class="img">
                          <img v-if="snapshotImageSrc(item.key)" :src="snapshotImageSrc(item.key)" alt="">
                          <div v-else class="w100 h100 flx-center">暂无{{ item.label }}图片</div>
                        </div>
                      </el-carousel-item>
                    </el-carousel>
                    <div v-else class="w100 h100 flx-center">暂无图片</div>
                  </div>
                </div>
              </div>
              <div class="mt20 address">
                <div class="title">
                  <div class="text">当前位置</div>
                </div>
                <div class="modal-map mt10" style="width: 640px; height: 89px;">
                  <Gis :centerPoint="[details?.location?.lat, details?.location?.lng]" style="height: 60px !important; border-radius: 4px 4px 0 0;" />
                  <div class="flx-align-center ml17 mt6">
                    <svg-icon icon-class="address" style="font-size: 14px; color: #fff" />
                    <span class="ml10" style="color: #FFF;font-family: Inter;font-size: 12px;line-height: 15px;">{{ details?.location?.address || '暂无位置信息' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="flex1 dialog-content h100">
            <div class="warning">
              <div class="title">
                <div class="text">告警详情</div>
              </div>
              <div class="mt10 detail p20">
                <div class="desc" style="height: auto; border: none">
                  <div class="item flx-justify-between">
                    <span class="name">设备名称：</span>
                    <span class="value">{{ robotBaseInfo?.[details?.robotId]?.name || '-' }}</span>
                  </div>
                  <div class="item flx-justify-between mt20">
                    <span class="name">区域名称：</span>
                    <span class="value">{{ details?.location?.address || '暂无位置信息' }}</span>
                  </div>
                  <div class="item flx-justify-between mt20">
                    <span class="name">告警时间：</span>
                    <span class="value">{{ details.eventTime }}</span>
                  </div>
                  <div class="item flx-justify-between mt20" style="align-items: flex-start">
                    <span class="name" style="width: 75px">告警内容：</span>
                    <span class="value flex1 tar">{{ details.content || details.title }}</span>
                  </div>
                  <!--  <div v-if="details.taskName" class="item flx-justify-between mt20">
                      <span class="name">所属任务：</span>
                      <span class="value">{{ details.taskName }}</span>
                    </div>
                    <div v-if="details.humanTaskName" class="item flx-justify-between mt20">
                      <span class="name">等待节点：</span>
                      <span class="value">{{ details.humanTaskName }}</span>
                    </div>-->
                  <!-- <div class="item flx-justify-between mt20">
                    <span class="name">告警类型：</span>
                    <span class="value">{{ typeFormat('qh_alarm_record_type', details.ErrorType) || '-' }}</span>
                  </div> -->
                  <div class="item flx-justify-between mt20">
                    <span class="name">严重等级：</span>
                    <span class="status" :class="riskThemeClass">{{ details.levelName || '高风险' }}</span>
                  </div>
                </div>
                <div class="title mt32 with-b-t pt20">
                  <div class="text">告警处置</div>
                </div>
                <div class="operation mt20">
                  <div class="flx-justify-between" @click="execute(0)">
                    <div class="flx-justify-center">
                      <img src="@/assets/images/new-bi/file.svg" alt="">
                      <!-- <div class="text ml10">预案处置</div> -->
                      <div class="text ml10">立即处置</div>
                    </div>
                    <img src="@/assets/images/new-bi/right.svg" alt="" style="font-size: 14px;">
                  </div>
                  <div class="flx-justify-between success mt15" @click="execute(1)">
                    <div class="flx-justify-center">
                      <img src="@/assets/images/new-bi/file.svg" alt="">
                      <!-- <div class="text ml10">确认处置</div> -->
                      <div class="text ml10">稍后处置</div>
                    </div>
                    <img src="@/assets/images/new-bi/right.svg" alt="" style="font-size: 14px;">
                  </div>
                  <div class="flx-justify-between danger mt15" @click="execute(2)">
                    <div class="flx-justify-center">
                      <img src="@/assets/images/new-bi/file.svg" alt="">
                      <div class="text ml10">误报</div>
                    </div>
                    <img src="@/assets/images/new-bi/right.svg" alt="" style="font-size: 14px;">
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <WarningExecute ref="warningExecuteRef" @close="() => details = {}" />
    <WarningExecuteError ref="warningExecuteErrorRef" @close="close" />
    <WarningExecuteNo ref="warningExecuteNoRef" @close="close" />
  </el-dialog>
</template>

<script>
import WarningExecute from './WarningExecute.vue';
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
import { mapState, mapActions } from 'vuex';
import { executeAlarm } from '../../../../../api/media.js';
import { buildSnapshotOptions, loadSnapshotObjectUrls } from '@/utils/alarm-snapshot'

export default {
  name: 'WarningInfo',
  dicts: ['qh_alarm_record_type'],
  components: {
    // Gis,
    WarningExecute,
    WarningExecuteNo,
    WarningExecuteError
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotAlarmObj', 'gisMapCenterPoint', 'workflowAlarms']),
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    },
    currenAlarm() {
      return
    },
    isWorkflowAlarm() {
      return this.details?.workflowActionable === true
    },
    alarmLevelKey() {
      return String(this.details?.level || '').trim().toLowerCase()
    },
    /** 高风险 red / 中风险 orange / 其它 primary */
    riskThemeClass() {
      if (this.alarmLevelKey === 'high') return 'red'
      if (this.alarmLevelKey === 'medium') return 'orange'
      return 'primary'
    },
    flashIconColor() {
      if (this.alarmLevelKey === 'high') return '#FFDD00'
      if (this.alarmLevelKey === 'medium') return '#FFB347'
      return '#4DB3FF'
    },
    /** 自动弹出才播详情边框流光；手动打开不加 */
    showPulseBorder() {
      return this.show && !this.manualOpen
    },
    pulseBorderClass() {
      if (!this.showPulseBorder) return ''
      if (this.alarmLevelKey === 'high') return 'red-light'
      if (this.alarmLevelKey === 'medium') return 'orange-light'
      return 'primary-light'
    }
  },
  data() {
    return {
      dialogVisible: false,
      warningVisible: false,
      manualOpen: false,
      details: {
        location: {
          lat: this.gisMapCenterPoint?.[0] || '',
          longitude: this.gisMapCenterPoint?.[1] || ''
          // lat: '',
          // longitude: ''
        }
      },
      reason: '',
      selectedValue: '',
      options: [],
      dialogImageUrl: '',
      dialogIRUrl: '',
      loading: false,
      timer: null,
      workflowQueue: [],
      deferredAlarmIds: new Set(),
      show: false,
      snapshotObjectUrls: {},
      snapshotLoadSeq: 0
    }
  },
  created() {
    this.$root.$on('bi-open-warn-info', this.openManual)
  },
  beforeDestroy() {
    this.$root.$off('bi-open-warn-info', this.openManual)
    if (this.timer) {
      clearTimeout(this.timer)
      this.timer = null
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['removeAlarm']),
    /** 列表手动打开：直接详情，无小框与边框动画 */
    openManual(item) {
      if (!item) return
      this.open(item, { manual: true })
    },
    close() {
      if (this.isWorkflowAlarm && this.details.alarmId != null) {
        this.deferredAlarmIds.add(String(this.details.alarmId))
        this.workflowQueue = this.workflowQueue.filter(item => String(item.alarmId) !== String(this.details.alarmId))
      }
      this.resetDialog()
      this.openNextWorkflowAlarm()
    },
    /**
     * @param {object} data 告警数据
     * @param {{ manual?: boolean }} options manual=true 时跳过小框与详情边框动画
     */
    open(data, options = {}) {
      const manual = Boolean(options.manual)
      if (manual) {
        if (this.timer) {
          clearTimeout(this.timer)
          this.timer = null
        }
        this.manualOpen = true
        this.show = false
        this.warningVisible = false
        this.loading = false
        this.details = { ...data }
        this.applySnapshotOptions(this.details)
        this.dialogVisible = true
        return
      }
      if (this.dialogVisible || this.warningVisible) return
      this.manualOpen = false
      this.loading = false
      this.details = {
        ...data
      }
      this.applySnapshotOptions(this.details)
      this.warningVisible = true
      this.timer = setTimeout(() => {
        this.warningVisible = false
        // v-else 重建详情弹窗后再打开，避免闪一下被关掉
        this.$nextTick(() => {
          this.dialogVisible = true
        })
      }, 2000)
    },
    applySnapshotOptions(item) {
      this.options = buildSnapshotOptions(item)
      this.selectedValue = this.options[0]?.key || ''
      this.loadDetailSnapshots()
    },
    async execute(type) {
      // 0 立即处置 1 稍后处置 2 误报
      if (!this.details.alarmId || this.loading) return
      if (type === 1) {
        this.close()
        return
      }
      if (type === 2) {
        try {
          await this.$secondaryConfirm({
            title: '误报',
            message: '是否确认为误报',
            confirmText: '确认',
            cancelText: '取消'
          })
        } catch (error) {
          return
        }
      }
      const alarm = { ...this.details }
      const disposalStatus = type === 2 ? 'FALSE_ALARM' : 'IMMEDIATE_DISPOSAL'
      this.loading = true
      try {
        const response = await executeAlarm({ ...alarm, disposalStatus })
        if (response?.success === false) {
          throw new Error(response.message || '告警处置失败')
        }
        this.removeAlarm(alarm)
        this.workflowQueue = this.workflowQueue.filter(item => String(item.alarmId) !== String(alarm.alarmId))
        this.resetDialog()
        if (type === 0) {
          this.$refs.warningExecuteRef.open(alarm.alarmId)
        } else {
          this.$message.success('已标记为误报')
        }
        this.openNextWorkflowAlarm()
      } catch (error) {
        this.$message.error(error?.message || '告警处置失败')
      } finally {
        this.loading = false
      }
    },
    resetDialog() {
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
      this.warningVisible = false
      this.dialogVisible = false
      this.manualOpen = false
      this.show = false
      this.details = {}
      this.selectedValue = ''
      this.options = []
      this.snapshotObjectUrls = {}
    },
    openNextWorkflowAlarm() {
      if (this.dialogVisible || this.warningVisible || this.details.alarmId) return
      const next = this.workflowQueue[0]
      if (next) this.open(next)
    },
    handleChangeSelect(name) {
      this.$refs.carouselRef.setActiveItem(name)
    },
    handleChangeCarousel(index) {
      this.selectedValue = this.options[index].key
    },
    snapshotImageSrc(key) {
      return this.snapshotObjectUrls[key] || ''
    },
    async loadDetailSnapshots() {
      const seq = ++this.snapshotLoadSeq
      this.snapshotObjectUrls = {}
      const keys = this.options.map(item => item.key)
      if (!keys.length) return
      const nextUrls = await loadSnapshotObjectUrls(this.details?.snapshotUrl, this.details, keys)
      if (seq !== this.snapshotLoadSeq) return
      this.snapshotObjectUrls = nextUrls
    }
  },
  watch: {
    robotAlarmObj: {
      handler(newVal) {
        if (!newVal || this.isWorkflowAlarm) return
        // 手动打开：不跟 store 生命周期绑定
        if (this.manualOpen) return
        const alarms = Object.values(newVal).filter(Boolean)
        const currentExists = alarms.some(item => String(item.alarmId) === String(this.details.alarmId))
        if (this.details.alarmId && !currentExists) {
          this.resetDialog()
          this.openNextWorkflowAlarm()
          return
        }
        if (!this.details.alarmId && alarms[0]) this.open(alarms[0])
      },
      immediate: true,
      deep: true
    },
    details: {
      handler(newVal) {
        if (!newVal.alarmId && this.dialogVisible) this.dialogVisible = false
        // 先屏蔽
        // this.dialogVisible = newVal.alarmId !== undefined
        // if (newVal.alarmId && !this.timer) {
        //   this.timer = setInterval(() => {
        //     this.show = !this.show
        //   }, 100)
        // } else {
        //   if (this.timer) {
        //     clearInterval(this.timer)
        //   }
        // }
      },
      deep: true
    },
    selectedRobot: {
      handler(newVal) {
        if (!newVal) return
        this.selectedRobot = newVal
      },
      deep: true
    },
    dialogVisible: {
      handler(newVal) {
        if (newVal) {
          // 自动弹出一律开边框流光；手动打开不加动画
          this.show = !this.manualOpen
        } else {
          this.show = false
        }
      }
    },
    workflowAlarms: {
      handler(items) {
        this.workflowQueue = (items || []).filter(item => !this.deferredAlarmIds.has(String(item.alarmId)))
        if (this.isWorkflowAlarm && !this.workflowQueue.some(item => String(item.alarmId) === String(this.details.alarmId))) {
          this.resetDialog()
        }
        this.openNextWorkflowAlarm()
      },
      immediate: true,
      deep: true
    }
  }
}
</script>

<style lang="scss" scoped>
@import "./scss/warning-info.scss";
::v-deep .el-dialog {

  position: unset !important;
}
.execute-dialog1 {
  ::v-deep .el-dialog {
    position: unset !important;
    margin-top: 0 !important;
    background: transparent;
    border: none;
    .el-dialog__header {
      display: none;
    }
    .el-dialog__body {
      height: 136px;
      padding: 0 !important;
    }
  }
}
.custom-warning {
  border-radius: 2px;
  /* primary：蓝色 */
  border: 1px solid #1C9DFF;
  background: rgba(9, 45, 72, 0.5);
  -webkit-box-shadow: 0 0 20px 0 #2575AA inset;
  box-shadow: 0 0 20px 0 #2575AA inset;
  backdrop-filter: blur(5px);
  animation: pulseZoom 2s ease-in-out 1;
  animation-fill-mode: forwards;

  &.red {
    border: 1px solid #FF0202;
    background: rgba(72, 9, 9, 0.5);
    -webkit-box-shadow: 0 0 20px 0 #B30000 inset;
    box-shadow: 0 0 20px 0 #B30000 inset;
  }

  &.orange {
    border: 1px solid #FF7100;
    background: rgba(108, 60, 17, 0.5);
    -webkit-box-shadow: 0 0 20px 0 #FF7100 inset;
    box-shadow: 0 0 20px 0 #FF7100 inset;
  }

  &.primary {
    border: 1px solid #1C9DFF;
    background: rgba(9, 45, 72, 0.5);
    -webkit-box-shadow: 0 0 20px 0 #2575AA inset;
    box-shadow: 0 0 20px 0 #2575AA inset;
  }
}

/* 关键帧定义：scale 从 0.5 → 1 → 0.5 → 1 → 0.5 */
@keyframes pulseZoom {
  0% {
    opacity: 0;
    transform: scale(0);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
  67% {
    opacity: 0.6;
    transform: scale(0.8);
  }
  84% {
    opacity: 1;
    transform: scale(1);
  }
  100% {
    opacity: 0.6;
    transform: scale(0.8);  /* 最终回到 1 */
  }
}

</style>
