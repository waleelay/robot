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
                  :key="item.value"
                  :label="item.label"
                  :value="item.value">
                  
                  <div class="flx-align-center">
                    <el-radio v-model="selectedValue" :label="item.value" class="custom-radio">{{ item.label }}</el-radio>
                    <!-- <span class="ml10">{{ item.label }}</span> -->
                  </div>
                </el-option>
              </el-select>
            </div>
          </div>
          <div class="list-box mt10">
            <div class="mt10" style="width: 640px; height: 360px; border: 0.5px solid #1665A2; background: #001D46;">
              <el-carousel trigger="click" :autoplay="false" height="100%" ref="carouselRef" @change="handleChangeCarousel">
                <el-carousel-item v-for="item in options" :key="item.value" :name="item.value">
                  <img :src="item.url" alt="">
                </el-carousel-item>
              </el-carousel>
            </div>
          </div>
        </div>
        <div class="mt20 address">
          <div class="title">
            <div class="text">告警位置</div>
          </div>
          <div class="modal-map mt10" style="width: 640px; height: 83px;">
            <Gis :centerPoint="[30.745482638228058, 106.03737831115724]" />
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
              <span class="value">{{ details.deviceName }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">地图名称：</span>
              <span class="value">{{ details.mapName }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">告警时间：</span>
              <span class="value">{{ details.time }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">告警类型：</span>
              <span class="value">{{ details.warningType }}</span>
            </div>
            <div class="item flx-justify-between mt20">
              <span class="name">严重等级：</span>
              <span class="status">{{ details.level }}</span>
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
            <div class="flx-justify-between success mt20" @click="execute(1)">
              <div class="flx-justify-center">
                <img src="@/assets/images/bi/new/file.svg" alt="">
                <div class="text ml10">无需处置</div>
              </div>
              <img src="@/assets/images/bi/new/right.svg" alt="" style="font-size: 14px;">
            </div>
            <div class="flx-justify-between danger mt20" @click="execute(2)">
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
    <el-dialog
      class="execute-dialog flx-align-center"
      v-dialogDrag
      width="514px"
      height="292px"
      :visible.sync="executeDialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
      :show-close="false"
      title=""
    >
      <el-input v-model="reason" type="textarea" :rows="5" placeholder="请输入无需处置的原因" />
      <template slot="footer">
        <el-button tt="modal"  @click="executeDialogVisible = false">取消</el-button>
        <el-button tt="modal" class="ml10" @click="execute">确认</el-button>
      </template>
    </el-dialog>
    <el-dialog
      class="error-dialog flx-align-center"
      v-dialogDrag
      width="514px"
      height="164px"
      :visible.sync="errorDialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
      :show-close="false"
      title=""
    >
      <template slot="title">
        <div class="flx-align-center custom-header">
          <img src="@/assets/images/bi/new/warning-icon.png" alt="" width="24px" height="24px">
          <span class="ml10">误报</span>
        </div>
      </template>
      是否确认为误报
      <template slot="footer">
        <el-button tt="modal"  @click="errorDialogVisible = false">取消</el-button>
        <el-button tt="modal" class="ml10" @click="save">确认</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script>
import { setErrorisDeal } from '@/api/cruise/resultandError'
import Gis from './gis.vue';
export default {
  name: 'warningInfo',
  components: {
    Gis
  },
  computed: {
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    }
  },
  data() {
    return {
      dialogVisible: true,
      executeDialogVisible: false,
      errorDialogVisible: false,
      details: {
        deviceName: '巡逻机器狗-01',
        mapName: '总装车间一层',
        time: '2024-11-18 14:30:25',
        warningType: '疑似发生打架斗殴',
        level: '严重'
      },
      reason: '',
      selectedValue: '',
      options: [
        { value: '选项1', label: '黄金糕', url: '' },
        { value: '选项2', label: '双皮奶', url: '' },
        { value: '选项3', label: '蚵仔煎', url: '' },
        { value: '选项4', label: '龙须面', url: '' },
        { value: '选项5',label: '北京烤鸭', url: '' },
        { value: '选项15',label: '北京烤鸭', url: '' },
        { value: '选项52',label: '北京烤鸭', url: '' }
      ],
      errorInfo: [
        [
          { label: "负责机器", key: "dogName" },
          { label: "发现异常时间", key: "CreateTime" },
          { label: "地图名称", key: "mapName" },
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
        mapName: "map",
        pointName: "point",
        errorName: "火灾异常",
        errorType: "紧急",
        errorDeal: false,
        isDeal: 0,
        FMaxTemperature: 90
      },
      dialogImageUrl: '',
      dialogIRUrl: '',
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
    execute(type) {
      // 0 预案处置 1 无需处置 2 误报
      if (type === 0) {

      }
      switch (type) {
        case 0:
          
          break;
        case 1:
          this.reason = ''
          this.executeDialogVisible = true
          break;
        default:
          this.errorDialogVisible = true
          break;
      }
    },
    save() {},
    handleChangeSelect(name) {
      this.$refs.carouselRef.setActiveItem(name)
    },
    handleChangeCarousel(index) {
      this.selectedValue = this.options[index].value
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
      this.dialogVisible = true
      this.details = {}
      this.errorData = {
        dogName: message.dogName,
        taskName: message.taskName,
        mapName: message.mapName,
        pointName: message.pointName,
        errorName: message.errorName,
        errorType: message.errorType == 1 ? '紧急' : '普通',
        FMaxTemperature: message.errorName === '火灾异常' ? this.formatTemperature(message.FMaxTemperature) : null,
        CreateTime: message.createTime,
        ErrorId: message.errorId,
      }
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
  }
}
</script>

<style lang="scss" scoped>
@import "./warningInfo.scss";

</style>
