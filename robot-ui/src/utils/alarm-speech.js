const LEVEL_LABEL = {
  high: '高风险',
  medium: '中风险',
  low: '低风险'
}

function alarmIdOf(alarm) {
  return alarm?.alarmId ?? alarm?.id ?? null
}

export function buildAlarmSpeechText(alarm) {
  if (!alarm) return ''
  const levelKey = String(alarm.level || '').trim().toLowerCase()
  const level =
    String(alarm.levelName || '').trim() ||
    LEVEL_LABEL[levelKey] ||
    ''
  const title = String(alarm.title || '').trim() || '任务执行告警'
  const content = String(alarm.content || '').trim()
  return [level, title, content].filter(Boolean).join('。')
}

/**
 * @param {object} alarm
 * @param {{ lastSpokenId?: string|null }} [options]
 * @returns {string|null} spoken alarmId, or null if skipped
 */
export function speakAlarm(alarm, options = {}) {
  if (!alarm || typeof window === 'undefined' || !window.speechSynthesis) return null
  const id = alarmIdOf(alarm)
  if (id == null) return null
  const idText = String(id)
  if (options.lastSpokenId != null && String(options.lastSpokenId) === idText) return null
  const text = buildAlarmSpeechText(alarm)
  if (!text) return null
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  window.speechSynthesis.speak(utterance)
  return idText
}

export function cancelAlarmSpeech() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return
  window.speechSynthesis.cancel()
}

/** Resume synthesis after a user gesture (browser autoplay policy). */
export function unlockAlarmSpeech() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return
  try {
    window.speechSynthesis.resume()
  } catch (e) {
    /* ignore */
  }
}
