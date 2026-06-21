<template>
  <div class="side">
    <div class="side-container h100 flex-column">
      <div class="back mb10 flx-center">
        <svg-icon icon-class="back" style="font-size: 20px;" />返回
      </div>
      <div v-for="item in sideList" :key="item.index" class="side-item flx-center flex-column" :class="{ 'is-active': activeIndex === item.index }" @click="activeIndex = item.index; $emit('updateSide', item.index)">
        <img v-if="activeIndex === item.index" :src="item.imgActive" alt="" width="70" height="70">
        <img v-else :src="item.imgNormal" alt="" width="50" height="50">
        <span :style="{ 'margin-top': activeIndex === item.index ? '0px' : '10px' }">{{ item.name }}</span>
      </div>
    </div>
  </div>
</template>

<script>

export default {
  name: 'Side',
  props: {
    activeSide: {
      type: String,
      default: 'panorama'
    }
  },
  data() {
    return {
      activeIndex: this.activeSide,
      sideList: [
        {
          index: 'panorama',
          name: '全景\n地图',
          imgActive: require('@/assets/images/new-bi/side-address-active.png'),
          imgNormal: require('@/assets/images/new-bi/side-address.png'),
        },
        {
          index: 'monitor',
          name: '实时\n监控',
          imgActive: require('@/assets/images/new-bi/side-monitor-active.png'),
          imgNormal: require('@/assets/images/new-bi/side-monitor.png'),
        },
        {
          index: 'business',
          name: '业务\n管理',
          imgActive: require('@/assets/images/new-bi/side-business-active.png'),
          imgNormal: require('@/assets/images/new-bi/side-business.png'),
        },
        {
          index: 'app',
          name: '更多\n应用',
          imgActive: require('@/assets/images/new-bi/side-app-active.png'),
          imgNormal: require('@/assets/images/new-bi/side-app.png'),
        },
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
.side {
  position: fixed;
  left: 0;
  top: 94px;
  height: calc(100vh - 94px);
  background: rgba(0, 0, 0, 0.10);
  backdrop-filter: blur(10px);
  z-index: 1;
  .back {
    width: 72px;
    height: 38px;
    color: #159AFF;
    font-family: YouSheBiaoTiHei;
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
    line-height: 38px;
    letter-spacing: 0.28px;
    border: 1px solid #159AFF;
    background: linear-gradient(180deg, rgba(21, 154, 255, 0.00) 0%, rgba(2, 6, 14, 0.20) 50.46%, rgba(1, 11, 74, 0.50) 100%);
    box-shadow: 0 -4px 10px 0 #0F4B70 inset;
    backdrop-filter: blur(5px);
  }
}
.side-container {
  width: 72px;
  .side-item {
    flex: 1;
    height: 218px;
    color: #159AFF;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    font-weight: 600;
    line-height: 26px; /* 162.5% */
    border: 1px solid #159AFF;
    background: linear-gradient(180deg, rgba(21, 154, 255, 0.00) 0%, rgba(2, 6, 14, 0.20) 50.46%, rgba(1, 11, 74, 0.50) 100%);
    box-shadow: 0 -4px 10px 0 #159AFF inset;
    backdrop-filter: blur(5px);
    cursor: pointer;
    &.is-active {
      color: #0BF9FE;
      font-weight: 700;;
      font-size: 18px;
      border: 1px solid #078385;
      background: linear-gradient(180deg, rgba(0, 250, 255, 0.00) 0%, rgba(0, 79, 81, 0.50) 100%);
      box-shadow: 0 0 10px 0 #0BF9FE inset;
      backdrop-filter: blur(5px);
    }
    & + .side-item {
      margin-top: 10px;
    }
    span {
      white-space: pre-line;// 保留换行符
      // white-space: pre-wrap;// 保留空白和换行符
    }
  }
}
</style>