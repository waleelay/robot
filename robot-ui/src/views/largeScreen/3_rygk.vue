<template>
  <div class="contents">
    <div class="info-content d-flex h100">
      <div class="side-box d-flex flex-column left d-flex">
        <div class="card rygkgk">
          <div class="title flx-justify-between">
            <div class="text">人员管控概况</div>
            <el-radio-group v-model="rwqsTimeType" size="large" class="custom-radio-button">
              <el-radio-button label="今日" value="今日" />
              <el-radio-button label="本月" value="本月" />
              <el-radio-button label="当年" value="当年" />
            </el-radio-group>
          </div>
          <div class="content mt20">
            <div class="d-flex withbluebg pr10 pl10">
                <div class="flx-justify-between flex1 pr10" style="border-right: 1px solid #FFF">
                    <div class="desc" style="border: none;">累计人员清点次数</div>
                    <div>
                        <span class="num">12</span> 次
                    </div>
                </div>
                <div class="flx-justify-between flex1 pl10">
                    <div class="desc" style="border: none;">累计清点人次</div>
                    <div>
                      <span class="num">1372</span> 人次
                    </div>
                </div>
            </div>
            <div class="d-flex withimg mt20">
              <div class="item d-flex flx-align-center">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/warning2.svg" />
                </div>
                <div class="ml20 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div>
                    <span class="num">13</span> 次
                  </div>
                  <div class="desc">累计告警次数</div>
                </div>
              </div>
              <div class="item d-flex flx-align-center ml50">
                <div class="img flx-justify-center">
                  <img src="@/assets/images/bi/warning3.png" />
                </div>
                <div class="ml20 flex-column h100" style="align-items: flex-start; justify-content: space-between;">
                  <div class="count">
                    <span class="num">8</span> 次
                  </div>
                  <div class="desc">累计有效告警次数</div>
                </div>
              </div>
            </div>
            <div class="t2 mt20">有效告警类型分布情况</div>
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
          </div>
        </div>
        <div class="card mt10 rygkddrw">
          <div class="title">人员管控调度任务</div>
          <div class="content">
            <div class="mt20 flex1" style="min-height: 240px">
              <div class="mm_table_box table">
                <el-table :data="tableData1.data" ref="dataTableRef1">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      {{ tableData1.page * tableData1.size + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="name" key="name" width="90" label="任务名称" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="period" key="period" width="90" label="执行周期" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="nextTime" key="nextTime" width="90" label="下次执行时间" show-overflow-tooltip></el-table-column>
                  <el-table-column width="70" label="操作" align="center" show-overflow-tooltip>
                    <template slot-scope="scope">
                      <el-button type="primary" :class="{ 'gray': scope.row.status === '执行中' }">{{ scope.row.status }}</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-pagination
                  v-if="tableData1.total > tableData1.size"
                  v-model:current-page="tableData1.page"
                  background
                  :page-size="tableData1.size"
                  :total="tableData1.total"
                  layout="prev, pager, next"
                  @current-change="getGjData"
                  class="mt10"
                />
              </div>
            </div>
          </div>
        </div>
        <div class="card flex1 flex-column mt10 ddrwzxqk">
          <div class="title">调度任务执行情况</div>
          <div class="content mt20 flex1 flex-column">
            <div class="flex1">
              <div class="mm_table_box table">
                <el-table :data="tableData2.data" ref="dataTableRef2">
                  <el-table-column type="index" key="index" width="50" label="序号" align="center">
                    <template slot-scope="scope">
                      {{ tableData2.page * tableData2.size + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="name" key="jobName" width="110" label="执行装备" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="desc" key="desc" width="100" label="调度任务" show-overflow-tooltip></el-table-column>
                  <el-table-column prop="time" key="time" width="70" label="执行时间" show-overflow-tooltip></el-table-column>
                  <el-table-column width="70" label="操作" align="center" show-overflow-tooltip>
                    <template slot-scope="scope">
                      <el-button type="primary">查看</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-pagination
                  v-if="tableData2.total > tableData2.size"
                  v-model:current-page="tableData2.page"
                  background
                  :page-size="tableData2.size"
                  :total="tableData2.total"
                  layout="prev, pager, next"
                  @current-change="getGjData"
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
      <div class="middle">
        <Map @toggle-dialog="toggleDialog" :dogId="dogId"/>
      </div>
      <div class="side-box d-flex flex-column right">
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
            <div class="t2 mt20 flx-justify-between">
              <span>未清点人员业务告警</span>
              <span style="font-size: 13px; letter-spacing: normal;">2025-07-04 14:40:23</span>
            </div>
            <div class="mt20">
              <el-carousel trigger="click" :autoplay="false" :interval="100000" style="height: 248px;">
                <el-carousel-item v-for="item in 4" :key="item">
                  <div class="flex-column flx-justify-between">
                    <div class="flx-justify-between">
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                    </div>
                    <div class="flx-justify-between mt10">
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                      <div class="flex-column flx-align-center">
                        <img style="width: 70px; height: 95px; background: orange;">
                        <span class="mt5" style="font-size: 14px; line-height: 16px;">张三（一监区）</span>
                      </div>
                    </div>
                  </div>
                </el-carousel-item>
              </el-carousel>
            </div>
          </div>
        </div>
        <div class="card flex1 flex-column mt10 zbsssjck">
          <div class="title">装备实时数据查看</div>
          <div class="content">
            <div class="mt20">
              <el-tabs
                v-model="tabName"
                type="card"
                class="demo-tabs"
                @tab-click="handleClick"
              >
                <el-tab-pane
                  v-for="item of robotTabs"
                  :label="item.name"
                  :name="item.value"
                >
                </el-tab-pane>
              </el-tabs>
              <div class="jc-div flex1" style="height: 438px">
                <div class="video-div">
                  <div class="t3">周界巡逻机器狗上装设备-视频监控001</div>
                  <MonitorRstp @change="videoMotionControl($event)" style="height: 246px;"/>
                </div>
                <div class="voice-div mt10">
                  <div class="t3">周界巡逻机器狗上装设备-双向对讲设备</div>
                  <!-- <VideoIRRstp @change="videoMotionControl($event)"/> -->
                   <div style="height: 130px;">对讲</div>
                   <div class="mt10 flx-justify-between">
                    <el-button type="primary">静音</el-button>
                    <el-button type="primary">音量+</el-button>
                    <el-button type="primary">音量-</el-button>
                  </div>
                </div>
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
    <div
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
    </div>
  </div>
</template>

<script>
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
export default {
  name: "rygk",
  props: {
    dogId: {
      type:[Number, String],
      required: true
    },
    dogName: {
      type:[String],
      required: true
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
    VideoIRRstp
  },
  data() {
    return {
      dialogVisible: false,
      isShowBtmSide: true,
      motionId: null,
      rwqsTimeType: '今日',
      tableData1: {
        data: [
          { name: '周界定时巡逻任务', period: '每隔1h执行1次', nextTime: '15:00', status: '待执行'},
          { name: '一监区监舍巡逻任务', period: '每日20:30执行', nextTime: '15:00', status: '待执行'},
          { name: '成衣车间巡逻任务', period: '每1h执行1次', nextTime: '15:00', status: '执行中'},
          { name: '公共区域定时巡逻', period: '每1h执行1次', nextTime: '15:00', status: '执行中'},
          { name: '多功能厅定时巡逻', period: '每日06:30执行', nextTime: '15:00', status: '已执行'},
          { name: '监狱医院巡逻任务', period: '每日08:00执行', nextTime: '15:00', status: '已执行'},
          { name: '成衣车间巡逻任务', period: '每1h执行1次', nextTime: '15:00', status: '已执行'},
        ],
        total: 10,
        size: 5,
        page: 0
      },
      tableData2: {
        data: [
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '待执行'},
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '待执行'},
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '执行中'},
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '执行中'},
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '已执行'},
          { name: '周界巡逻机器狗', time: '15:00', desc: '周界巡逻任务', status: '已执行'},
          // { name: '成衣车间巡逻任务', nextTime: '每1h执行1次', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已执行'},
        ],
        total: 10,
        size: 5,
        page: 0
      },
      tableData3: {
        data: [
          { name: '巡逻机器狗', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
          { name: '点名机器人', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
          { name: '公服机器人', type: '设备告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
          { name: '跟踪机器狗', type: '任务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
          { name: '公服机器人', type: '业务告警', desc: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
        ],
        total: 10,
        size: 5,
        page: 0
      },
      robotTabs: [
        { name: '周界巡逻机器人', value: '周界巡逻机器人' },
        { name: '监舍巡逻机器人', value: '监舍巡逻机器人' },
        { name: '多功能厅巡逻..', value: '多功能厅巡逻..' },
        { name: '监狱医院机器人', value: '监狱医院机器人' }
      ],
      tabName: '周界巡逻机器人'
    };
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },

  methods: {
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
    handleClick(tab) {
      console.log('点击tab=======', tab);
    },
    // 表格数据
    getGjData() {}
  },
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

.contents {
  // height: 100%;
  height: 1016px;
  .info-content {
    padding: 20px 30px;
    .side-box {
      // width: 464px;
      // height: 961px;
      // background-image: url("../../assets/images/largescreen/side-bg.png");
      // background-size: 100% 100%;
      // padding: 20px 0;
      // box-sizing: border-box;
      // display: flex;
      // flex-direction: column;
      // justify-content: space-between;
      // align-items: center;
      &.left {
        -webkit-animation: slide-in-fwd-left 0.4s
          cubic-bezier(0.25, 0.46, 0.45, 0.94);
        animation: slide-in-fwd-left 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
      }
      &.right {
        -webkit-animation: slide-in-fwd-right 0.4s
          cubic-bezier(0.25, 0.46, 0.45, 0.94);
        animation: slide-in-fwd-right 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
      }
    }

    .middle {
      width: 1082px;
    }
  }
  .btm-wrapper {
    width: 1173px;
    height: 59px;
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    margin: 0 auto;
    &::before {
      content: "";
      width: 1173px;
      height: 59px;
      background-image: url("../../assets/images/largescreen/side-btm-bg.png");
      background-size: 100% 100%;
      cursor: pointer;
      position: absolute;
    }
    &.show {
      &::before {
        background-image: url("../../assets/images/largescreen/side-btm-bg1.png");
        background-size: 100% 100%;
      }
    }
    .btm-side {
      width: 894px;
      height: 232px;
      background-image: url("../../assets/images/largescreen/side-bg1.png");
      background-size: 100% 100%;
      position: absolute;
      left: 0;
      right: 0;
      bottom: 91px;
      margin: 0 auto;
      // animation: fade-top 0.5s linear;
      -webkit-animation: slide-in-fwd-bottom 0.4s
        cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
      animation: slide-in-fwd-bottom 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94)
        both;
      &.hidden {
        // animation: fade-bottom 5s linear;
        // transform: translateY(calc(100% + 100px));
      }
    }
  }

  .LjDialog {
    ::v-deep .el-dialog {
      height: 75%;
      background-color: rgba(9, 31, 44, 0.01);
      .el-dialog__header {
        text-align: center;
        //.el-dialog__title {
        //  color: white;
        //  font-size: 32px;
        //  font-weight: bold;
        //}
        .el-dialog__headerbtn {
          background: linear-gradient(180deg, #42e5ff 0%, #1fc6ff 100%);
          font-size: 24px;
          border-radius: 50px;
          .el-dialog__close {
            color: black;
          }
        }
      }
      .el-dialog__body {
        height: 100%;
        color: white;
        font-size: 18px;
      }
      .LjDialog-footer {
        text-align: right;
        margin: 30px 0 0 0;
      }
    }
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
