<template>
  <div class="bottom_centre">
    <div class="item">
      <div class="result-title">
        <span class="decorate">|</span>
        <span class="title_font"> 各时间段训练 </span>
        <el-date-picker
          @change="handleValue1Change()"
          v-model="value1"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :picker-options="pickerOptions1">
        </el-date-picker>
      </div>
      <!--      第一个表-->
      <div class="charts-one" id="one"></div>
    </div>
    <div class="item">
      <div class="result-title">
        <span class="decorate">|</span>
        <span class="title_font"> 整体训练统计 </span>
        <el-date-picker
          @change="handleValue2Change()"
          v-model="value2"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :picker-options="pickerOptions2">
        </el-date-picker>
      </div>
      <div class="echart-content">
        <!--        第二个表-->
        <div class="charts-two">
          <div class="pie" id="two"></div>
        </div>
        <div class="result-info">
          <div class="info-item">
            <div class="arrive-img">
              <img :src='unarriveImg' />
            </div>
            <div class="arrive-text">
              <div class="font">严重异常数</div>
              <div class="font"><span class="arrive-num">{{unarriveNum}}</span>个</div>
            </div>
          </div>
          <div class="info-item">
            <div class="arrive-img">
              <img :src='arriveImg' />
            </div>
            <div class="arrive-text">
              <div class="font">普通异常数</div>
              <div class="font"><span class="arrive-num">{{arriveNum}}</span>个</div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script>

import {getCruiseBarDate,getCruisePieDate } from "../../api/bigScreen";
import { getRobotList} from '@/api/robot/robotMessage.js'

export default {
  data() {
    return {
      pieTitle:1000,
      arriveNum: 1500,
      unarriveNum: 88,
      arriveImg:require('../../assets/bigScreen/icon/blueUser.png'),
      unarriveImg:require('../../assets/bigScreen/icon/yellowUser.png'),
      //  日期搜索
      value1: '',
      value2: '',
      echartOne: null,  // 添加 echartOne
      echartTwo: null,
      pickerOptions1: {
        disabledDate: (time) => {
          // 禁用今天以后的日期
          const today = new Date();
          return time.getTime() > today.getTime(); // 禁用未来的日期
        },
        onPick: ({maxDate, minDate}) => {
          // 确保最大选择范围不超过7天
          if (minDate && maxDate) {
            const oneWeek = 7 * 24 * 60 * 60 * 1000; // 7天的毫秒数
            if (maxDate - minDate > oneWeek) {
              this.$message.error('最多只能选择7天的时间范围');
              this.value1 = null; // 如果超过7天，清空选择
            }
          }
        }
      },
      pickerOptions2: {
        disabledDate: (time) => {
          // 禁用今天以后的日期
          const today = new Date();
          return time.getTime() > today.getTime(); // 禁用未来的日期
        },
      },
      digId:undefined,
    };
  },
  async mounted() {
    await this.initCharts();    // 初始化图表
    // await this.getDogList();
    this.value1 = this.setDefaultDateRange();
    this.value2 = this.setDefaultDateRange();
    await this.getValue1Info(); // 先加载数据
    await this.handleValue2Change();
      // await this.initCharts();    // 后初始化图表
  },
  methods: {
    getDogList() {
      let queryParams={
          dogState: 1,
          dogName: "",
          pageNum: 1,
          pageSize: 50,
        }
      return getRobotList(queryParams).then(res => {
        // console.log(res)
        this.dogId=res.rows[0].dogId
        // console.log(this.dogId)
      })
    },
    initCharts(){
      this.echartOne = this.$echarts.init(document.getElementById("one"))
      this.echartOne.setOption({
        tooltip: {},
        xAxis: {
          data: ['8点', '9点', '10点', '11点', '12点']
        },
        yAxis: [
          {
            name:'未到',
            type: 'value',
            position: 'left',
            axisTick: {
              show: false,
            },
            axisLine: {
              show: false,
            },
            axisLabel: {
              formatter: "{value}人",
            },
            min: 0,
            max: 200,
            splitNumber:5,
            interval:40,
          },
          {
            name: '已到',
            type: 'value',
            position: 'right',
            axisTick: {
              show: false,
            },
            axisLine: {
              show: false,
            },
            axisLabel: {
              formatter: "{value}人",
            },
            min:0,
            max:10000,
            splitNumber:5,
            interval:2000,
          }
        ],
        legend: {
          // bottom:10,
          top:10,
          textStyle: {
            color:'#fff'
          }
        },
        series: [
          {
            name: '未到人数',
            yAxisIndex:0,
            type: 'bar',
            data: [5, 20, 36, 10, 20],
            itemStyle: {
              color: "rgba(232,208,129,1)"
            }
          },
          {
            name: '已到人数',
            yAxisIndex:1,
            type: 'bar',
            data: [1000, 2000, 4000, 6000, 3000],
            itemStyle: {
              color: "rgba(130,231,236,1)"
            }
          },
        ],
        dataZoom: [{
          type:'slider',
          show:true,
          start:0,//数据窗口范围的起始百分比,表示1%
          end:100,//数据窗口范围的结束百分比,表示35%坐标
          height: 15
        }]
      })
      this.echartTwo = this.$echarts.init(document.getElementById("two"))
      this.echartTwo.setOption({
        title: {
          text: this.pieTitle+'次',
          left: 'center',
          top: 'center',
          textStyle: {
            color: '#ccc'
          }
        },
        series: [
          {
            type: 'pie',
            data: [
              {
                value: 23,
                name: '未到人数'
              },
              {
                value: 900,
                name: '已到人数'
              }
            ],
            radius:['40%','80%'],
            itemStyle: {
              borderWidth: 0.5,
              borderColor: "rgba(11, 1, 1, 1)",
              normal: {
                color: function(colors) {
                  var colorList=[
                    'rgba(232,208,129,1)','rgba(130,231,236,1)',
                  ];
                  return colorList[colors.dataIndex];
                }
              }
            }
          }
        ]
      })
    },
    // 设置默认日期范围为前天凌晨到昨天凌晨
    setDefaultDateRange() {
      const now = new Date();
      const yesterday = new Date(now);
      const dayBeforeYesterday = new Date(now);
      yesterday.setDate(now.getDate() - 1); // 设置为昨天
      yesterday.setHours(23, 59, 59, 999);  // 昨天 23:59:59
      dayBeforeYesterday.setDate(now.getDate() - 7); // 前7天
      dayBeforeYesterday.setHours(0, 0, 0, 0);       // 前7天 00:00
      return [dayBeforeYesterday, yesterday];
    },
    handleValue1Change() {
      if (this.value1 && this.value1.length === 2) {
        console.log(this.value1)
        let [startDate, endDate] = this.value1;
        startDate = new Date(startDate);
        startDate.setHours(0, 0, 0, 0);
        endDate = new Date(endDate);
        endDate.setHours(23, 59, 59, 999);
        const oneWeek = 7 * 24 * 60 * 60 * 1000; // 7 天的毫秒数
        if (endDate - startDate > oneWeek) {
          this.$message.error('最多只能选择7天的时间范围');
          this.value1 = this.setDefaultDateRange(); // 清空选择
          this.getValue1Info()
          return; // 阻止继续执行
        }
        this.value1 = [startDate, endDate];
      }
      this.getValue1Info()
    },
    getNextHundred(arr) {
      // console.log(arr)
      // 找到数组中的最大值
      const maxVal = Math.max(...arr);
      // console.log(maxVal)
      // 计算比最大值大的最小且能被100整除的值
      const nextHundred = Math.ceil(maxVal / 5) * 5;
      // console.log(maxVal)
      return nextHundred;
    },
    replaceNullWithZero(arr) {
      return arr.map(value => value === null ? 0 : value);
    },
    getValue1Info() {
      const query ={StartTime: this.changeDate(this.value1[0]),EndTime: this.changeDate(this.value1[1]),dogId:1}
      // console.log(query)
      getCruiseBarDate(query).then(res => {
        // console.log(res)
        let noNamedMax = 0
        let namedMax = 0
        if (res.data[0].ServerCounts.length !== 0) {
          noNamedMax = this.getNextHundred(res.data[0].ServerCounts)
          namedMax = this.getNextHundred(res.data[0].NormCounts)
        } else {
          noNamedMax = 0
          namedMax = 0
        }
        // console.log(noNamedMax+"+"+ namedMax)
        this.echartOne.setOption({
          xAxis:{
            data: res.data[0].xAxis
          },
          yAxis: [
            {
              name:'紧急',
              type: 'value',
              position: 'left',
              axisTick: {
                show: false,
              },
              axisLine: {
                show: false,
              },
              axisLabel: {
                formatter: "{value}件",
              },
              min: 0,
              max: noNamedMax,
              splitNumber:5,
              interval:noNamedMax/5,
            },
            {
              name: '普通',
              type: 'value',
              position: 'right',
              axisTick: {
                show: false,
              },
              axisLine: {
                show: false,
              },
              axisLabel: {
                formatter: "{value}人",
              },
              min:0,
              max:namedMax,
              splitNumber:5,
              interval:namedMax/5,
            }
          ],
          series: [
            {
              name: '紧急异常',
              yAxisIndex:0,
              type: 'bar',
              data:this.replaceNullWithZero(res.data[0].ServerCounts) ,
              // data: [5,45,1,10,79,1,2,3,4,5,8,9,22,44],
              itemStyle: {
                color: "rgba(232,208,129,1)"
              }
            },
            {
              name: '普通异常',
              yAxisIndex:1,
              type: 'bar',
              data: this.replaceNullWithZero(res.data[0].NormCounts) ,
              // data: [5,45,1,10,79,1,2,3,4,5,8,9,22,44],
              itemStyle: {
                color: "rgba(130,231,236,1)"
              }
            },
          ]
        })
      }).catch(error => {
        console.log(error)
      })
    },
    handleValue2Change() {
      if (this.value2 && this.value2.length === 2) {
        console.log(this.value2)
        let [startDate, endDate] = this.value2;
        startDate = new Date(startDate);
        startDate.setHours(0, 0, 0, 0);
        endDate = new Date(endDate);
        endDate.setHours(23, 59, 59, 999);
        const oneWeek = 7 * 24 * 60 * 60 * 1000; // 7 天的毫秒数
        if (endDate - startDate > oneWeek) {
          this.$message.error('最多只能选择7天的时间范围');
          this.value2 = this.setDefaultDateRange(); // 清空选择
          this.getValue2Info()
          return; // 阻止继续执行
        }
        this.value2 = [startDate, endDate];
      }
      this.getValue2Info()
    },
    getValue2Info() {
      // console.log(this.value2)
      const query ={StartTime: this.changeDate(this.value2[0]),EndTime: this.changeDate(this.value2[1]),dogId:1}
      // console.log(query)
      getCruisePieDate(query).then(res => {
        // console.log(res)
        this.arriveNum = res.data[2].value
        this.unarriveNum = res.data[1].value
        this.echartTwo.setOption({
          title: {
            text: res.data[0].value.toString()+'次',
            left: 'center',
            top: 'center',
            textStyle: {
              color: '#ccc'
            }
          },
          series: [
            {
              type: 'pie',
              data: [
                {
                  value: res.data[1].value,
                  name: '严重异常'
                },
                {
                  value: res.data[2].value,
                  name: '普通异常'
                }
              ],
            }
          ]
        })
      }).catch(error => {
        console.log(error)
      })
    },
    // 将数据转变成有横岗的时间形式
    changeDate(date) {
      var year = date.getFullYear();//年
      var month = date.getMonth() + 1;//月
      var strDate = date.getDate();//日
      var hour = date.getHours();//时
      var minute = date.getMinutes();//分
      month = month > 9 ? month : '0' + month
      strDate = strDate > 9 ? strDate : '0' + strDate
      hour = hour > 9 ? hour : '0' + hour
      minute = minute > 9 ? minute : '0' + minute
      var newdate = year+'-'+month+'-'+strDate+' '+hour+':'+minute
      return newdate
    },
  },
};
</script>
<style lang="scss" scoped>
::v-deep .el-input__inner {
  background-color: transparent !important;
  border-color:#80ffff;
  //box-shadow: 1px 1px 5px 1px  RGB(128,255,255,0.8) inset;
  height: 30px;
  width: 240px;
  margin-left: 70px;
}
::v-deep .el-range-input {
  background-color: transparent !important;
  color: #d0d0d0;
}
.bottom_centre {
  height: 100%;
  width: 100%;
  padding: 3%;
  display: flex;
  justify-content: space-between;
  .item {
    width: 48%;
    .result-title {
      height: 10%;
      background: linear-gradient( 270deg, rgba(32,123,175,0.54) 0%, rgba(24,98,149,0.5) 24%, #0E4577 100%);
      .decorate {
        color: rgb(123,255,244);
        font-size: 22px;
        text-align: left;
      }
      .title_font{
        font-family: PingFangSC, PingFang SC;
        font-weight: 800;
        font-size: 16px;
        line-height: 22px;
        text-align: left;
      }
    }
    .charts-one {
      width: 100%;
      height: 100%;
    }
    .echart-content {
      width: 100%;
      height: 90%;
      display: flex;
      justify-content: space-between;
      align-items: center;
      .charts-two {
        background: rgba(53,78,102,0.6);
        width: 55%;
        height: 85%;
        .pie {
          height: 100%;
          width: 100%;
        }
      }
      .result-info {
        width: 40%;
        height: 85%;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        .info-item {
          height: 45%;
          background: rgba(53,78,102,0.6);
          display: flex;
          align-items: center;
          .arrive-img {
            width: 30%;
            margin: 10%;
          }
          .arrive-text {
            width: 60%;
            display: flex;
            flex-direction: column;
            justify-content: center;
            font-size: 12px;
            .font {
              font-weight: 400;
              font-size: 14px;
              color: #D8EDFF;
              line-height: 20px;
              text-align: left;
              font-style: normal;
            }
            .arrive-num {
              font-weight: normal;
              font-size: 24px;
              color: #FFFFFF;
              line-height: 24px;
              text-align: left;
              font-style: normal;
            }
          }
        }
      }

    }
  }
}
</style>
