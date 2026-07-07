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
    <div class="flx-center wp274 hp136 custom-warning">
      <svg-icon icon-class="warning" style="font-size: 76px; color: #FFDD00"></svg-icon>
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
    <div class="custom-modal-container warning-batch-container error" :class="{ 'error-light': show }">
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
          <div class="close mr10" @click="details = {}">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content p20 flex" style="position: relative; z-index: 2">
          <div class="flex1">
            <div class="tuxiang">
              <div class="image-box">
                <div class="title flx-justify-between">
                  <div class="text">告警画面</div>
                  <div class="select-list" style="display: none">
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
                          <!-- <span class="ml10">{{ item.label }}</span> -->
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
                    <el-carousel trigger="click" :autoplay="false" height="100%" ref="carouselRef" @change="handleChangeCarousel">
                      <el-carousel-item v-for="item in options" :key="item.key" :name="item.key">
                        <div v-if="item.url" class="img">
                          <img :src="getImageSrc(item.url)" alt="">
                        </div>
                        <div v-else class="w100 h100 flx-center">暂无{{item.label}}图片</div>
                      </el-carousel-item>
                    </el-carousel>
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
                    <span class="value flex1 tar">{{ details.title }}</span>
                  </div>
                  <!-- <div class="item flx-justify-between mt20">
                    <span class="name">告警类型：</span>
                    <span class="value">{{ typeFormat('qh_alarm_record_type', details.ErrorType) || '-' }}</span>
                  </div> -->
                  <div class="item flx-justify-between mt20">
                    <span class="name">严重等级：</span>
                    <span class="status" :class="{ error: details?.level?.toLowerCase() === 'high', orange: details?.level?.toLowerCase() === 'medium', green: details?.level?.toLowerCase() === 'low' }">{{ details.levelName || '高风险' }}</span>
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
const defaultOptions = [
  { key: 'visible', label: '云台可见光', url: '1' },
  { key: 'thermal', label: '云台红外光', url: '2' },
  { key: 'front', label: '前置摄像头', url: '3' }
]
import Gis from './../../../../largeScreen/dialog/gis.vue';
import WarningExecute from './WarningExecute.vue';
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
import { mapState, mapActions } from 'vuex';
import { executeAlarm } from '../../../../../api/media.js';
export default {
  name: 'WarningInfo',
  dicts: ['qh_alarm_record_type'],
  components: {
    Gis,
    WarningExecute,
    WarningExecuteNo,
    WarningExecuteError
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo', 'robotAlarmObj']),
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    },
    currenAlarm() {
      return 
    }
  },
  data() {
    return {
      dialogVisible: false,
      warningVisible: false,
      details: {
        location: {
          lat: 30.7453550,
          longitude: 106.0376233
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
      show: false
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotAlarmInfo']),
    close() {
      this.details = {}
    },
    open(data) {
      if (this.dialogVisible || this.warningVisible) return
      this.loading = false
      this.details = {
        ...data
      }
      this.selectedValue = defaultOptions[0].key
      this.options = [...defaultOptions].map(item => ({
        ...item,
        url: this.details?.snapshotUrl?.[item.key] || '',
        t: new Date().getTime()
      }))
      this.warningVisible = true
      this.timer = setTimeout(() => {
        this.warningVisible = false
        this.dialogVisible = true
      }, 2000)
    },
    async execute(type) {
      // 0 立即处置 1 稍后处置 2 误报
      switch (type) {
        case 0:
          await executeAlarm({ alarmId: this.details.alarmId, disposalStatus: 'IMMEDIATE_DISPOSAL' })
          this.setRobotAlarmInfo({ robotId: this.details.robotId, alarmInfo: this.details, close: true });
          this.details = {}
          this.$refs.warningExecuteRef.open(this.details.alarmId)
          break;
        case 1:
          // this.$refs.warningExecuteNoRef.open(this.details.alarmId) // 无需处置
          this.details = {}
          break;
          default:
          this.$refs.warningExecuteErrorRef.open(this.details.alarmId)
          this.setRobotAlarmInfo({ robotId: this.details.robotId, alarmInfo: this.details, close: true });
          break;
      }
    },
    handleChangeSelect(name) {
      this.$refs.carouselRef.setActiveItem(name)
    },
    handleChangeCarousel(index) {
      this.selectedValue = this.options[index].key
    },
    getImageSrc(url) {
      const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
      return `${preUrl}${url}`
    }
  },
  watch: {
    robotAlarmObj: {
      handler(newVal) {
        if (!newVal) return        
        const { alarmId } = this.details
        if (!alarmId) {
          this.details = { ...Object.values(newVal)[0] || {} }
          this.selectedValue = defaultOptions[0].key
          this.options = [...defaultOptions].map(item => ({
            ...item,
            url: this.details?.snapshotUrl?.[item.key] || '',
            t: new Date().getTime()
          }))
        } else if (!newVal[alarmId]) {
          this.details = {}
          this.selectedValue = ''
          this.options = [...defaultOptions]
        }
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
          this.show = true
          // this.timer = setInterval(() => {
            //   this.show = !this.show
            // }, 300)
          } else {
            this.show = false
          // if (this.timer) {
          //   clearInterval(this.timer)
          // }
        }
      }
    }
  },
  beforeDestroy() {
    if (this.timer) {
      clearTimeout(this.timer)
      this.timer = null
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
  border: 1px solid #FF0202;
  background: rgba(72, 9, 9, 0.5);
  -webkit-box-shadow: 0 0 20px 0 #B30000 inset;
  box-shadow: 0 0 20px 0 #B30000 inset;
  backdrop-filter: blur(5px);
  animation: pulseZoom 2s ease-in-out 1;
  /* 保持最后状态（默认 forwards，也可以不加，但明确语义） */
  animation-fill-mode: forwards;
  /* 让动画在播放完毕后保留最后一帧（scale(0.5)） 但示例中是来回两次，最终回到0.5，符合需求 */
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
