import Vue from 'vue'

import Cookies from 'js-cookie'

import Element from 'element-ui'
import './assets/styles/element-variables.scss'

import './utils/dialogDrag'
import '@/assets/styles/index.scss' // global css
import '@/assets/styles/font.css' // font css
import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

import './assets/icons' // icon
import './permission' // permission control

//echarts
import * as echarts from "echarts"
//视频播放
import './static/adapter.min'
import './static/webrtcstreamer'

// 全局方法挂载
Vue.prototype.download = download
Vue.prototype.$echarts = echarts


Vue.use(Element, {
  size: Cookies.get('size') || 'medium' // set element-ui default size
})
Vue.use(directive)
Vue.use(plugins)

/**
 * If you don't want to use mock-server
 * you want to use MockJs for mock api
 * you can execute: mockXHR()
 *
 * Currently MockJs will be used in the production environment,
 * please remove it before going online! ! !
 */

Vue.config.productionTip = false

new Vue({
  el: '#app',
  router,
  store,
  // devtools: process.env.NODE_ENV === "development",
  devtools: true,
  render: h => h(App),
  created() {
    // 开发环境需要打开
    // this.$store.dispatch('websocket/initWebSocket');
    // this.$store.dispatch('voiceCall/initWebsocket');
    // this.$store.dispatch('bigScreen/initializeStore');
  }
})
