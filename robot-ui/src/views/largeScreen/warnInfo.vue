<!--告警信息-->
<template>
  <div class="warnInfo">
<!--    统计的已处理与未处理异常-->
    <div class="info-content">
      <div class="info-item" v-for="item in infoList" :key="item.key">
        <div class="info-name">{{ item.name }}</div>
        <div class="info-value">{{ item.value }}</div>
      </div>
    </div>
<!--    异常列表-->
    <div class="table-box">
      <el-table :data="tableData"
                @row-click="handleClick"
                @header-click="getWarnList"
                height="190">
        <el-table-column
          v-for="item in tableList"
          :key="item.key"
          :prop="item.key"
          :label="item.name"
          show-overflow-tooltip
        >
          <template slot-scope="scope">
            <span
              :class="{
                processed:
                  item.key == 'state' && scope.row[item.key] === '未处理',
              }"
            >
              {{ scope.row[item.key] }}
            </span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button type="text" @click="handleClick(scope.row)"
            >详情</el-button
          >
        </template>
      </el-table-column> -->
      </el-table>
    </div>
<!--    出现3s的弹窗-->
    <el-dialog
      class="dialog-wrapper"
      v-dialogDrag
      width="1270px"
      height="912px"
      :visible.sync="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      title="异常报告"
    >
      <div class="dialog-content">
        <div class="error-image">
          <div class="info-image">
            <img :src="getImageSrc(dialogImageUrl)" class="image" />
          </div>
          <div class="info-image">
            <img :src="getImageSrc(dialogIRUrl)" class="image" />
          </div>
        </div>
        <div class="info-box">
          <div class="info-title">异常报告</div>
          <div class="error-info">
            <div class="info-details">
              <div
                class="info-row"
                v-for="(item, index) in errorInfo[0]"
                v-if="item.key !== 'FMaxTemperature' || (item.key === 'FMaxTemperature' && errorData[item.key] !== null)"
                :key="index">
                <span class="label">{{ item.label }}：</span>
                <span :class="{urgen: item.key === 'errorType' && errorData[item.key] === '紧急'}">
                    {{ errorData2[item.key] }}
              </span>
              </div>
            </div>
            <div class="info-details">
              <div
                class="info-row"
                v-for="(item, index) in errorInfo[1]"
                v-if="item.key !== 'FMaxTemperature' || (item.key === 'FMaxTemperature' && errorData[item.key] !== null)"
                :key="index">
                <span class="label">{{ item.label }}：</span>
                <span :class="{urgen: item.key === 'errorType' && errorData[item.key] === '紧急'}">
                    {{ errorData2[item.key] }}
              </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
<!--    可以处理异常的弹窗-->
    <el-dialog
      class="dialog-wrapper"
      v-dialogDrag
      width="1270px"
      height="912px"
      :visible.sync="dialogVisible2"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      title="异常报告"
    >
      <div class="dialog-content">
        <div class="error-image">
          <div class="info-image">
            <img :src="getImageSrc(dialogImageUrl2)" class="image" />
          </div>
          <div class="info-image">
            <img :src="getImageSrc(dialogIRUrl2)" class="image" />
          </div>
        </div>
        <div class="info-box">
          <div class="info-title">异常报告</div>
          <div class="error-info">
            <div class="info-details">
              <div
                class="info-row"
                v-for="(item, index) in errorInfo2[0]"
                v-if="item.key !== 'FMaxTemperature' || (item.key === 'FMaxTemperature' && errorData2[item.key] !== null)"
                :key="index">
              <span class="label">{{ item.label }}：</span>
                <span :class="{urgen: item.key === 'errorType' && errorData2[item.key] === '紧急'}">
                    {{ errorData2[item.key] }}
              </span>
              </div>
            </div>
            <div class="info-details">
              <div
                class="info-row"
                v-for="(item, index) in errorInfo2[1]"
                v-if="item.key !== 'FMaxTemperature' || (item.key === 'FMaxTemperature' && errorData2[item.key] !== null)"
                :key="index">
              <span class="label">{{ item.label }}：</span>
                <span v-if="item.key !== 'errorDeal'"
                      :class="{urgen: item.key === 'errorType' && errorData2[item.key] === '紧急'}">
                    {{ errorData2[item.key] }}
              </span>
                <el-switch
                  v-else
                  v-model="errorData2.errorDeal"
                  @change="handleSwitch($event)"
                  active-color="#13ce66"
                  inactive-color="#ff4949"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {warnInfo} from '@/api/cruise/resultandError'
import {mapState} from "vuex";
import { setErrorisDeal } from '@/api/cruise/resultandError'

export default {
  name: "warnInfo",
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    }
  },
  computed: {
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    },
    lastConsumedFirePersonErrorId() {
      return this.$store.getters['websocket/getLastConsumedFirePersonErrorId'];
    }
  },
  watch: {
    dogId(newVal) {
      // console.log("当前机器人 ID 变化:", newVal);
      this.getWarnList()
    },
    //监听到有异常就去再次访问这个列表
    firePersonError: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length -1];
          if (message && message.errorId !== this.lastConsumedFirePersonErrorId) {
            this.$store.commit('websocket/setLastConsumedFirePersonErrorId', message.errorId);
            this.getWarnList()
            this.handleErrorMsg(message)
          }
        }
      },
      immediate: true,
    }
  },
  data() {
    return {
      infoList: {
        processed: { name: "已处理", value: 0 },
        unprocessed: { name: "未处理", value: 0 },
      },
      tableList: [
        { name: "时间", key: "CreateTime" },
        { name: "区域", key: "area" },
        { name: "设备名称", key: "dogName" },
        // { name: "告警类型", key: "alarmType" },
        { name: "告警内容", key: "ErrorName" },
        { name: "状态", key: "state" },
      ],
      tableData: [],
      dialogVisible: false,
      errorInfo: [
        [
          { label:"负责机器", key: "dogName"},
          { label:"任务名称", key: "taskName"},
          { label:"地图名称", key: "mapName"},
          { label:"点位名称", key: "pointName"},
        ],
        [
          { label:"异常类型", key: "errorName"},
          { label:"紧急情况", key: "errorType"},
          { label: "最高温度", key: "FMaxTemperature"}
        ]
      ],
      errorData:{
        dogName:"1111111",
        taskName:"aaaaaaaaaa",
        mapName:"map",
        pointName: "point",
        errorName: "error",
        errorType: "紧急",
      },
      dialogImageUrl:'',
      dialogIRUrl:'',
      dialogVisible2: false,
      errorInfo2:[
        [
          { label: "负责机器", key: "dogName"},
          { label: "发现异常时间", key: "CreateTime"},
          { label: "地图名称", key: "mapName"},
          { label: "点位名称", key: "pointName"},
        ],[
          { label: "异常类型", key: "errorName"},
          { label: "紧急情况", key: "errorType"},
          { label: "最高温度", key: "FMaxTemperature"},
          { label: "是否处理", key: "errorDeal"},

        ]
      ],
      errorData2:{
        ErrorId:"",
        dogName:"1111111",
        CreateTime:"aaaaaaaaaa",
        mapName:"map",
        pointName: "point",
        errorName: "火灾异常",
        errorType: "紧急",
        errorId:1,
        errorDeal: false,
        isDeal:0,
        FMaxTemperature:90
      },
      dialogImageUrl2:'',
      dialogIRUrl2:'',
    };
  },
  mounted() {
    this.infoList.processed.value = this.tableData.filter(
      (item) => item.status === "已处理"
    ).length;
    this.infoList.unprocessed.value = this.tableData.filter(
      (item) => item.status === "处理中"
    ).length;
  },
  methods: {
    //点击查看弹窗
    handleClick(row) {
      // console.log(row);
      this.dialogVisible2 = true
      this.errorData2 = {
        ErrorId: row.ErrorId,
        dogName: row.dogName,
        CreateTime: row.CreateTime,
        mapName: row.MapName,
        pointName: row.PointName,
        errorName: row.ErrorName,
        errorType: row.ErrorType ? '紧急':'普通',
        isDeal: row.IsDeal,
        errorDeal: row.IsDeal ? true: false,
        FMaxTemperature: row.ErrorName === '火灾异常'? this.formatTemperature(row.FMaxTemperature) : null
      }
      this.dialogImageUrl2 = row.ImageUrl
      this.dialogIRUrl2 = row.nirUrl
    },
    formatTemperature(temp) {
      return `${Math.floor(temp)}℃`
    },
    handleSwitch(value) {
      let query = {
        ErrorId:this.errorData2.ErrorId,
        IsDeal: value?1:0}
      // console.log(query)
      setErrorisDeal(query).then(res => {
        this.$message.success('成功切换异常状态')
        this.getWarnList()
        this.dialogVisible2 = false
      })
    },
    //获取异常数据
    getWarnList() {
      let query = {dogId:this.dogId }
      // console.log(query)
      warnInfo(query).then(res => {
        let data = res.data
        // console.log(data)
        this.infoList.processed.value = data.DealCounts
        this.infoList.unprocessed.value = data.NotDealCounts
        this.tableData = data.errorInfos.map(item => {
          return {
            ...item,
            state: item.IsDeal === 1 ? '已处理' : '未处理',
            area: item.MapName+item.PointName,
          }
        })
      })
    },
    getImageSrc(imgUrl) {
      return `https://${imgUrl}`;
    },
    //websocket开启弹窗S
    handleErrorMsg(message) {
      this.dialogVisible = true
      this.errorData = {
        dogName:message.dogName,
        taskName:message.taskName,
        mapName:message.mapName,
        pointName: message.pointName,
        errorName: message.errorName,
        errorType: message.errorType==1 ? '紧急': '普通',
        FMaxTemperature: message.errorName === '火灾异常'? this.formatTemperature(message.FMaxTemperature) : null
      }
      this.dialogImageUrl = message.imageUrl
      this.dialogIRUrl = message.nirUrl

      if (this.timer) {
        clearTimeout(this.timer);
      }
      // 三秒后隐藏信息
      this.timer = setTimeout(() => {
        this.dialogVisible = false
      }, 3000);
    },
  },
};
</script>
<style lang="scss" scoped>
.warnInfo {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  .info-content {
    display: flex;
    justify-content: space-around;
    .info-item {
      width: 213px;
      height: 66px;
      line-height: 25px;
      font-weight: bold;
      font-size: 16px;
      color: #d8edff;
      padding: 10px 0 10px 80px;
      position: relative;
      &::before {
        content: "";
        width: 36px;
        height: 36px;
        position: absolute;
        left: 15px;
        top: 0;
        bottom: 0;
        margin: auto 0;
      }

      .info-name {
      }
      .info-value {
      }
      &:nth-child(1) {
        background-image: url("../../assets/images/largescreen/warning-bg.png");
        background-size: 100% 100%;
        &::before {
          background-image: url("../../assets/images/largescreen/warning-be.png");
          background-size: 100% 100%;
        }
      }
      &:nth-child(2) {
        background-image: url("../../assets/images/largescreen/warning-bg1.png");
        background-size: 100% 100%;
        &::before {
          background-image: url("../../assets/images/largescreen/warning-be1.png");
          background-size: 100% 100%;
        }
      }
    }
  }
  .table-box {
    width: calc(100% - 5px);
    box-sizing: border-box;
    ::v-deep .el-table {
      width: 426px;
      &::before {
        height: 0;
      }
      .el-table__cell {
        border-bottom: none;
        text-align: center;
        padding: 0;
      }
      .el-table__header-wrapper {
        th {
          height: 32px;
          line-height: 32px;
          background: #093661;
          font-size: 12px;
          color: #ffffff;
        }
      }
      .el-table__body-wrapper {
        background: #062b50;
        .el-table__row {
          font-size: 12px;
          color: #ffffff;
          height: 32px;
          line-height: 32px;
          &:nth-child(odd) {
            background: #062b50;
          }
          &:nth-child(even) {
            background: #063462;
          }
          &:hover {
            background-color: #435f7a;
          }
        }
      }
    }
    ::v-deep .el-table--enable-row-hover .el-table__body tr {
      cursor: pointer;
      &:hover > td {
        background-color: transparent;
      }
    }
    .processed {
      color: #e01010;
    }
  }
  .dialog-wrapper {
    ::v-deep .el-dialog {
      background-color: transparent;

      .el-dialog__header {
        height: 62px;
        position: relative;
        text-align: center;

        .el-dialog__title {
          color: #fff;
          font-size: 32px;
          font-weight: bold;
          position: absolute;
          left: 0;
          right: 0;
          top: 40px;
          margin: 0 auto;
        }

        .el-dialog__headerbtn {
          width: 40px;
          height: 40px;
          background-image: url("../../assets/images/largescreen/dialog-close-btn.png");
          background-size: 100% 100%;
          top: 0;
          bottom: 0;
          margin: auto 0;
        }
      }

      .el-dialog__body {
        height: 694px;
        background-image: url("../../assets/images/largescreen/dialog-bg1.png");
        background-size: 100% 100%;
      }
    }
    .dialog-content {
      height: 100%;
      color: #ffffff;
      padding: 30px 10px 0 10px;
      box-sizing: border-box;

      display: flex;
      flex-direction: column;
      //flex-wrap: wrap;
      //position: relative;

      .info-box {
        width: 100%;
        height: 40%;
        margin-bottom: 10px;
        &.info-box1 {
          width: 1198px;
          ::v-deep .el-table {
            &::before {
              height: 0;
            }
            .el-table__cell {
              border-bottom: none;
            }
            .el-table__header-wrapper {
              th {
                height: 40px;
                line-height: 40px;
                background: #355e87;
                font-size: 14px;
                color: #ffffff;
              }
            }
            .el-table__body-wrapper {
              .el-table__row {
                font-size: 14px;
                color: #ffffff;
                height: 40px;
                line-height: 40px;
                &:nth-child(odd) {
                  background: #12334b;
                }
                &:nth-child(even) {
                  background: #233f5c;
                }
                &:hover {
                  background-color: #355e87;
                }
              }
            }
          }
          ::v-deep .el-table--enable-row-hover .el-table__body tr:hover > td {
            background-color: transparent;
          }
        }

        .info-title {
          font-weight: bold;
          font-size: 16px;
          position: relative;
          padding-left: 10px;
          line-height: 50px;
          &::before {
            content: "";
            position: absolute;
            left: 0;
            top: 0;
            bottom: 0;
            margin: auto 0;
            width: 4px;
            height: 12px;
            background: #1fc6ff;
            border-radius: 3px;
          }
        }
        .error-info {
          display: flex;
          justify-content: space-between;
        }
        .info-details {
          width: 48%;
          .info-row {
            height: 40px;
            line-height: 40px;
            padding: 0 15px;
            box-sizing: border-box;

            &:nth-child(odd) {
              background: #233f5c;
            }
            &:nth-child(even) {
              background: #12334b;
            }
            .label {
              width: 110px;
              display: inline-block;
            }
            .urgen {
              color: #ff4949;
            }
          }
        }
      }
      .error-image {
        height: 60%;
        width: 100%;
        display: flex;
        justify-content: space-between;
        .info-image {
          width: 100%;
          height: 100%;
          background-image: url("../../assets/images/largescreen/dialog-img-bg1.png");
          background-size: 100% 100%;
          display: flex;
          justify-content: center;
          align-items: center;
          .image {
            width: 95%;
            height: 95%;
          }
        }
      }
    }
  }
}
</style>
