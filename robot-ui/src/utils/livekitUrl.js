/**
 * 将后端返回的 LiveKit 地址转换为当前页面可用的公网入口。
 * HTTPS 大屏统一复用 Nginx 的 /livekit WSS 代理；HTTP 开发环境保留原地址。
 */
function resolveLiveKitUrl(livekitUrl, location) {
  const currentLocation = location
    || (typeof window !== 'undefined' ? window.location : null)
  if (currentLocation && currentLocation.protocol === 'https:') {
    return `wss://${currentLocation.host}/livekit`
  }
  return livekitUrl
}

module.exports = {
  resolveLiveKitUrl
}
