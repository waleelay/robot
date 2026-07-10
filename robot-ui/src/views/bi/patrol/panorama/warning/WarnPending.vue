<template>
  <div class="flx-center custom-warning p20">
    <svg-icon icon-class="warning" style="font-size: 20px; color: #FFDD00"></svg-icon>
    <span class="ml10 info">{{ content }}</span>
    <span class="ml10 view">点击查看</span>
  </div>
</template>

<script>
export default {
  name: 'WarnPending',
  props: {
    title: {
      type: String,
      default: ''
    },
    content: {
      type: String,
      default: ''
    }
  }
}
</script>

<style lang="scss" scoped>
  .custom-warning {
    border-radius: 2px;
    border: 1px solid #FF0202;
    background: rgba(72, 9, 9, 0.50);
    box-shadow: 0 0 20px 0 #B30000 inset;
    backdrop-filter: blur(5px);
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
  }
</style>
