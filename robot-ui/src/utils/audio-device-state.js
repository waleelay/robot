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
