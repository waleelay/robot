import { createFileObjectUrl, getFilePlayUrl, revokeFileObjectUrl } from '@/api/media'

/** @type {Map<string, { url?: string, loading?: Promise<string> }>} */
const objectUrlCache = new Map()

/** @type {Map<string, { playback?: object, expiresAt?: number, loading?: Promise<object> }>} */
const playUrlCache = new Map()

const PLAY_URL_REFRESH_BUFFER_MS = 60 * 1000

/**
 * 按 fileId 复用 blob URL，避免同一文件在多个组件中重复拉取。
 */
export async function getCachedFileObjectUrl(fileId) {
  const id = String(fileId || '').trim()
  if (!id) return ''

  const existing = objectUrlCache.get(id)
  if (existing?.url) return existing.url
  if (existing?.loading) return existing.loading

  const loading = createFileObjectUrl(id)
    .then(url => {
      objectUrlCache.set(id, { url })
      return url
    })
    .catch(error => {
      objectUrlCache.delete(id)
      throw error
    })

  objectUrlCache.set(id, { loading })
  return loading
}

function playUrlStillValid(entry) {
  if (!entry?.playback?.playUrl) return false
  if (!entry.expiresAt) return true
  return entry.expiresAt - Date.now() > PLAY_URL_REFRESH_BUFFER_MS
}

/**
 * 按 fileId 复用 play-url，避免列表缩略图与详情重复申请播放地址。
 */
export async function getCachedFilePlayUrl(fileId) {
  const id = String(fileId || '').trim()
  if (!id) return null

  const existing = playUrlCache.get(id)
  if (playUrlStillValid(existing)) return existing.playback
  if (existing?.loading) return existing.loading

  const loading = getFilePlayUrl(id)
    .then(playback => {
      const expiresAtRaw = playback?.expiresAt || playback?.data?.expiresAt
      const expiresAt = expiresAtRaw ? new Date(expiresAtRaw).getTime() : 0
      playUrlCache.set(id, {
        playback,
        expiresAt: Number.isFinite(expiresAt) ? expiresAt : 0
      })
      return playback
    })
    .catch(error => {
      playUrlCache.delete(id)
      throw error
    })

  playUrlCache.set(id, { loading })
  return loading
}

export function invalidateCachedFile(fileId) {
  const id = String(fileId || '').trim()
  if (!id) return
  const objectEntry = objectUrlCache.get(id)
  if (objectEntry?.url) revokeFileObjectUrl(objectEntry.url)
  objectUrlCache.delete(id)
  playUrlCache.delete(id)
}

export function clearFileObjectUrlCache() {
  objectUrlCache.forEach(entry => {
    if (entry.url) revokeFileObjectUrl(entry.url)
  })
  objectUrlCache.clear()
  playUrlCache.clear()
}