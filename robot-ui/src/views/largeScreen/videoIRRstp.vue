<template>
  <div class="monitor_rstp">
    <VIDEO ref="videoRef" :rtsp="visurl" :isRstp="true" :idName="idName" @change="videoMotionControl($event)"/>
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
        // 红外
        // visurl: 'rtsp://admin:yoseen2018@192.168.1.202:554/Streaming/Channels/201',
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
    };
  },
  mounted() {
    if (this.info && this.info.nirUrl) {
      this.visurl = this.info.nirUrl
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
      if (this.info && this.info.nirUrl) {
        this.visurl = this.info.nirUrl
      }
    }
  }
};
</script>

<style lang="scss">
.monitor_rstp {
  height: 100%;
  width: 100%;
}
</style>
