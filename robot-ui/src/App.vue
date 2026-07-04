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
  <div id="app" :class="{'big-screen-background' :isBigScreen, 'default-background': !isBigScreen}">
    <router-view />
  </div>
</template>

<script>
import { getBasicMessage } from "@/api/menu"
import axios from "axios";

export default {
  name: "App",
  data() {
    return {
      isBigScreen: false
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
