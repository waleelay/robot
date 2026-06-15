<template>
  <video
    class="video"
    ref="video"
    preload="auto"
    muted
    @loadeddata="handleVideoEvent"
    @timeupdate="handleVideoTimeupdate"
    @click="fullScreen"
    style="display: block;"
  >
    <!-- controls -->
    <!-- :poster="poster" -->
    <!-- @canplay="handleVideoEvent" -->
    <!-- autoplay="autoplay" -->
    <source :src="rtsp" type="video/mp4">
  </video>
</template>

<script>
export default {
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
    index: {
      type: Number,
      required: true
    }
  },
  data() {
    return {
    }
  },
  computed: {
    poster() {
      return this.isRstp ? require('@/assets/images/bi/hongwai.png') : ''
    }
  },
  methods: {
    handleVideoEvent() {
      this.$emit('changeVideo', { type: 'status', index: this.index })
    },
    handleVideoTimeupdate(e) {
      this.$emit('changeVideo', { type: 'time', index: this.index, currentTime: e.target.currentTime })
    },
    fullScreen(e) {
      this.$emit('changeVideo', { type: 'fullScreen', index: this.index })
    }
  },
  watch: {
    rtsp: function() {
      console.log(123, this.rtsp);
      
      if (this.rtsp) {
        this.$refs.video.load()
      }
    }
  }
}
</script>
