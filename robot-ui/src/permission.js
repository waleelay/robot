import router from './router'
import store from './store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { initAuth, isAuthenticated, login } from '@/auth'

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
  if (to.meta.title) {
    store.dispatch('settings/setTitle', to.meta.title)
  }
  next()
})

router.afterEach(() => {
  NProgress.done()
})
