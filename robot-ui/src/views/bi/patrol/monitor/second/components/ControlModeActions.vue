<template>
  <div class="control-mode-status flx-justify-between w100" :class="[extraClass, { 'is-spread': spread }]">
    <div class="status-left flx-align-center">
      <span class="status-label">当前状态:</span>
      <div class="status-badge flx-center" :class="isNavMode ? 'is-nav' : 'is-manual'">
        <span>{{ isNavMode ? '自主导航中' : '非自主导航' }}</span>
        <svg-icon icon-class="control" class="status-icon" />
      </div>
    </div>
    <div v-if="showActions" class="status-actions flx-align-center">
      <div
        v-if="isNavMode"
        class="mode-action-btn curp flx-center"
        @click="$emit('takeover')"
      >立即接管</div>
      <template v-else>
        <div class="mode-action-btn curp flx-center" @click="$emit('resume')">恢复</div>
        <div class="mode-action-btn curp flx-center" @click="$emit('terminate')">终止任务</div>
      </template>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ControlModeActions',
  props: {
    isNavMode: Boolean,
    showResume: Boolean,
    /** 状态与按钮两端对齐（地图弹窗宽行） */
    spread: {
      type: Boolean,
      default: false
    },
    extraClass: {
      type: [String, Object, Array],
      default: ''
    }
  },
  computed: {
    // 自主导航始终可接管；非自主导航仅任务中显示恢复/终止
    showActions() {
      return this.isNavMode || this.showResume
    }
  }
}
</script>

<style lang="scss" scoped>
.control-mode-status {
  gap: 20px;
  &.is-spread {
    width: 100%;
    justify-content: space-between;
  }
  &.vertical-class {
    width: auto !important;
    &, .status-left {
      justify-content: flex-start;
      flex-direction: column;
      align-items: flex-start;
    }
  }
}

.status-left {
  gap: 6px;
  
}

.status-label {
  color: #FFF;
  font-family: "Microsoft YaHei";
  font-size: 14px;
  line-height: normal;
  white-space: nowrap;
}

.status-badge {
  gap: 10px;
  padding: 6px 10px;
  color: #FFF;
  font-family: "Alibaba PuHuiTi";
  font-size: 14px;
  line-height: 12px;
  letter-spacing: 0.857px;
  white-space: nowrap;
  &.is-nav {
    width: max-content;
    background: rgba(0, 131, 218, 0.5);
  }
  &.is-manual {
    background: rgba(101, 101, 101, 0.5);
  }
}

.status-icon {
  width: 16px;
  height: 16px;
  font-size: 16px;
  color: #FFF;
}

.status-actions {
  gap: 6px;
}

.mode-action-btn {
  padding: 4px 10px;
  color: #6AC5FF;
  font-family: "Alibaba PuHuiTi";
  font-size: 14px;
  line-height: normal;
  letter-spacing: 0.857px;
  white-space: nowrap;
  background: rgba(17, 69, 108, 0.5);
  border: 1px solid #4AB8FF;
  box-sizing: border-box;
  &:hover {
    color: #0BF9FE;
    border-color: #0BF9FE;
  }
}
</style>
