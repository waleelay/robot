import { getFileInlineUrl, getFilePlayUrl, revokeFileObjectUrl } from '@/api/media'

/** @type {Map<string, { url?: string, expiresAt?: number, loading?: Promise<string> }>} */
const objectUrlCache = new Map()
const objectUrlQueue = []
const MAX_OBJECT_URL_CONCURRENCY = 4
let activeObjectUrlRequests = 0
let objectUrlGeneration = 0

/** @type {Map<string, { playback?: object, expiresAt?: number, loading?: Promise<object> }>} */
const playUrlCache = new Map()

const PLAY_URL_REFRESH_BUFFER_MS = 60 * 1000

function signedUrlStillValid(entry) {
  return Boolean(entry?.url && entry.expiresAt - Date.now() > PLAY_URL_REFRESH_BUFFER_MS)
}

function drainObjectUrlQueue() {
  while (activeObjectUrlRequests < MAX_OBJECT_URL_CONCURRENCY && objectUrlQueue.length) {
    const task = objectUrlQueue.shift()
    activeObjectUrlRequests += 1
    Promise.resolve().then(task.load).then(task.resolve, task.reject).finally(() => {
      activeObjectUrlRequests -= 1
      drainObjectUrlQueue()
    })
  }
}

function scheduleObjectUrlLoad(load) {
  return new Promise((resolve, reject) => {
    objectUrlQueue.push({ load, resolve, reject })
    drainObjectUrlQueue()
  })
}

/**
 * 按 fileId 复用内联预签名地址，并在到期前重新签发。
 */
export async function getCachedFileObjectUrl(fileId) {
  const id = String(fileId || '').trim()
  if (!id) return ''

  const existing = objectUrlCache.get(id)
  if (signedUrlStillValid(existing)) return existing.url
  if (existing?.loading) return existing.loading

  const generation = objectUrlGeneration
  const loading = scheduleObjectUrlLoad(() => getFileInlineUrl(id))
    .then(({ url, expiresAt: expiresAtRaw }) => {
      if (generation !== objectUrlGeneration) {
        revokeFileObjectUrl(url)
        throw new Error('文件缓存已清空')
      }
      const expiresAt = expiresAtRaw ? new Date(expiresAtRaw).getTime() : 0
      objectUrlCache.set(id, {
        url,
        expiresAt: Number.isFinite(expiresAt) ? expiresAt : 0
      })
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
  objectUrlGeneration += 1
  const error = new Error('文件缓存已清空')
  objectUrlQueue.splice(0).forEach(task => task.reject(error))
  objectUrlCache.forEach(entry => {
    if (entry.url) revokeFileObjectUrl(entry.url)
  })
  objectUrlCache.clear()
  playUrlCache.clear()
}
