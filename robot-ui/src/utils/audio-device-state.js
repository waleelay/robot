/**
 * 合并一次边缘端扬声器状态上报。
 * audioReportRevision 是前端接收序号，不依赖边缘端是否提供时间戳，也不要求值发生变化。
 */
export function mergeReportedAudioState(current, status, { preserveExisting = false } = {}) {
  const next = { ...(current || {}) }
  const volume = status?.volume === undefined ? status?.volumePercent : status.volume
  if (volume !== undefined && !(preserveExisting && next.volume !== undefined)) {
    next.volume = volume
  }
  if (status?.muted !== undefined && !(preserveExisting && next.muted !== undefined)) {
    next.muted = status.muted
  }
  if (status?.audioStatusUpdatedAt !== undefined &&
      !(preserveExisting && next.audioStatusUpdatedAt !== undefined)) {
    next.audioStatusUpdatedAt = status.audioStatusUpdatedAt
  }
  next.audioReportRevision = (Number(next.audioReportRevision) || 0) + 1
  return next
}

export function audioStatusVersion(robotId, deviceId, status = {}) {
  return [
    robotId || '',
    deviceId || '',
    status.audioReportRevision || 0,
    status.audioStatusUpdatedAt || '',
    status.volume,
    status.muted
  ].join('|')
}
