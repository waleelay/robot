export const events = [
  'fullscreenchange',
  'webkitfullscreenchange',
  'mozfullscreenchange',
  'MSFullscreenChange'
]

/** 当前 Fullscreen API 全屏元素 */
export const getFullscreenElement = () => (
  document.fullscreenElement ||
  document.mozFullScreenElement ||
  document.webkitFullscreenElement ||
  document.msFullscreenElement ||
  null
)

// 切换元素全屏（视频框等）
export const toggleFullscreen = async (isFullscreen, idName) => {
  if (!isFullscreen) {
    await enterFullscreen(idName)
  } else {
    await exitFullscreen()
  }
  await new Promise(resolve => requestAnimationFrame(resolve))
}

export const handleKeydown = (event, isFullscreen) => {
  if (event.key === 'Escape' && isFullscreen) {
    exitFullscreen()
  }
}

// 是否存在任意 Fullscreen API 全屏元素
export const getFullscreenStatus = () => !!getFullscreenElement()

/** 指定元素是否处于 Fullscreen API 全屏 */
export const isElementFullscreen = (idOrEl) => {
  const el = typeof idOrEl === 'string' ? document.getElementById(idOrEl) : idOrEl
  const current = getFullscreenElement()
  return !!el && !!current && current === el
}

/**
 * 页面（网页）是否处于全屏：
 * - documentElement/body 的 Fullscreen API
 * - 或 F11 浏览器窗口全屏（无 API 元素）
 * 不包含视频框等元素全屏
 */
export const isPageFullscreen = () => {
  const el = getFullscreenElement()
  if (el === document.documentElement || el === document.body) return true
  // F11 不会触发 Fullscreen API，需通过窗口尺寸判断；有任意元素全屏时不算 F11
  if (el) return false
  return window.innerHeight === screen.height && window.innerWidth === screen.width
}

/** 切换网页全屏（documentElement） */
export const togglePageFullscreen = async () => {
  const el = getFullscreenElement()
  if (el === document.documentElement || el === document.body) {
    await exitFullscreen()
    return
  }
  // 已是 F11 浏览器全屏时无法用脚本退出
  if (!el && isPageFullscreen()) return
  // 无全屏，或当前是视频等元素全屏：切到网页全屏
  const root = document.documentElement
  const request = root.requestFullscreen || root.webkitRequestFullscreen || root.mozRequestFullScreen || root.msRequestFullscreen
  if (request) await request.call(root)
}

// 进入全屏
export const enterFullscreen = async (idName) => {
  const el = document.getElementById(idName)

  if (!el) return
  const methods = [
    'requestFullscreen',
    'mozRequestFullScreen',
    'webkitRequestFullscreen',
    'msRequestFullscreen'
  ]
  for (const method of methods) {
    if (el[method]) {
      await el[method]()
      break
    }
  }
}

// 退出全屏
export const exitFullscreen = async () => {
  const methods = [
    'exitFullscreen',
    'mozCancelFullScreen',
    'webkitExitFullscreen',
    'msExitFullscreen'
  ]

  for (const method of methods) {
    if (document[method]) {
      await document[method]()
      break
    }
  }
}
