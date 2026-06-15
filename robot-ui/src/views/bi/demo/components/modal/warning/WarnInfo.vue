<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-03-31 17:05:58
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\modal\warning\WarnInfo.vue
 * @Version: 
-->
<template>
  <el-dialog
    class="dialog-wrapper flx-align-center"
    v-dialogDrag
    width="1094px"
    height="632px"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="异常报告"
  >
    <div class="dialog-content d-flex">
      <div class="tuxiang">
        <div class="image-box">
          <div class="title flx-justify-between">
            <div class="text">告警画面</div>
            <div class="select-list">
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
            <div class="mt10" style="width: 640px; height: 355px; border: 0.5px solid #1665A2; background: #001D46;">
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
            <Gis :centerPoint="[details.latitude, details.longitude]" style="height: 60px !important; border-radius: 4px 4px 0 0;" />
            <div class="flx-align-center ml17 mt6">
              <svg-icon icon-class="address" style="font-size: 14px; color: #fff" />
              <span class="ml10" style="color: #FFF;font-family: Inter;font-size: 12px;line-height: 15px;">{{ details.longLatName || '暂无位置信息' }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="warning ml20">
         <div class="title">
          <div class="text">告警详情</div>
         </div>
         <div class="mt10 detail p20">
          <div class="desc">
            <div class="item flx-justify-between">
              <span class="name">设备名称：</span>
              <span class="value">{{ details.dogName }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">地图名称：</span>
              <span class="value">{{ details.MapName }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">告警时间：</span>
              <span class="value">{{ details.CreateTime }}</span>
            </div>
            <div class="item flx-justify-between mt20" style="align-items: flex-start">
              <span class="name" style="width: 70px">告警内容：</span>
              <span class="value flex1">{{ details.ErrorName }}</span>
            </div>
            <!-- <div class="item flx-justify-between mt20">
              <span class="name">告警类型：</span>
              <span class="value">{{ typeFormat('qh_alarm_record_type', details.ErrorType) || '-' }}</span>
            </div> -->
            <div class="item flx-justify-between mt20">
              <span class="name">严重等级：</span>
              <span class="status">{{ details.level || '严重' }}</span>
            </div>
          </div>
          <div class="title mt32 with-b-t pt20">
            <div class="text">告警处置</div>
          </div>
          <div class="operation mt20">
            <div class="flx-justify-between" @click="execute(0)">
              <div class="flx-justify-center">
                <img src="@/assets/images/bi/new/file.svg" alt="">
                <div class="text ml10">预案处置</div>
              </div>
              <img src="@/assets/images/bi/new/right.svg" alt="" style="font-size: 14px;">
            </div>
            <div class="flx-justify-between success mt15" @click="execute(1)">
              <div class="flx-justify-center">
                <img src="@/assets/images/bi/new/file.svg" alt="">
                <div class="text ml10">确认处置</div>
              </div>
              <img src="@/assets/images/bi/new/right.svg" alt="" style="font-size: 14px;">
            </div>
            <div class="flx-justify-between danger mt15" @click="execute(2)">
              <div class="flx-justify-center">
                <img src="@/assets/images/bi/new/file.svg" alt="">
                <div class="text ml10">误报</div>
              </div>
              <img src="@/assets/images/bi/new/right.svg" alt="" style="font-size: 14px;">
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
import { setErrorisDeal } from '@/api/cruise/resultandError'
import Gis from './../../../../../largeScreen/dialog/gis.vue';
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
export default {
  name: 'WarningInfo',
  dicts: ['qh_alarm_record_type'],
  components: {
    Gis,
    WarningExecuteNo,
    WarningExecuteError
  },
  computed: {
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    }
  },
  data() {
    return {
      disabled: false,
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
      loading: false
    }
  },
  mounted() {  },
  watch: {
    //监听到有异常就去再次访问这个列表
    firePersonError: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length - 1];
          if (message) {
            // this.getWarnList()
            this.$store.dispatch('websocket/getRefreshWarningTime')
            this.handleErrorMsg(message)
          }
        }
      },
      immediate: true,
    }
  },
  methods: {
    open(data, disabled) {
      // this.disabled = disabled
      if (this.dialogVisible) return
      this.loading = false
      this.details = {
        ...data,
        level: '严重',
        latitude: data.latitude || '',
        longitude: data.longitude || ''
      }
      this.options[0].url = data.ImageUrl || ''
      this.options[1].url = data.nirUrl || ''
      this.options[2].url = data.beforeUrl || ''
      this.options[3].url = data.afterUrl || ''
      this.selectedValue = this.options[0].key
      this.dialogVisible = true
    },
    typeFormat(dictKey, value) {
      return this.selectDictLabel(this.dict.type[dictKey], value);
    },
    execute(type) {
      // 0 预案处置 1 无需处置 2 误报
      switch (type) {
        case 0:
          console.log('预案处置');
          this.dialogVisible = false
          this.$store.dispatch('extra/setRouteDetails', { key: 'xlxc', row: this.details })
          break;
        case 1:
          this.reason = ''
          this.$refs.warningExecuteErrorRef.open(this.details.ErrorId)
          break;
        default:
          this.$refs.warningExecuteNoRef.open(this.details.ErrorId)
          break;
      }
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
    handleSwitch(value) {
      let query = {
        ErrorId: this.errorData.ErrorId,
        IsDeal: value ? 1 : 0
      }
      setErrorisDeal(query).then(res => {
        this.$message.success('成功切换异常状态')
        // this.getWarnList()
        this.$store.dispatch('websocket/getRefreshWarningTime')
        this.dialogVisible = false
      }).catch(() => {
        this.errorData.errorDeal = !query.IsDeal
      })
    },
    getImageSrc(imgUrl) {
      return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${imgUrl}`;
    },
    //websocket开启弹窗S
    handleErrorMsg(message) {
      if (this.dialogVisible) return
      this.dialogVisible = true
      const obj = {
        // message全是驼峰
        dogName: message.dogName,
        TaskName: message.taskName,
        MapName: message.mapName,
        PointName: message.pointName,
        ErrorName: message.errorName,
        ErrorType: message.errorType,
        FMaxTemperature: message.errorName === '火灾异常' ? this.formatTemperature(message.FMaxTemperature) : null,
        CreateTime: message.createTime,
        ErrorId: message.errorId,
        latitude: message.latitude || 30.7453550,
        longitude: message.longitude || 106.0376233,
        // 地点名
        longLatName: message.longLatName
      }
      this.options[0].url = message.imageUrl || ''
      this.options[1].url = message.nirUrl || ''
      this.options[2].url = message.beforeUrl || ''
      this.options[3].url = message.afterUrl || ''
      this.details = obj
      this.errorData = obj
      this.dialogImageUrl = message.imageUrl
      this.dialogIRUrl = message.nirUrl

      if (this.timer) {
        clearTimeout(this.timer);
      }
      // 三秒后隐藏信息
      // this.timer = setTimeout(() => {
      //   this.dialogVisible = false
      // }, 3000);
    },
    getAllData() {
      this.dialogVisible = false
      this.$store.dispatch('websocket/getRefreshWarningTime')
      this.$emit('getAllData')
    }
  }
}
</script>

<style lang="scss" scoped>
@import "./scss/warning-info.scss";
</style>
