<template>
  <div class="w100 h100 flx-center flex-column empty" :style="{ '--textColor': textColor }">
    <img src="@/assets/images/new-bi/empty.png" :style="{ width, height: 'auto', opacity }" alt="" class="empty-img" />
    <span class="empty-text mt10">{{ text }}</span>
  </div>
</template>

<script>
export default {  
  name: 'Empty',
  props: {
    width: {
      type: String,
      default: '100%'
    },
    textColor: {
      type: String,
      default: '#DFEDFF'
    },
    opacity: {
      type: Number,
      default: 1
    },
    text: {
      type: String,
      default: '暂无数据'
    },
  }
}
</script>

<style lang="scss" scoped>
.empty {
  &-text {
    color: var(--textColor, #DFEDFF);
    font-family: "Microsoft YaHei";
    font-size: 16px;
    line-height: 28px; /* 175% */
  }
}
</style>
