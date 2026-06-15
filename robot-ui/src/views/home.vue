<template>
  <div class="home-bg p20 h100">
    <div class="overview d-flex p20">
      <div class="item flex1 flex-column flx-justify-between yjr">
        <div class="t">已接入装备数量</div>
        <div class="num"><span>{{ sbglData.allUpperCount }}</span>个</div>
      </div>
      <div class="item flex1 flex-column flx-justify-between yxz">
        <div class="t">运行中装备数量</div>
        <div class="num"><span>{{ sbglData.runningCount }}</span>个</div>
      </div>
      <div class="item flex1 flex-column flx-justify-between kxz">
        <div class="t">空闲中装备数量</div>
        <div class="num"><span>{{ sbglData.idleCount }}</span>个</div>
      </div>
      <div class="item flex1 flex-column flx-justify-between lx">
        <div class="t">离线装备数量</div>
        <div class="num"><span>{{ sbglData.offlineCount }}</span>个</div>
      </div>
      <div class="item flex1 flex-column flx-justify-between gj">
        <div class="t">告警数量</div>
        <div class="d-flex w100 mt10">
          <div class="flex1 flex-column">
            <span class="t2">设备告警</span>
            <div class="num mt10 pl5"><span>{{ gjData.deviceAlert }}</span>个</div>
          </div>
          <div class="flex1 flex-column">
            <span class="t2">业务告警</span>
            <div class="num mt10 pl5"><span>{{ gjData.businessAlert }}</span>个</div>
          </div>
          <div class="flex1 flex-column">
            <span class="t2">任务告警</span>
            <div class="num mt10 pl5"><span>{{ gjData.taskAlert }}</span>个</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getDeviceOverview, getWarningInfo } from '../api/bi';

  export default {
    data() {
      return {
        sbglData: {
          idleCount: 0,// 空闲
          runningCount: 0,// 运行中
          allRobotCount: 0,// 接入数量
          allUpperCount: 0,// 上装设备
          offlineCount: 0,// 离线
        },
        gjData: {
          businessAlert: 0,
          taskAlert: 0,
          deviceAlert: 0,
        }
      }
    },
    created() {
      this.getInfo()
    },
    methods: {
      getInfo() {
        getDeviceOverview().then(res => {
          if (res.code === 200) {
            this.sbglData = { ...this.sbglData, ...res.data }
          }
        })
        getWarningInfo({StartTime: null, EndTime: null, pageNum: 1, pageSize: 5 }).then(res => {
          this.gjData.businessAlert = res.data.businessAlert
          this.gjData.taskAlert = res.data.taskAlert
          this.gjData.deviceAlert = res.data.deviceAlert
        })
      }
    }
  }
</script>

<style lang="scss" scoped>
.home-bg {
  background: #F0F2F5;
}
.overview {
  background: #fff;
  border-radius: 10px;
  gap: 20px;
  & > .item {
    align-items: flex-start;
    padding: 20px;
    height: 150px;
    border-radius: 10px;
    .t {
      font-family: 'Arial Negreta', 'Arial Normal', 'Arial', sans-serif;
      font-weight: 700;
      font-style: normal;
      letter-spacing: 10px;
      color: #FFFFFF;
    }
    .num {
      width: 100%;
      color: #333;
      line-height: 20px;
      letter-spacing: 10px;
      font-family: 'Arial Normal', 'Arial', sans-serif;
      font-weight: 400;
      text-align: right;
      span {
        font-weight: 700;
        font-size: 36px;
        font-family: 'Arial Negreta', 'Arial Normal', 'Arial', sans-serif;
        color: rgba(0, 121, 254, 0.996078431372549);
      }
    }
    &.yjr {
      background: linear-gradient(-63.4644040511838deg, rgba(255, 255, 255, 1) 0%, rgba(0, 121, 254, 1) 100%);
    }
    &.yxz {
      background: linear-gradient(-63.1780187984183deg, rgba(255, 255, 255, 1) 1%, rgba(8, 85, 216, 1) 100%)
    }
    &.kxz {
      background: linear-gradient(-63.2039187060265deg, rgba(255, 255, 255, 1) 0%, rgba(89, 177, 22, 1) 99%);
    }
    &.lx {
      background: linear-gradient(-63.434948822922deg, rgba(255, 255, 255, 1) 0%, rgba(243, 0, 0, 1) 100%)
    }
    &.gj {
      background: linear-gradient(-63.434948822922deg, rgba(255, 255, 255, 1) 0%, rgba(85, 85, 85, 1) 100%);
      .t2 {
        color: #fff;
        font-size: 14px;
        line-height: 16px;
      }
      .num span {
        color: rgba(217, 0, 27, 0.996078431372549)
      }
      .num {
        text-align: left;
        line-height: 42px;
      }
    }
  }
}
</style>