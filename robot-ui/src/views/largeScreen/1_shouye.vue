<template>
  <div class="contents">
    <GisMap />
    <div class="info-content flx-justify-between h100">
      <div class="side-box d-flex flex-column left d-flex">
        <!-- hp440 -->
        <div class="card sbgl">
          <div class="title flx-justify-between">设备概览</div>
          <div class="content mt10">
            <div class="flx-justify-between withimg mt10">
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/new/icon1.png" />
                </div>
                <div class="ml10 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="desc">接入机器人数量</div>
                  <div class="count">
                    <span class="num">{{ sbglData.allRobotCount }}</span>台
                  </div>
                </div>
              </div>
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/new/icon1.png" />
                </div>
                <div class="ml10 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="desc">接入上装设备数量</div>
                  <div class="count">
                    <span class="num">{{ sbglData.allUpperCount }}</span>台
                  </div>
                </div>
              </div>
            </div>
            <div class="title-line flx-justify-between mt20">
              设备状态情况
            </div>
            <div class="flx-justify-between mt10 pr24 pl24">
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ sbglData.idleCount }}</span>台
                  </div>
                  <div class="desc mt10">空闲中机器人</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ sbglData.runningCount }}</span>台
                  </div>
                  <div class="desc mt10">运行中机器人</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">{{ sbglData.offlineCount }}</span>台
                  </div>
                  <div class="desc mt10">离线机器人</div>
                </div>
              </div>
            </div>
            <div class="mt20 robot-list">
              <!-- <PieChart id="sbglChart" ref="sbglChartRef" :data="sbglData.pieChartData" /> -->
               <div class="item flx-justify-between" v-for="item in sbglData.pieChartData.data">
                  <div class="text" style="width: 70px">{{ item.name }}</div>
                  <div class="flex1 ml12 mr12 progress" :style="{ '--rate': item.rate }"></div>
                  <div class="rate">{{ item.rate || 0 }}</div>
               </div>
            </div>
          </div>
        </div>
        <div class="card flex1 flex-column mt10 cjzcqk">
          <div class="title">场景注册情况</div>
          <div class="content mt10 flex1 flex-column">
            <div class="flx-center mt10">
              <div class="single-div flx-align-center">
                <img src="@/assets/images/bi/new/icon1.png" width="38px" height="30px" />
                <span class="ml10 mr10">注册场景数量</span>
                <span class="count">
                  <span class="num">{{ sceneData.sceneCount }}</span>个
                </span>
              </div>
            </div>
            <div class="title-line flx-justify-between mt20">
              场景注册调度任务数量
            </div>
            <div class="flx-justify-between mt10 pr24 pl24">
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ sceneData.patrolCount }}</span>台
                  </div>
                  <div class="desc mt10">巡逻巡查</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ sceneData.personnelCount }}</span>台
                  </div>
                  <div class="desc mt10">人员清点</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">{{ sceneData.vehicleCount }}</span>台
                  </div>
                  <div class="desc mt10">车辆跟踪</div>
                </div>
              </div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">{{ sceneData.emergencyCount }}</span>台
                  </div>
                  <div class="desc mt10">应急处突</div>
                </div>
              </div>
            </div>

            <div class="flx-justify-between mt20">
              <div class="title-line flx-justify-between mt20">
                <div class="text">场景调度任务趋势</div>
                <el-radio-group v-model="rwqsTimeType" size="large" @change="getSceneRegisterData" class="custom-radio-button">
                  <el-radio-button label="日" />
                  <el-radio-button label="月" />
                  <el-radio-button label="年" />
                </el-radio-group>
              </div>
            </div>
            <div class="mt10 flex1">
              <BarChart id="cjddrwqsChart" ref="cjddrwqsChartRef" :data="sceneData.barChartData" />
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
        <Map1 />
        <GisMap />
      </div> -->
      <!-- <div class="middle">
        <el-popover
          :width="300"
          :visible-arrow="false"
          trigger="hover"
          popper-class="test-popover"
          v-for="item in dogList"
          :key="item.dogId"
        >
        <template #reference>
          <el-button type="primary">{{ item.dogName }}</el-button>
          </template>
          <template #default>
            <div class="box p10">
              <div class="title flx-justify-between">
                <span>监区点名机器人</span>
                <el-button type="primary" class="wp42">运行中</el-button>
              </div>
              <div class="content mt10 pr10 pl10">
                <div>装备类型：四足机器人</div>
                <div class="mt10">装备型号：绝影X30 Pro</div>
                <div class="mt10">控制模式：自动控制</div>
                <div class="mt10">当前速度：1.5m/s</div>
                <div class="mt10">当前电量：98%</div>
                <div class="mt10">调度任务：一监区监舍人员清点</div>
                <div class="mt10">是否告警：否</div>
              </div>
              <div class="footer mt20 flx-center">
                <el-button type="primary" class="wp60" @click="openModal(item)">远程控制</el-button>
                <el-button type="primary" class="wp60 gray">一键返航</el-button>
                <el-button type="primary" class="wp60">退出充电桩</el-button>
              </div>
            </div>
          </template>
        </el-popover>
        <Map @toggle-dialog="toggleDialog" :dogId="dogId"/>
      </div> -->
      <div class="side-box d-flex flex-column right">
        <!-- hp516 -->
          <div class="card gjts">
          <div class="title flx-justify-between">
            <div class="text">告警提示</div>
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :picker-options="pickerOptions2"
              @change="e => getWarnList(e, 'timeRange')">
            </el-date-picker>
          </div>
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
            <div class="mt10" style="height: 243px">
              <div class="mm_table_box table h100">
                <el-table :data="gjData.tableData.data" ref="dataTableRef2" style="height: calc(100% - 34px)" @row-click="executeWarning($event)">
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
                  <el-table-column prop="ErrorName" key="ErrorName" min-width="110" label="告警内容" show-overflow-tooltip></el-table-column>
                  <el-table-column key="status" width="62" label="告警状态" align="center">
                    <template slot-scope="scope">
                      <span class="text-error" v-if="scope.row.IsDeal === '0'">未处理</span>
                      <span class="text-success" v-if="scope.row.IsDeal === '1'">已处理</span>
                      <span class="text-warning" v-if="scope.row.IsDeal === '2'">误报</span>
                    </template>
                  </el-table-column>
                  <!-- <el-table-column width="90" label="告警处置" show-overflow-tooltip> -->
                    <!-- <template slot-scope="scope">
                       <el-select
                          tt="primary"
                          v-model="scope.row.IsDeal"
                          placeholder="无"
                          size="large"
                          style="width: 240px"
                          @change="e => handleChangeWarningType(e, scope.row.ErrorId)"
                        > -->
                        <!-- <el-option
                          v-for="item in gjOptions"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        /> -->
                        <!-- <el-option
                          v-for="dict in dict.type.qh_alarm_record_state"
                          :key="dict.value"
                          :label="dict.label"
                          :value="dict.value"
                        />
                      </el-select>
                    </template>
                  </el-table-column> -->
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
        <!-- <div class="card gjts">
          <div class="title flx-justify-between">
            <div>告警提示</div>
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :picker-options="pickerOptions2"
              @change="e => getWarnList(e, 'timeRange')">
            </el-date-picker>
          </div>
          <div class="content mt20">
            <div class="d-flex withimg">
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/warning.svg" />
                </div>
                <div class="ml20 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="count">
                    <span class="num text-error">{{ gjData.unprocessedAlert }}</span> 个
                  </div>
                  <div class="desc">未处理告警数量</div>
                </div>
              </div>
              <div class="item d-flex flx-align-center ml50">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/success.svg" />
                </div>
                <div class="ml20 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="count">
                    <span class="num">{{ gjData.alertProcessed }}</span> 个
                  </div>
                  <div class="desc">已处理告警数量</div>
                </div>
              </div>
            </div>
            
          </div>
        </div> -->
        <div class="card flex1 flex-column mt10 ddrwzx">
          <div class="title">调度任务执行情况</div>
          <div class="content flex1 flex-column mt10">
            <div class="flx-justify-between mt20 pr24 pl24">
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ ddrwzxqkData.pendingTasks }}</span> 个
                  </div>
                  <div class="desc mt10">待执行</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">{{ ddrwzxqkData.executingTasks }}</span> 个
                  </div>
                  <div class="desc mt10">执行中</div>
                </div>
              </div>
              <div class="vertical-line"></div>
              <div class="desc-item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">{{ ddrwzxqkData.executedTasks }}</span> 个
                  </div>
                  <div class="desc mt10">已执行</div>
                </div>
              </div>
            </div>
            <div class="title-line flx-justify-between mt10">调度任务执行情况</div>
            <div class="mt10 flex1">
              <div class="mm_table_box table h100">
                <el-table :data="ddrwzxqkData.tableData.data" ref="dataTableRef2" style="height: calc(100% - 34px);">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      <span class="td-index">
                        {{ (ddrwzxqkData.tableData.page - 1) * ddrwzxqkData.tableData.size + scope.$index + 1 }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="jobName" key="jobName" label="任务名称" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="jobDescription" key="jobDescription" label="执行周期" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="executeStatus" key="executeStatus" width="62" label="执行状态" show-overflow-tooltip>
                    <template slot-scope="scope">
                      {{ scope.row.executeStatus === 0 ? '待执行' : scope.row.executeStatus === 1 ? '执行中': '已执行' }}
                    </template>
                  </el-table-column>
                  <el-table-column width="75" label="操作" show-overflow-tooltip align="center">
                    <template slot-scope="scope">
                      <el-button
                        tt="primary"
                        @click="[0, 2].includes(scope.row.executeStatus) ? manualExecuteDdrw(scope.row, scope.$index) : controlDevice1(scope.row)"
                      >{{ scope.row.executeStatus === 0 ? '手动执行' : scope.row.executeStatus === 1 ? '立即结束' : '再次执行' }}</el-button>
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
        <!-- <ItemWrap title="人员管控" height="590px" className="icon3">
          <PersonalControl :dogId="dogId"/>
        </ItemWrap>
        <ItemWrap title="告警信息" height="330px" className="icon4">
          <WarnInfo :dogId="dogId"/>
        </ItemWrap> -->
      </div>
    </div>
    
    <el-dialog title="查看告警图片" :visible.sync="pictureOpen" width="800px" append-to-body>
      <el-carousel class="imgdiv">
        <!-- <el-carousel-item v-for="(imgUrl,index) in cruiseImgList" :key="index"> -->
        <el-carousel-item>
          <img :src="imgUrl" alt="巡航图片" class="img"/>
        </el-carousel-item>
      </el-carousel>
    </el-dialog>
    <WarningInfo ref="warningRef" />
  </div>
</template>

<script>
import Map1 from "./map1";
import GisMap from './gis/index.vue'
import Monitor from "./monitor";
import Dialog from "./dialog";
import PatrolReport from "./patrolReport";

import PersonalControl from "./personalControl";
import WarnInfo from "./warnInfo";
import RedDetection from "./redDetection";
import MonitorRstp from "./monitorRstp"
import VideoIRRstp from './videoIRRstp'

import BarChart from './chart/barChart'
import PieChart from './chart/pieChart'
import NewDialog from './dialog/index'
import WarningInfo from "./dialog/warnInfo.vue";
import { getDeviceOverview } from "@/api/bi";
import { getWarningInfo, getDdrwzxqkInfo, getSceneRegister, executeDdrw, controlDevice } from "../../api/bi";
export default {
  name: "shouye",
  dicts: ['qh_alarm_record_type'],
  props: {
    // dogId: {
    //   type:[Number, String],
    //   required: true
    // },
    // dogName: {
    //   type:[String],
    //   required: true
    // }
  },
  computed: {
    // 告警
    refreshWarningTime() {
      return this.$store.getters['websocket/getRefreshWarningTime'];
    }
  },
  components: {
    PersonalControl,
    Map1,
    GisMap,
    Monitor,
    Dialog,
    PatrolReport,
    WarnInfo,
    RedDetection,
    MonitorRstp,
    VideoIRRstp,
    BarChart,
    PieChart,
    NewDialog,
    WarningInfo
  },
  data() {
    return {
      dialogVisible: false,
      dialogVisible1: false,
      isShowBtmSide: true,
      pictureOpen: false,
      imgUrl: '',
      motionId: null,
      dogList: [
        { dogName: '机器狗1', dogId: 1 },
        { dogName: '机器狗2', dogId: 2 }
      ],
      selectedRobot: null, // 存储选中的机器人ID
      selectedRobotName: null,// 存储选中的机器人Name
      rwqsTimeType: '日',
      timeRange: [],
      sbglData: {
        idleCount: 0,// 空闲
        runningCount: 0,// 运行中
        allRobotCount: 0,// 接入数量
        allUpperCount: 0,// 上装设备
        offlineCount: 0,// 离线
        droneCount: 0,// 无人机
        robotDogCount: 0,// 机器狗数量
        bipedalRobotCount: 0,// 双足机器人
        wheeledRobotCount: 0,// 轮式机器人
        pieChartData: {
          legendData: ['无人机', '轮式机器人', '双足机器人', '机器狗'],
          data: [
            { value: 0, name: '无人机' },
            { value: 0, name: '轮式机器人' },
            { value: 0, name: '双足机器人' },
            { value: 0, name: '机器狗' }
          ]
        },
      },
      sceneTypeObj: {
        0: '巡逻巡查',
        1: '人员清点',
        2: '车辆跟踪',
        3: '应急处突',
      },
      sceneData: {
        sceneCount: 0,
        patrolCount: 0,
        personnelCount: 0,
        vehicleCount: 0,
        emergencyCount: 0,
        barChartData: {
          legendData: [],
          xData: [],
          // legendData: ['巡逻巡查', '人员清点', '车辆跟踪', '应急处突'],
          // xData: ['2.28', '3.1', '3.2', '3.3', '3.4', '3.5', '3.6'],
          // xData: ['2025.08', '2025.07', '2025.06', '2025.05', '2025.04', '2025.03'],
          // xData: ['2025', '2024', '2023', '2022', '2021'],
          yData: [
            // [48.5, 49.2, 58.8, 68.7, 45, 52, 60],
            // [30, 35, 40, 45, 38, 42, 50],
            // [20, 25, 30, 35, 28, 32, 40],
            // [20, 25, 30, 35, 28, 32, 40]
            // [48.5, 49.2, 58.8, 68.7, 0, 52],
            // [30, 35, 40, 45, 0, 42],
            // [20, 25, 30, 35, 0, 32],
            // [20, 25, 30, 35, 0, 32]
            // [48.5, 49.2, 58.8, 68.7, 45],
            // [30, 35, 40, 45, 38],
            // [20, 25, 30, 35, 28],
            // [20, 25, 30, 35, 28]
          ]
        }
      },
      pickerOptions: {
        disabledDate: (date) => {
          return new Date(date).getTime() > new Date().getTime()
        },
      },
      gjData: {
        tableData: {
          data: [
            { name: '巡逻机器狗', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理'},
            { name: '点名机器人', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理'},
            { name: '公服机器人', type: '设备告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '未处理'},
            { name: '跟踪机器狗', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处理'},
            { name: '公服机器人', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处理'},
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
      pickerOptions2: {
        disabledDate: (time) => {
          // 禁用今天以后的日期
          const today = new Date();
          return time.getTime() > today.getTime(); // 禁用未来的日期
        },
      },
      timer: null
    };
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  mounted() {
    this.getAllData()
    this.timer = setInterval(() => {
      this.getAllData()
    }, 60 * 1000)
  },
  methods: {
    getAllData() {
      this.getSbglData()
      this.getSceneRegisterData()
      this.getWarnList()
      this.getDdrwzxqkDataList()
    },
    toggleDialog() {
      this.dialogVisible = true;
    },
    openModal(item) {
      this.dogId1 = item.value
      // console.log("选中的机器人 ID:", value);
      this.selectedRobot = item.value;
      this.selectedRobotName = item.label;
      // 可以在这里进行其他逻辑处理，比如获取该机器人的详细信息
      this.dialogVisible1 = true;
    },
    handleClose() {
      this.$refs.dialogRef.destoryPlugin();
      this.dialogVisible = false;
      history.go(0);
    },
    handleClose1() {
      this.dialogVisible1 = false;
    },
    videoMotionControl(item) {
      // console.log(item)
      this.motionId = item
    },
    toggleBtmSide() {
      this.isShowBtmSide = !this.isShowBtmSide;
    },
    // 设备概览
    getSbglData() {
      getDeviceOverview().then(res => {
        if (res.code === 200) {
          this.sbglData = { ...this.sbglData, ...res.data }
          const { legendData, data } = this.sbglData.pieChartData
          data[0].value = res.data.droneCount
          data[1].value = res.data.wheeledRobotCount
          data[2].value = res.data.bipedalRobotCount
          data[3].value = res.data.robotDogCount
          const total = data[0].value + data[1].value + data[2].value + data[3].value
          data.map(item => {
            item.rate = (item.value / total).toFixed(2) + '%'
            return
          })
          this.sbglData.pieChartData = {
            legendData,
            data
          }
        }
      })
    },
    // 场景注册
    getSceneRegisterData() {
      const timeFlag = this.rwqsTimeType === '日' ? 0 : this.rwqsTimeType === '月' ? 1 : 2
      getSceneRegister({ timeFlag }).then(res => {
        if (res.code === 200) {
          this.sceneData = res.data
          this.sceneData.barChartData = {
            legendData: [],
            xData: [],
            yData: [],
          }
          this.sceneData.barChartData.xData = res.data.timeList
          if (res.data.sceneList && res.data.sceneList.length) {
            res.data.sceneList.map(item => {
              this.sceneData.barChartData.legendData.push(this.sceneTypeObj[item.type])
              this.sceneData.barChartData.yData.push(item.list)
            })
          }
        }
      })
    },
    test(item) {
      item.loading = true
    },
    changeDate(date) {
      var year = date.getFullYear(); //年
      var month = date.getMonth() + 1;//月
      var strDate = date.getDate();//日
      var hour = date.getHours();//时
      var minute = date.getMinutes();//分
      var second = date.getSeconds()
      month = month > 9 ? month : '0' + month
      strDate = strDate > 9 ? strDate : '0' + strDate
      hour = hour > 9 ? hour : '0' + hour
      minute = minute > 9 ? minute : '0' + minute
      second = second >9 ? second : '0' +second
      var newdate = year+'-'+month+'-'+strDate+' '+hour+':'+minute+ ':'+second
      return newdate
    },
    executeWarning(row) {
      this.$refs.warningRef.open(row)
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
            area: item.MapName+item.PointName,
          }
        })
      })
    },
    handleRowClick(row) {
      if (!row.ImageUrl) return
      this.imgUrl = row.ImageUrl
      this.pictureOpen = true
    },
    getDdrwzxqkDataList(page) {
      if (page) {
        this.ddrwzxqkData.tableData.page = page
      }
      let query = {pageNum: this.ddrwzxqkData.tableData.page, pageSize: this.ddrwzxqkData.tableData.size }
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
    controlDevice1(row) {
      controlDevice('shutdown', row.equipmentId).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        } 
      })
    },
    manualExecuteDdrw(item, index) {
      if (item.loading === true) {
        return false
      }
      this.$set(this.ddrwzxqkData.tableData.data, index, { ...item, executeStatus: 1 })
      this.refreshTableLayout('dataTableRef2');
      item.loading = true
      executeDdrw({ jobId: item.jobId, jobGroup: item.jobGroup, executeStatus: 1 }).then(res => {
        item.loading = false
        if (res.code === 200) {
          this.$message.success(res.msg)
          this.getDdrwzxqkDataList()
        } else {
          this.$message.error(res.msg)
          this.$set(this.ddrwzxqkData.tableData.data, index, { ...item, executeStatus: 2 })
          this.refreshTableLayout('dataTableRef2');
        }
      }).catch(() => {
        item.loading = false
        this.$set(this.ddrwzxqkData.tableData.data, index, { ...item, executeStatus: 0 })
        this.refreshTableLayout('dataTableRef2');
      })
    },
    refreshTableLayout(dataTableRef) {
      if (this.$refs.dataTableRef && this.$refs.dataTableRef.doLayout) {
        this.$nextTick(() => {
          this.$refs.dataTableRef.doLayout();
        });
      }
    },
    getRowKey(row) {
      return row.id;
    },
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
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
  }
};
</script>
<style lang="scss" scoped>
.single-div {
  padding: 0 10px;
  border: 1px solid #2C4473;
  span {
    color: #9DD5FF;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    font-style: normal;
    font-weight: 700;
    line-height: 48px; /* 112.5% */
  }
  .count {
    color: #DEEAFF;
    font-family: "Microsoft YaHei";
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
    line-height: 36px; /* 257.143% */
    .num {
      color: #D5EDFF;
      text-shadow: 0 2px 4px rgba(183, 207, 255, 0.25);
      font-family: Bahnschrift;
      font-size: 26px;
      font-style: normal;
      font-weight: 400;
      line-height: 36px; /* 138.462% */
    }
  }
}
.robot-list {
  .item {
    .text {
      color: #72C2FF;
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 20px; /* 142.857% */
    }
    .progress {
      position: relative;
      height: 10px;
      border: 1px solid #092d46;
      &::after {
        position: absolute;
        top: 0;
        left: 0;
        width: var(--rate);
        height: 100%;
        background: linear-gradient(270deg, #14A0FF 0%, #0031C4 100%);
        content: '';
      }
    }
    .rate {
      width: 41px;
      color: #FFF;
      font-family: Bahnschrift;
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 20px; /* 142.857% */
      text-align: right;
    }
    & + .item {
      margin-top: 10px;
    }
  }
}
</style>
