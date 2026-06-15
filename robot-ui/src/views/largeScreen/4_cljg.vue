<template>
  <div class="contents">
    <!-- <CljgDetail />
    <div class="info-content flx-justify-between h100" :style="{ display: 'none' }"> -->
    <CljgDetail v-if="openDetail" />
    <div class="info-content flx-justify-between h100" :style="{ display: openDetail ? 'none' : 'flex' }">
      <div class="side-box d-flex flex-column left d-flex">
        <div class="card xlxcqk">
          <div class="title flx-justify-between">
            <div class="text">外来车辆预约情况</div>
            <el-radio-group v-model="rwqsTimeType" size="large" class="custom-radio-button" @change="getCarData">
              <el-radio-button label="今日" value="今日" />
              <el-radio-button label="本月" value="本月" />
              <el-radio-button label="当年" value="当年" />
            </el-radio-group>
          </div>
          <div class="content mt10">
            <div class="flx-justify-between withimg">
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/new/icon1.png" />
                </div>
                <div class="ml10 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="desc">外来车辆预约情况</div>
                  <div class="count">
                    <span class="num">{{ carDataInfo.totalCount }}</span>辆
                  </div>
                </div>
              </div>
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/new/icon1.png" />
                </div>
                <div class="ml10 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="desc">平台监管车辆数</div>
                  <div class="count">
                    <span class="num">{{ carDataInfo.executingCount }}</span>辆
                  </div>
                </div>
              </div>
            </div>
            <div class="with-border flx-align-center mt10">
              <div class="item flx-justify-between">
                <span>已进入车辆</span>
                <span class="count">
                  <span class="num">{{ carDataInfo.executedCount }}</span>辆
                </span>
              </div>
              <div class="item flx-justify-between">
                <span>未进入车辆</span>
                <span class="count">
                  <span class="num">{{ carDataInfo.unexecutedCount }}</span>辆
                </span>
              </div>
            </div>
            <div class="title-line mt10 flx-justify-between">
              <div class="text">预约车辆详细信息</div>
              <!-- <el-button tt="text" type="text" @click="detail({})" class="mr20">详情测试</el-button>
              <el-button tt="text" type="text" @click="execute({})" class="mr20">下发执行</el-button> -->
              <el-button tt="text" type="text" @click="add">新增</el-button>
            </div>
            <div class="mm_table_box table h100 mt10" style="height: 243px !important;">
              <el-table :data="carListData.tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px);">
                <el-table-column prop="equipmentName" key="equipmentName" width="42" label="照片">
                  <template slot-scope="scope">
                    <!-- `` -->
                    <!-- <img src="@/assets/images/bi/new/icon1.png" width="20px" height="20px" alt="" style="vertical-align: middle;" /> -->
                    <img :src="getImg(scope.row.driverPhoto)" width="20px" height="20px" alt="" style="vertical-align: middle;" />
                  </template>
                </el-table-column>
                <el-table-column prop="driverName" key="jobName1" width="52" label="驾驶员" show-overflow-tooltip></el-table-column>
                <el-table-column prop="goodsType" key="jobName2" label="货物类型" show-overflow-tooltip></el-table-column>
                <el-table-column prop="reservationTime" key="startTime" label="预约时间" show-overflow-tooltip></el-table-column>
                <el-table-column width="70" label="操作" align="center" show-overflow-tooltip>
                  <template slot-scope="scope">
                    <!-- <el-button tt="primary" style="width: 60px" @click="detail(scope.row)" v-if="scope.row.status === '2' || scope.row.status === '1'">查看</el-button>
                    <el-button tt="primary" class="warning" style="width: 60px" @click="viewRecord(scope.row)" v-if="scope.row.status === '2'">回放</el-button> -->
                    <!-- <el-button tt="primary" style="width: 60px" @click="execute(scope.row)">下发执行</el-button> -->
                    <el-button tt="primary" class="warning" style="width: 60px" v-if="scope.row.status === '1'" @click="detail(scope.row)">执行中</el-button>
                    <el-button tt="primary" class="success" style="width: 60px" v-if="scope.row.status === '2'">已完成</el-button>
                    <el-button tt="primary" class="is-disabled" style="width: 60px" v-if="scope.row.status === '2'">未执行</el-button>
                    <el-button tt="primary" style="width: 60px" @click="execute(scope.row)" v-if="scope.row.status === '0'">下发执行</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <!-- <el-button tt="primary" style="width: 60px" @click="executeStep()">继续执行</el-button>
              <el-button tt="primary" style="width: 60px" @click="executeStep1()">发送语音</el-button> -->
              <!-- v-if="carListData.tableData.total > carListData.tableData.size" -->
              <el-pagination
                v-model:current-page="carListData.tableData.page"
                background
                :page-size="carListData.tableData.size"
                :total="carListData.tableData.total"
                layout="prev, pager, next"
                @current-change="getCarListInfo"
                class="mt10"
              />
            </div>
            <!-- <div class="flx-justify-between mt20">
              <div class="item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">2</span> 个
                  </div>
                  <div class="desc mt10">业务告警</div>
                </div>
              </div>
              <div class="item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">1</span> 个
                  </div>
                  <div class="desc mt10">任务告警</div>
                </div>
              </div>
              <div class="item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">0</span> 个
                  </div>
                  <div class="desc">设备告警</div>
                </div>
              </div>
            </div> -->
          </div>
        </div>
        <div class="card mt10 xlxcddrw">
          <div class="title flx-justify-between">
            实时告警提示
          </div>
          <div class="content mt10">
            <div class="title-line flx-justify-between">
              未处理告警分布情况
            </div>
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
            <div class="title-line mt10 flx-justify-between">
              告警详细信息
            </div>
            <div class="mm_table_box table h100 mt10" style="height: 243px !important;">
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
                    <!-- <dict-tag :options="dict.type.qh_alarm_record_type" :value="scope.row.ErrorType"/> -->
                  </template>
                </el-table-column>
                <el-table-column prop="ErrorName" key="ErrorName" min-width="100" label="告警内容" show-overflow-tooltip></el-table-column>
                <!-- <el-table-column width="85" label="告警处置" show-overflow-tooltip>
                  <template slot-scope="scope">
                      <el-select
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
        <Map @toggle-dialog="toggleDialog" :dogId="dogId"/>
      </div> -->
      <div class="side-box d-flex flex-column right" style="display: none">
        <!-- hp516 -->
        <div class="card gjts">
          <div class="title">告警提示</div>
          <div class="content mt20">
            <div class="t2 mt20">未处理告警分布情况</div>
            <div class="flx-justify-between mt20">
              <div class="item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">2</span> 个
                  </div>
                  <div class="desc mt10">业务告警</div>
                </div>
              </div>
              <div class="item">
                <div class="flx-justify-between flex-column">
                  <div class="count">
                    <span class="num">1</span> 个
                  </div>
                  <div class="desc mt10">任务告警</div>
                </div>
              </div>
              <div class="item">
                <div class="flx-justify-between flex-column h100">
                  <div class="count">
                    <span class="num">0</span> 个
                  </div>
                  <div class="desc">设备告警</div>
                </div>
              </div>
            </div>
            <div class="t2 mt20">告警详细信息</div>
            <div class="mt20" style="height: 240px; background: orange;">表格</div>
          </div>
        </div>
        <div class="card flex1 flex-column mt10 ddrwzx">
          <div class="title">装备实时数据查看</div>
          <div class="content flex1 flex-column">
            <div class="mt20 h100" style="background: orange;">具体</div>
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
    <!-- <div
      class="btm-wrapper"
      @click="toggleBtmSide"
      :class="{ show: !isShowBtmSide }"
    >
      <DeviceTalk
        class="btm-side"
        :class="{ hidden: !isShowBtmSide }"
        v-show="isShowBtmSide"
        :dogId="dogId"
        :dogName="dogName"
        :motionId = "motionId"
      />
    </div>
    <div class="LjDialog">
      <el-dialog
        v-dialogDrag
        width="60%"
        :visible.sync="dialogVisible"
        :modal-append-to-body="false"
        :close-on-click-modal="false"
        :modal="false"
        :show-close="true"
        :before-close="handleClose"
      >
        <Dialog ref="dialogRef" />
      </el-dialog>
    </div> -->
    <AddCar ref="addCarRef" @refresh="getAllData" />
    <ExecuteModal ref="executeRef" @refresh="getAllData" @detail="detail" />
  </div>
</template>

<script>
import GisMap from "./gis/index.vue";
import Map from "./map";
import Monitor from "./monitor";
import Dialog from "./dialog";
import DeviceTalk from "./deviceTalk";
import PatrolReport from "./patrolReport";

import PersonalControl from "./personalControl";
import WarnInfo from "./warnInfo";
import RedDetection from "./redDetection";
import MonitorRstp from "./monitorRstp"
import VideoIRRstp from './videoIRRstp'
import { controlVoice, controlVoiceStatus, executeDdrw, getDdrwzxqkInfo, getCarList, getCarOverview, getXlxcWarningInfo, executeTaskByStep, playVoice, getCarInfoById } from "../../api/bi"
import AddCar from "./cljg_modal/addCar.vue";
import ExecuteModal from "./cljg_modal/execute.vue";
import CljgDetail from './remote/cljg/index.vue'
export default {
  name: "cljg",
  props: {
    dogId: {
      type:[Number, String],
      // required: true
    },
    dogName: {
      type:[String],
      // required: true
    }
  },
  components: {
    PersonalControl,
    Map,
    Monitor,
    Dialog,
    DeviceTalk,
    PatrolReport,
    WarnInfo,
    RedDetection,
    MonitorRstp,
    VideoIRRstp,
    GisMap,
    AddCar,
    ExecuteModal,
    CljgDetail
  },
  data() {
    return {
      openDetail: false,
      dialogVisible: false,
      isShowBtmSide: true,
      motionId: null,
      rwqsTimeType: '今日',
      carDataInfo: {
        executedCount: 0,
        unexecutedCount: 0,
        count: 0, //yuyue
        executingCount: 0
      },
      carListData: {
        tableData: {
          data: [
            { jobName: '周界定时巡逻任务', period: '每隔1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '一监区监舍人员清点', period: '每日20:30执行', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 0, loading: false},
            { jobName: '成衣车间人员清点', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 1, loading: false},
            { jobName: '公共区域定时巡逻', period: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', executeStatus: 3, loading: false},
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
    };
  },
  computed: {
    // 是否打开详情
    routeDetails() {
      return this.$store.getters['extra/getRouteDetails'];
    },
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  mounted() {
    this.$store.dispatch('extra/setRouteDetails', {})
    // this.getAllData()
    // this.timer = setInterval(() => {
    //   this.getAllData()
    // }, 60 * 1000)
  },
  methods: {
    getAllData() {
      this.getCarData()
      this.getWarnList()
      this.getCarListInfo()
    },
    getImg(url) {
      return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${url}`
      // return `http://192.168.124.204:8181/file/${url}`
    },
    // 巡逻巡查概况
    getCarData() {
      const scope = this.rwqsTimeType === '今日' ? 'DAY' : this.rwqsTimeType === '本月' ? 'MONTH' : 'YEAR'
      getCarOverview({ scope }).then(res => {
        if (res.code === 200) {
          this.carDataInfo = {
            executedCount: res.data.executedCount || 0,
            unexecutedCount: res.data.unexecutedCount || 0,
            totalCount: res.data.totalCount || 0,
            executingCount: res.data.executingCount || 0,
            businessAlert: res.data.businessAlert || 0,
            taskAlert: res.data.taskAlert || 0,
            deviceAlert: res.data.deviceAlert || 0
          }
        }
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
    getCarListInfo(page) {
      if (page) {
        this.carListData.tableData.page = page
      }
      let query = {pageNum: this.carListData.tableData.page, pageSize: this.carListData.tableData.size }
      getCarList(query).then(res => {
        // this.carListData.pendingTasks = data.pendingTasks
        // this.carListData.executingTasks = data.executingTasks
        // this.carListData.executedTasks = data.executedTasks
        this.carListData.tableData.data = res.rows
        this.carListData.tableData.total = res.total
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
    toggleDialog() {
      this.dialogVisible = true;
    },
    handleClose() {
      this.$refs.dialogRef.destoryPlugin();
      this.dialogVisible = false;
      history.go(0);
    },
    videoMotionControl(item) {
      // console.log(item)
      this.motionId = item
    },
    toggleBtmSide() {
      this.isShowBtmSide = !this.isShowBtmSide;
    },
    add() {
      this.$refs.addCarRef.open();
    },
    detail(row) {
      if (row.bindPaths) {
        this.$store.dispatch('extra/setRouteDetails', { key: 'cljg', row })
      } else {
        getCarInfoById(row.id).then(res => {
          if (res.code === 200) {
            const bindPaths = []
            const equipmentList = []
            const newObj = JSON.parse(JSON.stringify({ ...row, ...res.data }))
            newObj.pathVOS.map(item => {
              bindPaths.push(item)
              equipmentList.push({ id: item.equipmentId, name: item.equipmentName, endpoint: item.equipmentEndpoint })
            })
            newObj.bindPaths = bindPaths
            newObj.equipmentList = equipmentList
            newObj.pathId = newObj.pathId || newObj.pathVOS[0].pathId
            this.$store.dispatch('extra/setRouteDetails', { key: 'cljg', row: newObj })
          } else {
            this.$message.error(res.msg || '查询失败')
          }
        })
        // this.$emit('detail', { ...this.info, paths: this.pathList[0].filter(item => pathIds.includes(item.pathId))[0], equipmentList: this.equipmentList.filter(item => this.form.equipmentIds.includes(item.id)) })
        // this.$store.dispatch('setRouteDetails', { key: 'cljg', row })
      }
    },
    viewRecord(row) {

    },
    execute(row) {
      this.$refs.executeRef.open(row)
    },
    executeStep(row) {
      const obj = {"createBy":null,"createTime":null,"updateBy":null,"updateTime":null,"remark":null,"id":23,"driverName":"dsa","driverPhoto":"public-bucket/574902c7-5014-4fdf-b470-e84e9f12ac3d-driverFile.jpg","driverImage":null,"licensePlate":"川A1425S","licensePlateImage":null,"goodsType":"百货","reservationTime":"2025-11-22 20:32:39","status":"0","pathIdsStr":null,"goodsPhotos":null,"pathIds":null,"pathId":39,"startTime":null,"endTime":null}
      executeTaskByStep(obj).then(res1 => {
        if (res1.code === 200) {
          this.$modal.msgSuccess(res1.msg);
        } else {
          this.$modal.msgSuccess(res1.msg);
        }
      });
    },
    executeStep1(row) {
      playVoice().then(res1 => {
        if (res1.code === 200) {
          this.$modal.msgSuccess(res1.msg);
          this.dialogVisible = false
        } else {
          this.$modal.msgSuccess(res1.msg);
        }
      });
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
    }
  },
  watch: {
    routeDetails: {
      handler(newVal) {
        if (newVal.key && newVal.key === 'cljg') {
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
<style lang="scss" scoped>
@keyframes fade-top {
  0% {
    transform: translateY(calc(100% + 100px));
    opacity: 0;
  }
  100% {
    transform: translateY(0);
    opacity: 1;
  }
}
@keyframes fade-bottom {
  0% {
    transform: translateY(calc(100% + 100px));
    opacity: 1;
  }
  80% {
    opacity: 0.8;
    transform: translateY(100px);
  }
  100% {
    transform: translateY(calc(100% + 100px));
    opacity: 1;
  }
}
@-webkit-keyframes slide-in-fwd-bottom {
  0% {
    -webkit-transform: translateZ(-1400px) translateY(calc(100% + 100px));
    transform: translateZ(-1400px) translateY(calc(100% + 100px));
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateY(0);
    transform: translateZ(0) translateY(0);
    opacity: 1;
  }
}
@keyframes slide-in-fwd-bottom {
  0% {
    -webkit-transform: translateZ(-1400px) translateY(calc(100% + 100px));
    transform: translateZ(-1400px) translateY(calc(100% + 100px));
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateY(0);
    transform: translateZ(0) translateY(0);
    opacity: 1;
  }
}

@-webkit-keyframes slide-in-fwd-left {
  0% {
    -webkit-transform: translateZ(-1400px) translateX(-1000px);
    transform: translateZ(-1400px) translateX(-1000px);
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateX(0);
    transform: translateZ(0) translateX(0);
    opacity: 1;
  }
}

@keyframes slide-in-fwd-left {
  0% {
    -webkit-transform: translateZ(-1400px) translateX(-1000px);
    transform: translateZ(-1400px) translateX(-1000px);
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateX(0);
    transform: translateZ(0) translateX(0);
    opacity: 1;
  }
}

@-webkit-keyframes slide-in-fwd-right {
  0% {
    -webkit-transform: translateZ(-1400px) translateX(1000px);
    transform: translateZ(-1400px) translateX(1000px);
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateX(0);
    transform: translateZ(0) translateX(0);
    opacity: 1;
  }
}

@keyframes slide-in-fwd-right {
  0% {
    -webkit-transform: translateZ(-1400px) translateX(1000px);
    transform: translateZ(-1400px) translateX(1000px);
    opacity: 0;
  }
  100% {
    -webkit-transform: translateZ(0) translateX(0);
    transform: translateZ(0) translateX(0);
    opacity: 1;
  }
}

@keyframes rotating {
  0% {
    -webkit-transform: rotate(0) scale(1);
    transform: rotate(0) scale(1);
  }
  50% {
    -webkit-transform: rotate(180deg) scale(1.1);
    transform: rotate(180deg) scale(1.1);
  }
  100% {
    -webkit-transform: rotate(360deg) scale(1);
    transform: rotate(360deg) scale(1);
  }
}
</style>
