const MICROPHONE_SOURCE = 'microphone'
const intentionalDisconnectRooms = new WeakSet()
export const ROOM_DISCONNECT_TIMEOUT_MS = 3000

export function markIntentionalRoomDisconnect(room) {
  if (room && (typeof room === 'object' || typeof room === 'function')) {
    intentionalDisconnectRooms.add(room)
  }
}

export function isIntentionalRoomDisconnect(room) {
  return Boolean(room && intentionalDisconnectRooms.has(room))
}

export function mediaOperationCancelledError() {
  const error = new Error('对讲启动已取消')
  error.code = 'MEDIA_OPERATION_CANCELLED'
  return error
}

/**
 * LiveKit 断线是尽力而为的网络清理，不能无限阻塞页面状态和通话租约释放。
 * 即使 SDK Promise 永久 pending，也在截止时间后完成本地生命周期收口。
 */
export async function disconnectRoomSafely(room, {
  timeoutMs = ROOM_DISCONNECT_TIMEOUT_MS,
  context = 'LiveKit Room'
} = {}) {
  if (!room || typeof room.disconnect !== 'function') return false
  markIntentionalRoomDisconnect(room)
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

/**
 * LiveKit 部分异步调用不接收 AbortSignal。这里让页面生命周期可以立即结束等待，
 * 同时保留对原 Promise 的观察，在它迟到成功时执行资源回收。
 */
export async function waitForMediaOperation(task, operation, {
  onCancel = null,
  onLateSuccess = null
} = {}) {
  const signal = operation && operation.controller && operation.controller.signal
  if (signal && signal.aborted) {
    if (onCancel) await onCancel()
    throw mediaOperationCancelledError()
  }

  const taskPromise = Promise.resolve().then(task)
  if (!signal) return taskPromise

  let rejectCancellation
  const cancellation = new Promise((resolve, reject) => { rejectCancellation = reject })
  const onAbort = () => {
    Promise.resolve(onCancel && onCancel()).catch(error => {
      console.warn('[media] 取消媒体操作时清理失败', error)
    })
    rejectCancellation(mediaOperationCancelledError())
  }
  signal.addEventListener('abort', onAbort, { once: true })
  taskPromise.then(() => {
    if (signal.aborted && onLateSuccess) {
      Promise.resolve(onLateSuccess()).catch(error => {
        console.warn('[media] 清理迟到成功的媒体操作失败', error)
      })
    }
  }, () => {})

  try {
    const result = await Promise.race([taskPromise, cancellation])
    if (signal.aborted) {
      if (onLateSuccess) await onLateSuccess()
      throw mediaOperationCancelledError()
    }
    return result
  } finally {
    signal.removeEventListener('abort', onAbort)
  }
}

function microphonePublications(participant) {
  if (!participant) return []
  const publications = []
  let direct = null
  try {
    direct = typeof participant.getTrackPublication === 'function'
      ? participant.getTrackPublication(MICROPHONE_SOURCE)
      : null
  } catch (error) {
    console.warn('[media] 查询本地麦克风发布失败', error)
  }
  if (direct) publications.push(direct)
  if (participant.trackPublications && typeof participant.trackPublications.forEach === 'function') {
    try {
      participant.trackPublications.forEach(publication => {
        if (publication && publication.source === MICROPHONE_SOURCE && !publications.includes(publication)) {
          publications.push(publication)
        }
      })
    } catch (error) {
      console.warn('[media] 遍历本地麦克风发布失败', error)
    }
  }
  return publications
}

/**
 * 结束通话时释放浏览器麦克风采集。
 * 静音只暂停发送，不会停止 MediaStreamTrack，因此不能用于挂断清理。
 */
export async function releaseLocalMicrophone(room) {
  const participant = room && room.localParticipant
  if (!participant) return
  const publications = microphonePublications(participant)
  await Promise.all(publications.map(async publication => {
    const track = publication.track
    try {
      if (track && typeof participant.unpublishTrack === 'function') {
        const unpublish = participant.unpublishTrack(track, false)
        if (unpublish && typeof unpublish.catch === 'function') {
          unpublish.catch(error => console.warn('[media] 取消发布本地麦克风失败', error))
        }
      }
    } catch (error) {
      console.warn('[media] 取消发布本地麦克风失败', error)
    } finally {
      // SDK 在断线态可能无法完成 unpublish，仍需直接停止底层采集。
      if (track && typeof track.stop === 'function') {
        try { track.stop() } catch (error) { console.warn('[media] 停止本地麦克风轨道失败', error) }
      }
      const mediaStreamTrack = track && track.mediaStreamTrack
      if (mediaStreamTrack && mediaStreamTrack.readyState !== 'ended' && typeof mediaStreamTrack.stop === 'function') {
        try { mediaStreamTrack.stop() } catch (error) { console.warn('[media] 停止底层麦克风采集失败', error) }
      }
    }
  }))
}

/**
 * 启用麦克风时允许页面生命周期抢占等待，并兜底清理迟到成功的轨道。
 * LiveKit 本身不接收 AbortSignal，因此取消后仍需监听原 Promise 的最终结果。
 */
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
  if (!room || typeof room.connect !== 'function') throw new Error('对讲媒体连接失败')
  const disconnect = () => disconnectRoomSafely(room, { context: '取消中的 LiveKit Room' })
  return waitForMediaOperation(() => room.connect(livekitUrl, token), operation, {
    onCancel: disconnect,
    onLateSuccess: disconnect
  })
}

export async function releaseIntercomClientMedia(camera, {
  disconnectRoom = false,
  preserveRemoteAudio = false
} = {}) {
  if (!camera) return
  const room = camera.room
  await releaseLocalMicrophone(room)
  if (!preserveRemoteAudio && camera.remoteAudioTrack && typeof camera.remoteAudioTrack.detach === 'function') {
    try { camera.remoteAudioTrack.detach() } catch (error) { console.warn('[media] 分离远端音频轨道失败', error) }
  }
  const audioElement = camera.remoteAudioElement
  if (!preserveRemoteAudio && audioElement && typeof audioElement.remove === 'function') {
    try { audioElement.remove() } catch (error) { console.warn('[media] 移除远端音频元素失败', error) }
  }
  if (disconnectRoom && room && typeof room.disconnect === 'function') {
    await disconnectRoomSafely(room)
  }
}
