<template>
  <div class="monitor_rstp">
    <div class="video">
      <VIDEO :rtsp="visurl" :newVisurl="newVisurl" :idName="idName" ref="videoRef" @change="videoMotionControl($event)"/>
    </div>
  </div>
</template>

<script>
import Index from "./index";
import VIDEO from "../../components/bigScreen/video"

export default {
  name: "Home",
  components: { Index, VIDEO },
  props: {
    info: {
      type: Object,
      default: () => ({
        // visurl: 'rtsp://admin:yoseen2018@192.168.1.202:554/Streaming/Channels/101'
      })
    },
    idName: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      visurl: '',
      newVisurl: ''
    };
  },
  computed: {
    //获取视频流地址
    videoUrl() {
      return this.$store.getters['websocket/getVideoUrl'];
    },
  },
  mounted() {
    if (this.info && this.info.videoUrl) {
      this.visurl = this.info.videoUrl      
    }
  },
  beforeDestroy() {},
  methods: {
    videoMotionControl(item) {
      // console.log(item)
      this.$emit('change', item);
    }
  },
  watch: {
    info: function() {
      if (this.info && this.info.videoUrl) {
        this.visurl = this.info.videoUrl
      }
    },
    videoUrl: {
      handler(val) {
        console.log('监听流地址', val)
        if (val) {
          this.newVisurl = val
        }
      },
      immediate: true
    }
  }
};
</script>

<style lang="scss">
.monitor_rstp {
  height: 100%;
  width: 100%;
  .video {
    height: 100%;
    width: 100%;
    display: block;
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
        height: 850px;
        background-image: url("../../assets/images/largescreen/dialog-bg.png");
        background-size: 100% 100%;
      }
    }
    .dialog-content {
      height: 100%;
      padding: 30px 10px 0 10px;
      box-sizing: border-box;
      position: relative;
      // 待对接（假数据）
      &::before {
        content: "";
        width: 1186px;
        height: 708px;
        position: absolute;
        //background-image: url("../../assets/images/largescreen/demo-img3.png");
        background-size: 100% 100%;
      }

      .control-box {
        position: absolute;
        width: 148px;
        height: 148px;
        background-image: url("../../assets/images/largescreen/control-bg.png");
        background-size: 100% 100%;
        right: 24px;
        bottom: 24px;
        .control-item {
          width: 32px;
          height: 32px;
          cursor: pointer;
          border-radius: 50%;
          position: absolute;
          background-image: url("../../assets/images/largescreen/control-btn.png");
          background-size: 100% 100%;
          &:nth-child(1) {
            left: 0;
            right: 0;
            top: 10px;
            margin: 0 auto;
          }
          &:nth-child(2) {
            left: 0;
            right: 0;
            bottom: 10px;
            margin: 0 auto;
            transform: rotate(180deg);
          }
          &:nth-child(3) {
            left: 10px;
            top: 0;
            bottom: 0;
            margin: auto 0;
            transform: rotate(-90deg);
          }
          &:nth-child(4) {
            right: 10px;
            top: 0;
            bottom: 0;
            margin: auto 0;
            transform: rotate(90deg);
          }
          &:hover {
            background-image: url("../../assets/images/largescreen/control-btn1.png");
            background-size: 100% 100%;
          }
        }
        .label {
          width: 44px;
          height: 44px;
          line-height: 44px;
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          margin: auto auto;
          font-weight: bold;
          font-size: 12px;
          color: #ffffff;
          text-align: center;
        }
      }
    }
  }
}
</style>
