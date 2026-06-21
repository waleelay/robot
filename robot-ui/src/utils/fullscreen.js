export const events = [
  'fullscreenchange',
  'webkitfullscreenchange',
  'mozfullscreenchange',
  'MSFullscreenChange'
]
// 切换全屏
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
    // 退出全屏
    exitFullscreen()
  }
}


// 检查全屏状态
export const getFullscreenStatus = () => {
  return !!(
    document.fullscreenElement ||
    document.mozFullScreenElement ||
    document.webkitFullscreenElement ||
    document.msFullscreenElement
  )
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