<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="异常报告"
    >
    <!-- v-dialogDrag -->
    <!-- 612 597 -->
    <template slot="footer"></template>
    <div class="custom-modal-container warning-batch-container">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">告警快速处理列表</div>
          <div class="close mr10" @click="dialogVisible = false">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content p10 flex">
          <div class="flex1 task">
            <div class="waning-imgs">
              <div class="title flx-justify-between">
                <div class="second-title">告警画面</div>
                <div class="select-list">
                  <el-select
                    v-model="selectedValue"
                    placeholder="请选择"
                    class="custom-select warning-modal-select"
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
              <div class="list-box">
                <div class="mt10" style="width: 576px; height: 324px; border: 0.5px solid #1665A2; background: #001D46;">
                  <el-carousel trigger="click" :autoplay="false" height="100%" ref="carouselRef" @change="handleChangeCarousel">
                    <el-carousel-item v-for="item in options" :key="item.key" :name="item.key">
                      <div v-if="item.url" class="img-b w100 h100">
                        <img :src="getImageSrc(item.url)" alt="">
                        <!-- <img src="@/assets/images/new-bi/video-bg.png" class="w100 h100" alt=""> -->
                      </div>
                      <div v-else class="w100 h100 flx-center">暂无{{item.label}}图片</div>
                    </el-carousel-item>
                    <div class="page">{{ getCurrentPage() }}/{{ options.length }}</div>
                    <div class="download" @click="download()" style="cursor: pointer;">下载原图</div>
                  </el-carousel>
                </div>
              </div>
            </div>
            <div class="mt20 details">
              <div class="second-title">告警详情</div>
              <div class="mt10">
                <div class="flex">
                  <div class="item flex1">
                    <span class="name">告警时间：</span>
                    <span class="value">{{ details.eventTime }}</span>
                  </div>
                  <div class="item flex1 pl30">
                    <span class="name" style="width: 70px">告警内容：</span>
                    <span class="value flex1">{{ details.title }}</span>
                  </div>
                </div>
                <div class="flex mt10">
                  <div class="item flex1">
                    <span class="name">告警类型：</span>
                    <span class="value">{{ details.categoryName }}</span>
                  </div>
                  <div class="item flex1 pl30" style="align-items: flex-start">
                    <span class="name">装备名称：</span>
                    <span class="value">{{ details.deviceName }}</span>
                  </div>
                </div>
                <div class="flex mt10">
                  <div class="item flex1">
                    <span class="name">严重等级：</span>
                    <span class="status" :class="{ error: details?.level?.toLowerCase() === 'high', orange: details?.level?.toLowerCase() === 'medium', green: details?.level?.toLowerCase() === 'low' }">{{ details.levelName || '-'}}</span>
                  </div>
                  <div class="item flex1 pl30">
                    <span class="name">执行任务：</span>
                    <span class="value">{{ details.taskName || '-' }}</span>
                  </div>
                </div>
              </div>
              <div class="btns mt20 mr46 flx-align-center">
                <el-button type="primary" class="flex1" @click="execute(0)">立即处置</el-button>
                <el-button type="primary" class="flex1 success" @click="execute(1)">稍后处置</el-button>
                <el-button type="primary" class="flex1 error" @click="execute(2)">误报屏蔽</el-button>
              </div>
            </div>
          </div>
          <div class="flex1 task">
            <div class="second-title">告警列表</div>
            <div class="mt10">
              <div class="custom-search-div">
                <el-input
                  placeholder="请输入内容"
                  v-model="searchValue"
                  clearable
                  @keyup.enter.native="handleChangeTab(tabIndex)"
                  @clear="handleChangeTab(tabIndex)"
                >
                  <svg-icon slot="prefix" icon-class="search"></svg-icon>
                </el-input>
              </div>
              <div class="flx-justify-between mt10">
                <div class="custom-tab-button warning-tab-button flex">
                  <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="handleChangeTab(item.value)">{{ item.label }}</div>
                </div>
              </div>
              <div class="list-box mt10 pr8 hp423">
                <div v-for="(item, index) in warningInfo.listData" :key="item.alarmId" class="item wp280 pt9 pr10 pb9 pl10 flx-justify-between" :class="{ selected: warningInfo.selectedRobotRows.includes(item) }" @click="handleClickWarningRow(item, index)">
                  <div class="flx-align-center w100">
                    <div class="img">
                      <img src="@/assets/images/new-bi/video-bg.png" alt="" class="w100 h100">
                    </div>
                    <div class="ml10 flex1">
                      <div class="flx-justify-between flx-align-start">
                        <div class="status" :class="{'success': item.categoryName === '业务告警', 'primary': item.categoryName === '任务告警', 'primary-light': item.categoryName === '设备告警'}">
                          {{ item.categoryName }}
                        </div>
                        <div class="date">{{ item.eventTime?.split(' ')?.[0] || '-' }}</div>
                      </div>
                      <div class="flx-justify-between">
                        <div class="info mt5">{{ item.title }}</div>
                        <div class="selected-symbol">
                          <svg-icon icon-class="success" style="font-size: 16px;"></svg-icon>
                        </div>
                      </div>
                      <div class="flx-align-center mt4 address"> 
                        <svg-icon icon-class="address"></svg-icon>
                        <span class="ml10">{{ item?.location?.address || '暂无位置信息' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <WarningExecute ref="warningExecuteRef" @close="close" />
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
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
import WarningExecute from './WarningExecute.vue';
import { mapActions, mapState } from 'vuex';
import { executeAlarm } from '../../../../../api/media.js';
import { previewImageBlob } from '../../../../../api/new-bi.js';
export default {
  name: 'WarningInfoBatch',
  components: { WarningExecuteNo, WarningExecuteError, WarningExecute },
  data() {
    return {
      dialogVisible: false,
      details: {},
      selectedValue: 'visible',
      options: [],
      loading: false,
      searchValue: '',
      tabList: [
        {
          label: '全部',
          value: 0
        },
        {
          label: '高风险',
          value: 1
        },
        {
          label: '中风险',
          value: 2
        },
        {
          label: '低风险',
          value: 3
        }
      ],
      tabIndex: 0,
      selectedAll: false,
      warningInfo: {
        listData: [
          // {
          //   name: '绝影X30PRO',
          //   battery: 60,
          //   type: 0,
          //   info: '车辆识别',
          //   status: 0,
          // },
          // {
          //   name: '绝影002',
          //   battery: 60,
          //   type: 2,
          //   info: '车辆识别',
          //   status: 0,
          // },
          // {
          //   name: '绝影003',
          //   battery: 60,
          //   type: 3,
          //   info: '车辆识别',
          //   status: 1,
          // },
          // {
          //   name: '绝影004',
          //   battery: 30,
          //   type: 1,
          //   info: '车辆识别',
          //   status: 0,
          // },
          // {
          //   name: '绝影005',
          //   battery: 30,
          //   type: 3,
          //   info: '车辆识别',
          //   status: 0,
          // },
          // {
          //   name: '绝影006',
          //   battery: 30,
          //   type: 1,
          //   info: '车辆识别',
          //   status: 0,
          // },
          // {
          //   name: '绝影007',
          //   battery: 30,
          //   type: 1,
          //   info: '车辆识别',
          //   status: 0,
          // }
        ],
        count: 0,
        selectedRobotRows: [],
      },
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['alarmsData']),
  },
  mounted() {},
  watch: {
    alarmsData: {
      handler(newVal, oldVal) {
        if (Object.keys(newVal).length) {
          this.handleChangeTab(this.tabIndex)
        }
      },
      deep: true
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotAlarmInfo']),
    open(alarms) {
      if (this.dialogVisible) return
      this.dialogVisible = true
      this.handleChangeTab(0)
    },
    handleChangeTab(tabIndex) {
      this.tabIndex = tabIndex
      this.selectedAll = false
      const searchValue = this.searchValue.toString()
      this.warningInfo.listData = (tabIndex === 0 ? [
        ...(this.alarmsData.high?.items || []),
        ...(this.alarmsData.medium?.items || []),
        ...(this.alarmsData.low?.items || [])
      ] : [
        ...(this.alarmsData[tabIndex === 1 ? 'high' : tabIndex === 2 ? 'medium' : 'low']?.items || [])
      ]).filter(item => item.title.includes(searchValue) || item.categoryName.includes(searchValue) || item?.location?.address.includes(searchValue))
      const { alarmId } = this.details
      if (this.warningInfo.listData.length) {
        const obj = alarmId ? (this.warningInfo.listData.find(item => item.alarmId === alarmId) || this.warningInfo.listData[0]) : this.warningInfo.listData[0]              
        this.details = obj
        this.warningInfo.selectedRobotRows = [this.details]
        // 图片
        if (this.details.alarmId) {
          this.selectedValue = defaultOptions[0].key
          this.options = [...defaultOptions].map(item => ({
            ...item,
            url: this.details?.snapshotUrl?.[item.key] || '',
            t: new Date().getTime()
          }))
        }
      } else {
        this.details = {}
        this.warningInfo.selectedRobotRows = []
        this.selectedValue = ''
        this.options = [...defaultOptions]
      }
    },
    getCurrentPage() {
      const index = this.options.findIndex(item => item.key === this.selectedValue)
      return index === -1 ? 0 : index + 1
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
    },
    handleClickWarningRow(item, index) {
      // 单选
      this.warningInfo.selectedRobotRows = [item];
      this.details = item
      if (this.details.alarmId) {
        this.selectedValue = defaultOptions[0].key
        this.options = [...defaultOptions].map(item => {
          item.url = this.details?.snapshotUrl?.[item.key] || ''
          return item
        })        
      }
    },
    async execute(type) {
      if (!this.details.alarmId) return
      // 0 立即处置 1 稍后处置 2 误报
      switch (type) {
        case 0:
          await executeAlarm({ alarmId: this.details.alarmId, disposalStatus: 'IMMEDIATE_DISPOSAL' })
          this.setRobotAlarmInfo({ robotId: this.details.robotId, alarmInfo: this.details, close: true });
          this.$refs.warningExecuteRef.open(this.details.alarmId)
          // this.details = {}
          break;
        case 1:
          // this.$refs.warningExecuteNoRef.open(this.details.alarmId) // 无需处置
          // this.details = {}
          break;
          default:
          this.$refs.warningExecuteErrorRef.open(this.details.alarmId)
          this.setRobotAlarmInfo({ robotId: this.details.robotId, alarmInfo: this.details, close: true });
          break;
      }
    },
    async download() {
      const url = this.options.find(item => item.key === this.selectedValue)?.url || ''
      try {
        const res = await previewImageBlob(url)
        const blob = new Blob([res], {type: this.getMimeType(res.type) || ''});
        // 创建 Blob URL
        const blobUrl = window.URL.createObjectURL(blob);

        // 创建下载链接
        const link = document.createElement('a');
        link.href = blobUrl;
        // 设置文件名
        link.download = new Date().getTime() + '.jpg';
        document.body.appendChild(link);

        // 触发下载
        link.click();

        // 移除链接并释放 Blob URL
        document.body.removeChild(link);
        window.URL.revokeObjectURL(blobUrl);
      } catch (error) {
        this.$message.error('下载失败')
      }
    },
    getMimeType(extension) {
      const mimeTypes = {
        pdf: 'application/pdf',
        txt: 'text/plain',
        doc: 'application/msword',
        docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        xls: 'application/vnd.ms-excel',
        xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        png: 'image/png',
        jpg: 'image/jpeg',
        jpeg: 'image/jpeg',
        gif: 'image/gif',
        csv: 'text/csv',
        json: 'application/json',
        zip: 'application/zip',
        rar: 'application/x-rar-compressed'
        // 更多 MIME 类型可以根据需求添加
      };
      // 默认类型
      return mimeTypes[extension] || 'application/octet-stream';
    },
    close() {
      this.dialogVisible = false
      this.warningInfo.listData = []
      this.warningInfo.count = 0
      this.warningInfo.selectedRobotRows = []
      this.details = {}
      this.selectedValue = ''
    }
  }
}
</script>

<style lang="scss" scoped>
@import "./scss/warning-batch.scss";
</style>
