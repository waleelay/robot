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
                        <!-- <img :src="getImageSrc(item.url)" alt=""> -->
                        <img src="@/assets/images/new-bi/video-bg.png" class="w100 h100" alt="">
                      </div>
                      <div v-else class="w100 h100 flx-center">暂无{{item.label}}图片</div>
                    </el-carousel-item>
                    <div class="page">{{ getCurrentPage() }}/{{ options.length }}</div>
                    <div class="download">下载原图</div>
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
                    <span class="status">{{ details.levelName || '-'}}</span>
                  </div>
                  <div class="item flex1 pl30">
                    <span class="name">执行任务：</span>
                    <span class="value">{{ details.taskName }}</span>
                  </div>
                </div>
              </div>
              <div class="btns mt20 mr46 flx-align-center">
                <el-button type="primary" class="flex1">预案处置</el-button>
                <el-button type="primary" class="flex1 success" @click="$refs.warningExecuteNoRef.open(details.ErrorId)">一键处置</el-button>
                <el-button type="primary" class="flex1 error" @click="$refs.warningExecuteErrorRef.open(details.ErrorId)">误报屏蔽</el-button>
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
                  @keyup.enter.native="handleQuery"
                  @clear="handleQuery"
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
                        <span class="ml10">{{ item.location }}</span>
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
      <WarningExecuteError ref="warningExecuteErrorRef" @getAllData="getAllData" />
      <WarningExecuteNo ref="warningExecuteNoRef" @getAllData="getAllData" />
  </el-dialog>
</template>

<script>
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
export default {
  name: 'WarningInfoBatch',
  components: { WarningExecuteNo, WarningExecuteError },
  data() {
    return {
      dialogVisible: false,
      details: {
        dogName: '巡逻机器狗-01',
        MapName: '总装车间一层',
        CreateTime: '2024-11-18 14:30:25',
        ErrorType: '',
        // 告警内容
        ErrorName: '',
        level: '严重',
        // latitude: 30.7453550,
        // longitude: 106.0376233
        latitude: '',
        longitude: ''
      },
      reason: '',
      selectedValue: '',
      options: [
        { key: 'ImageUrl', label: '云台可见光', url: '1' },
        { key: 'nirUrl', label: '云台红外光', url: '2' },
        { key: 'beforeUrl', label: '机器狗前置', url: '3' },
        { key: 'afterUrl', label: '机器狗后置', url: '4' }
      ],
      errorInfo: [
        [
          { label: "负责机器", key: "dogName" },
          { label: "发现异常时间", key: "CreateTime" },
          { label: "地图名称", key: "MapName" },
          { label: "点位名称", key: "pointName" },
        ], [
          { label: "异常类型", key: "errorName" },
          { label: "紧急情况", key: "errorType" },
          { label: "最高温度", key: "FMaxTemperature" },
          { label: "是否处理", key: "errorDeal" },

        ]
      ],
      errorData: {
        ErrorId: "",
        dogName: "1111111",
        CreateTime: "aaaaaaaaaa",
        MapName: "map",
        pointName: "point",
        errorName: "火灾异常",
        errorType: "紧急",
        errorDeal: false,
        isDeal: 0,
        FMaxTemperature: 90
      },
      dialogImageUrl: '',
      dialogIRUrl: '',
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
          {
            name: '绝影X30PRO',
            battery: 60,
            type: 0,
            info: '车辆识别',
            status: 0,
          },
          {
            name: '绝影002',
            battery: 60,
            type: 2,
            info: '车辆识别',
            status: 0,
          },
          {
            name: '绝影003',
            battery: 60,
            type: 3,
            info: '车辆识别',
            status: 1,
          },
          {
            name: '绝影004',
            battery: 30,
            type: 1,
            info: '车辆识别',
            status: 0,
          },
          {
            name: '绝影005',
            battery: 30,
            type: 3,
            info: '车辆识别',
            status: 0,
          },
          {
            name: '绝影006',
            battery: 30,
            type: 1,
            info: '车辆识别',
            status: 0,
          },
          {
            name: '绝影007',
            battery: 30,
            type: 1,
            info: '车辆识别',
            status: 0,
          }
        ],
        count: 3,
        selectedTaskRows: [],
        selectedRobotRows: [],
      },
      alarms: {}
    }
  },
  mounted() {  },
  methods: {
    open(alarms) {
      if (this.dialogVisible) return
      this.dialogVisible = true
      this.alarms = alarms
      this.warningInfo.listData = [
        ...(alarms.high?.items || []),
        ...(alarms.medium?.items || []),
        ...(alarms.low?.items || [])
      ]
      this.details = this.warningInfo.listData[0] || {}
      // 图片
      this.selectedValue = this.options[0].key
    },
    handleChangeTab(tabIndex) {
      this.tabIndex = tabIndex
      this.selectedAll = false
      this.warningInfo.selectedRobotRows = []
      this.warningInfo.listData = tabIndex === 0 ? [
        ...(this.alarms.high?.items || []),
        ...(this.alarms.medium?.items || []),
        ...(this.alarms.low?.items || [])
      ] : [
        ...(this.alarms[tabIndex === 1 ? 'high' : tabIndex === 2 ? 'medium' : 'low']?.items || [])
      ]
    },
    getCurrentPage() {
      const index = this.options.findIndex(item => item.key === this.selectedValue)
      return index === -1 ? 0 : index + 1
    },
    typeFormat(dictKey, value) {
      return this.selectDictLabel(this.dict.type[dictKey], value);
    },
    handleChangeSelect(name) {
      this.$refs.carouselRef.setActiveItem(name)
    },
    handleChangeCarousel(index) {
      this.selectedValue = this.options[index].key
    },
    formatTemperature(temp) {
      return `${Math.floor(temp)}℃`
    },
    getImageSrc(imgUrl) {
      return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${imgUrl}`;
    },
    handleQuery() {
      if (this.searchValue) {
        const arr = [
          ...(this.alarms.high?.items || []),
          ...(this.alarms.medium?.items || []),
          ...(this.alarms.low?.items || [])
        ].filter(item => item.title.includes(this.searchValue) || item.categoryName.includes(this.searchValue) || item.location.includes(this.searchValue))
        this.warningInfo.listData = arr
        this.warningInfo.selectedRobotRows = []
      } else {
        this.handleChangeTab(this.tabIndex)
      }
    },
    handleClickWarningRow(item, index) {
      // 单选
      this.warningInfo.selectedRobotRows = [item];
      this.details = item
      // this.selectedValue = item.name
    },
    getAllData() {}
  }
}
</script>

<style lang="scss" scoped>
@import "./scss/warning-batch.scss";
</style>
