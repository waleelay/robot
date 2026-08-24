import router from './router'
import store from './store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { initAuth, isAuthenticated, login } from '@/auth'
import {
  firstAccessibleRouteName,
  firstPatrolRouteName,
  hasBigscreenPermission,
  isBigscreenPermissionEnabled
} from '@/utils/bigscreen-access'

NProgress.configure({ showSpinner: false })

function redirectToAccessible(next, routeName) {
  if (routeName) {
    next({ name: routeName, replace: true })
    return
  }
  next({ path: '/401', query: { noGoBack: '1' }, replace: true })
}

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
  if (to.path !== '/401'
    && to.path !== '/404'
    && to.matched.some(route => route.meta.requiresAuth)) {
    if (isBigscreenPermissionEnabled()) {
      try {
        await store.dispatch('bigscreenAccess/ensureLoaded')
      } catch (error) {
        console.error('获取大屏菜单权限失败', error)
        next({ path: '/401', query: { noGoBack: '1', reason: 'loadFailed' }, replace: true })
        return
      }
    }
    const permissions = store.getters.bigscreenPermissions
    // `/`、`/bi` 按菜单权限落到第一个可访问页，避免无指挥中心权限时直达 401
    if (to.path === '/' || to.path === '/bi') {
      redirectToAccessible(next, firstAccessibleRouteName(permissions))
      return
    }
    if (to.path === '/bi/patrol') {
      redirectToAccessible(next, firstPatrolRouteName(permissions))
      return
    }
    if (isBigscreenPermissionEnabled()) {
      const authorized = to.matched.every(route => {
        if (route.meta.unavailable) return false
        return !route.meta.permission || hasBigscreenPermission(permissions, route.meta.permission)
      })
      if (!authorized) {
        next({ path: '/401', query: { noGoBack: '1' }, replace: true })
        return
      }
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
