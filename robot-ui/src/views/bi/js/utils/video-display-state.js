const SOURCE_STARTING_STATUSES = ['INIT', 'REQUESTING_CLIENT', 'ROOM_READY']
const SOURCE_FAILED_STATUSES = ['FAILED', 'TIMEOUT']
const FIXED_CAMERA_MARKERS = ['FIXED_CAMERA', '固定摄像头']
const FIXED_CAMERA_ERROR_TEXT = {
  FIXED_CAMERA_CONFIG_FAILED: '固定摄像头配置异常',
  RTSP_PROBE_FAILED: '摄像头码流不可达',
  PUBLISH_FAILED: '视频推流启动失败',
  PUBLISH_PROCESS_EXITED: '视频推流进程异常退出',
  GATEWAY_COMMAND_QUEUE_FULL: '视频网关繁忙，请稍后重试',
  CLIENT_PUBLISH_TIMEOUT: '视频源启动超时',
  LK_PUBLISH_TIMEOUT: '视频轨道发布超时',
  TRACK_INTERRUPTED_TIMEOUT: '视频轨道中断'
}

function isFixedCamera(camera, slot, equipment) {
  return [
    camera.sourceType,
    slot.sourceType,
    slot.robot?.sourceType,
    equipment.sourceType,
    equipment.typeCode,
    equipment.equipmentType,
    equipment.type
  ].some(value => FIXED_CAMERA_MARKERS.includes(value))
}

function sourceFailureText(camera, slot, fallback) {
  const session = camera.session || slot.session || {}
  return FIXED_CAMERA_ERROR_TEXT[session.lastErrorCode || session.errorCode] || fallback
}

function usesFixedCameraGateway(equipment = {}) {
  return String(equipment.protocolType || 'RTSP').toUpperCase() !== 'RTMP'
}

function fixedCameraBlockingState(equipment) {
  if (equipment.enabled === false) {
    return { key: 'fixed-camera-disabled', text: '固定摄像头已停用' }
  }
  if (equipment.configReady === false) {
    return { key: 'fixed-camera-config-invalid', text: '固定摄像头配置不完整' }
  }
  const gatewayHealth = equipment.gatewayHealth || {}
  if (usesFixedCameraGateway(equipment) && gatewayHealth.status === 'OFFLINE') {
    return { key: 'fixed-camera-gateway-offline', text: '固定摄像头网关离线' }
  }
  return null
}

function fixedCameraStreamState(equipment) {
  const streamHealth = equipment.streamHealth || {}
  if (streamHealth.status !== 'UNAVAILABLE') return null
  return {
    key: 'fixed-camera-stream-unavailable',
    text: FIXED_CAMERA_ERROR_TEXT[streamHealth.reasonCode] || '摄像头码流不可达'
  }
}

/**
 * 将会话、观看端和本地 Track 事实转换成纯展示状态。
 * 展示结果不得反向驱动 Room、Track 或 Publisher 生命周期。
 */
export function resolveVideoDisplayState(camera = {}, slot = {}, equipment = {}) {
  const status = camera.session?.status || camera.status || slot.session?.status || slot.status || ''
  const deviceOffline = camera.status === 'offline' || (!camera.session && slot.status === 'offline')
  const fixedCamera = isFixedCamera(camera, slot, equipment)
  const hasVideo = Boolean(
    camera.hasVideo || camera.remoteVideoTrack || slot.hasVideo || slot.remoteVideoTrack
  )

  // 本地已有 Track 时始终展示画面，避免后端状态短暂滞后遮挡正在播放的视频。
  if (hasVideo) return { visible: false, key: 'playing', text: '', icon: '', tone: 'success' }

  const fixedBlocking = fixedCamera ? fixedCameraBlockingState(equipment) : null
  if (fixedBlocking) {
    return { visible: true, ...fixedBlocking, icon: 'unlink1', tone: 'danger' }
  }
  if (!fixedCamera && (deviceOffline || status === 'offline')) {
    return { visible: true, key: 'device-offline', text: '设备离线', icon: 'unlink1', tone: 'danger' }
  }
  if (camera.restarting) {
    return { visible: true, key: 'source-restarting', text: '正在重启视频源', icon: 'loading', tone: 'warning' }
  }
  if (SOURCE_FAILED_STATUSES.includes(status)) {
    const text = fixedCamera ? sourceFailureText(camera, slot, '视频源启动失败') : '视频源启动失败'
    return { visible: true, key: 'source-failed', text, icon: 'unlink1', tone: 'danger', action: 'restart-source' }
  }
  if (status === 'INTERRUPTED') {
    const text = fixedCamera ? sourceFailureText(camera, slot, '视频源中断') : '视频源中断'
    return { visible: true, key: 'source-interrupted', text, icon: 'unlink1', tone: 'danger', action: 'restart-source' }
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
  if (fixedCamera) {
    const streamState = fixedCameraStreamState(equipment)
    if (streamState) {
      return { visible: true, ...streamState, icon: 'unlink1', tone: 'danger' }
    }
    const gatewayUnknown = usesFixedCameraGateway(equipment) && equipment.gatewayHealth?.status === 'UNKNOWN'
    const streamUnknown = equipment.streamHealth?.status === 'UNKNOWN'
    if (gatewayUnknown || streamUnknown) {
      return { visible: true, key: 'fixed-camera-health-unknown', text: '摄像头健康状态待确认', icon: 'loading', tone: 'warning', action: 'refresh-playback' }
    }
    if (slot.sourceStartFailed) {
      return { visible: true, key: 'source-failed', text: '视频源启动失败', icon: 'unlink1', tone: 'danger', action: 'restart-source' }
    }
  }
  return { visible: true, key: 'not-playing', text: '未播放', icon: 'unlink1', tone: 'idle', action: 'refresh-playback' }
}
