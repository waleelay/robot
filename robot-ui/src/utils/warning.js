/*
 * @Author: dengxumei
 * @Date: 2026-03-31 16:18:23
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-03-31 16:29:01
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\utils\warning.js
 * @Version: 
 */
import Vue from 'vue'
const 
Vue.directive('warning', {
  bind(el, binding, vnode) {
    const handleClick = () => {
      // 获取传入的数据
      const data = binding.value || {}
      el._clickHandler = handleClick;
      el.addEventListener('click', handleClick)
    }
  },
  unbind(el) {
    if (el._clickHandler) {
      el.removeEventListener('click', el._clickHandler)
      delete el._clickHandler
    }
  }
})
