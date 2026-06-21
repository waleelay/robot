<template>
  <div @click="handleClickVideo"
       class="video-contianer"
       :id="idName"
       :class="{ 'controls-visible': isFullScreen }"
       style="cursor: pointer;">
    <video class="video"
           ref="video"
           preload="auto"
           autoplay="autoplay"
           muted />
    <div class="video-msg w100"
         v-if="isFullScreen && rtsp">{{ newVisurl === rtsp ? '当前设备未执行任务' : '当前设备执行任务中' }}</div>
    <el-button type="default"
               class="control-btn start-btn"
               @click="handleOpenAlg"
               :disabled="newVisurl === rtsp || !newVisurl"
               v-if="newVisurl !== 'empty'">
      {{ openAlg ? '关闭推理' : '启用推理' }}
    </el-button>
    <button class="control-btn full-btn"
            @click="toggleFullscreen"
            title="全屏"
            :style="{ display: isFullScreen ? 'block' : 'none' }">
      <i>⛶</i>
    </button>
    <div class="control-box">
      <div class="control-item"
           :title="item.label"
           v-for="(item, index) in controlList"
           :key="index"
           @click="handleControl(item.value)"></div>
      <div class="label">视角</div>
    </div>
    <!-- :poster="poster" -->
    <!-- <div class="mask"
         @click="handleClickVideo"
         :class="{ 'active-video-border': selectStatus }"></div> -->
    <!-- <el-dialog class="dialog-wrapper"
               v-dialogDrag
               width="1270px"
               height="912px"
               :visible.sync="dialogVisible"
               :modal-append-to-body="false"
               :close-on-click-modal="false"
               :modal="false"
               :show-close="true"
               :before-close="handleClose"
               title="视频监控"
               @opened="openDialog"
               @close="closeDialog">
      <div class="dialog-content">
        <div class="largevideo">
          <video class="enlarged-video"
                 ref="enlargedVideo"
                 preload="auto"
                 autoplay="autoplay"
                 muted />
        </div>
        <div class="control-box">
          <div class="control-item"
               :title="item.label"
               v-for="(item, index) in controlList"
               :key="index"
               @click="handleControl(item.value)"></div>
          <div class="label">视角</div>
        </div>
      </div>
    </el-dialog> -->
  </div>
</template>

<script>
import WebRtcStreamer from '../../../public/hk/webrtcstreamer'

export default {
  name: 'videoCom',
  props: {
    rtsp: {
      type: String,
      required: true,
    },
    isRstp: {
      type: Boolean,
      required: false,
      default: false
    },
    isOn: {
      type: Boolean,
      default: false,
    },
    spareId: {
      type: Number,
    },
    selectStatus: {
      type: Boolean,
      default: false,
    },
    idName: {
      type: String,
      default: ''
    },
    newVisurl: {
      type: String,
      default: 'empty'
    }
  },
  computed: {
    // poster() {
    //   return this.isRstp && ? require('@/assets/images/bi/hongwai.png') : ''
    // }
  },
  data() {
    return {
      socket: null,
      result: null,
      pic: null,
      webRtcServer: null,
      enlargedWebRtcServer: null,
      clickCount: 0,
      dialogVisible: false,
      controlList: [
        { label: "上", action: "up", value: 21 },
        { label: "下", action: "down", value: 22 },
        { label: "左", action: "left", value: 23 },
        { label: "右", action: "right", value: 24 },
      ],
      flag: false,
      isFullScreen: false,
      resizeObserver: null,
      openAlg: false
    }
  },
  watch: {
    rtsp() {
      console.log('video---------this.rtsp------this.idName-----', this.rtsp, this.idName)
      if (this.webRtcServer) {
        this.webRtcServer.disconnect()
      }
      if (this.enlargedWebRtcServer) {
        this.enlargedWebRtcServer.disconnect()
      }
      if (this.dialogVisible) {
        this.initEnlargedVideo()
      } else {
        this.initVideo()
      }
    },
    // dialogVisible(newVal) {
    //   console.log('123');

    //   if (newVal) {
    //     // this.initEnlargedVideo()
    //   } else {
    //     // this.stopEnlargedVideo()
    //   }
    // }
  },
  destroyed() {
    console.log('%c销毁视频' + this.webRtcServer, 'color: #00f');
    if (this.webRtcServer) {
      this.webRtcServer.disconnect()
    }
    if (this.enlargedWebRtcServer) {
      this.enlargedWebRtcServer.disconnect()
    }
  },
  beforeCreate() {
    window.onbeforeunload = () => {
      if (this.webRtcServer) {
        this.webRtcServer.disconnect()
      }
      if (this.enlargedWebRtcServer) {
        this.enlargedWebRtcServer.disconnect()
      }
    }
  },
  created() { },
  mounted() {
    // this.initVideo()
    // window.addEventListener('keydown', this.handleKeydown)
    // window.addEventListener('keyup', this.handleKeyup)

    // document.addEventListener('fullscreenchange', this.handleApiFullscreen)
    // document.addEventListener('webkitscreenchange', this.handleApiFullscreen)
    // document.addEventListener('mozfullscreenchange', this.handleApiFullscreen)
    // document.addEventListener('msfullscreenchange', this.handleApiFullscreen)
    // window.addEventListener("resize", () => {
    //   console.log('改变了');

    //   const el = document.getElementById(this.idName)
    //   setTimeout(() => {
    //     this.isFullScreen = this.isElementFullscreen(el)
    //   }, 500);
    // });
    const el = document.getElementById(this.idName)
    this.resizeObserver = new ResizeObserver(entries => {
      for (let entry of entries) {
        // console.log('尺寸变化', entry.contentRect);
        //   setTimeout(() => {
        this.isFullScreen = this.isElementFullscreen(el)
        // }, 500);
      }
    })
    this.resizeObserver.observe(el)
  },
  methods: {
    // 检查元素是否全屏
    isElementFullscreen(element) {
      return (
        document.fullscreenElement === element ||
        document.webkitFullscreenElement === element ||
        document.mozFullScreenElement === element ||
        document.msFullscreenElement === element
      );
    },
    openDialog() {
      this.initEnlargedVideo()
    },
    closeDialog() {
      this.stopEnlargedVideo()
    },
    initVideo(rtspUrl) {
      console.log('打开小窗口连接=======', this.$refs.video);
      try {
        this.webRtcServer = new WebRtcStreamer(
          this.$refs.video,
          process.env.NODE_ENV === 'development' ? process.env.VUE_APP_WEBRTC : `${location.origin}/webrtc`
          // process.env.NODE_ENV === 'development' ? process.env.VUE_APP_WEBRTC : `https://127.0.0.1/webrtc`
        )
        this.webRtcServer.connect(rtspUrl || this.rtsp, null,
          {
            rtsp: {
              transport: 'udp',
              timeout: 10,
              buffertime: 500, // 适当增加缓冲时间(ms)
            },
            webrtc: {
              // videoCodec: 'VP8', // 尝试不同编解码器
              latency: 500, // 目标延迟(ms)
              videoCodec: 'H264',
              // bitrate: 2000 // 限制最大比特率(kbps)
              minBitrate: 2000,
              maxBitrate: 8000,
              startBitrate: 5000,
              adaptive: true
              // audio: true
            }
          }
        )
      } catch (error) {
        console.log(error)
      }
    },
    initEnlargedVideo() {
      console.log('打开窗口', this.$refs.enlargedVideo);
      try {
        if (this.webRtcServer) {
          this.webRtcServer.disconnect()
          this.webRtcServer = null
        }
        this.enlargedWebRtcServer = new WebRtcStreamer(
          this.$refs.enlargedVideo,
          process.env.NODE_ENV === 'development' ? process.env.VUE_APP_WEBRTC : `${location.origin}/webrtc`
          // process.env.NODE_ENV === 'development' ? process.env.VUE_APP_WEBRTC : `https://127.0.0.1/webrtc`
        )
        this.enlargedWebRtcServer.connect(this.rtsp)
      } catch (error) {
        console.log('Enlarged video init error:', error)
      }

    },
    stopEnlargedVideo() {
      console.log('关闭窗口', this.$refs.enlargedVideo);
      if (this.enlargedWebRtcServer) {
        this.enlargedWebRtcServer.disconnect()
        this.enlargedWebRtcServer = null
      }
      this.initVideo()
    },
    dbClick() {
      this.clickCount++
      if (this.clickCount === 2) {
        this.btnFull()
        this.clickCount = 0
      }
      setTimeout(() => {
        if (this.clickCount === 1) {
          this.clickCount = 0
        }
      }, 250)
    },
    btnFull() {
      const elVideo = this.$refs.video
      if (elVideo.webkitRequestFullScreen) {
        elVideo.webkitRequestFullScreen()
      } else if (elVideo.mozRequestFullScreen) {
        elVideo.mozRequestFullScreen()
      } else if (elVideo.requestFullscreen) {
        elVideo.requestFullscreen()
      }
    },
    handleClickVideo() {
      // this.dialogVisible = true
      // const elVideo = this.$refs.video
      const elVideo = document.getElementById(this.idName)
      if (elVideo.requestFullScreen) {
        elVideo.requestFullScreen()
      } else if (elVideo.webkitRequestFullScreen) {
        elVideo.webkitRequestFullScreen()
      } else if (elVideo.mozRequestFullScreen) {
        elVideo.mozRequestFullScreen()
      } else if (elVideo.requestFullscreen) {
        elVideo.requestFullscreen()
      }
    },
    // 启用推理/关闭推理
    handleOpenAlg() {
      this.openAlg = !this.openAlg
      if (this.webRtcServer) {
        this.webRtcServer.disconnect()
      }
      this.initVideo(this.openAlg ? this.newVisurl : this.rtsp)
    },
    toggleFullscreen() {
      this.isApiFullScreen = false
      const videoContainer = document.getElementById(this.idName)
      // if (!document.fullscreenElement) {
      //   if (videoContainer.requestFullscreen) {
      //       videoContainer.requestFullscreen();
      //   } else if (videoContainer.mozRequestFullScreen) {
      //       videoContainer.mozRequestFullScreen();
      //   } else if (videoContainer.webkitRequestFullscreen) {
      //       videoContainer.webkitRequestFullscreen();
      //   } else if (videoContainer.msRequestFullscreen) {
      //       videoContainer.msRequestFullscreen();
      //   }
      // } else {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      } else if (document.mozCancelFullScreen) {
        document.mozCancelFullScreen();
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      } else if (document.msExitFullscreen) {
        document.msExitFullscreen();
      }
      // }
    },
    handleClose(done) {
      this.dialogVisible = false
      done()
    },
    handleControl(item) {
      this.$emit('change', item);
    }
  },
}
</script>

<style scoped lang="scss">
.active-video-border {
  border: 2px salmon solid;
}
.video-contianer {
  position: relative;
  width: 100%;
  height: 100%;
  .video {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    overflow: hidden;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
    background: #000;
  }

  &.controls-visible .control-box {
    opacity: 1;
  }
  .full-btn {
    position: absolute;
    right: 0;
    bottom: 10px;
    padding-right: 8px;
    background: transparent;
    border: none;
    color: white;
    cursor: pointer;
    font-size: 1.1rem;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s;
  }
  .start-btn {
    position: absolute;
    width: auto;
    top: 20px;
    right: 20px;
    height: auto;
    padding: 0 10px;
    line-height: 24px;
    background: #fff;
    &:not(.is-disabled) {
      color: #fff;
      background: #00ac3a;
      border-color: #00ac3a;
      &:hover,
      &:focus {
        color: #fff;
        background: #0fcb4e;
        border-color: #0fcb4e;
      }
    }
    &.is-disabled {
      color: #b7bcc2;
      background: #f3f3f3;
    }
  }

  .video-msg {
    position: absolute;
    top: 40px;
    left: 0;
    text-align: center;
    color: #ff4d4f;
    font-size: 20px;
    letter-spacing: 5px;
  }

  .full-btn:hover {
    background: rgba(255, 255, 255, 0.2);
    transform: scale(1.1);
  }

  .full-btn:active {
    transform: scale(0.95);
  }

  .full-btn.large {
    font-size: 1.4rem;
  }

  .control-box {
    position: absolute;
    width: 148px;
    height: 148px;
    background-image: url("../../assets/images/largescreen/control-bg.png");
    background-size: 100% 100%;
    right: 24px;
    bottom: 24px;
    z-index: 2; /* 确保控制按钮浮在视频上方 */
    opacity: 0;
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

  .mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    cursor: pointer;
  }
  .dialog-wrapper {
    ::v-deep .el-dialog {
      // background-color: transparent;
      background-color: #012c43;
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
        padding: 0;
        position: relative;
      }
    }
    .dialog-content {
      height: 100%;
      padding: 30px 10px 0 10px;
      box-sizing: border-box;
      position: relative;
      //display: flex;
      //justify-content: center;
      //align-items: center;
      .largevideo {
        height: 90%;
        width: 90%;
        .enlarged-video {
          width: 92%;
          height: 90%;
          //height: calc(100% - 178px); /* 留出控制区域空间 */
          object-fit: cover;
          position: absolute;
          top: 50px;
          left: 50px;
          z-index: 1; /* 确保视频在底层 */
        }
      }
      .control-box {
        position: absolute;
        width: 148px;
        height: 148px;
        background-image: url("../../assets/images/largescreen/control-bg.png");
        background-size: 100% 100%;
        right: 24px;
        bottom: 24px;
        z-index: 2; /* 确保控制按钮浮在视频上方 */
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
