import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)

function loadAlarmSpeech() {
  const source = readFileSync(new URL('../src/utils/alarm-speech.js', import.meta.url), 'utf8')
  const compiled = require('@babel/core').transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  }).code
  const exports = {}
  const speechSynthesis = {
    cancelCalls: 0,
    speakCalls: [],
    resumeCalls: 0,
    cancel() { this.cancelCalls += 1 },
    speak(utterance) { this.speakCalls.push(utterance) },
    resume() { this.resumeCalls += 1 }
  }
  function SpeechSynthesisUtterance(text) {
    this.text = text
    this.lang = ''
  }
  vm.runInNewContext(compiled, {
    exports,
    window: { speechSynthesis },
    SpeechSynthesisUtterance,
    require: () => ({})
  })
  return { api: exports, speechSynthesis, SpeechSynthesisUtterance }
}

test('告警播报文案优先 levelName，缺省时回退 level 映射', () => {
  const { api } = loadAlarmSpeech()
  assert.equal(
    api.buildAlarmSpeechText({
      levelName: '高风险',
      level: 'medium',
      title: '明火检测',
      content: '检测到明火'
    }),
    '高风险。明火检测。检测到明火'
  )
  assert.equal(
    api.buildAlarmSpeechText({
      level: 'MEDIUM',
      title: '人员闯入',
      content: '区域异常'
    }),
    '中风险。人员闯入。区域异常'
  )
  assert.equal(
    api.buildAlarmSpeechText({ level: 'high', title: '告警' }),
    '高风险。告警'
  )
  assert.equal(api.buildAlarmSpeechText({ title: '仅标题' }), '仅标题')
  assert.equal(
    api.buildAlarmSpeechText({ content: '只有内容' }),
    '任务执行告警。只有内容'
  )
  assert.equal(api.buildAlarmSpeechText(null), '')
})

test('speakAlarm 去重、cancel 后 speak，并设置 zh-CN', () => {
  const { api, speechSynthesis } = loadAlarmSpeech()
  const alarm = {
    alarmId: 'a-1',
    levelName: '高风险',
    title: '明火',
    content: '内容'
  }
  const first = api.speakAlarm(alarm, { lastSpokenId: null })
  assert.equal(first, 'a-1')
  assert.equal(speechSynthesis.cancelCalls, 1)
  assert.equal(speechSynthesis.speakCalls.length, 1)
  assert.equal(speechSynthesis.speakCalls[0].lang, 'zh-CN')
  assert.equal(speechSynthesis.speakCalls[0].text, '高风险。明火。内容')

  const skipped = api.speakAlarm(alarm, { lastSpokenId: 'a-1' })
  assert.equal(skipped, null)
  assert.equal(speechSynthesis.speakCalls.length, 1)

  api.cancelAlarmSpeech()
  assert.equal(speechSynthesis.cancelCalls, 2)

  api.unlockAlarmSpeech()
  assert.equal(speechSynthesis.resumeCalls, 1)
})

test('WarnInfo 自动 open 播报，手动打开不播报，关窗取消', () => {
  const source = readFileSync(new URL(
    '../src/views/bi/patrol/panorama/warning/WarnInfo.vue', import.meta.url
  ), 'utf8')
  assert.match(source, /from '@\/utils\/alarm-speech'/)
  assert.match(source, /speakCurrentAlarm\(\)/)
  assert.match(source, /this\.speakCurrentAlarm\(\)/)
  assert.match(source, /openManual\(item\) \{\s*if \(!item\) return[\s\S]*this\.open\(item, \{ manual: true \}\)/)
  assert.doesNotMatch(source, /openManual[\s\S]{0,400}speakCurrentAlarm/)
  assert.match(source, /resetDialog\(\) \{[\s\S]*cancelAlarmSpeech\(\)/)
  assert.match(source, /beforeDestroy\(\) \{[\s\S]*cancelAlarmSpeech\(\)/)
})

test('Bi 大屏首次交互解锁 speechSynthesis', () => {
  const source = readFileSync(new URL('../src/views/bi/Bi.vue', import.meta.url), 'utf8')
  assert.match(source, /unlockAlarmSpeech/)
  assert.match(source, /bindSpeechUnlock/)
  assert.match(source, /addEventListener\('click'/)
  assert.match(source, /addEventListener\('keydown'/)
})
