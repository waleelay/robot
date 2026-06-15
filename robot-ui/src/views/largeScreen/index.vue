<template>
  <div class="contents">
    <div class="info-content">
      <div class="side-box left">
        <ItemWrap title="视频监控" height="310px" className="icon0">
          <MonitorRstp @change="videoMotionControl($event)"/>
        </ItemWrap>
        <ItemWrap title="红外探测" height="310px" className="icon1">
          <VideoIRRstp @change="videoMotionControl($event)"/>
        </ItemWrap>
        <ItemWrap title="巡查报告" height="310px" className="icon2">
          <PatrolReport :dogId="dogId" />
        </ItemWrap>
      </div>
      <div class="middle">
        <Map @toggle-dialog="toggleDialog" :dogId="dogId"/>
      </div>
      <div class="side-box right">
        <ItemWrap title="人员管控" height="590px" className="icon3">
          <PersonalControl :dogId="dogId"/>
        </ItemWrap>
        <ItemWrap title="告警信息" height="330px" className="icon4">
          <WarnInfo :dogId="dogId"/>
        </ItemWrap>
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
  name: "Index",
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
      dogId: '',
      dogName: ''
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
  height: 100%;
  .info-content {
    height: 100%;
    display: flex;
    justify-content: space-evenly;
    .side-box {
      width: 464px;
      height: 961px;
      background-image: url("../../assets/images/largescreen/side-bg.png");
      background-size: 100% 100%;
      padding: 20px 0;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      align-items: center;
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
      width: 954px;
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
