<template>
  <div class="bottom_right">
    <button @click="player">播放</button>
    <video id="video" autoplay width="600" height="400"></video>
  </div>
</template>

<script>
import 'webrtc-adapter';
export default {
  data() {
    return {};
  },
  mounted() {
    // 在页面刷新或关闭时断开 WebRTC 连接
    window.addEventListener('beforeunload', this.handleBeforeUnload);
  },
  beforeDestroy() {
    //似乎可以关闭线程
    // 页面销毁时断开 WebRTC 连接
    if (this.webRtcServer) {
      this.webRtcServer.disconnect();
      this.webRtcServer = null;
    }
    // 移除 beforeunload 事件监听器
    window.removeEventListener('beforeunload', this.handleBeforeUnload);
  },
  destroyed() {
    // 确保事件监听器在组件销毁时被移除
    window.removeEventListener('beforeunload', this.handleBeforeUnload);
  },
  methods: {
    player() {
      this.webRtcServer = new WebRtcStreamer(
        "video",
        'http://192.168.1.115:8000'//本机ip+端口8000
      );

      this.webRtcServer.connect(
        "rtsp://192.168.1.105:8554/test"//这是填自己的rtsp流
      );
    },
    handleBeforeUnload() {
      // 处理页面刷新或关闭时的清理工作
      if (this.webRtcServer) {
        this.webRtcServer.disconnect();
      }
    },
    // 并不能杀进程，只是会清空！
    handleClose() {
      // 手动关闭 WebRTC 连接
      if (this.webRtcServer) {
        this.webRtcServer.disconnect();
        this.webRtcServer = null;
      }
    }
  },

};
</script>
<style lang="scss" scoped>
.bottom_right {
  height: 100%;
  padding: 5%;
}
</style>
