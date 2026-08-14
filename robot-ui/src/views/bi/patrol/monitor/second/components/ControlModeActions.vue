<template>
  <div v-if="visible" class="control-mode-actions flx-align-center" :class="extraClass">
    <div v-if="isNavMode" class="takeover-btn curp flx-center" @click="$emit('takeover')">立即接管</div>
    <template v-else>
      <div class="takeover-btn curp flx-center" @click="$emit('resume')">恢复</div>
      <div class="takeover-btn curp flx-center ml10" @click="$emit('terminate')">终止任务</div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'ControlModeActions',
  props: {
    isNavMode: Boolean,
    showResume: Boolean,
    extraClass: {
      type: [String, Object, Array],
      default: ''
    }
  },
  computed: {
    visible() {
      return this.isNavMode || this.showResume
    }
  }
}
</script>

<style lang="scss" scoped>
.takeover-btn {
  padding: 4px 10px;
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 12px;
  line-height: 16px;
  border-radius: 4px;
  background: #021328;
  box-shadow: 0 0 14px 2px #09F inset;
  white-space: nowrap;
  &:hover {
    color: #0BF9FE;
  }
}
</style>