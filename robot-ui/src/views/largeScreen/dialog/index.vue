<template>
  <div class="contents my-map-dialog h100">
    <div class="container d-flex h100">
      <div class="side-box d-flex flex-column left d-flex h100 ovya no-w-scroll">
        <!-- hp440 -->
        <div class="card">
          <div class="content">
            <el-descriptions title=""
                             :column="2"
                             border>
              <el-descriptions-item label="装备名称"
                                    label-align="center"
                                    align="center"
                                    :span="2">{{ deviceData.deviceName }}</el-descriptions-item>
              <el-descriptions-item label="装备状态"
                                    label-align="center"
                                    align="center">{{ deviceData.onDockState ? '空闲中' : '运行中' }}</el-descriptions-item>
              <el-descriptions-item label="运动状态"
                                    label-align="center"
                                    align="center">{{ deviceData.motionState }}</el-descriptions-item>
              <el-descriptions-item label="累计里程"
                                    label-align="center"
                                    align="center">{{ deviceData.sumOdom }}KM</el-descriptions-item>
              <el-descriptions-item label="当次里程"
                                    label-align="center"
                                    align="center">{{ deviceData.curOdom }}KM</el-descriptions-item>
              <el-descriptions-item label="定位状态"
                                    label-align="center"
                                    align="center">{{ deviceData.location }}</el-descriptions-item>
              <el-descriptions-item label="电机状态"
                                    label-align="center"
                                    align="center">{{ deviceData.motorState }}</el-descriptions-item>
              <el-descriptions-item label="自主充电状态"
                                    label-align="center"
                                    align="center">{{ deviceData.chargeState }}</el-descriptions-item>
              <el-descriptions-item label="控制模式"
                                    label-align="center"
                                    align="center">{{ deviceData.controlMode }}</el-descriptions-item>
              <el-descriptions-item label="速度"
                                    label-align="center"
                                    align="center">{{ deviceData.speed }}m/s</el-descriptions-item>
              <el-descriptions-item label="电量"
                                    label-align="center"
                                    align="center">{{ deviceData.battery }}%</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
        <div class="card flex-column mt20 cjzcqk">
          <div class="title">装备告警信息</div>
          <div class="content mt10">
            <div style="min-height: 100px;">
              <div class="mm_table_box table">
                <el-table :data="gjData.tableData.data"
                          ref="dataTableRef1"
                          border
                          :class="{ 'no-data': !gjData.tableData.data.length }">
                  <el-table-column type="index"
                                   key="index"
                                   width="50"
                                   label="序号"
                                   align="center">
                    <template slot-scope="scope">
                      {{ (gjData.tableData.page - 1) * gjData.tableData.size + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="CreateTime"
                                   key="CreateTime"
                                   label="告警时间"
                                   show-overflow-tooltip></el-table-column>
                  <el-table-column prop="ErrorName"
                                   key="ErrorName"
                                   label="告警内容"
                                   show-overflow-tooltip></el-table-column>
                </el-table>
                <el-pagination v-if="gjData.tableData.total > gjData.tableData.size"
                               v-model:current-page="gjData.tableData.page"
                               background
                               :page-size="gjData.tableData.size"
                               :total="gjData.tableData.total"
                               layout="prev, pager, next"
                               @current-change="e => getWarnList(e, 'page')"
                               class="mt10 old" />
              </div>
            </div>
          </div>
        </div>
        <div class="card flex-column mt20 cjzcqk">
          <div class="title">装备路径规划</div>
          <div class="content mt10">
            <div style="min-height: 100px;">
              <div class="mm_table_box table">
                <el-table :data="ljData.tableData.data"
                          ref="dataTableRef1"
                          border
                          :class="{ 'no-data': !ljData.tableData.data.length }">
                  <el-table-column type="index"
                                   key="index"
                                   width="50"
                                   label="序号"
                                   align="center">
                    <template slot-scope="scope">
                      {{ (ljData.tableData.page - 1) * ljData.tableData.size + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="LineInfo.LineName"
                                   key="LineInfo.LineName"
                                   label="路径名称"
                                   show-overflow-tooltip></el-table-column>
                  <el-table-column prop="status"
                                   key="status"
                                   label="路径状态"
                                   show-overflow-tooltip>
                    <template slot-scope="scope">
                      有效
                    </template>
                  </el-table-column>
                </el-table>
                <el-pagination v-if="ljData.tableData.total > ljData.tableData.size"
                               v-model:current-page="ljData.tableData.page"
                               background
                               :page-size="ljData.tableData.size"
                               :total="ljData.tableData.total"
                               layout="prev, pager, next"
                               @current-change="getLjData"
                               class="mt10 old" />
              </div>
            </div>
          </div>
        </div>
        <div class="card flex-column mt20 cjzcqk">
          <div class="title">装备调度任务</div>
          <div class="content mt10">
            <div style="min-height: 100px;">
              <div class="mm_table_box table">
                <el-table :data="ddrwzxqkData.tableData.data"
                          ref="dataTableRef3"
                          border
                          :class="{ 'no-data': !ddrwzxqkData.tableData.data.length }">
                  <el-table-column type="index"
                                   key="index"
                                   width="50"
                                   label="序号"
                                   align="center">
                    <template slot-scope="scope">
                      {{ (ddrwzxqkData.tableData.page - 1) * ddrwzxqkData.tableData.size + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="jobName"
                                   key="jobName"
                                   label="任务名称"
                                   show-overflow-tooltip></el-table-column>
                  <el-table-column prop="jobDescription"
                                   key="jobDescription"
                                   label="执行周期"
                                   show-overflow-tooltip></el-table-column>
                </el-table>
                <el-pagination v-if="ddrwzxqkData.tableData.total > ddrwzxqkData.tableData.size"
                               v-model:current-page="ddrwzxqkData.tableData.page"
                               background
                               :page-size="ddrwzxqkData.tableData.size"
                               :total="ddrwzxqkData.tableData.total"
                               layout="prev, pager, next"
                               @current-change="getDdrwzxqkDataList"
                               class="mt10 old" />
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="middle p20 flex-column"
           style="width: 784px">
        <Map @getDeviceData="getDeviceData"
             :data1="{ currentImage, dogId, dogName, Res, X0, Y0, deviceData: mapData, endpoint }"
             style="background: #ccc;"
             ref="mapRef1" />
        <div class="btm-wrapper1 mt20 hp260">
          <ControlDevice class="btm-side"
                      :dogId="dogId"
                      :dogName="dogName"
                      :endpoint="endpoint"
                      :motionId="motionId"
                      :deviceData="deviceData"
                      @handleClose="handleClose" />
        </div>
      </div>
      <div class="side-box d-flex flex-column right h100 ovya no-w-scroll">
        <div class="card jc-div">
          <div class="video-div">
            <div class="t3">周界巡逻机器狗上装设备-视频监控</div>
            <!-- <MonitorRstp v-if="getMountedEquipmentInfo('video')"
                         @change="videoMotionControl($event)"
                         :info="getMountedEquipmentInfo('video')"
                         style="height: 262px; background: #797979;"
                         idName="dialog-monitor-video" /> -->
            <Box v-if="getMountedEquipmentInfo('video')" prefixId="box-dialog-" :dogId="dogId" style="height: 220px; background: #000; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3); max-height: 262px; background: #797979;" />
            <div v-else
                 class="w100 hp262 flx-center"
                 style="background: #f2f2f2; color: #333; font-size: 24px; letter-spacing: 10px;">
              暂无此上装设备
            </div>
          </div>
          <div class="infrared-div mt10">
            <div class="t3">周界巡逻机器狗上装设备-红外探测</div>
            <template v-if="getMountedEquipmentInfo('video')">
              <VideoIRRstp @change="videoMotionControl($event)"
                           :info="getMountedEquipmentInfo('video')"
                           style="height: 246px; background: #797979;"
                           idName="dialog-ir-video" />
              <div class="mt10 flx-justify-between">
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['up'])">上</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['down'])">下</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['left'])">左</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['right'])">右</el-button>
              </div>
              <div class="mt10 flx-justify-between">
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['snapshot'])">拍照</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['video'])">录像</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['jiaojuPlus'])">焦距+</el-button>
                <el-button type="primary"
                           @click.native="handleClickPTZControl(btnObj['jiaojuMinus'])">焦距-</el-button>
              </div>
            </template>
            <div v-else
                 class="w100 hp286 flx-center"
                 style="background: #f2f2f2; color: #333; font-size: 24px; letter-spacing: 10px;">
              暂无此上装设备
            </div>
          </div>
          <div class="voice-div mt20">
            <div class="t3">周界巡逻机器狗上装设备-双向对讲设备</div>
            <!-- <VideoIRRstp @change="videoMotionControl($event)"/> -->
            <template v-if="getMountedEquipmentInfo('audio')">
              <div class="hp230 mt10"
                   style="position: relative;">
                <div class="progress"
                     :style="{ opacity: showVolume ? 1 : 0, '--volume': Math.ceil(volume / 255 * 100) + '%' }">
                  <div class="volume">当前音量：{{ volume }}</div>
                </div>
                <div class="talk-box1"
                     :title="isTalk ? '结束对讲' : '开始对讲'"
                     @click="handleTalk"
                     :class="{ 'is-talk': isTalk,'disabled': isDisabled  }"></div>
              </div>
              <div class="mt10 flx-justify-between">
                <el-button type="primary"
                           @click="handleControlVoice('MUTE')">静音</el-button>
                <el-button type="primary"
                           @click="handleControlVoice('VOLUME_UP')">音量+</el-button>
                <el-button type="primary"
                           @click="handleControlVoice('VOLUME_DOWN')">音量-</el-button>
              </div>
            </template>
            <div v-else
                 class="w100 hp245 mt10 flx-center"
                 style="background: #f2f2f2; color: #333; font-size: 24px; letter-spacing: 10px;">
              暂无此上装设备
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
var WebVideoCtrl = window.WebVideoCtrl
var g_iWndIndex3 = 0
import axios from "axios";
import { VoiceWebSocket } from '@/utils/voiceWebsocket';
import { v4 as uuidv4 } from 'uuid'; // 引入 UUID 库
import { controlVoice, controlVoiceStatus, getDdrwzxqkInfo, getWarningInfo } from '../../../api/bi';
import { getRouteList } from '../../../api/task/taskRoute';

import Map from "../map2.vue";
import ControlDevice from "./ControlDevice";
import MonitorRstp from "../monitorRstp"
import VideoIRRstp from '../videoIRRstp'
import BarChart from '../chart/barChart'
import PieChart from '../chart/pieChart'
import Cookies from 'js-cookie'
import Box from "../box/box";


export default {
  name: 'bigScreenDialog',
  props: {
    // data: {
    //   type: Object,
    //   default: () => ({
    //     currentImage: '',
    //     dogId: '',
    //     dogName: '',
    //     X0: 0.0,
    //     Y0: 0.0,
    //     Res: 0.1,
    //     deviceData: {
    //       deviceName: '',
    //       gearState: '-空闲中-',
    //       motionState: '',
    //       sumOdom: 0,
    //       curOdom: 0,
    //       location: '',
    //       motorState: '',
    //       chargeState: '',
    //       controlMode: '',
    //       speed: 0,
    //       dogState: '正常',
    //       battery: 0,
    //       status: "离线",
    //       dogType: '',
    //       controlMode: '',
    //       model: '绝影X30 Pro',
    //       task: '无',
    //       isWarning: '否'
    //     }
    //   })
    // }
  },
  computed: {
    // 语音系统是否连接
    voiceConnect() {
      return this.$store.getters['voiceCall/getConnected'];
    },
    // 语音websocket
    voiceWebSocket() {
      return this.$store.getters['voiceCall/getVoiceWebSocket'];
    },
    refreshWarningTime() {
      return this.$store.getters['websocket/getRefreshWarningTime'];
    }
  },
  components: { Map, ControlDevice, MonitorRstp, VideoIRRstp, BarChart, PieChart, Box },
  data() {
    return {
      motionId: null,
      rwqsTimeType: '日',
      pickerOptions: {
        disabledDate: (date) => {
          return new Date(date).getTime() > new Date().getTime()
        },
      },
      pieChartData: {
        legendData: ['无人机', '轮式机器人', '双足机器人', '机器狗'],
        data: [
          { value: 3, name: '无人机' },
          { value: 5, name: '轮式机器人' },
          { value: 2, name: '双足机器人' },
          { value: 5, name: '机器狗' }
        ]
      },
      barChartData: {
        legendData: ['巡逻巡查', '人员清点', '车辆跟踪', '应急处突'],
        xData: ['2.28', '3.1', '3.2', '3.3', '3.4', '3.5', '3.6'],
        yData: [
          [48.5, 49.2, 58.8, 68.7, 45, 52, 60],
          [30, 35, 40, 45, 38, 42, 50],
          [20, 25, 30, 35, 28, 32, 40],
          [20, 25, 30, 35, 28, 32, 40]
        ]
      },
      gjData: {
        tableData: {
          data: [
            { name: '巡逻机器狗', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理' },
            { name: '点名机器人', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理' },
            { name: '公服机器人', type: '设备告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理' },
            { name: '跟踪机器狗', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处理' },
            { name: '公服机器人', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处理' },
          ],
          total: 10,
          size: 5,
          page: 1
        },
        alertProcessed: 0,
        unprocessedAlert: 0,
        businessAlert: 0,
        taskAlert: 0,
        deviceAlert: 0,
      },
      ljData: {
        tableData: {
          data: [
            { name: '周界巡逻路径', status: '有效' },
            { name: '成衣车间点名路径', status: '无效' },
            { name: '一监区二层监舍点名路径', status: '无效' },
            { name: '电子车间点名路径', status: '有效' },
          ],
          total: 10,
          size: 5,
          page: 1
        }
      },
      ddrwzxqkData: {
        tableData: {
          data: [
            { jobName: '周界定时巡逻任务', period: '每隔1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false },
            { jobName: '一监区监舍人员清点', period: '每日20:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false },
            { jobName: '成衣车间人员清点', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false },
            { jobName: '公共区域定时巡逻', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false },
            { jobName: '多功能厅定时巡逻', period: '每日06:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false },
            { jobName: '会见大厅公共法律服', period: '每日08:00执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false },
          ],
          total: 10,
          size: 5,
          page: 1
        },
        pendingTasks: 0,
        executingTasks: 0,
        executedTasks: 0,
      },
      //语音对讲
      isTalk: false,
      isDisabled: false,
      // voiceWebSocket: null,
      btnObj: {
        up: 21,
        down: 22,
        left: 23,
        right: 24,
        snapshot: 25,
        video: 26,
        jiaojuPlus: 27,
        jiaojuMinus: 28
      },
      volume: 0,
      isMuted: false,
      showVolume: false,
      showTimer: null,
      timer: '',
      currentImage: '',
      dogId: '',
      dogName: '',
      endpoint: '',
      X0: 0.0,
      Y0: 0.0,
      Res: 0.1,
      mapData: {},
      deviceData: {
        deviceName: '',
        gearState: '-空闲中-',
        motionState: '',
        sumOdom: 0,
        curOdom: 0,
        location: '',
        motorState: '',
        chargeState: '',
        controlMode: '',
        speed: 0,
        dogState: '正常',
        battery: 0,
        status: "离线",
        dogType: '',
        controlMode: '',
        model: '绝影X30 Pro',
        task: '无',
        isWarning: '否',
        onDockState: ''
      },
      dogInfo: {}
    }
  },
  async mounted() {
    // 下面是暂时注释的：
    // this.init()
    // 初始化 WebSocket，使用唯一 clientId
    // const clientId = `A-${uuidv4()}`;
    // // 初始化 WebSocket
    // this.voiceWebSocket = new VoiceWebSocket(clientId, {
    //   serverUrl: process.env.VUE_APP_VOICEWEBSOCKET_URL || 'ws://192.168.1.2:8000/voice',
    //   targetId: 'C',
    //   onError: (error) => {
    //     this.$message.error(`语音对讲错误: ${error.message || '连接失败'}`);
    //     this.isTalk = false;
    //     this.isDisabled = false;
    //   },
    //   onStatusChange: (status) => {
    //     console.log('WebSocket 状态:', status);
    //     if (status === 'disconnected') {
    //       this.$message.warning('语音对讲连接断开，正在尝试重连...');
    //       this.isTalk = false;
    //       this.isDisabled = false;
    //     }
    //   },
    //   onConnect: (targetId) => {
    //     this.$message.success(`已连接到 ${targetId}`);
    //   }
    // });
    // this.voiceWebSocket.init();

    this.getAllData()
    this.timer = setInterval(() => {
      this.getAllData()
    }, 60 * 1000)
  },
  beforeDestroy() {
    // 清理资源
    if (this.voiceConnect) {
      this.voiceWebSocket.stopRecording();
    }
    if (this.timer) {
      clearInterval(this.timer)
    }
    if (this.showTimer) clearTimeout(this.showTimer)
  },
  methods: {
    open(data) {
      this.currentImage = data.currentImage
      this.dogId = data.dogId
      this.dogName = data.dogName
      this.endpoint = data.endpoint
      this.Res = data.Res
      this.X0 = data.X0
      this.Y0 = data.Y0
      this.deviceData = { ...this.deviceData, ...data.deviceData, deviceName: this.dogName }
      this.mapData = { ...this.deviceData, ...data.deviceData }
      this.dogInfo = { ...data.dogInfo }
      Cookies.set('targetId', this.dogId)
      Cookies.set('targetName', this.dogName)
    },
    getMountedEquipmentInfo(typeValue) {
      const arr = (this.dogInfo && this.dogInfo.qhMountedEquipmentList) ? this.dogInfo.qhMountedEquipmentList.filter(item => item.type === typeValue) : []
      return arr.length ? arr[0] : null
    },
    getAllData() {
      this.getWarnList()
      this.getDdrwzxqkDataList()
      this.getLjData()
    },
    getDeviceData(data) {
      this.$set(this, 'deviceData', { ...this.deviceData, ...data, deviceName: this.dogName })
    },
    handleClose() {
      this.$emit('handleClose')
    },
    videoMotionControl(item) {
      this.motionId = item
    },
    // 路径
    getLjData(page) {
      if (page) {
        this.ljData.tableData.page = page
      }
      const queryParams = { pageNum: this.ljData.tableData.page, pageSize: this.ljData.tableData.size, LineName: undefined, MapId: this.MapId }
      getRouteList(queryParams).then(res => {
        // console.log(res)
        this.ljData.tableData.data = res.rows;
        this.ljData.tableData.total = res.total;
      }
      );
    },
    // 表格-获取异常数据
    getWarnList(e, type) {
      if (type === 'page') {
        this.gjData.tableData.page = e
      }
      let query = { StartTime: null, EndTime: null, pageNum: this.gjData.tableData.page, pageSize: this.gjData.tableData.size, dogId: this.dogId }
      getWarningInfo(query).then(res => {
        let data = res.data
        this.gjData.alertProcessed = data.alertProcessed
        this.gjData.unprocessedAlert = data.unprocessedAlert
        this.gjData.businessAlert = data.businessAlert
        this.gjData.taskAlert = data.taskAlert
        this.gjData.deviceAlert = data.deviceAlert
        this.gjData.tableData.total = data.total
        this.gjData.tableData.data = data.errorInfos.map(item => {
          return {
            ...item,
            area: item.MapName + item.PointName,
          }
        })
      })
    },
    getDdrwzxqkDataList(page) {
      if (page) {
        this.ddrwzxqkData.tableData.page = page
      }
      let query = { pageNum: this.ddrwzxqkData.tableData.page, pageSize: this.ddrwzxqkData.tableData.size }
      getDdrwzxqkInfo(query).then(res => {
        let data = res.data
        this.ddrwzxqkData.pendingTasks = data.pendingTasks
        this.ddrwzxqkData.executingTasks = data.executingTasks
        this.ddrwzxqkData.executedTasks = data.executedTasks
        this.ddrwzxqkData.tableData.data = data.sysJobList.map(item => {
          item.loading = false
          return item
        })
        this.ddrwzxqkData.tableData.total = data.total
      })
    },
    getRowKey(row) {
      return row.id;
    },

    // 语音、云台
    async handleTalk() {
      if (this.isDisabled) {
        this.$message.warning('请勿重复点击！间隔3s后可再次点击。');
        return;
      }
      this.isTalk = !this.isTalk;
      this.isDisabled = true;
      setTimeout(() => {
        this.isDisabled = false;
        if (!this.voiceConnect) {
          this.isTalk = false
          this.$message.error('语音设备连接失败')
        }
      }, 3000);
      if (this.voiceConnect) {
        if (this.isTalk) {
          const res = await controlVoiceStatus('on', this.dogInfo.endpoint)
           console.log('%c打开语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          const res = await controlVoiceStatus('off', this.dogInfo.endpoint)
           console.log('%c关闭语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.stopRecording();
          this.voiceWebSocket.sendMessage({ type: 'stopTalk', clientId: this.voiceWebSocket.clientId });
        }
      }
    },
    async startRecording() {
      try {
        await this.voiceWebSocket.startRecording(this.dogId);
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording(this.dogId);
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
    // 获取视频流登录认证
    init() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc) => {
          g_iWndIndex3 = parseInt($(xmlDoc).find("SelectWnd").eq(0).text(), 10);
        },
        cbDoubleClickWnd: function () { },
        cbEvent: (iEventType, iParam1) => {
          if (iEventType === 2) {
            console.log("窗口" + iParam1 + "回放结束！");
          } else if (iEventType === -1) {
            console.log("设备" + iParam1 + "网络错误！");
            console.log("网络错误，请检查设备连接！");
          }
        },
        cbInitPluginComplete: () => {
          WebVideoCtrl.I_InsertOBJECTPlugin("divPlugin3").then(
            () => {
              WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
                if (bFlag) {
                  console.log(
                    "检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！"
                  );
                } else {
                  console.log("初始化成功");
                  this.login();
                }
              });
            },
            () => {
              // alert(
              //   "插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！"
              // );
              console.log(
                "插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！"
              );
            }
          );
        },
      });
    },
    login() {
      WebVideoCtrl.I_Login(
        this.ip,
        1,
        this.port,
        this.userName,
        this.password,
        {
          timeout: 3000,
          success: function (xmlDoc) {
            this.getDevicePort(`${this.ip}_${this.port}`);
          }.bind(this),
          error: function (error) {
            console.log(error);
            // alert('登录失败：' + error);
          },
        }
      );
    },
    getDevicePort(szDeviceIdentify) {
      if (!szDeviceIdentify) return;
      WebVideoCtrl.I_GetDevicePort(szDeviceIdentify).then(
        (oPort) => {
          console.log("登录成功", oPort);
          this.startRealPlay();
        },
        (oError) => {
          console.log(oError.errorMsg);
        }
      );
    },
    startRealPlay() {
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex3);
      var startRealPlay = () => {
        WebVideoCtrl.I_StartRealPlay(`${this.ip}_${this.port}`, {
          iStreamType: 1,
          iChannelID: 1,
          bZeroChannel: false,
          iWndIndex: g_iWndIndex3,
          success: () => {
            console.log("开始预览成功！");
          },
          error: function (oError) {
            console.log("开始预览失败！", oError.errorMsg);
          },
        });
      };
      if (oWndInfo != null) {
        WebVideoCtrl.I_Stop({
          iWndIndex: g_iWndIndex3,
          success: () => {
            startRealPlay();
          },
        });
      } else {
        startRealPlay();
      }
    },
    // 麦克风控制
    handleControlVoice(command) {
      if (this.showTimer) clearTimeout(this.showTimer)
      controlVoice(command, this.endpoint.split(':')[0]).then(res => {
        if (res.code === 200) {
          this.volume = res.data.volume
          this.isMuted = res.data.isMuted
          this.showVolume = true
          this.showTimer = setTimeout(() => {
            this.showTimer = false
          }, 3000);
        }
      })
    },
    handleClickPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex)
    },
    mouseDownPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex, 0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23, 1)
    },
    async yuntaiControl(dwPTZCommand, dwStop) {
      try {
        const response = await axios.get(process.env.NODE_ENV === 'development' ? process.env.VUE_APP_YUNTAI_CONTROL : `${location.origin}/yuntai`, {
          params: {
            dwPTZCommand,
            // dwStop: dwStop,
            deviceId: Cookies.get('targetId')
          },
        })
      } catch (err) { }
    }
  },
  watch: {
    // 监听到有异常刷新告警
    refreshWarningTime: {
      handler(val) {
        if (val) {
          this.getWarnList(1, 'page')
        }
      }
    }
  }
}
</script>

<style lang="scss">
.el-descriptions {
  .el-descriptions-item__label {
    text-align: center;
    background: #f2f2f2 !important;
  }
  .is-bordered .el-descriptions-item__cell {
    color: #333;
    font-size: 13px;
    text-align: center;
    line-height: 33px;
    border-color: #797979;
  }
}
.el-dialog {
  .card {
    .title {
      background: #0079fe !important;
    }
  }
  .t3 {
    height: 30px;
    line-height: 30px;
    padding-left: 10px;
    color: #fff;
    font-size: 14px;
    font-weight: normal;
    background: rgba($color: #000000, $alpha: 0.8);
  }
}
.talk-box1 {
  width: 150px;
  height: 150px;
  margin: auto;
  transform: translateY(40px);
  background-image: url("../../../assets/images/largescreen/talk-bg.png");
  background-size: 100% 100%;
  cursor: pointer;
  &.is-talk {
    background-image: url("../../../assets/images/largescreen/talk-bg1.png");
    background-size: 100% 100%;
    animation: fade-scale 2s ease-in-out infinite;
  }
  &.disabled {
    cursor: none !important;
  }
}
</style>
