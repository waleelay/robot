import router from './router'
import store from './store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { initAuth, isAuthenticated, login } from '@/auth'
import { hasBigscreenPermission } from '@/utils/bigscreen-access'

NProgress.configure({ showSpinner: false })

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  try {
    await initAuth()
  } catch (error) {
    console.error('初始化统一登录失败', error)
    NProgress.done()
    return
  }
  if (!isAuthenticated()) {
    try {
      await login(to.fullPath)
    } finally {
      NProgress.done()
    }
    return
  }
  if (to.path !== '/401' && to.path !== '/404' && to.matched.some(route => route.meta.requiresAuth)) {
    try {
      await store.dispatch('bigscreenAccess/ensureLoaded')
    } catch (error) {
      console.error('获取大屏菜单权限失败', error)
      next({ path: '/401', query: { noGoBack: '1' }, replace: true })
      return
    }
    const permissions = store.getters.bigscreenPermissions
    const authorized = to.matched.every(route => {
      if (route.meta.unavailable) return false
      return !route.meta.permission || hasBigscreenPermission(permissions, route.meta.permission)
    })
    if (!authorized) {
      next({ path: '/401', query: { noGoBack: '1' }, replace: true })
      return
    }
  }
  if (to.meta.title) {
    store.dispatch('settings/setTitle', to.meta.title)
  }
  next()
})

router.afterEach(() => {
  NProgress.done()
})
