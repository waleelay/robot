const STATUS_LABELS = {
  NONE: '无产物',
  PENDING_BIND: '待关联',
  UPLOADING: '上传中',
  FINALIZING: '合并中',
  PROCESSING: '处理中',
  READY: '已就绪',
  PARTIAL_FAILED: '部分失败',
  FAILED: '失败',
  EXPIRED: '已过期',
  ABORTED: '已终止',
  DELETED: '已删除',
  UNKNOWN: '暂不可用'
}

export function indexArtifactProgress(response) {
  return (response && response.instances ? response.instances : []).reduce((result, item) => {
    if (item && item.workflowInstanceId !== undefined && item.workflowInstanceId !== null) {
      result[String(item.workflowInstanceId)] = item
    }
    return result
  }, {})
}

export function artifactProgressFor(instanceProgress, artifact) {
  const fileId = artifact && (artifact.fileId || (artifact.metadata && artifact.metadata.fileId))
  const mediaRef = artifact && (artifact.mediaRef || (artifact.metadata && artifact.metadata.mediaRef))
  return ((instanceProgress && instanceProgress.artifacts) || []).find(item =>
    (fileId && item.fileId === fileId) || (!fileId && mediaRef && item.mediaRef === mediaRef)
  ) || null
}

export function artifactProgressLabel(value) {
  return STATUS_LABELS[value] || value || '暂不可用'
}

export function artifactProgressClass(value) {
  if (value === 'READY' || value === 'NONE') return 'green'
  if (['FAILED', 'PARTIAL_FAILED', 'EXPIRED', 'ABORTED', 'DELETED'].indexOf(value) !== -1) return 'red'
  if (['PENDING_BIND', 'UPLOADING', 'FINALIZING', 'PROCESSING'].indexOf(value) !== -1) return 'orange'
  return 'info'
}

export function artifactProgressPercent(summary) {
  const value = Number(summary && summary.uploadPercent)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0
}
