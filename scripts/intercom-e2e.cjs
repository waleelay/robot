#!/usr/bin/env node

const assert = require('node:assert/strict')
const https = require('node:https')
const mqtt = require('../robot-ui/node_modules/mqtt')
const WebSocket = require('../robot-ui/node_modules/ws')

const centerHost = process.env.CENTER_HOST || '192.168.124.234'
const centerPort = Number(process.env.CENTER_HTTPS_PORT || 4443)
const mqttUrl = process.env.MQTT_URL || 'mqtt://192.168.124.235:1884'
const primaryRobotId = process.env.ROBOT_ID || 'test111'
const secondaryRobotId = process.env.SECONDARY_ROBOT_ID || 'test222'
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS || 20000)
const runId = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`

const operatorA = {
  userId: `e2e-user-a-${runId}`,
  orgId: 'org001',
  clientId: `e2e-client-a-${runId}`
}
const operatorB = {
  userId: `e2e-user-b-${runId}`,
  orgId: 'org001',
  clientId: `e2e-client-b-${runId}`
}

const activeSessions = []
const outstandingCalls = new Set()
const sockets = []
let mqttClient

function log(step, detail = '') {
  process.stdout.write(`[e2e] ${step}${detail ? `: ${detail}` : ''}\n`)
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function identityHeaders(identity) {
  return {
    'X-User-Id': identity.userId,
    'X-Org-Id': identity.orgId,
    'X-Roles': 'MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR',
    'X-Client-Id': identity.clientId
  }
}

function api(path, { method = 'GET', identity = operatorA, body } = {}) {
  const payload = body === undefined ? null : Buffer.from(JSON.stringify(body))
  const headers = identityHeaders(identity)
  if (payload) {
    headers['Content-Type'] = 'application/json'
    headers['Content-Length'] = payload.length
  }
  return new Promise((resolve, reject) => {
    const request = https.request({
      host: centerHost,
      port: centerPort,
      path,
      method,
      headers,
      rejectUnauthorized: false,
      timeout: timeoutMs
    }, response => {
      const chunks = []
      response.on('data', chunk => chunks.push(chunk))
      response.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8')
        let data = text
        try {
          data = text ? JSON.parse(text) : null
        } catch (_) {}
        resolve({ status: response.statusCode, data, text })
      })
    })
    request.on('timeout', () => request.destroy(new Error(`HTTP timeout: ${method} ${path}`)))
    request.on('error', reject)
    if (payload) request.write(payload)
    request.end()
  })
}

function expectStatus(response, expected, label) {
  assert.equal(
    response.status,
    expected,
    `${label}: expected HTTP ${expected}, got ${response.status}: ${response.text}`
  )
  return response.data
}

class EventStream {
  constructor(name) {
    this.name = name
    this.events = []
    this.waiters = new Set()
  }

  push(event) {
    this.events.push(event)
    for (const waiter of [...this.waiters]) {
      if (waiter.predicate(event)) {
        clearTimeout(waiter.timer)
        this.waiters.delete(waiter)
        waiter.resolve(event)
      }
    }
  }

  mark() {
    return this.events.length
  }

  waitFor(predicate, label, from = 0, waitMs = timeoutMs) {
    const existing = this.events.slice(from).find(predicate)
    if (existing) return Promise.resolve(existing)
    return new Promise((resolve, reject) => {
      const waiter = {
        predicate,
        resolve,
        timer: setTimeout(() => {
          this.waiters.delete(waiter)
          reject(new Error(`${this.name} timeout waiting for ${label}`))
        }, waitMs)
      }
      this.waiters.add(waiter)
    })
  }
}

async function connectMqtt() {
  const stream = new EventStream('MQTT')
  mqttClient = mqtt.connect(mqttUrl, {
    clientId: `intercom-e2e-${runId}`,
    reconnectPeriod: 1000,
    connectTimeout: timeoutMs,
    clean: true
  })
  mqttClient.on('message', (topic, payload) => {
    let data
    try {
      data = JSON.parse(payload.toString('utf8'))
    } catch (_) {
      data = payload.toString('utf8')
    }
    stream.push({ topic, data })
  })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('MQTT connect timeout')), timeoutMs)
    mqttClient.once('connect', () => {
      clearTimeout(timer)
      resolve()
    })
    mqttClient.once('error', reject)
  })
  await new Promise((resolve, reject) => {
    mqttClient.subscribe([
      'robot/+/media/video/intercom/call/state',
      'robot/+/media/video/intercom/status'
    ], { qos: 1 }, error => error ? reject(error) : resolve())
  })
  return stream
}

function publish(topic, data) {
  return new Promise((resolve, reject) => {
    mqttClient.publish(topic, JSON.stringify(data), { qos: 1, retain: false }, error => {
      if (error) reject(error)
      else resolve()
    })
  })
}

async function publishRobotStatus(robotId) {
  await publish(`robot/${robotId}/media/client/status`, {
    robotId,
    clientId: `robot-media-client-${robotId}`,
    name: robotId === secondaryRobotId ? 'E2E测试机器人' : robotId,
    type: '轮式机器人',
    battery: 100,
    status: 'online',
    controlMode: 'MANUAL',
    stateSeq: Date.now(),
    missionStatus: 'IDLE',
    navigationStatus: 'IDLE',
    estopActive: false,
    cameras: [{
      cameraId: 'camera01',
      deviceId: 'camera01',
      groupType: 'body',
      name: '主摄像头',
      quality: 'sub'
    }],
    devices: [],
    timestamp: new Date().toISOString()
  })
}

async function invite(robotId, suffix, timeoutSeconds = 60) {
  const callId = `call_e2e_${robotId}_${runId}_${suffix}`
  outstandingCalls.add(callId)
  await publish(`robot/${robotId}/media/video/intercom/call/invite`, {
    callId,
    robotId,
    deviceId: 'camera01',
    channel: 'visible',
    quality: 'sub',
    reason: '自动化端到端测试',
    timeoutSeconds,
    timestamp: new Date().toISOString()
  })
  return callId
}

async function cancelCall(robotId, callId) {
  await publish(`robot/${robotId}/media/video/intercom/call/cancel`, {
    callId,
    robotId,
    reason: '自动化测试清理',
    timestamp: new Date().toISOString()
  })
  outstandingCalls.delete(callId)
}

async function connectWebSocket(identity) {
  const stream = new EventStream(`WebSocket ${identity.clientId}`)
  const url = `wss://${centerHost}:${centerPort}/ws/control?clientId=${encodeURIComponent(identity.clientId)}`
  const socket = new WebSocket(url, {
    rejectUnauthorized: false,
    headers: {
      'X-User-Id': identity.userId,
      'X-Org-Id': identity.orgId,
      'X-Roles': 'MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR'
    }
  })
  sockets.push(socket)
  socket.on('message', payload => {
    try {
      stream.push(JSON.parse(payload.toString('utf8')))
    } catch (_) {}
  })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('WebSocket connect timeout')), timeoutMs)
    socket.once('open', () => {
      clearTimeout(timer)
      resolve()
    })
    socket.once('error', reject)
  })
  return { socket, stream }
}

async function wsRequest(channel, type, payload, expectedType) {
  const requestId = `e2e-${type}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  const from = channel.stream.mark()
  channel.socket.send(JSON.stringify({ type, requestId, payload }))
  return channel.stream.waitFor(
    event => event.type === expectedType && event.requestId === requestId,
    expectedType,
    from
  )
}

async function queryCalls(channel) {
  const response = await wsRequest(
    channel,
    'video.intercom.call.query',
    {},
    'video.intercom.call.list'
  )
  return Array.isArray(response.payload) ? response.payload : []
}

async function stopTrackedSession(tracked) {
  if (!tracked || tracked.stopped) return
  const response = await api(
    `/api/control/video-sessions/${tracked.sessionId}/intercom/stop`,
    { method: 'POST', identity: tracked.identity }
  )
  if (![200, 409].includes(response.status)) {
    throw new Error(`cleanup stop ${tracked.sessionId} failed: ${response.status} ${response.text}`)
  }
  tracked.stopped = true
}

async function run() {
  log('connect MQTT and WebSocket')
  const mqttEvents = await connectMqtt()
  const wsA = await connectWebSocket(operatorA)

  await publishRobotStatus(secondaryRobotId)
  await sleep(500)

  log('single incoming call')
  const callA = await invite(primaryRobotId, 'single')
  const incomingA = await wsA.stream.waitFor(
    event => event.event === 'video.intercom.call.incoming' && event.data?.callId === callA,
    `incoming ${callA}`
  )
  assert.equal(incomingA.data.status, 'RINGING')

  const ringingStateA = await mqttEvents.waitFor(
    event => event.topic === `robot/${primaryRobotId}/media/video/intercom/call/state` &&
      event.data?.callId === callA &&
      event.data?.status === 'ringing',
    `ringing state ${callA}`
  )
  assert.equal(ringingStateA.data.robotId, primaryRobotId)

  const callsBeforeAccept = await queryCalls(wsA)
  assert(callsBeforeAccept.some(call => call.callId === callA), 'ringing call missing from query')

  log('manual intercom blocked while robot is ringing')
  const blockedManual = await api(
    `/api/control/robots/${primaryRobotId}/cameras/camera01/video/intercom/start`,
    { method: 'POST', identity: operatorA, body: { quality: 'sub', reuse: true } }
  )
  const blockedManualBody = expectStatus(blockedManual, 409, 'manual intercom while ringing')
  assert.equal(blockedManualBody.code, 'ROBOT_CALL_RINGING')

  log('accept call through WebSocket')
  const acceptedA = await wsRequest(
    wsA,
    'video.intercom.call.accept',
    { callId: callA },
    'video.intercom.call.accepted'
  )
  outstandingCalls.delete(callA)
  const sessionA = acceptedA.payload?.intercom?.sessionId
  assert(sessionA, `accepted payload missing sessionId: ${JSON.stringify(acceptedA)}`)
  const trackedA = { sessionId: sessionA, identity: operatorA, stopped: false }
  activeSessions.push(trackedA)

  log('HTTP heartbeat uses same browser clientId')
  const heartbeatA = await api(
    `/api/control/video-sessions/${sessionA}/intercom/heartbeat`,
    { method: 'POST', identity: operatorA }
  )
  expectStatus(heartbeatA, 200, 'intercom heartbeat')

  log('same operator cannot accept a second robot call')
  const callB = await invite(secondaryRobotId, 'busy')
  await wsA.stream.waitFor(
    event => event.event === 'video.intercom.call.incoming' && event.data?.callId === callB,
    `incoming ${callB}`
  )
  const failedAcceptB = await wsRequest(
    wsA,
    'video.intercom.call.accept',
    { callId: callB },
    'video.intercom.call.operation-failed'
  )
  assert.match(failedAcceptB.payload?.message || '', /先结束当前通话/)
  const callsAfterFailedAccept = await queryCalls(wsA)
  assert(callsAfterFailedAccept.some(call => call.callId === callB), 'busy accept consumed ringing call')

  log('different operator accepts waiting call')
  const wsB = await connectWebSocket(operatorB)
  const acceptedB = await wsRequest(
    wsB,
    'video.intercom.call.accept',
    { callId: callB },
    'video.intercom.call.accepted'
  )
  outstandingCalls.delete(callB)
  const sessionB = acceptedB.payload?.intercom?.sessionId
  assert(sessionB, `second accepted payload missing sessionId: ${JSON.stringify(acceptedB)}`)
  const trackedB = { sessionId: sessionB, identity: operatorB, stopped: false }
  activeSessions.push(trackedB)
  const heartbeatB = await api(
    `/api/control/video-sessions/${sessionB}/intercom/heartbeat`,
    { method: 'POST', identity: operatorB }
  )
  expectStatus(heartbeatB, 200, 'second operator heartbeat')

  log('same robot duplicate call is busy')
  const duplicateCall = await invite(primaryRobotId, 'duplicate')
  const duplicateBusy = await mqttEvents.waitFor(
    event => event.topic === `robot/${primaryRobotId}/media/video/intercom/call/state` &&
      event.data?.callId === duplicateCall &&
      event.data?.status === 'busy',
    `busy state ${duplicateCall}`
  )
  assert.equal(duplicateBusy.data.robotId, primaryRobotId)
  outstandingCalls.delete(duplicateCall)

  log('enable video, close popup video, keep audio heartbeat')
  const videoStart = await api(
    `/api/control/robots/${primaryRobotId}/cameras/camera01/video/start`,
    { method: 'POST', identity: operatorA, body: { quality: 'sub', reuse: true } }
  )
  const videoSession = expectStatus(videoStart, 200, 'start video')
  assert.equal(videoSession.sessionId, sessionA, 'video should reuse the intercom session')
  const videoStop = await api(
    `/api/control/video-sessions/${sessionA}/stop`,
    { method: 'POST', identity: operatorA }
  )
  expectStatus(videoStop, 200, 'close popup video')
  const heartbeatAfterVideoStop = await api(
    `/api/control/video-sessions/${sessionA}/intercom/heartbeat`,
    { method: 'POST', identity: operatorA }
  )
  const heartbeatAfterVideoStopBody = expectStatus(
    heartbeatAfterVideoStop,
    200,
    'audio heartbeat after closing video'
  )
  assert(
    ['STARTING', 'ACTIVE'].includes(heartbeatAfterVideoStopBody.intercomStatus),
    `unexpected intercom status after video close: ${heartbeatAfterVideoStopBody.intercomStatus}`
  )

  log('hang up both active calls')
  await stopTrackedSession(trackedB)
  await stopTrackedSession(trackedA)
  await sleep(6000)
  const releasedVideoSession = expectStatus(
    await api(`/api/control/video-sessions/${sessionA}`, { identity: operatorA }),
    200,
    'video session after hangup'
  )
  assert(
    !(releasedVideoSession.viewerCount === 0 &&
      releasedVideoSession.intercomStatus === 'IDLE' &&
      releasedVideoSession.status === 'STREAMING'),
    'late robot status reactivated a released video session'
  )

  log('multiple robots ring concurrently and stay in queue')
  await publishRobotStatus(secondaryRobotId)
  const queueCallA = await invite(primaryRobotId, 'queue-a')
  const queueCallB = await invite(secondaryRobotId, 'queue-b')
  await wsA.stream.waitFor(
    event => event.event === 'video.intercom.call.incoming' && event.data?.callId === queueCallA,
    `incoming ${queueCallA}`
  )
  await wsA.stream.waitFor(
    event => event.event === 'video.intercom.call.incoming' && event.data?.callId === queueCallB,
    `incoming ${queueCallB}`
  )
  const queuedCalls = await queryCalls(wsA)
  assert(queuedCalls.some(call => call.callId === queueCallA), 'first queued call missing')
  assert(queuedCalls.some(call => call.callId === queueCallB), 'second queued call missing')

  await wsRequest(
    wsA,
    'video.intercom.call.reject',
    { callId: queueCallA },
    'video.intercom.call.rejected'
  )
  outstandingCalls.delete(queueCallA)
  await wsRequest(
    wsA,
    'video.intercom.call.reject',
    { callId: queueCallB },
    'video.intercom.call.rejected'
  )
  outstandingCalls.delete(queueCallB)
  const callsAfterReject = await queryCalls(wsA)
  assert(!callsAfterReject.some(call => [queueCallA, queueCallB].includes(call.callId)))

  log('PASS', 'all intercom scenarios completed')
}

async function cleanup() {
  for (const tracked of activeSessions.reverse()) {
    try {
      await stopTrackedSession(tracked)
    } catch (error) {
      log('cleanup warning', error.message)
    }
  }
  for (const callId of [...outstandingCalls]) {
    const robotId = callId.includes(`_${secondaryRobotId}_`) ? secondaryRobotId : primaryRobotId
    try {
      await cancelCall(robotId, callId)
    } catch (error) {
      log('cleanup warning', error.message)
    }
  }
  for (const socket of sockets) {
    try {
      socket.close()
    } catch (_) {}
  }
  if (mqttClient) {
    await new Promise(resolve => mqttClient.end(false, {}, resolve))
  }
}

run()
  .catch(error => {
    process.exitCode = 1
    console.error(`[e2e] FAIL: ${error.stack || error.message}`)
  })
  .finally(cleanup)
