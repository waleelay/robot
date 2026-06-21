<template>
  <div class="contents">
    <GisMap @changeWebrtcServer="changeWebrtcServer" @getDogList="getDogList" ref="mapRef" />
    <!-- <XlxcDetail />
    <div class="info-content flx-justify-between h100 xlxc" :style="{ display: 'none' }"> -->
    <XlxcDetail v-if="openDetail" />
    <div class="info-content flx-justify-between h100 xlxc" :style="{ display: openDetail ? 'none' : 'flex' }">
      <div class="side-box d-flex flex-column left d-flex">
        <div class="card xlxcqk">
          <div class="title flx-justify-between">
            <div class="text">巡逻巡查概况</div>
            <el-radio-group v-model="rwqsTimeType" size="large" @change="getXlxcData" class="custom-radio-button">
              <el-radio-button label="今日" value="今日" />
              <el-radio-button label="本月" value="本月" />
              <el-radio-button label="当年" value="当年" />
            </el-radio-group>
          </div>
          <div class="content mt10">
            <div class="flx-justify-between withimg">
              <div class="item d-flex flx-align-center" style="width: auto">
                <div class="img flx-justify-center" style="width: 60px; height: 60px">
                  <img src="@/assets/images/bi/new/icon1.png" width="60px" height="60px" />
                </div>
                <div class="ml10 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="desc">累计巡逻巡查次数</div>
                  <div class="count mt10">
                    <span class="num" style="font-size: 36px;">{{ xlxcDataInfo.patrolCount }}</span>次
                  </div>
                </div>
              </div>
              <div class="item d-flex flx-align-center ml24">
                <div class="with-border">
                  <div class="item flx-justify-between w100" style="height: 36px; line-height: 36px; ">
                    <span>累计告警次数</span>
                    <span class="count">
                      <span class="num" style="font-size: 18px">{{ xlxcDataInfo.errorCount }}</span>次
                    </span>
                  </div>
                  <div class="item flx-justify-between w100 mt10 ml0" style="height: 36px; line-height: 36px; ">
                    <span>累计有效告警次数</span>
                    <span class="count">
                      <span class="num" style="font-size: 18px">{{ xlxcDataInfo.effectiveCount }}</span>次
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div class="mt10">
              <div class="title-line flx-justify-between">
                有效告警类型分布情况
              </div>
              <div class="flx-justify-between mt10 pr24 pl24">
                <div class="desc-item">
                  <div class="flx-justify-between flex-column">
                    <div class="count">
                      <span class="num">{{ xlxcDataInfo.businessAlert }}</span> 个
                    </div>
                    <div class="desc mt10">业务告警</div>
                  </div>
                </div>
                <div class="vertical-line"></div>
                <div class="desc-item">
                  <div class="flx-justify-between flex-column">
                    <div class="count">
                      <span class="num">{{ xlxcDataInfo.taskAlert }}</span> 个
                    </div>
                    <div class="desc mt10">任务告警</div>
                  </div>
                </div>
                <div class="vertical-line"></div>
                <div class="desc-item">
                  <div class="flx-justify-between flex-column h100">
                    <div class="count">
                      <span class="num">{{ xlxcDataInfo.deviceAlert }}</span> 个
                    </div>
                    <div class="desc mt10">设备告警</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="card mt10 xlxcddrw">
          <div class="title">巡逻巡查调度任务</div>
          <div class="content mt10">
            <div class="flex1" style="height: 253px">
              <div class="mm_table_box table h100">
                <el-table :data="ddrwData.tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px)">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      <span class="td-index">
                        {{ (ddrwData.tableData.page - 1) * ddrwData.tableData.size + scope.$index + 1 }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="jobName" key="jobName" min-width="80" label="任务名称" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="jobDescription" key="jobDescription" width="80" label="执行周期" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="nextValidTime" key="nextValidTime" width="88" label="下次执行时间" show-overflow-tooltip></el-table-column>
                  <!-- <el-table-column key="status" width="70" label="告警状态">
                    <template slot-scope="scope">
                      <span class="text-error" v-if="scope.row.status === '未处理'">{{ scope.row.status }}</span>
                      <span class="text-success" v-if="scope.row.status === '已处理'">{{ scope.row.status }}</span>
                    </template>
                  </el-table-column> -->
                  <el-table-column width="75" label="操作" align="center" show-overflow-tooltip>
                    <template slot-scope="scope">
                      <!-- :class="{ 'gray': scope.row.executeStatus === 1 }" -->
                      <el-button tt="primary"
                        :disabled="scope.row.executeStatus === 1"
                        @click="manualExecuteDdrw(scope.row, scope.$index)"
                        style="width: 60px"
                      >
                        {{ scope.row.executeStatus === 1 ? '执行中': '手动执行' }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <!-- v-if="ddrwData.tableData.total > ddrwData.tableData.size" -->
                <el-pagination
                  v-model:current-page="ddrwData.tableData.page"
                  background
                  :page-size="ddrwData.tableData.size"
                  :total="ddrwData.tableData.total"
                  layout="prev, pager, next"
                  @current-change="getXlxcDdrwList"
                  class="mt10"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="card flex1 flex-column mt10 ddrwzxqk">
          <div class="title">调度任务执行情况</div>
          <div class="content mt10 flex1 flex-column">
            <div class="flex1" style="height: 253px">
              <div class="mm_table_box table h100">
                <el-table :data="ddrwzxqkData.tableData.data" ref="dataTableRef2" style="height: calc(100% - 34px);">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      <span class="td-index">
                        {{ (ddrwzxqkData.tableData.page - 1) * ddrwzxqkData.tableData.size + scope.$index + 1 }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="equipmentName" key="equipmentName" label="执行装备" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="jobName" key="jobName" label="调度任务" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="startTime" key="startTime" width="70" label="执行时间" show-overflow-tooltip></el-table-column>
                  <el-table-column width="44" label="操作" align="center" show-overflow-tooltip>
                    <template slot-scope="scope">
                      <el-button tt="primary" @click="openLuxiangDialog(scope.row)">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <!-- v-if="ddrwzxqkData.tableData.total > ddrwzxqkData.tableData.size" -->
                <el-pagination
                  v-model:current-page="ddrwzxqkData.tableData.page"
                  background
                  :page-size="ddrwzxqkData.tableData.size"
                  :total="ddrwzxqkData.tableData.total"
                  layout="prev, pager, next"
                  @current-change="getDdrwzxqkDataList"
                  class="mt10"
                />
              </div>
            </div>
          </div>
        </div>
        <!-- <ItemWrap title="视频监控" height="310px" className="icon0">
          <MonitorRstp @change="videoMotionControl($event)"/>
        </ItemWrap>
        <ItemWrap title="红外探测" height="310px" className="icon1">
          <VideoIRRstp @change="videoMotionControl($event)"/>
        </ItemWrap>
        <ItemWrap title="巡查报告" height="310px" className="icon2">
          <PatrolReport :dogId="dogId" />
        </ItemWrap> -->
      </div>
      <!-- <div class="middle">
        <Map1 @changeWebrtcServer="changeWebrtcServer" @getDogList="getDogList" ref="mapRef" />
      </div> -->
      <div class="side-box d-flex flex-column right">
        <!-- hp516 -->
        <div class="card gjts">
          <div class="title">告警提示</div>
          <div class="content mt10">
           <div class="title-line flx-justify-between">未处理告警分布情况</div>
            <div class="flx-justify-between mt10 pr24 pl24">
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ gjData.businessAlert }}</span> 个
                  </div>
                  <div class="desc mt10">业务告警</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ gjData.taskAlert }}</span> 个
                  </div>
                  <div class="desc mt10">任务告警</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">{{ gjData.deviceAlert }}</span> 个
                  </div>
                  <div class="desc mt10">设备告警</div>
                </div>
              </div>
            </div>
            <div class="title-line flx-justify-between mt20">告警详细信息</div>
            <div class="mt10" style="height: 253px">
              <div class="mm_table_box table h100">
                <el-table :data="gjData.tableData.data" ref="dataTableRef2" style="height: calc(100% - 34px)">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      <span class="td-index">
                        {{ (gjData.tableData.page - 1) * gjData.tableData.size + scope.$index + 1 }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="dogName" key="dogName" min-width="80" label="装备名称" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="ErrorType" key="ErrorType" width="62" label="告警类型" show-overflow-tooltip>
                    <template slot-scope="scope">
                      <!-- {{ scope.row.ErrorType === 0 ? '设备告警' : scope.row.ErrorType === 1 ? '业务告警': '任务告警' }} -->
                      <dict-tag :options="dict.type.qh_alarm_record_type" :value="scope.row.ErrorType"/>
                    </template>
                  </el-table-column>
                  <el-table-column prop="ErrorName" key="ErrorName" min-width="100" label="告警内容" show-overflow-tooltip></el-table-column>
                  <el-table-column width="85" label="告警处置" show-overflow-tooltip>
                    <template slot-scope="scope">
                       <!-- <el-select
                          tt="primary"
                          v-model="scope.row.IsDeal"
                          placeholder="Select"
                          size="large"
                          style="width: 240px"
                          @change="e => handleChangeWarningType(e, scope.row.ErrorId)"
                        >
                        <el-option
                          v-for="dict in dict.type.qh_alarm_record_state"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                        />
                      </el-select> -->
                      <el-button tt="primary"
                        :disabled="[1,2].includes(scope.row.IsDeal)"
                        @click="executeWarning(scope.row)"
                        style="width: 60px"
                      >
                        {{ scope.row.IsDeal === '1' ? '已处理': scope.row.IsDeal === '2' ? '系统误报': '去处理' }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                  <!-- v-if="gjData.tableData.total > gjData.tableData.size" -->
                <el-pagination
                  v-model:current-page="gjData.tableData.page"
                  background
                  :page-size="gjData.tableData.size"
                  :total="gjData.tableData.total"
                  layout="prev, pager, next"
                  @current-change="e => getWarnList(e, 'page')"
                  class="mt10"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="card mt10 zbsssjck" style="height: calc(100% - 495px);">
          <div class="title">装备实时数据查看</div>
          <div class="content mt10" style="height: calc(100% - 37px); min-height: 275px">
            <template v-if="robotTabs.length">
              <el-tabs
                v-model="tabName"
                type="card"
                class="demo-tabs"
                @tab-click="handleClick"
              >
                <el-tab-pane
                  v-for="item of robotTabs"
                  :label="item.name"
                  :name="item.id + ''"
                  :key="item.id"
                >
                </el-tab-pane>
              </el-tabs>
              <div class="jc-div no-w-scroll" style="height: calc(100% - 39px); overflow-y: auto; overflow-x: hidden">
                <template v-if="robotTabs.length">
                  <template v-if="getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'video')">
                    <div class="video-div">
                      <div class="t3" style="color: #D0DEEE; font-size: 14px;">周界巡逻机器狗上装设备-视频监控</div>
                      <Box class="w100" prefixId="box-" :dogId="robotTabs[tabIndex].id" style="height: 220px; background: #000; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);" />
                      <!-- <MonitorRstp ref="monitorRspRef" @change="videoMotionControl($event)" :info="getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'video')" style="height: 246px; background: #797979;" idName="monitor-video" /> -->
                    </div>
                    <div class="infrared-div mt10">
                      <div class="t3" style="color: #D0DEEE; font-size: 12px;">周界巡逻机器狗上装设备-红外探测</div>
                      <VideoIRRstp ref="videoRspRef" @change="videoMotionControl($event)" :info="getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'video')" style="height: 246px; background: #797979;" idName="ir-video" />
                      <div class="mt10 flx-justify-between">
                        <el-button tt1="primary" key="dsads" @click.native="handleClickPTZControl(btnObj['up'])">上</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['down'])">下</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['left'])">左</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['right'])">右</el-button>
                      </div>
                      <div class="mt10 flx-justify-between">
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['snapshot'])">拍照</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['video'])">录像</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['jiaojuPlus'])">焦距+</el-button>
                        <el-button tt1="primary" @click.native="handleClickPTZControl(btnObj['jiaojuMinus'])">焦距-</el-button>
                      </div>
                    </div>
                  </template>
                  <div class="voice-div mt10" v-if="getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'audio')">
                    <div class="t3" style="color: #D0DEEE; font-size: 12px;">周界巡逻机器狗上装设备-双向语音对讲</div>
                    <!-- <VideoIRRstp @change="videoMotionControl($event)"/> -->
                      <div class="hp130 mt10" style="position: relative;">
                        <div class="progress" :style="{ opacity: showVolume ? 1 : 0, '--volume': Math.ceil(volume / 255 * 100) + '%' }">
                          <div class="volume">当前音量：{{ volume }}</div>
                        </div>
                        <div
                          class="talk-box1 wp120 hp120" style="transform: unset"
                          :title="isTalk ? '结束对讲' : '开始对讲'"
                          @click="handleTalk"
                          :class="{ 'is-talk': isTalk, 'disabled': isDisabled  }"
                        ></div>
                      </div>
                      <div class="mt10 flx-justify-between">
                      <el-button tt1="primary" @click="handleControlVoice('MUTE')">{{ isMuted ? '取消静音' : '静音' }}</el-button>
                      <el-button tt1="primary" @click="handleControlVoice('VOLUME_UP')">音量+</el-button>
                      <el-button tt1="primary" @click="handleControlVoice('VOLUME_DOWN')">音量-</el-button>
                    </div>
                  </div>
                </template>
                <div v-else class="w100 h100 flx-center" style="background: #f2f2f2; color: #333; font-size: 24px; letter-spacing: 10px;">
                  暂无上装设备
                </div>
              </div>
            </template>
            <div v-else class="w100 h100 flx-center" style="background: #f2f2f2; color: #333; font-size: 24px; letter-spacing: 10px;">
              暂无装备
            </div>
          </div>
        </div>
        <!-- <ItemWrap title="人员管控" height="590px" className="icon3">
          <PersonalControl :dogId="dogId"/>
        </ItemWrap>
        <ItemWrap title="告警信息" height="330px" className="icon4">
          <WarnInfo :dogId="dogId"/>
        </ItemWrap> -->
      </div>
    </div>
    <el-dialog
      v-dialogDrag
      width="1630px"
      :visible="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      :before-close="handleCloseLuxiangDialog"
      align-center
      class="no-header map-dialog luxiang-dialog"
      @opened="openLuxiangDialog"
    >
      <LxDialog v-if="dialogVisible" ref="lxDialogRef" />
    </el-dialog>
    <WarningInfo ref="warningRef" @getAllData="getAllData" />
  </div>
</template>

<script>
var WebVideoCtrl = window.WebVideoCtrl
var g_iWndIndex3 = 0
import axios from "axios";
import { v4 as uuidv4 } from 'uuid'; // 引入 UUID 库

// import Map from "./map";
import Map1 from './map1.vue'
import Monitor from "./monitor";
import Dialog from "./dialog";
import LxDialog from "./dialog/luxiang/index.vue";
import WarningInfo from "./dialog/warnInfo.vue";
import PatrolReport from "./patrolReport";

import PersonalControl from "./personalControl";
import WarnInfo from "./warnInfo";
import RedDetection from "./redDetection";
import MonitorRstp from "./monitorRstp"
import VideoIRRstp from './videoIRRstp'
import { controlVoice, controlVoiceStatus, executeDdrw, getDdrwzxqkInfo, getXlxcDdrwzxqkInfo, getXlxcOverview, getXlxcWarningInfo } from "../../api/bi";
import { setErrorisDeal } from "../../api/cruise/resultandError";
import { listMotion } from "../../api/rsp/equipment";
import Cookies from 'js-cookie'
import Box from './box/box.vue'
import GisMap from './gis/index.vue'
import XlxcDetail from './remote/xlxc/index.vue'
export default {
  name: "xlxc",
  dicts: ['qh_mounted_equipment_type', 'qh_alarm_record_type', 'qh_alarm_record_state'],
  props: {
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
    // 告警
    refreshWarningTime() {
      return this.$store.getters['websocket/getRefreshWarningTime'];
    },
    // 是否打开详情
    routeDetails() {
      return this.$store.getters['extra/getRouteDetails'];
    },
  },
  components: {
    PersonalControl,
    Monitor,
    Dialog,
    PatrolReport,
    WarnInfo,
    RedDetection,
    MonitorRstp,
    VideoIRRstp,
    Map1,
    LxDialog,
    WarningInfo,
    Box,
    XlxcDetail,
    GisMap
  },
  data() {
    return {
      openDetail: false,
      dialogVisible: false,
      isShowBtmSide: true,
      motionId: null,
      rwqsTimeType: '今日',
      xlxcDataInfo: {
        patrolCount: 0,
        errorCount: 0,
        effectiveCount: 0,
        businessAlert: 0,
        taskAlert: 0,
        deviceAlert: 0,
      },
      ddrwData: {
        tableData: {
          data: [
            { jobName: '周界定时巡逻任务', period: '每隔1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '一监区监舍人员清点', period: '每日20:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '成衣车间人员清点', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false},
            { jobName: '公共区域定时巡逻', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false},
            { jobName: '多功能厅定时巡逻', period: '每日06:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false},
            { jobName: '会见大厅公共法律服', period: '每日08:00执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false},
          ],
          total: 10,
          size: 6,
          page: 1
        },
        pendingTasks: 0,
        executingTasks: 0,
        executedTasks: 0,
      },
      ddrwzxqkData: {
        tableData: {
          data: [
            { jobName: '周界定时巡逻任务', period: '每隔1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '一监区监舍人员清点', period: '每日20:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '成衣车间人员清点', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false},
            { jobName: '公共区域定时巡逻', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false},
            { jobName: '多功能厅定时巡逻', period: '每日06:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false},
            // { jobName: '会见大厅公共法律服', period: '每日08:00执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 2, loading: false},
          ],
          total: 10,
          size: 5,
          page: 1
        },
        pendingTasks: 0,
        executingTasks: 0,
        executedTasks: 0,
      },
      gjData: {
        tableData: {
          data: [
            { ErrorId: 1, name: '巡逻机器狗', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { ErrorId: 2, name: '点名机器人', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { ErrorId: 3, name: '公服机器人', type: '设备告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { ErrorId: 4, name: '跟踪机器狗', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
            { ErrorId: 5, name: '公服机器人', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
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
      gjOptions: [
        { label: '未处理', value: 0 },
        { label: '已处置', value: 1 },
        { label: '系统误报', value: 2 }
      ],
      robotTabs: [
        // { name: '周界巡逻机器人', id: '周界巡逻机器人' },
        // { name: '监舍巡逻机器人', id: '监舍巡逻机器人' },
        // { name: '多功能厅巡逻..', id: '多功能厅巡逻..' },
        // { name: '监狱医院机器人', id: '监狱医院机器人' }
      ],
      tabName: '周界巡逻机器人',
      tabIndex: 0,

      //语音对讲
      isTalk: false,
      isDisabled: false,
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
      zxjlInfo: {}
    };
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  mounted() {
    // this.getDogList()
    // 下面是暂时注释的：
    // this.init()
    // this.connectWebsocket()

    this.$store.dispatch('extra/setRouteDetails', {})
    // this.getAllData()
    // this.timer = setInterval(() => {
    //   this.getAllData()
    // }, 60 * 1000)
  },
  beforeDestroy() {
    // 清理资源
    if (this.voiceConnect) {
      this.voiceWebSocket.stopRecording(this.robotTabs[this.tabIndex].endpoint);
    }
    if (this.timer) {
      clearInterval(this.timer)
    }
    if (this.showTimer) clearTimeout(this.showTimer)
  },
  methods: {
    getAllData() {
      this.getXlxcData()
      this.getXlxcDdrwList()
      this.getWarnList()
      this.getDdrwzxqkDataList()
    },
    getDogList(data) {
      console.log('调用了=========', data);
      
      // // 带参：装备状态 state: ccc
      // listMotion().then(res => {
      //   this.robotTabs = res.rows
      //   if (this.robotTabs.length > 0) {
      //     this.tabName = this.robotTabs[0].id.toString();
      //   }
      // })
      // 大屏地图选中，更新机器狗数据，但不应该选中地图中的机器狗，默认选中第一个
      if (data) {
        this.robotTabs = data.dogList
        if (this.robotTabs.length > 0) {
          this.tabIndex = 0
          this.tabName = this.robotTabs[0].id.toString()
          Cookies.set('targetId', this.robotTabs[0].id)
          Cookies.set('targetName', this.robotTabs[0].name)
          // this.tabName = data.dogId.toString();
          // this.robotTabs.map((item, index) => {
          //   if (item.id === data.dogId) {
          //     Cookies.set('targetId', item.id)
          //     Cookies.set('targetName', item.name)
          //   }
          // })
        }
      }
    },
    // 字典翻译 qh_mounted_equipment_type
    // {{ typeFormat('qh_mounted_equipment_type', getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'audio').type) }}
    typeFormat(dictKey, value) {
      return this.selectDictLabel(this.dict.type[dictKey], value);
    },
    getMountedEquipmentInfo(qhMountedEquipmentList, typeValue) {
      const arr = qhMountedEquipmentList.filter(item => item.type === typeValue)
      return arr.length ? arr[0] : null
    },
    handleClose() {
      this.$refs.dialogRef.destoryPlugin();
      this.dialogVisible = false;
      history.go(0);
    },
    openLuxiangDialog(row) {
      if (row) {
        this.zxjlInfo = row
        // this.zxjlInfo = {
        //   "createBy": null,
        //   "createTime": null,
        //   "updateBy": null,
        //   "updateTime": null,
        //   "remark": null,
        //   "id": 36723,
        //   "equipmentName": "机器狗",
        //   "sceneName": "实验室场景",
        //   "jobName": "0814cs聚众",
        //   "startTime": "2025-08-28 09:25:30",
        //   "endTime": "2025-08-28 09:27:28",
        //   "isAlarm": "N",
        //   "jobId": 196,
        //   "jobGroup": "INSPECTION",
        //   "equipmentId": 17,
        //   "sceneId": 16,
        //   "videoSwitch": "N",
        //   "mapInfo": {
        //       "createBy": "admin",
        //       "createTime": "2025-08-01 09:39:05",
        //       "updateBy": null,
        //       "updateTime": null,
        //       "remark": null,
        //       "id": 27,
        //       "name": "会议室地图2",
        //       "startXCoordinate": -3.1,
        //       "startYCoordinate": -15,
        //       "startZCoordinate": 0,
        //       "startCoordinate": null,
        //       "mapFileId": null,
        //       "mapFileUrl": "public-bucket/6f4359b7-45d6-40ce-bfd5-34c09dcfcbd8-file.jpg",
        //       "geographicCoordinateSystem": null,
        //       "projectionCoordinateSystem": null,
        //       "status": "1",
        //       "resolution": null,
        //       "inputPoints": null,
        //       "points": null
        //   },
        //   "videoRecords": [
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 102,
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:25:48",
        //           "endTime": "2025-08-28 09:26:34",
        //           "videoUrl": null,
        //           "nirUrl": "public-bucket/df209ebb-93aa-4c44-aa2f-4ca864ce884c-nirVideo_36723_2.mp4",
        //           "deviceId": 17,
        //           "deviceName": null,
        //           "pointId": 221,
        //           "pathId": 31,
        //           "taskType": 0,
        //           "taskRecordId": 36723
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 103,
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:25:48",
        //           "endTime": "2025-08-28 09:26:34",
        //           "videoUrl": "public-bucket/e6b7e84e-82b2-4b33-9c1b-702683564b30-Video_36723_2.mp4",
        //           "nirUrl": "public-bucket/df209ebb-93aa-4c44-aa2f-4ca864ce884c-nirVideo_36723_2.mp4",
        //           "deviceId": 17,
        //           "deviceName": null,
        //           "pointId": 221,
        //           "pathId": 31,
        //           "taskType": 0,
        //           "taskRecordId": 36723
        //       }
        //   ],
        //   "alarmRecords": [
        //     {
        //       "createBy": null,
        //       "createTime": null,
        //       "updateBy": null,
        //       "updateTime": null,
        //       "remark": null,
        //       "id": 71047,
        //       "equipmentName": "机器狗",
        //       "sceneName": "实验室场景",
        //       "alarmType": "1",
        //       "alarmTime": "2025-08-28 09:26:30",
        //       "alarmStartTime": null,
        //       "alarmEndTime": null,
        //       "alarmContent": "人员落单，人员落单",
        //       "jobId": 191,
        //       "jobGroup": "INSPECTION",
        //       "limit": null,
        //       "equipmentId": null,
        //       "sceneId": 16,
        //       "state": "1",
        //       "imageUrl": "public-bucket/fd3bc6f7-110b-4fe7-87fa-d65bcb4afe3f-36655_191_temp.jpg",
        //       "nirUrl": null,
        //       "mapName": "会议室地图2",
        //       "pointName": "路径点1"
        //     },
        //     {
        //       "createBy": null,
        //       "createTime": null,
        //       "updateBy": null,
        //       "updateTime": null,
        //       "remark": null,
        //       "id": 71046,
        //       "equipmentName": "机器狗",
        //       "sceneName": "实验室场景",
        //       "alarmType": "1",
        //       "alarmTime": "2025-08-28 09:26:50",
        //       "alarmStartTime": null,
        //       "alarmEndTime": null,
        //       "alarmContent": "人员落单，人员落单",
        //       "jobId": 191,
        //       "jobGroup": "INSPECTION",
        //       "limit": null,
        //       "equipmentId": null,
        //       "sceneId": 16,
        //       "state": "1",
        //       "imageUrl": "public-bucket/167aba2c-21b0-4fc2-a136-4495cb43bc20-36655_191_temp.jpg",
        //       "nirUrl": null,
        //       "mapName": "会议室地图2",
        //       "pointName": "路径点1"
        //     }
        //   ],
        //   "qhPoints": [
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 193,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 196,
        //           "videoEquipmentConfigId": 126,
        //           "audioEquipmentConfigId": 127,
        //           "mapInfoId": null,
        //           "pointName": "路径点1",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": -0.294077,
        //           "posY": -0.22317,
        //           "posZ": -0.00541052,
        //           "angleYaw": -2.13529,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 1
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 194,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 197,
        //           "videoEquipmentConfigId": 127,
        //           "audioEquipmentConfigId": 128,
        //           "mapInfoId": null,
        //           "pointName": "路径点2",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": -1.48185,
        //           "posY": -1.89352,
        //           "posZ": -0.0278905,
        //           "angleYaw": -1.72318,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 2
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 195,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 198,
        //           "videoEquipmentConfigId": 128,
        //           "audioEquipmentConfigId": 129,
        //           "mapInfoId": null,
        //           "pointName": "路径点3",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": -1.62115,
        //           "posY": -8.08522,
        //           "posZ": -0.025462,
        //           "angleYaw": -0.07105,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 3
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 196,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 199,
        //           "videoEquipmentConfigId": 129,
        //           "audioEquipmentConfigId": 130,
        //           "mapInfoId": null,
        //           "pointName": "路径点4",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": 1.4151,
        //           "posY": -7.86176,
        //           "posZ": -0.044444,
        //           "angleYaw": 1.46346,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 4
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 197,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 200,
        //           "videoEquipmentConfigId": 130,
        //           "audioEquipmentConfigId": 131,
        //           "mapInfoId": null,
        //           "pointName": "路径点5",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": 1.52195,
        //           "posY": -1.62047,
        //           "posZ": -0.0526383,
        //           "angleYaw": 1.68401,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 5
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "pointId": 198,
        //           "pointLastId": null,
        //           "pathId": 26,
        //           "motionEquipmentConfigId": 201,
        //           "videoEquipmentConfigId": 131,
        //           "audioEquipmentConfigId": 132,
        //           "mapInfoId": null,
        //           "pointName": "路径点6",
        //           "qhMotionEquipmentConfig": null,
        //           "mapID": 0,
        //           "posX": 0.0322144,
        //           "posY": -0.271276,
        //           "posZ": -0.0297177,
        //           "angleYaw": 0.0699558,
        //           "qhAudioEquipmentConfig": null,
        //           "qhVideoEquipmentConfig": null,
        //           "value": 6
        //       }
        //   ],
        //   "qhPointRecords": [
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 652,
        //           "pointName": "路径点1",
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:25:30",
        //           "endTime": "2025-08-28 09:25:48",
        //           "taskRecordId": 36723,
        //           "waitTime": 10,
        //           "mapID": 0,
        //           "posX": -0.00236038,
        //           "posY": -0.0570589,
        //           "posZ": -0.0185055,
        //           "angleYaw": -0.00773495,
        //           "value": 1
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 653,
        //           "pointName": "路径点1",
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:25:49",
        //           "endTime": "2025-08-28 09:26:03",
        //           "taskRecordId": 36723,
        //           "waitTime": 0,
        //           "mapID": 0,
        //           "posX": -1.28631,
        //           "posY": -0.0528795,
        //           "posZ": 0.029516,
        //           "angleYaw": -3.09415,
        //           "value": 2
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 654,
        //           "pointName": "路径点2",
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:26:03",
        //           "endTime": "2025-08-28 09:26:14",
        //           "taskRecordId": 36723,
        //           "waitTime": 0,
        //           "mapID": 0,
        //           "posX": -1.56646,
        //           "posY": -1.51624,
        //           "posZ": -0.0462651,
        //           "angleYaw": -1.64952,
        //           "value": 3
        //       },
        //       {
        //           "createBy": null,
        //           "createTime": null,
        //           "updateBy": null,
        //           "updateTime": null,
        //           "remark": null,
        //           "id": 655,
        //           "pointName": "路径点1",
        //           "jobId": 196,
        //           "startTime": "2025-08-28 09:26:14",
        //           "endTime": "2025-08-28 09:27:28",
        //           "taskRecordId": 36723,
        //           "waitTime": 0,
        //           "mapID": 0,
        //           "posX": -0.0273946,
        //           "posY": -0.126181,
        //           "posZ": -0.0561049,
        //           "angleYaw": -0.039084,
        //           "value": 4
        //       }
        //   ]
        // }
        this.dialogVisible = true
      } else {
        this.$nextTick(() => {
          this.$refs.lxDialogRef.getData(this.zxjlInfo)
        })
      }
    },
    handleCloseLuxiangDialog() {
      this.dialogVisible = false;
    },
    videoMotionControl(item) {
      this.motionId = item
      this.handleClickPTZControl(item)
    },
    toggleBtmSide() {
      this.isShowBtmSide = !this.isShowBtmSide;
    },
    // TODO:改动大屏地图的选中机器狗
    handleClick(tab) {
      // sessionStorage{ targetId: this.tabName, name: this.robotTabs[this.tabIndex] }
      const arr = this.robotTabs.filter(item => item.id == tab.name)
      Cookies.set('targetId', arr[0].id)
      Cookies.set('targetName', arr[0].name)
      // this.$refs.mapRef.changeDog(arr[0].id)
      // console.log(123, this.$refs.mapRef);
      
    },
    // 巡逻巡查概况
    getXlxcData() {
      const timeFlag = this.rwqsTimeType === '今日' ? 0 : this.rwqsTimeType === '本月' ? 1 : 2
      getXlxcOverview({ timeFlag }).then(res => {
        if (res.code === 200) {
          this.xlxcDataInfo = {
            patrolCount: res.data.patrolCount,
            errorCount: res.data.errorCount,
            effectiveCount: res.data.effectiveCount,
            businessAlert: res.data.businessAlert,
            taskAlert: res.data.taskAlert,
            deviceAlert: res.data.deviceAlert
          }
        }
      })
    },
    // 巡逻巡查调度任务
    getXlxcDdrwList(page) {
      if (page) {
        this.ddrwData.tableData.page = page
      }
      let query = {pageNum: this.ddrwData.tableData.page, pageSize: this.ddrwData.tableData.size, taskType: 0 }
      getDdrwzxqkInfo(query).then(res => {
        let data = res.data
        this.ddrwData.pendingTasks = data.pendingTasks
        this.ddrwData.executingTasks = data.executingTasks
        this.ddrwData.executedTasks = data.executedTasks
        this.ddrwData.tableData.data = data.sysJobList.map(item => {
          item.loading = false
          return item
        })
        this.ddrwData.tableData.total = data.total
      })
    },
    executeWarning(row) {
      this.$refs.warningRef.open(row)
    },
    manualExecuteDdrw(item, index) {
      if (item.loading === true) {
        return false
      }
      this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 1 })
      item.loading = true
      executeDdrw({ jobId: item.jobId, jobGroup: item.jobGroup, executeStatus: 1 }).then(res => {
        item.loading = false
        if (res.code === 200) {
          this.$message.success(res.msg)
          this.getDdrwzxqkDataList()
        } else {
          this.$message.error(res.msg)
          this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 0 })
        }
      }).catch(() => {
        item.loading = false
        this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 0 })
      })
    },
    // 表格-获取异常数据
    getWarnList(e, type) {
      if (type === 'page') {
        this.gjData.tableData.page = e
      }
      if (type === 'timeRange' && e.length > 0) {
        this.gjData.tableData.page = 1
        query.StartTime = this.changeDate(this.timeRange[0])
        query.EndTime = this.changeDate(this.timeRange[1])
      }
      let query = {StartTime: null, EndTime: null, pageNum: this.gjData.tableData.page, pageSize: this.gjData.tableData.size }
      getXlxcWarningInfo(query).then(res => {
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
            area: item.MapName+item.PointName,
          }
        })
      })
    },
    getDdrwzxqkDataList(page) {
      if (page) {
        this.ddrwzxqkData.tableData.page = page
      }
      let query = {pageNum: this.ddrwzxqkData.tableData.page, pageSize: this.ddrwzxqkData.tableData.size, jobTaskType: 0 }
      getXlxcDdrwzxqkInfo(query).then(res => {
        // this.ddrwzxqkData.pendingTasks = data.pendingTasks
        // this.ddrwzxqkData.executingTasks = data.executingTasks
        // this.ddrwzxqkData.executedTasks = data.executedTasks
        this.ddrwzxqkData.tableData.data = res.rows
        this.ddrwzxqkData.tableData.total = res.total
      })
    },
    handleChangeWarningType(value, ErrorId) {
      setErrorisDeal({ ErrorId, IsDeal: value }).then(res => {
        if (res.code === 200) {
          this.$message.success('成功切换告警状态')
          this.getWarnList()
        } else {
          this.$message.error(res.msg || '切换失败')
        }
      })
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
          const res = await controlVoiceStatus('on', this.robotTabs[this.tabIndex].endpoint)
           console.log('%c打开语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          const res = await controlVoiceStatus('off', this.robotTabs[this.tabIndex].endpoint)
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
        await this.voiceWebSocket.startRecording(this.robotTabs[this.tabIndex].id);
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording(this.robotTabs[this.tabIndex].id);
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
    // -------------------------------------------------未使用
    init() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc) => {
          g_iWndIndex3 = parseInt($(xmlDoc).find("SelectWnd").eq(0).text(), 10);
        },
        cbDoubleClickWnd: function () {},
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
    // -------------------------------------------------
    // 麦克风控制
    handleControlVoice(command) {
      if (this.showTimer) clearTimeout(this.showTimer)
      controlVoice(command, this.robotTabs[this.tabIndex].endpoint.split(':')[0]).then(res => {
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
      this.yuntaiControl(iPTZIndex,0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23,1)
    },
    async yuntaiControl(dwPTZCommand, dwStop) {
      try {
        const url = process.env.NODE_ENV === 'development'
          ? process.env.VUE_APP_YUNTAI_CONTROL
          : `${location.origin}/yuntai`;

        const response = await axios.get(url, {
          params: {
            dwPTZCommand,
            deviceId: Cookies.get('targetId')
          },
          responseType: 'blob'
        });

        // =============================================
        // 仅从响应头获取文件名，没有则不触发下载
        // =============================================
        const disposition = response.headers['content-disposition'];

        // 有下载头才执行下载
        if (disposition) {
          // 正则精确提取 filename="xxx"
          const filenameMatch = disposition.match(/filename="([^"]+)"/);
          const filename = filenameMatch ? filenameMatch[1] : 'download';

          // 触发下载
          const blob = new Blob([response.data], {
            type: response.headers['content-type']
          });
          const link = document.createElement('a');
          link.href = URL.createObjectURL(blob);
          link.download = filename;
          document.body.appendChild(link);
          link.click();
          link.remove();
          URL.revokeObjectURL(link.href);
        }

      } catch (err) {
        console.error('云台控制失败：', err);
      }
    },
    changeWebrtcServer(type) {
      if (!this.$refs.videoRspRef.$refs.videoRef.webRtcServer) return
      if (type === 'open') {
        // console.log('%c打开11111111111', 'color: #0f0', this.$refs.monitorRspRef.$refs.videoRef.$refs.video, this.$refs.monitorRspRef.$refs.videoRef.webRtcServer);

        // 打开视频流
        // if (this.$refs.monitorRspRef.$refs.videoRef.$refs.video) {
        //   this.$refs.monitorRspRef.$refs.videoRef.webRtcServer.disconnect()
        //   if (this.$refs.monitorRspRef.$refs.videoRef.enlargedWebRtcServer) {
        //     this.$refs.monitorRspRef.$refs.videoRef.enlargedWebRtcServer.disconnect()
        //   }
        //   console.log('%c创建大屏连接，关闭小窗口', 'color: #0f0');

        //   this.$refs.monitorRspRef.$refs.videoRef.initVideo()
        // }
        if (this.$refs.videoRspRef.$refs.videoRef.$refs.video) {
          this.$refs.videoRspRef.$refs.videoRef.webRtcServer.disconnect()
          if (this.$refs.videoRspRef.$refs.videoRef.enlargedWebRtcServer) {
            this.$refs.videoRspRef.$refs.videoRef.enlargedWebRtcServer.disconnect()
          }
          this.$refs.videoRspRef.$refs.videoRef.initVideo()
        }
      } else {
        // console.log('%c关闭111111111111', 'color: #f0f', this.$refs.monitorRspRef.$refs.videoRef.$refs.video, this.$refs.monitorRspRef.$refs.videoRef.webRtcServer);
        // 关闭视频流
        // if (this.$refs.monitorRspRef.$refs.videoRef.$refs.video) {
        //   console.log('%c关闭大屏连接，打开小窗口', 'color: #f0f');
        //   if (this.$refs.monitorRspRef.$refs.videoRef.webRtcServer) {
        //     this.$refs.monitorRspRef.$refs.videoRef.webRtcServer.disconnect()
        //   }
        //   if (this.$refs.monitorRspRef.$refs.videoRef.enlargedWebRtcServer) {
        //     this.$refs.monitorRspRef.$refs.videoRef.enlargedWebRtcServer.disconnect()
        //   }
        // }
        if (this.$refs.videoRspRef.$refs.videoRef.$refs.video) {
          if (this.$refs.videoRspRef.$refs.videoRef.webRtcServer) {
            this.$refs.videoRspRef.$refs.videoRef.webRtcServer.disconnect()
          }
          if (this.$refs.videoRspRef.$refs.videoRef.enlargedWebRtcServer) {
            this.$refs.videoRspRef.$refs.videoRef.enlargedWebRtcServer.disconnect()
          }
        }
        if (this.voiceConnect) {
          this.voiceWebSocket.stopRecording()
        }
      }
    }
  },
  watch: {
    // 监听到有异常刷新告警
    refreshWarningTime: {
      handler(val) {
        if (val) {
          this.getXlxcData()
          this.getWarnList(1, 'page')
        }
      }
    },
    routeDetails: {
      handler(newVal) {
        if (newVal.key && newVal.key === 'xlxc') {
          this.openDetail = true
        } else {
          if (this.timer) {
            clearInterval(this.timer)
          }
          this.getAllData()
          this.timer = setInterval(() => {
            this.getAllData()
          }, 60 * 1000)
          this.openDetail = false
        }
      }
    }
  }
};
</script>
