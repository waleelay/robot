/**
 * 告警弹窗本地模拟场景
 *
 * 开关（优先级：操作面板即时触发 > URL > localStorage > 文件常量）：
 * - ENABLE_ALARM_DIALOG_MOCK = true 时显示操作面板，并允许解析自动场景
 * - URL：?alarmMock=single_high
 * - localStorage：bi_alarm_mock=medium_then_high
 * - ALARM_MOCK_SCENARIO：非空则作为挂载时自动跑的默认场景
 */

/** 本机联调可开；上线前改为 false（面板一并隐藏） */
export const ENABLE_ALARM_DIALOG_MOCK = false

/** 非空时挂载自动跑一次；有操作面板时建议保持 '' */
export const ALARM_MOCK_SCENARIO = ''

export const STORAGE_KEY = 'bi_alarm_mock'

export const ALARM_MOCK_SCENARIO_META = [
  { key: 'single_medium', label: '单条中风险', desc: '空闲自动弹详情' },
  { key: 'single_high', label: '单条高风险', desc: '黄闪 -> 详情' },
  { key: 'multi_medium', label: '多条中风险', desc: '只自动首条，其余入队' },
  { key: 'multi_high', label: '多条高风险', desc: '关一条后短延迟续弹' },
  { key: 'medium_then_high', label: '中->高抢占', desc: '中风险打开后高风险抢占' },
  { key: 'manual_then_high', label: '手动->高抢占', desc: '手动详情中来高风险抢占' },
  { key: 'manual_then_medium', label: '手动->中入队', desc: '手动详情中来中风险只入队' }
]

export const ALARM_MOCK_SCENARIOS = ['off', ...ALARM_MOCK_SCENARIO_META.map(item => item.key)]

export function resolveAlarmMockScenario() {
  if (!ENABLE_ALARM_DIALOG_MOCK) return 'off'
  const fromUrl = readQueryScenario()
  if (fromUrl) return fromUrl
  try {
    const fromLs = String(localStorage.getItem(STORAGE_KEY) || '').trim()
    if (fromLs) return normalizeScenario(fromLs)
  } catch (e) { /* ignore */ }
  const fromConst = String(ALARM_MOCK_SCENARIO || '').trim()
  if (fromConst) return normalizeScenario(fromConst)
  return 'off'
}

function readQueryScenario() {
  try {
    const params = new URLSearchParams(window.location.search || '')
    const raw = params.get('alarmMock')
    if (raw == null || raw === '') return ''
    return normalizeScenario(raw)
  } catch (e) {
    return ''
  }
}

export function normalizeScenario(raw) {
  const key = String(raw || '').trim().toLowerCase()
  if (!key || key === 'false' || key === '0') return 'off'
  if (key === 'true' || key === '1') return 'single_high'
  if (ALARM_MOCK_SCENARIOS.includes(key)) return key
  return 'off'
}

export function isMockAlarmId(alarmId) {
  return String(alarmId || '').startsWith('mock-')
}

export function isMockRobotId(robotId) {
  return String(robotId || '').startsWith('mock-')
}

function nowText() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
}

export function buildMockAlarm(level, patch) {
  patch = patch || {}
  const lv = String(level || 'HIGH').toUpperCase()
  const isHigh = lv === 'HIGH'
  const id = patch.alarmId || ('mock-' + (isHigh ? 'high' : 'medium') + '-' + Date.now() + '-' + Math.floor(Math.random() * 1000))
  return Object.assign({
    alarmId: id,
    robotId: patch.robotId || ('mock-robot-' + (isHigh ? 'h' : 'm') + '-1'),
    deviceName: patch.deviceName || (isHigh ? '模拟高风险装备' : '模拟中风险装备'),
    title: patch.title || (isHigh ? '【模拟】明火检测' : '【模拟】人员闯入'),
    content: patch.content || (isHigh ? '模拟高风险告警内容，用于弹窗联调' : '模拟中风险告警内容，用于弹窗联调'),
    categoryName: patch.categoryName || (isHigh ? '火灾' : '安防'),
    level: lv,
    levelName: isHigh ? '高风险' : '中风险',
    status: 'unhandled',
    eventTime: patch.eventTime || nowText(),
    taskName: patch.taskName || '模拟巡检任务',
    location: patch.location || {
      lat: 31.2304,
      lng: 121.4737,
      address: '模拟地址'
    },
    snapshotUrl: patch.snapshotUrl || '',
    workflowActionable: false
  }, patch, {
    alarmId: id,
    level: lv
  })
}

export function runAlarmMockScenario(scenario, ctx, options) {
  options = options || {}
  const key = normalizeScenario(scenario)
  if (key === 'off') return
  const immediate = Boolean(options.immediate)
  const t0 = immediate ? 200 : 1200
  const t1 = immediate ? 2800 : 4200
  const tManual = immediate ? 2200 : 3200

  // eslint-disable-next-line no-console
  console.info('[alarm-dialog-mock] scenario=' + key + (immediate ? ' (panel)' : ''))

  const inject = function (level, patch) {
    const next = Object.assign({}, patch || {})
    if (next.alarmId) next.alarmId = next.alarmId + '-' + Date.now()
    ctx.injectAlarm(buildMockAlarm(level, next))
  }
  const delay = function (ms, fn) {
    ctx.schedule(setTimeout(fn, ms))
  }

  switch (key) {
    case 'single_medium':
      delay(t0, function () { inject('MEDIUM', { robotId: 'mock-robot-m1', alarmId: 'mock-medium-1' }) })
      break
    case 'single_high':
      delay(t0, function () { inject('HIGH', { robotId: 'mock-robot-h1', alarmId: 'mock-high-1' }) })
      break
    case 'multi_medium':
      delay(t0, function () {
        inject('MEDIUM', { robotId: 'mock-robot-m1', alarmId: 'mock-medium-a', title: '【模拟】中风险-A' })
        inject('MEDIUM', { robotId: 'mock-robot-m2', alarmId: 'mock-medium-b', title: '【模拟】中风险-B' })
        inject('MEDIUM', { robotId: 'mock-robot-m3', alarmId: 'mock-medium-c', title: '【模拟】中风险-C' })
      })
      break
    case 'multi_high':
      delay(t0, function () {
        inject('HIGH', { robotId: 'mock-robot-h1', alarmId: 'mock-high-a', title: '【模拟】高风险-A' })
        inject('HIGH', { robotId: 'mock-robot-h2', alarmId: 'mock-high-b', title: '【模拟】高风险-B' })
      })
      break
    case 'medium_then_high':
      delay(t0, function () { inject('MEDIUM', { robotId: 'mock-robot-m1', alarmId: 'mock-medium-then' }) })
      delay(t1, function () { inject('HIGH', { robotId: 'mock-robot-h1', alarmId: 'mock-high-preempt' }) })
      break
    case 'manual_then_high':
      delay(t0, function () {
        const manual = buildMockAlarm('MEDIUM', {
          robotId: 'mock-robot-manual',
          alarmId: 'mock-manual-medium-' + Date.now(),
          title: '【模拟】手动打开的中风险'
        })
        ctx.injectAlarm(manual)
        ctx.openManual(manual)
      })
      delay(tManual, function () { inject('HIGH', { robotId: 'mock-robot-h1', alarmId: 'mock-high-after-manual' }) })
      break
    case 'manual_then_medium':
      delay(t0, function () {
        const manual = buildMockAlarm('HIGH', {
          robotId: 'mock-robot-manual',
          alarmId: 'mock-manual-high-' + Date.now(),
          title: '【模拟】手动打开的告警'
        })
        ctx.injectAlarm(manual)
        ctx.openManual(manual)
      })
      delay(tManual, function () {
        inject('MEDIUM', {
          robotId: 'mock-robot-m2',
          alarmId: 'mock-medium-after-manual',
          title: '【模拟】后来的中风险(应只入队)'
        })
      })
      break
    default:
      break
  }
}
