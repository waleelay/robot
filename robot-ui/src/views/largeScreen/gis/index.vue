<template>
  <div class="new-map-div flx-justify-center" :class="{'custom-map-div': !isScreen}">
    <GisMap @changeWebrtcServer="changeWebrtcServer" @getDogList="getDogList" />
  </div>
</template>
<script>
import GisMap from "./components/index.vue";
export default {
  props: {
    isScreen: {
      type: Boolean,
      default: true
    }
  },
  components: {
    GisMap,
  },
  methods: {
    getDogList(data) {
      this.$emit('getDogList', { dogList: data.dogList })
    },
    changeWebrtcServer(type) {
      this.$emit('changeWebrtcServer', type)
    }
  },
  mounted() {
  },
  beforeDestroy() {
    // this.$emit('changeWebrtcServer', 'open')
  }
}
</script>

<style lang="scss" scoped>
.new-map-div:not(.custom-map-div) {
  position: absolute;
  top: 73px;
  left: 0;
  width: 100%;
  height: calc(100% - 73px);
  background: #001529;
}
</style>