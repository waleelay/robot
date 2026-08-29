import { getFileContent } from '@/api/media'
import { getCachedFileObjectUrl } from '@/utils/file-object-url-cache'

const FILE_CONTENT_PATH = /\/api\/bigscreen\/control\/files\/([^/]+)\/content/

export const ALARM_SNAPSHOT_OPTIONS = [
  { key: 'visible', label: '云台可见光' },
  { key: 'thermal', label: '云台红外光' },
  { key: 'front', label: '前置摄像头' }
]

export function firstAlarmImageValue(value) {
  if (Array.isArray(value)) return value.find(item => item != null && String(item).trim()) || ''
  return value != null && String(value).trim() ? value : ''
}

export function parseFileIdFromContentUrl(url) {
  if (!url) return ''
  const match = String(url).match(FILE_CONTENT_PATH)
  return match ? decodeURIComponent(match[1]) : ''
}

export function alarmSnapshotFileId(snapshotUrl, key, item = {}) {
  const fileId = parseFileIdFromContentUrl(snapshotUrl?.[key])
  if (fileId) return fileId
  if (key === 'visible') {
    return firstAlarmImageValue(item.imageFileIds) || item.imageFileId || ''
  }
  return ''
}

/** 仅保留 snapshotUrl 有值（或可见光有 imageFileId 兜底）的画面选项 */
export function buildSnapshotOptions(item = {}) {
  const snapshotUrl = item.snapshotUrl || {}
  return ALARM_SNAPSHOT_OPTIONS
    .filter(opt => Boolean(alarmSnapshotFileId(snapshotUrl, opt.key, item) || firstAlarmImageValue(snapshotUrl[opt.key])))
    .map(opt => ({
      ...opt,
      url: firstAlarmImageValue(snapshotUrl[opt.key]) || '',
      t: Date.now()
    }))
}

export async function loadSnapshotObjectUrls(snapshotUrl, item = {}, keys) {
  const resolvedKeys = keys && keys.length
    ? keys
    : buildSnapshotOptions(item).map(opt => opt.key)
  const urls = {}
  await Promise.all(resolvedKeys.map(async key => {
    const fileId = alarmSnapshotFileId(snapshotUrl, key, item)
    if (!fileId) return
    try {
      urls[key] = await getCachedFileObjectUrl(fileId)
    } catch (error) {
      // 无权限或文件不存在时保持占位，避免 <img> 直接请求 /content 出现 401。
    }
  }))
  return urls
}

/** 仅清空组件本地映射；blob 由 file-object-url-cache 统一复用，不在此处 revoke。 */
export function clearSnapshotObjectUrlMap(urls = {}) {
  Object.keys(urls).forEach(key => {
    delete urls[key]
  })
}

export async function downloadAlarmSnapshotFile(snapshotUrl, key, item = {}, filename) {
  const fileId = alarmSnapshotFileId(snapshotUrl, key, item)
  if (!fileId) throw new Error('暂无可下载图片')
  const data = await getFileContent(fileId)
  const blob = data instanceof Blob ? data : new Blob([data])
  const blobUrl = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = blobUrl
  link.download = filename || `${Date.now()}.jpg`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(blobUrl)
}
