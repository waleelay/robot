<template>
  <div v-if="list.length" class="custom-warning-container">
    <div v-for="item in list" :key="item.id" class="flx-center custom-warning p20 notification-enter-active">
      <svg-icon icon-class="warning" style="font-size: 20px; color: #FFDD00"></svg-icon>
      <span class="ml10 info">{{ item.content }}</span>
      <span class="ml10 view p4">查看</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'WarnPending',
  data() {
    return {
      timer: null,
      list: []
    }
  },
  mounted() {
    // this.timer = setTimeout(() => {
    //   this.list = [
    //     { id: 1, content: '告警事件跟踪:机器狗正在前往A区' },
    //   ]
    // }, 1000);
  },
  beforeDestroy() {
    clearTimeout(this.timer)
  }
}
</script>

<style lang="scss">
.custom-warning-container {
  position: absolute;
  top: 94px;
  right: 48px;
  z-index: 2;
  .custom-warning {
    border-radius: 2px;
    border: 1px solid #FF0202;
    background: rgba(72, 9, 9, 0.50);
    box-shadow: 0 0 20px 0 #B30000 inset;
    backdrop-filter: blur(5px);
    transform: translateY(-50%);
    .info {
      color: #FFF;
      font-size: 16px;
      line-height: 17.517px; /* 109.479% */
    }
    .view {
      color: #FFF;
      font-size: 12px;
      line-height: 16px;
      border-radius: 2px;
      background: #D76F00;
    }
    & + .custom-warning {
      margin-top: 16px;
    }
  }
}

.notification-enter-active {
  animation: slideInRight 1s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}
/* 离场动画 */
.notification-leave-active {
  animation: slideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes slideInRight {
  0% {
    opacity: 0;
    transform: translateX(30px) scale(0.9);
  }
  100% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

@keyframes slideOut {
  0% {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateX(30px) scale(0.9);
  }
}
</style>
