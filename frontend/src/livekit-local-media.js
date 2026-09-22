const MICROPHONE_SOURCE = 'microphone'
export const ROOM_DISCONNECT_TIMEOUT_MS = 3000

function cancelledError() {
  const error = new Error('对讲启动已取消')
  error.code = 'MEDIA_OPERATION_CANCELLED'
  return error
}

/**
 * LiveKit 断开属于尽力而为的网络清理，不能无限阻塞页面销毁和本地状态释放。
 */
export async function disconnectRoomSafely(room, {
  timeoutMs = ROOM_DISCONNECT_TIMEOUT_MS,
  context = 'LiveKit Room'
} = {}) {
  if (!room || typeof room.disconnect !== 'function') return false
  let timer = null
  const timeout = new Promise(resolve => {
    timer = setTimeout(() => resolve('timeout'), Math.max(0, timeoutMs))
  })
  try {
    const outcome = await Promise.race([
      Promise.resolve().then(() => room.disconnect()).then(() => 'disconnected'),
      timeout
    ])
    if (outcome === 'timeout') {
      console.warn(`[media] ${context} 断开超时，继续释放本地状态`)
      return false
    }
    return true
  } catch (error) {
    console.warn(`[media] ${context} 断开失败`, error)
    return false
  } finally {
    if (timer !== null) clearTimeout(timer)
  }
}

export async function waitForMediaOperation(task, operation, {
  onCancel = null,
  onLateSuccess = null
} = {}) {
  const signal = operation && operation.controller && operation.controller.signal
  if (signal && signal.aborted) {
    if (onCancel) await onCancel()
    throw cancelledError()
  }
  const taskPromise = Promise.resolve().then(task)
  if (!signal) return taskPromise
  let rejectCancellation
  const cancellation = new Promise((resolve, reject) => { rejectCancellation = reject })
  const onAbort = () => {
    Promise.resolve(onCancel && onCancel()).catch(() => {})
    rejectCancellation(cancelledError())
  }
  signal.addEventListener('abort', onAbort, { once: true })
  taskPromise.then(() => {
    if (signal.aborted && onLateSuccess) Promise.resolve(onLateSuccess()).catch(() => {})
  }, () => {})
  try {
    const result = await Promise.race([taskPromise, cancellation])
    if (signal.aborted) {
      if (onLateSuccess) await onLateSuccess()
      throw cancelledError()
    }
    return result
  } finally {
    signal.removeEventListener('abort', onAbort)
  }
}

/** 调试前端挂断时必须取消发布并停止采集，不能只把麦克风静音。 */
export async function releaseLocalMicrophone(room) {
  const participant = room && room.localParticipant
  if (!participant) return
  const publication = typeof participant.getTrackPublication === 'function'
    ? participant.getTrackPublication(MICROPHONE_SOURCE)
    : null
  const track = publication && publication.track
  if (!track) return
  try {
    if (typeof participant.unpublishTrack === 'function') {
      const unpublish = participant.unpublishTrack(track, false)
      if (unpublish && typeof unpublish.catch === 'function') {
        unpublish.catch(error => console.warn('[media] 取消发布本地麦克风失败', error))
      }
    }
  } catch (error) {
    console.warn('[media] 取消发布本地麦克风失败', error)
  } finally {
    if (typeof track.stop === 'function') {
      try { track.stop() } catch (error) { console.warn('[media] 停止本地麦克风轨道失败', error) }
    }
    const mediaStreamTrack = track.mediaStreamTrack
    if (mediaStreamTrack && mediaStreamTrack.readyState !== 'ended' && typeof mediaStreamTrack.stop === 'function') {
      try { mediaStreamTrack.stop() } catch (error) { console.warn('[media] 停止底层麦克风采集失败', error) }
    }
  }
}

/** LiveKit 启用请求不可取消；用生命周期信号抢占等待，并清理迟到成功的轨道。 */
export async function enableLocalMicrophone(room, operation) {
  const participant = room && room.localParticipant
  if (!participant || typeof participant.setMicrophoneEnabled !== 'function') {
    throw new Error('对讲媒体连接失败')
  }
  await waitForMediaOperation(() => participant.setMicrophoneEnabled(true, {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true
    }, {
      name: 'audio.operator.mic'
    }), operation, {
    onCancel: () => releaseLocalMicrophone(room),
    onLateSuccess: () => releaseLocalMicrophone(room)
  })
}

export async function connectRoomWithCancellation(room, livekitUrl, token, operation) {
  const disconnect = () => disconnectRoomSafely(room, { context: '取消中的 LiveKit Room' })
  return waitForMediaOperation(() => room.connect(livekitUrl, token), operation, {
    onCancel: disconnect,
    onLateSuccess: disconnect
  })
}
