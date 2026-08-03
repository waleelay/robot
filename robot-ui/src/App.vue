<!--
 * @Author: dengxumei
 * @Date: 2025-09-09 17:00:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-09-12 17:10:41
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\App.vue
 * @Version: 
-->
<template>
<!--  <div id="app">-->
  <div
    id="app"
    :class="{'big-screen-background' :isBigScreen, 'default-background': !isBigScreen && !isBiRoute}"
    :style="appInlineStyle"
  >
    <router-view />
  </div>
</template>

<script>
import { getBasicMessage } from "@/api/menu"
import axios from "axios";

const BI_MAP_BG_PAGES = ['/bi/index', '/bi/patrol/panorama']
const BG_GIS = '#1f2c43'
const BG_SLAM = '#112B4D'
const BG_BI_DEFAULT = '#021328'

export default {
  name: "App",
  data() {
    return {
      isBigScreen: false
    }
  },
  computed: {
    isBiRoute() {
      return (this.$route.path || '').startsWith('/bi')
    },
    // 大屏：地图页按 GIS/SLAM 区分，其余 bi 页统一深色；非 bi 走 default-background
    appBackgroundColor() {
      if (this.isBigScreen || !this.isBiRoute) return null
      const path = this.$route.path
      if (BI_MAP_BG_PAGES.includes(path)) {
        const mapId = this.$store.state.websocketExtraData?.globalMapId
        return mapId === 'gis' ? BG_GIS : BG_SLAM
      }
      return BG_BI_DEFAULT
    },
    appInlineStyle() {
      if (!this.appBackgroundColor) return null
      return { backgroundColor: this.appBackgroundColor }
    }
  },
  mounted() {
    // getBasicMessage()
    // this.initRecording()
  },
  watch: {
    '$route'(to) {
      this.isBigScreen = to.path === '/bigScreen';
    }
  },
  methods: {
    async initRecording() {
      try {
        axios.get(`http://192.168.1.5:10000//serverAudio/sendText?text=语音对讲已启动`);
        console.log('已成功初始化音频');
      } catch (error) {
        console.error('初始化音频操作失败:', error);
      }
    },
  },
  metaInfo() {
    return {
      title: this.$store.state.settings.dynamicTitle && this.$store.state.settings.title,
      titleTemplate: title => {
        return title ? `${title} - ${process.env.VUE_APP_TITLE}` : process.env.VUE_APP_TITLE
      }
    }
  }
};
</script>
<style scoped>
#app.big-screen-background {
  background-color: rgb(2,23,53);
}

#app.default-background {
  background-color: white;
}

#app .theme-picker {
  display: none;
}
/*弹窗打开关闭时关闭默认的过渡动画效果*/
.dialog-fade-enter-active {
  animation: none !important;
}
.dialog-fade-leave-active {
  animation: none !important;
}
</style>
