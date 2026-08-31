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
      <div class="box" :class="{ 'is-simple': simpleMode }">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">告警快速处理列表</div>
          <div class="close mr10" @click="close">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content p10 flex">
          <div class="task" :class="{ flex1: !simpleMode }">
            <div class="waning-imgs">
              <div class="title flx-justify-between">
                <div class="second-title">告警画面</div>
                <div v-if="options.length > 1" class="select-list">
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
                      </div>
                    </el-option>
                  </el-select>
                </div>
              </div>
              <div class="list-box">
                <div class="mt10" style="width: 576px; height: 324px; border: 0.5px solid #1665A2; background: #001D46;">
                  <el-carousel v-if="options.length" trigger="click" :autoplay="false" height="100%" ref="carouselRef" @change="handleChangeCarousel">
                    <el-carousel-item v-for="item in options" :key="item.key" :name="item.key">
                      <div class="img-b w100 h100">
                        <img v-if="snapshotImageSrc(item.key)" :src="snapshotImageSrc(item.key)" alt="">
                        <div v-else class="w100 h100 flx-center">暂无{{ item.label }}图片</div>
                      </div>
                    </el-carousel-item>
                    <div class="page">{{ getCurrentPage() }}/{{ options.length }}</div>
                    <div class="download" @click="download()" style="cursor: pointer;">下载原图</div>
                  </el-carousel>
                  <div v-else class="w100 h100 flx-center">暂无图片</div>
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
              <div class="w100 flx-justify-between btns mt20 mr46 flx-align-center">
                <el-button type="primary" class="flex1" @click="execute(0)">立即处置</el-button>
                <el-button type="primary" class="flex1 success" @click="execute(1)">稍后处置</el-button>
                <el-button type="primary" class="flex1 error" @click="execute(2)">误报屏蔽</el-button>
              </div>
            </div>
          </div>
          <div v-if="!simpleMode" class="flex1 task list-panel">
            <div class="second-title">告警列表</div>
            <div class="mt10">
              <div class="filter-panel">
                <div class="combined-filter flx-align-center">
                  <div class="type-part">
                    <el-select
                      v-model="searchType"
                      placeholder="条件"
                      popper-class="custom-select modal-search-select-popper p9"
                      @change="handleSearchTypeChange"
                    >
                      <el-option label="关键字" value="keyword" />
                      <el-option label="日期" value="date" />
                    </el-select>
                  </div>
                  <div class="value-part flex1">
                    <el-input
                      v-if="searchType === 'keyword'"
                      placeholder="请输入内容/类型/位置"
                      v-model="searchValue"
                      clearable
                      @keyup.enter.native="handleChangeTab(tabIndex)"
                      @clear="handleChangeTab(tabIndex)"
                    >
                      <svg-icon slot="prefix" icon-class="search"></svg-icon>
                    </el-input>
                    <el-date-picker
                      v-else
                      v-model="dateValue"
                      type="daterange"
                      range-separator="至"
                      start-placeholder="开始日期"
                      end-placeholder="结束日期"
                      format="yyyy-M-d"
                      value-format="yyyy-M-d"
                      clearable
                      :picker-options="pickerOptions"
                      @change="handleDateChange"
                    />
                  </div>
                </div>
              </div>
              <div class="flx-justify-between mt10">
                <div class="custom-tab-button warning-tab-button flex">
                  <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="handleChangeTab(item.value)">{{ item.label }}</div>
                </div>
              </div>
              <div class="list-box mt10 pr8 hp423">
                <div v-for="(item, index) in warningInfo.listData" :key="item.alarmId" class="item wp280 pt9 pr10 pb9 pl10 flx-justify-between" :class="{ selected: warningInfo.selectedRobotRows.includes(item) }" @click="handleClickWarningRow(item, index)">
                  <div class="flx-align-center w100">
                    <div class="img flx-center">
                      <AlarmSnapshotImage :item="item">
                        <span class="list-img-placeholder">暂无图片</span>
                      </AlarmSnapshotImage>
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
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
import WarningExecute from './WarningExecute.vue';
import AlarmSnapshotImage from '@/components/AlarmSnapshotImage.vue'
import { mapActions, mapState } from 'vuex';
import { executeAlarm } from '../../../../../api/media.js';
import {
  buildSnapshotOptions,
  downloadAlarmSnapshotFile,
  loadSnapshotObjectUrls
} from '@/utils/alarm-snapshot'
export default {
  name: 'WarningInfoBatch',
  components: { WarningExecuteNo, WarningExecuteError, WarningExecute, AlarmSnapshotImage },
  data() {
    return {
      dialogVisible: false,
      simpleMode: false,
      details: {},
      selectedValue: '',
      options: [],
      loading: false,
      searchType: 'keyword',
      searchValue: '',
      dateValue: [],
      pickerOptions: {
        disabledDate: (date) => {
          const before = `${new Date().getFullYear() - 9}-1-1 00:00:00`
          return (
            new Date(date).getTime() < new Date(before).getTime() ||
            new Date(date).getTime() > new Date().getTime()
          )
        }
      },
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
        listData: [],
        count: 0,
        selectedRobotRows: [],
      },
      snapshotObjectUrls: {},
      snapshotLoadSeq: 0,
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['alarmsData']),
    hasDateRange() {
      return Array.isArray(this.dateValue) && this.dateValue.length === 2
    }
  },
  mounted() {},
  watch: {
    alarmsData: {
      handler(newVal, oldVal) {
        if (!this.dialogVisible || this.simpleMode) return
        if (newVal && Object.keys(newVal).length) {
          this.handleChangeTab(this.tabIndex)
        }
      },
      deep: true
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['removeAlarm']),
    open({ item = null, simple = false } = {}) {
      this.simpleMode = !!simple
      this.searchType = 'keyword'
      this.searchValue = ''
      this.dateValue = []
      this.dialogVisible = true
      if (this.simpleMode && item) {
        this.applyItem(item)
        return
      }
      this.handleChangeTab(0)
      if (item?.alarmId) {
        const row = this.warningInfo.listData.find(row => row.alarmId === item.alarmId)
        if (row) this.handleClickWarningRow(row)
      }
    },
    applyItem(item) {
      this.details = { ...item }
      this.warningInfo.listData = [item]
      this.warningInfo.selectedRobotRows = [item]
      this.applySnapshotOptions(item)
    },
    applySnapshotOptions(item) {
      this.options = buildSnapshotOptions(item)
      this.selectedValue = this.options[0]?.key || ''
      this.loadDetailSnapshots()
    },
    handleChangeTab(tabIndex) {
      this.tabIndex = tabIndex
      this.selectedAll = false
      const searchValue = this.searchType === 'keyword' ? this.searchValue.toString() : ''
      this.warningInfo.listData = (tabIndex === 0 ? [
        ...(this.alarmsData.high?.items || []),
        ...(this.alarmsData.medium?.items || []),
        ...(this.alarmsData.low?.items || [])
      ] : [
        ...(this.alarmsData[tabIndex === 1 ? 'high' : tabIndex === 2 ? 'medium' : 'low']?.items || [])
      ]).filter(item => {
        if (this.searchType === 'date') {
          if (!this.hasDateRange) return true
          const t = new Date(item.eventTime).getTime()
          const startTs = new Date(`${this.dateValue[0]} 00:00:00`).getTime()
          const endTs = new Date(`${this.dateValue[1]} 23:59:59`).getTime()
          return !Number.isNaN(t) && t >= startTs && t <= endTs
        }
        return item.title.includes(searchValue)
          || item.categoryName.includes(searchValue)
          || item?.location?.address.includes(searchValue)
      })
      const { alarmId } = this.details
      if (this.warningInfo.listData.length) {
        const obj = alarmId ? (this.warningInfo.listData.find(item => item.alarmId === alarmId) || this.warningInfo.listData[0]) : this.warningInfo.listData[0]              
        this.details = obj
        this.warningInfo.selectedRobotRows = [this.details]
        if (this.details.alarmId) {
          this.applySnapshotOptions(this.details)
        }
      } else {
        this.details = {}
        this.warningInfo.selectedRobotRows = []
        this.selectedValue = ''
        this.options = []
        this.snapshotObjectUrls = {}
      }
    },
    handleSearchTypeChange() {
      this.handleChangeTab(this.tabIndex)
    },
    handleDateChange(val) {
      if (!val || (Array.isArray(val) && (val.length === 2 || val.length === 0))) {
        this.handleChangeTab(this.tabIndex)
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
    },
    handleClickWarningRow(item) {
      // 单选
      this.warningInfo.selectedRobotRows = [item]
      this.details = item
      if (this.details.alarmId) {
        this.applySnapshotOptions(this.details)
      }
    },
    async execute(type) {
      if (!this.details.alarmId || this.loading) return
      // 0 立即处置 1 稍后处置 2 误报
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
        this.removeAlarm(alarm.alarmId)
        this.close()
        if (type === 0) {
          this.$refs.warningExecuteRef.open(alarm.alarmId)
        } else {
          this.$message.success('已标记为误报')
        }
      } catch (error) {
        this.$message.error(error?.message || '告警处置失败')
      } finally {
        this.loading = false
      }
    },
    async download() {
      const key = this.selectedValue || this.options[0]?.key
      if (!key) {
        this.$message.warning('暂无可下载图片')
        return
      }
      try {
        await downloadAlarmSnapshotFile(this.details?.snapshotUrl, key, this.details)
      } catch (error) {
        this.$message.error(error?.message || '下载失败')
      }
    },
    close() {
      this.dialogVisible = false
      this.simpleMode = false
      this.searchType = 'keyword'
      this.searchValue = ''
      this.dateValue = []
      this.tabIndex = 0
      this.warningInfo.listData = []
      this.warningInfo.count = 0
      this.warningInfo.selectedRobotRows = []
      this.details = {}
      this.selectedValue = ''
      this.options = []
      this.snapshotObjectUrls = {}
    }
  }
}
</script>

<style lang="scss" scoped>
@import "./scss/warning-batch.scss";
.box.is-simple {
  width: auto !important;
}
.filter-panel {
  .combined-filter {
    height: 30px;
    border-radius: 4px;
    border: 1px solid #374E69;
    background: #111B2A;
    overflow: hidden;
    .type-part {
      flex-shrink: 0;
      width: 90px;
      height: 100%;
      border-right: 1px solid #374E69;
      ::v-deep .el-select {
        width: 100%;
        .el-input__inner {
          height: 30px;
          line-height: 30px;
          padding: 0 36px 0 10px;
          border: none;
          border-radius: 0;
          background: transparent;
          font-size: 14px;
          color: #8897AB;
        }
        .el-input__suffix {
          right: 10px;
          .el-select__caret {
            color: #8897AB;
            font-size: 16px;
          }
        }
        .el-input__icon {
          line-height: 30px;
        }
      }
    }
    .value-part {
      min-width: 0;
      height: 100%;
      ::v-deep .el-input {
        .el-input__prefix {
          left: 8px;
          color: #8897AB;
          line-height: 30px;
        }
        .el-input__inner {
          height: 30px;
          padding: 0 24px 0 30px;
          border: none;
          border-radius: 0;
          background: transparent;
          color: #fff;
          &::placeholder {
            color: #8897AB;
            font-size: 14px;
          }
        }
        .el-input__suffix {
          right: 2px;
          color: #8897AB;
        }
        .el-input__icon {
          line-height: 30px;
        }
      }
      ::v-deep .el-date-editor {
        width: 100%;
        height: 30px;
        padding: 0 8px;
        border: none;
        border-radius: 0;
        background: transparent;
        box-shadow: none;
        .el-range-input {
          background: transparent;
          color: #fff;
          font-size: 14px;
          &::placeholder {
            color: #8897AB;
          }
        }
        .el-range-separator {
          color: #8897AB;
          line-height: 30px;
          width: 16px;
          padding: 0;
        }
        .el-range__icon,
        .el-range__close-icon {
          line-height: 30px;
          color: #8897AB;
        }
      }
    }
  }
}
.list-img-placeholder {
  color: #6B8AA8;
  font-size: 12px;
}
</style>
