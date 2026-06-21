<template>
    <div class="contents">
      <div class="top"></div>
      <div v-if="change" class="info-content">
        <div class="left">
          <ItemWrap title="点名总览">
            <Left/>
          </ItemWrap>
        </div>
        <div class="middle">
          <div class="map">
            <Map @toggle-dialog="toggleDialog"/>
          </div>
          <div class="result">
            <ItemWrap title="点名结果统计">
              <Result/>
            </ItemWrap>
          </div>
        </div>
        <div class="right">
          <div class="error">
            <ItemWrap title="点名监控">
              <RollCall/>
            </ItemWrap>
          </div>
          <div class="monitor">
            <ItemWrap title="视频监控">
              <Monitor/>
            </ItemWrap>
          </div>
        </div>
      </div>
      <div v-else class="info-content">
        <div class="left">
          <ItemWrap title="巡航总览">
            <CruiseLeft/>
          </ItemWrap>
        </div>
        <div class="middle">
          <div class="map">
            <Map @toggle-dialog="toggleDialog"/>
          </div>
          <div class="result">
            <ItemWrap title="巡航结果统计">
              <CruiseResult/>
            </ItemWrap>
          </div>
        </div>
        <div class="right">
          <div class="error">
            <ItemWrap title="异常监控">
              <Error/>
            </ItemWrap>
          </div>
          <div class="monitor">
            <ItemWrap title="视频监控">
<!--              <Monitor/>-->
            </ItemWrap>
          </div>
        </div>
      </div>
      <!--弹窗页面（弹窗可以拖动）-->
      <div class="LjDialog">
        <el-dialog
          v-dialogDrag
          width="60%"
          :visible.sync="dialogVisible"
          :modal-append-to-body="false"
          :close-on-click-modal="false"
          :modal="false"
          :show-close="true"
          :before-close="handleClose">
          <Dialog ref="dialogRef"/>
        </el-dialog>
      </div>
    </div>
</template>

<script>
import Left from './left'
import Map from './map'
import Result from './result'
import Error from './error'
import Monitor from './monitor'
import Dialog from './dialog'
import RollCall from './rollCall'
import CruiseLeft from './cruiseLeft'
import CruiseResult from './cruiseResult'
export default {
  name: "Index",
  components: {
    Left,
    Map,
    Result,
    Error,
    Monitor,
    Dialog,
    RollCall,
    CruiseLeft,
    CruiseResult,
  },
  data() {
    return {
      // dialogVisible: true,
      dialogVisible: false,
    };
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  props: {
    change: {
      type: [Boolean, Number], // 支持 Boolean (true/false) 或 Number (1/0)
      default: true, // 默认值
      required: true, // 必须传递
    },
  },
  methods: {
    toggleDialog() {
      // console.log("tanchuang")
      this.dialogVisible = true
    },
    handleClose() {
      this.$refs.dialogRef.destoryPlugin()
      this.dialogVisible = false
      history.go(0);
    }
  },
};
</script>
<style lang="scss" scoped>

.contents {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  .top {
    height: 1%;
  }
  .info-content {
    height: 95%;
    display: flex;
    justify-content: space-evenly;

    .left {
      width: 24%;
    }
    .middle {
      width: 50%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;

      .map {
        height: 56%;
      }
      .result {
        height: 40%;
      }
    }
    .right {
      width: 24%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;

      .error {
        height: 56%;
      }
      .monitor {
        height: 40%;
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
          background: linear-gradient( 180deg, #42E5FF 0%, #1FC6FF 100%);
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
