import router from './router'
import store from './store'
import { Message } from 'element-ui'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isRelogin } from '@/utils/request'
import path from "path";

NProgress.configure({ showSpinner: false })


const whiteList = [
  '/login',
  '/register',
  '/bigScreen',
  '/images/1.jpg',
  '/bigScreen/default',
  '/bigScreen/shouye', 
  '/bigScreen/xlxc', 
  '/bigScreen/rygk', 
  '/bigScreen/cljg',
  '/bi/home',
  '/bi/index',
  '/bi/patrol/panorama',
  '/bi/patrol/monitor',
  '/bi/patrol/business',
  '/bi/patrol/business2',
  '/bi/patrol/statistics',
  '/bi/patrol/app',
  '/bi/staff',
  '/bi/vehicle',
  '/bi/demo',
]

router.beforeEach((to, from, next) => {
  NProgress.start(); // 开始顶部进度条动画

  if (getToken()) { // 检查是否有token（用户是否已登录）
    to.meta.title && store.dispatch('settings/setTitle', to.meta.title); // 如果目标路由有meta.title，则设置页面标题
    if (to.path === '/login') {
      store.dispatch('GetInfo').then(() => { // 获取用户信息
        isRelogin.show = false; // 关闭重新登录提示
        next({ path: '/bigScreen' });
      }).catch(err => {
        store.dispatch('LogOut').then(() => { // 如果获取用户信息失败，则登出
          Message.error(err); // 显示错误信息
          next({ path: '/bigScreen' }); // 重定向到首页
        });
      });
      // next({ path: '/bigScreen' }); // 如果已登录，且访问登录页，则重定向到首页
      NProgress.done(); // 结束进度条动画
    } else if (whiteList.indexOf(to.path) !== -1) {
      next(); // 如果目标路由在白名单中，则直接放行
    } else {
      if (store.getters.roles.length === 0) { // 如果用户角色信息未加载
        isRelogin.show = true; // 显示重新登录提示
        store.dispatch('GetInfo').then(() => { // 获取用户信息
          isRelogin.show = false; // 关闭重新登录提示
          store.dispatch('GenerateRoutes').then(accessRoutes => { // 生成可访问的路由表
            router.addRoutes(accessRoutes); // 动态添加可访问路由表
            next({ ...to, replace: true }); // 确保addRoutes已完成，重定向到目标路由
          });
        }).catch(err => {
          store.dispatch('LogOut').then(() => { // 如果获取用户信息失败，则登出
            Message.error(err); // 显示错误信息
            next({ path: '/bigScreen' }); // 重定向到首页
          });
        });
      } else {
        if (to.path === '/') {
          next('/home')
        } else {
          next(); // 如果用户角色信息已加载，则直接放行
        }
      }
    }
  } else {
    // 如果没有token
    if (whiteList.indexOf(to.path) !== -1) { // 如果目标路由在白名单中
      next(); // 直接放行
    } else {
      next({path:'/login'}); // 否则重定向到登录页，并带上重定向参数
      NProgress.done(); // 结束进度条动画
    }
  }
});


router.afterEach(() => {
  NProgress.done()
})
