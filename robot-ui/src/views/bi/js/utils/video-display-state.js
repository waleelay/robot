const SOURCE_STARTING_STATUSES = ['INIT', 'REQUESTING_CLIENT', 'ROOM_READY']
const SOURCE_FAILED_STATUSES = ['FAILED', 'TIMEOUT']

/**
 * 将会话、观看端和本地 Track 事实转换成纯展示状态。
 * 展示结果不得反向驱动 Room、Track 或 Publisher 生命周期。
 */
export function resolveVideoDisplayState(camera = {}, slot = {}) {
  const status = camera.session?.status || camera.status || slot.session?.status || slot.status || ''
  const deviceOffline = camera.status === 'offline' || (!camera.session && slot.status === 'offline')
  const hasVideo = Boolean(
    camera.hasVideo || camera.remoteVideoTrack || slot.hasVideo || slot.remoteVideoTrack
  )

  // 本地已有 Track 时始终展示画面，避免后端状态短暂滞后遮挡正在播放的视频。
  if (hasVideo) return { visible: false, key: 'playing', text: '', icon: '', tone: 'success' }

  if (deviceOffline || status === 'offline') {
    return { visible: true, key: 'device-offline', text: '设备离线', icon: 'unlink1', tone: 'danger' }
  }
  if (camera.restarting) {
    return { visible: true, key: 'source-restarting', text: '正在重启视频源', icon: 'loading', tone: 'warning' }
  }
  if (SOURCE_FAILED_STATUSES.includes(status)) {
    return { visible: true, key: 'source-failed', text: '视频源启动失败', icon: 'unlink1', tone: 'danger', action: 'restart-source' }
  }
  if (status === 'INTERRUPTED') {
    return { visible: true, key: 'source-interrupted', text: '视频源中断', icon: 'unlink1', tone: 'danger', action: 'restart-source' }
  }
  if (camera.viewerReconnecting) {
    return { visible: true, key: 'viewer-reconnecting', text: '播放连接恢复中', icon: 'loading', tone: 'warning' }
  }
  if (SOURCE_STARTING_STATUSES.includes(status) || (!camera.session && (camera.loading || slot.loading))) {
    return { visible: true, key: 'source-starting', text: '正在启动视频源', icon: 'loading', tone: 'processing' }
  }
  if (camera.connecting) {
    return { visible: true, key: 'viewer-connecting', text: '正在连接播放服务', icon: 'loading', tone: 'processing' }
  }
  if (status === 'STREAMING') {
    return { visible: true, key: 'viewer-failed', text: '播放端异常', icon: 'unlink1', tone: 'danger', action: 'refresh-playback' }
  }
  if (status === 'STOPPING') {
    return { visible: true, key: 'stopping', text: '正在关闭', icon: 'loading', tone: 'processing' }
  }
  return { visible: true, key: 'not-playing', text: '未播放', icon: 'unlink1', tone: 'idle', action: 'refresh-playback' }
}
