<template>
  <div class="h100 p30 ">
    <div class="flx-justify-between">
      <div class="tabs flx-align-center">
        <div class="custom-tab-button flex">
          <div
            v-for="item in tabDates"
            :key="item.value"
            class="tab-button-item pt5 pb5"
            style="font-size: 14px; line-height: 19px;"
            :class="{ 'is-active': tabDateIndex === item.value }"
            @click="handleClickTabDate(item.value)">
            {{ item.label }}
          </div>
        </div>
        <div class="custom-tab-button flex ml27">
          <div
            v-for="item  in tabRobots"
            :key="item.value"
            class="tab-button-item pt5 pb5"
            style="font-size: 14px; line-height: 19px;"
            :class="{ 'is-active': tabRobotIndex === item.value }"
            @click="handleClickTabRobot(item.value)">
            {{ item.label }}
          </div>
        </div>
      </div>
      <div class="table-btns ml10">
        <el-button type="primary" plain @click="viewHistoryReport">
          历史报告列表
        </el-button>
        <el-dropdown class="ml10" trigger="click">
          <el-button type="primary" plain class="report-btn">
            一键生成报告
            <svg-icon icon-class="download1" class="ml10" style="font-size: 16px" />
          </el-button>
          <el-dropdown-menu slot="dropdown" class="wp144 custom-dropdown-menu report-dropdown-menu p10">
            <div>
              <div class="types">
                <div class="desc">内容选择</div>
                <el-checkbox class="custom-check-box1" :indeterminate="isIndeterminate" v-model="checkAll" @change="handleCheckAllChange">全部模块</el-checkbox>
                <el-checkbox-group v-model="checkedCities" @change="handleCheckedCitiesChange" class="flex-column custom-check-group1">
                  <el-checkbox v-for="city in cities" :label="city" :key="city">{{city}}</el-checkbox>
                </el-checkbox-group>
              </div>
              <div class="mt2 date">
                <div class="desc">时间选择</div>
                <el-radio-group v-model="radioValue" class="custom-radio-group1 flex flex-column with-border vertical">
                  <el-radio v-for="item in radioList" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
                </el-radio-group>
              </div>
              <el-dropdown-item>
                <el-button type="primary" @click="generateReport" class="inner-btn w100 tac">
                  <svg-icon icon-class="download1" class="mr10" style="font-size: 16px" />
                  下载报告
                </el-button>
              </el-dropdown-item>
            </div>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div>
    <div class="overview d-flex flex-wrap mt20" v-loading="loading">
      <div
        v-for="(item, index) in kpiCards"
        :key="item.code"
        class="item hp140 flex1 flx-align-center pl36"
        :class="{ 'ml20': index !== 0 }">
        <div class="img wp74 hp86">
          <img :src="item.icon" alt="" srcset="">
        </div>
        <div class="ml30 flex-column">
          <div class="title mt4">{{ item.name }}</div>
          <div class="value mt4"><span class="mr8">{{ item.value }}</span>{{ item.unit }}</div>
          <div class="desc mt4 d-flex" style="align-items: end">
            <span class="mr10">{{ item.compareLabel }}</span>
            <svg-icon icon-class="increase" class="increase" :class="{ 't': item.trend === 'down' }" />
            <span class="ml2">{{ item.compareRate > 0 ? '+' : '' }}{{ item.compareRate }}<span style="font-size: 14px;">%</span></span>
          </div>
        </div>
      </div>
    </div>
    <div class="mt20 d-flex flex-wrap">
      <div class="flex1 flex-column yxsc">
        <div class="card-title title-920-37">
          <div class="text">
            装备运行时长
          </div>
        </div>
        <div class="hp346">
          <div class="p20 flex-column h100">
            <div class="desc flx-center mt4">
              <div>
                <span>总在线率</span>
                <span class="ml10 value">{{ equipmentRuntime.onlineRate || 0 }}%</span>
              </div>
              <div class="ml40">
                <span>任务完成率率</span>
                <span class="ml10 value">{{ equipmentRuntime.taskCompletionRate || 0 }}%</span>
              </div>
            </div>
            <BarChart class="mt5" :items="equipmentRuntime.items || []" />
          </div>
        </div>
      </div>
      <div class="flex1 flex-column ml20 warning">
        <div class="card-title title-920-37">
          <div class="text">
            AI告警分析 
          </div>
        </div>
        <div class="hp346 d-flex p20">
          <div class="flex1 flex-column">
            <div class="title">告警类型分布排行</div>
            <div class="mt30 w100 h100">
              <PieChart :items="aiAlarmAnalysis.alarmTypeRanking || []" />
            </div>
          </div>
          <div class="flex1 flex-column pl34 with-border">
            <div class="title">处理方式分布排行</div>
            <div class="mt30 w100 h100">
              <BarChart1 :items="aiAlarmAnalysis.handleMethodRanking || []" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="mt20 d-flex flex-wrap">
      <div class="flex1 flex-column">
        <div class="card-title title-920-37">
          <div class="text">
            告警高发区域排行榜
          </div>
        </div>
        <div class="ranking-list p20 d-flex flex-column hp279">
          <div v-for="(item, index) in alarmAreaRanking" :key="item.name" :class="{'mt16': index !== 0, light: index > 2}" class="ranking-list-item d-flex flex-column flx-justify-between">
            <div class="d-flex flex1 flx-justify-between w100">
              <div class="level wp60 hp23">TOP.{{ index + 1 }}</div>
              <div class="name ml20 flex1" :title="item.name">{{ item.name }}</div>
              <!-- <div class="number">{{ item.nums ? Util.getCurrencySize1(item.nums) : 0 }}</div> -->
              <div class="number">{{ item.nums || 0 }}</div>
            </div>
            <div class="progress w100 hp6 mt6" :style="{ '--percent': item.percent + '%' }"></div>
          </div>
        </div>
      </div>
      <div class="flex1 d-flex ml20">
        <div class="flex1 flex-column">
          <div class="card-title title-450-37">
            <div class="text">
              告警异常趋势图 
            </div>
          </div>
          <div class="hp279 p20">
            <LineChart :points="alarmTrendPoints" />
          </div>
        </div>
        <div class="flex1 flex-column ml20 task">
          <div class="card-title title-450-37">
            <div class="text">
              任务完成率 
            </div>
          </div>
          <div class="p20 hp279 flex-column flx-justify-between">
            <div class="task-list w100 mt10 d-flex flex-column">
              <div
                v-for="(item, index) in taskCompletionItems"
                :key="item.status"
                class="task-item flex-column"
                :class="{ 'mt16': index !== 0 }">
                <div class="d-flex flex1 flx-justify-between w100">
                  <div class="type">{{ item.name }}</div>
                  <div class="number">{{ item.percent }}%</div>
                </div>
                <div class="progress hp6 mt7" :style="{ '--percent': item.percent + '%' }"></div>
              </div>
            </div>
            <div class="desc hp73 flx-align-center pr20 pl20">
              <img src="@/assets/images/new-bi/data-task.png" class="wp44 hp52 ml20" alt="" srcset="">
              <div class="ml30">{{ taskInsight }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <HistoryReportList ref="historyReport" />
  </div>
</template>
 
<script>
import BarChart from './BarChart.vue';
import BarChart1 from './BarChart1.vue';
import LineChart from './LineChart.vue';
import PieChart from './PieChart.vue';
import HistoryReportList from './History.vue';
import { getPatrolStatisticsOverview, exportPatrolStatisticsReport } from '@/api/new-bi';
import { saveAs } from 'file-saver';
const cityOptions = ['装备运行时长', 'AI告警分析', '告警高发区域', '告警趋势图'];
const moduleMap = {
  装备运行时长: 'equipmentRuntime',
  AI告警分析: 'aiAlarmAnalysis',
  告警高发区域: 'alarmAreaRanking',
  告警趋势图: 'alarmTrend'
};
const rangeMap = {
  all: 'all',
  0: 'today',
  1: 'week',
  2: 'month',
  3: 'custom'
};
const deviceTypeMap = {
  all: 'all',
  0: 'UAV',
  1: 'ROBOT_DOG',
  2: 'UGV',
  3: 'HUMANOID_ROBOT'
};

export default {
  name: 'BiPatrolStatistics',
  components: { BarChart, BarChart1, LineChart, PieChart, HistoryReportList },
  data() {
    return {
      loading: false,
      statistics: null,
      checkAll: false,
      checkedCities: ['装备运行时长', 'AI告警分析'],
      cities: cityOptions,
      isIndeterminate: true,
      radioValue: 1,
      radioList: [{ label: '今日', value: 0 }, { label: '本周', value: 1 }, { label: '本月', value: 2 }],
      tabDates: [
        {
          value: 'all',
          label: '全部',
        },
        {
          value: 0,
          label: '今日',
        },
        {
          value: 1,
          label: '本周',
        },
        {
          value: 2,
          label: '本月',
        },
        {
          value: 3,
          label: '自定义',
        },
      ],
      tabDateIndex: 2,
      tabRobots: [
        {
          value: 'all',
          label: '全部',
        },
        {
          value: 0,
          label: '无人机',
        },
        {
          value: 1,
          label: '机器狗',
        },
        {
          value: 2,
          label: '无人车',
        },
        {
          value: 3,
          label: '机器人',
        },
      ],
      tabRobotIndex: 'all',
      rankList: [
        { name: '2监区8号楼', nums: 52 },
        { name: '训练场西门', nums: 20 },
        { name: '食堂大门', nums: 18 },
        { name: '3监区2号楼', nums: 10 },
        { name: '训练厂东门', nums: 6 },
      ]
    }
  },
  computed: {
    kpiCards() {
      const icons = {
        taskTotal: require('@/assets/images/new-bi/data-task.png'),
        patrolMileage: require('@/assets/images/new-bi/data-licheng.png'),
        aiAlarmTotal: require('@/assets/images/new-bi/data-ai.png'),
        autoHandleSuccessRate: require('@/assets/images/new-bi/data-execute.png')
      };
      const fallback = [
        { code: 'taskTotal', name: '任务执行总数', value: 288, unit: '个', compareLabel: '较上月', compareRate: 20, trend: 'up' },
        { code: 'patrolMileage', name: '总巡逻里程', value: 356.8, unit: 'KM', compareLabel: '较上月', compareRate: 20, trend: 'up' },
        { code: 'aiAlarmTotal', name: 'AI自动识别异常数', value: 288, unit: '个', compareLabel: '较上月', compareRate: 20, trend: 'up' },
        { code: 'autoHandleSuccessRate', name: '自动处置成功率', value: 288, unit: '%', compareLabel: '较上月', compareRate: -5, trend: 'down' }
      ];
      const source = this.statistics && this.statistics.kpis && this.statistics.kpis.length ? this.statistics.kpis : fallback;
      return source.map(item => ({ ...item, icon: icons[item.code] || icons.taskTotal }));
    },
    equipmentRuntime() {
      return (this.statistics && this.statistics.equipmentRuntime) || {};
    },
    aiAlarmAnalysis() {
      return (this.statistics && this.statistics.aiAlarmAnalysis) || {};
    },
    alarmTrendPoints() {
      return (this.statistics && this.statistics.alarmTrend && this.statistics.alarmTrend.points) || [];
    },
    alarmAreaRanking() {
      const source = (this.statistics && this.statistics.alarmAreaRanking && this.statistics.alarmAreaRanking.length)
        ? this.statistics.alarmAreaRanking.map(item => ({
          name: item.areaName,
          nums: item.count,
          percent: item.percent
        }))
        : this.rankList;
      const max = source[0] && source[0].nums ? source[0].nums : 1;
      return source.map(item => ({
        ...item,
        percent: item.percent || item.nums / max * 100
      }));
    },
    taskCompletionItems() {
      return (this.statistics && this.statistics.taskCompletion && this.statistics.taskCompletion.items) || [
        { status: 'COMPLETED', name: '已完成', percent: 98 },
        { status: 'RUNNING', name: '执行中', percent: 60 },
        { status: 'INTERRUPTED', name: '异常中断', percent: 10 }
      ];
    },
    taskInsight() {
      return (this.statistics && this.statistics.taskCompletion && this.statistics.taskCompletion.insight)
        || '本月对比上月任务处置时长缩短10%，系统响应速度提升';
    },
    statisticsParams() {
      return {
        range: rangeMap[this.tabDateIndex],
        deviceType: deviceTypeMap[this.tabRobotIndex]
      };
    }
  },
  mounted() {
    this.rankList.map(item => {
      item.percent = item.nums / this.rankList[0].nums * 100
      return item
    })
    this.loadStatistics()
  },
  methods: {
    handleClickTabDate(e) {
      this.tabDateIndex = e
      this.loadStatistics()
    },
    handleClickTabRobot(e) {
      this.tabRobotIndex = e
      this.loadStatistics()
    },
    // 历史报告列表
    viewHistoryReport() {
      this.$refs.historyReport.showModal()
    },
    // 生成报告
    async generateReport() {
      try {
        const modules = this.checkedCities.map(item => moduleMap[item]).filter(Boolean);
        const data = {
          modules,
          timeRange: {
            type: rangeMap[this.radioValue],
            startTime: null,
            endTime: null
          },
          deviceType: deviceTypeMap[this.tabRobotIndex],
          areaIds: [],
          alarmTypes: [],
          handleMethods: [],
          taskStatuses: [],
          format: 'PDF'
        };
        const blob = await exportPatrolStatisticsReport(data);
        saveAs(new Blob([blob], { type: 'application/pdf' }), `数据统计报告-${Date.now()}.pdf`);
      } catch (err) {
        console.error('报告下载失败', err);
      }
    },
    async loadStatistics() {
      this.loading = true;
      try {
        this.statistics = await getPatrolStatisticsOverview(this.statisticsParams);
      } finally {
        this.loading = false;
      }
    },
    handleCheckAllChange(val) {
      this.checkedCities = val ? cityOptions : [];
      this.isIndeterminate = false;
    },
    handleCheckedCitiesChange(value) {
      let checkedCount = value.length;
      this.checkAll = checkedCount === this.cities.length;
      this.isIndeterminate = checkedCount > 0 && checkedCount < this.cities.length;
    }
  }
}
</script>

<style lang="scss" scoped>
.overview {
  .item {
    background-color: #101F3C;
    .title {
      color: #DFEDFF;
      font-family: "Microsoft YaHei";
      font-size: 18px;
      line-height: 24px;
    }
    .value {
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 20px;
      font-weight: 600;
      line-height: 27px;
      span {
        font-size: 36px;
        font-weight: 700;
        line-height: 48px;
      }
    }
    .desc {
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 16px;
      font-weight: 400;
      line-height: 21px;
      .increase {
        color: #00FF60;
        font-size: 14px;
        line-height: 14px;
        & + span {
          color: #00FF60;
          // text-shadow: 1px solid rgba(0, 0, 0, 0.00);
          font-family: Bahnschrift;
          line-height: 11px;
        }
        &.t {
          color: #FF0000;
          transform: rotate(180deg);
          & + span {
            color: #FF0000;
          }
        }
      }

    }
  }
}
.card-title + div {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
  border: 1px solid #005BB5;
  box-shadow: 0 0 20px 0 rgba(0, 166, 255, 0.50) inset;
}
.yxsc {
  .desc {
    color: #DEEAFF;
    text-align: center;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    line-height: 36px;
    .value {
      color: #0BF9FE;
      font-family: Bahnschrift;
      font-size: 24px;
    }
    & > div + div {
      position: relative;
      &::before {
        content: '';
        position: absolute;
        top: 6px;
        left: -20.5px;
        width: 1px;
        height: 24px;
        background-color: rgba(222, 234, 255, 0.50);
      }
    }
  }
}
.ranking-list {
  .ranking-list-item  {
    height: calc(20% - 12.8px);
    & > div {
      .level {
        color: #D1E4FF;
        font-family: OPPOSans;
        font-size: 14px;
        font-style: italic;
        line-height: 23px;
        text-align: center;
        border: 1px solid #6EB7F5;
        background: linear-gradient(180deg, rgba(14, 33, 72, 0.80) 0%, rgba(46, 79, 143, 0.80) 100%);
        box-shadow: 0px 0px 4px 0px rgba(95, 148, 244, 0.34);
      }
      .name, .number {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        -o-text-overflow: ellipsis;
      }
      .name { 
        color: #AEC3DC;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 18px;
      }
      .number {
        width: 120px;
        color: #83C9FE;
        text-align: right;
        font-family: Bahnschrift;
        font-size: 18px;
        line-height: 22px;
      }
    }

    .progress {
      position: relative;
      background: rgba(130, 165, 230, 0.20);
      &::after {
        position: absolute;
        top: 0;
        left: 0;
        width: var(--percent);
        height: 100%;
        background: linear-gradient(90deg, rgba(51, 170, 255, 0.40) 0.03%, #1AFFC6 99.95%);
        content: "";
      }
    }
    &.light {
      .level {
        background: rgba(116, 155, 221, 0.18);
        border: none;
        box-shadow: none;
      }
      .progress::after {
        background: linear-gradient(90deg, #405F99 0.03%, #7184A7 99.95%);
      }
    }
  }
}
.task {
  .task-list {
    .task-item  {
      .type {
        color: #AEC3DC;
        font-family: "Microsoft YaHei";
        font-size: 14px;
      }
      .number {
        color: #83C9FE;
        text-align: right;
        font-family: Bahnschrift;
        font-size: 18px;
        line-height: 22px;
      }
      .progress {
        position: relative;
        background: rgba(130, 165, 230, 0.20);
        &::after {
          position: absolute;
          top: 0;
          left: 0;
          width: var(--percent);
          height: 100%;
          background: linear-gradient(90deg, rgba(51, 170, 255, 0.40) 0.03%, #1AFFC6 99.95%);
          content: "";
        }
      }
    }
  }
  .desc {
    background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
    border: 1px solid #005BB5;
    box-shadow: 0 0 20px 0 rgba(0, 166, 255, 0.50) inset;
    color: #0BF9FE;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    line-height: 21px;
  }
}
.warning {
  .title{
    color: #FFF;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    font-weight: 400;
    line-height: 28px; /* 175% */
  }
  .with-border {
    position: relative;
    &::before {
      position: absolute;
      top: 70px;
      left: 0;
      width: 1px;
      height: 207px;
      background-color: rgba(33, 85, 157, 0.50);
      content: ''
    }
  }
}
.table-btns {
  ::v-deep .el-button {
    color: #0BF9FE;
    font-family: "Alibaba PuHuiTi";
    font-size: 14px;
    letter-spacing: 0.28px;
    &.report-btn {
      color: #fff;
      border-radius: 4px;
      background: linear-gradient(91deg, #1971DE 6.17%, #07438E 99.29%);
      box-shadow: 0 0 14px 2px #09F inset;
    }
  }
}
.report-dropdown-menu {
  border-radius: 4px;
  border: 1px solid #485B7C;
  background: #0E1A2F;
  backdrop-filter: blur(10px);
  margin-top: 10px !important;
  .types, .date {
    .desc {
      padding: 0 10px;
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 34px;
    }
  }
  .date {
    position: relative;
    margin-top: 2px;
    &::before {
      position: absolute;
      top: -1px;
      left: 0;
      width: 100%;
      height: 1px;
      content: '';
      background-color: rgba(255, 255, 255, 0.20);
    }
  }
}

::v-deep .el-radio-group {
  &.custom-radio-group1 {
    .el-radio {
      padding: 0 10px;
      color: #FFF;
      font-size: 14px;
      line-height: 34px;
      &__label {
        color: #fff;
      }
      &__input {
        .el-radio__inner {
          width: 14px;
          height: 14px;
          border-radius: 50%;
          background: transparent;
          border-color: #485A7C !important;
          &::after {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background-color: transparent;
          }
          &:hover {
            border-color: #2368D4 !important;
          }
        }
        &.is-checked {
          .el-radio__inner {
            background-color: #2368D4 !important;
            &::after {
              background: #FFF;
              box-shadow: none;
            }
          }
          & + .el-radio__label {
            color: #fff !important;
          }
        }
      }
    }
    &.vertical {
      display: block !important;
      .el-radio {
        display: block;
        margin-right: 0 !important;
      }
    }
  }
}

::v-deep {
  .el-checkbox {
    padding: 0 10px;
    font-size: 14px;
    line-height: 34px;
    &__label {
      color: #fff;
    }
    &__input {
      line-height: 0;
      .el-checkbox__inner {
        width: 14px;
        height: 14px;
        border-color: #E5E8EB !important;
        background: transparent !important;
        &:hover {
          border-color: #2368D4 !important;
        }
      }
      &.is-checked {
        .el-checkbox__inner {
          background: #2368D4 !important;
          border-color: #2368D4 !important;
          &::after {
            border-color: #FFF !important;
          }
        }
        & + .el-checkbox__label {
          color: #fff !important;
        }
      }
    }
  }
  .el-checkbox-group {
    &.custom-check-group1 {
      &.vertical {
        display: block !important;
        .el-checkbox {
          display: block;
          margin-right: 0 !important;
        }
      }
    }
  }
  .inner-btn {
    border-radius: 3px;
    border: none;
    background: linear-gradient(0deg, #0263C4 0%, #0263C4 100%);
  }
}
</style>
