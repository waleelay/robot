import { mapActions, mapState } from "vuex";
import { takeoverControl, acquireControl, mediaClientId, sendEquipmentCommand, createConfirmToken } from "../../../../../../api/media";
import { errorMessage } from "../../../../../../utils";
import ControlModeWarning from "./ControlModeWarning.vue";

function defaultVehicleLightState() {
  return {
    front: { mode: 'OFF', brightness: 50 },
    rear: { mode: 'OFF', brightness: 50 }
  }
}

export default {
  components: { ControlModeWarning },
  computed: {
    ...mapState('websocketRobot', ['deviceStateCache', 'audioState']),
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    mediaSocket() {
      return this.$store.getters['websocketRobot/getMediaSocket'];
    },
    wsConnected() {
      return this.$store.getters['websocketRobot/getWsConnected'];
    },
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId'] || this.cameraInfo?.robotId || ''
    },
    selectedRobot() {
      return this.robotBaseInfo?.[this.selectedRobotId] || {}
    },
    controlProfiles() {
      return this.$store.getters['websocketRobot/getControlProfiles']
    },
    selectedControlProfile() {
      return this.controlProfiles[this.selectedRobotId || this.cameraInfo?.robotId || ''] || { devices: [] }
    },
    selectedControlLoading() {
      return !!this.controlProfileLoading[this.selectedRobotId || this.cameraInfo?.robotId || '']
    },
    // 本体
    baseDevice() {
      return this.controlDevice('base')
    },
    // 云台
    ptzDevice() {
      return this.controlDevices().find(device => device.deviceType === 'DUAL_LIGHT_PTZ')
    },
    // 捕网器
    netGunDevice() {
      return this.controlDevices().find(device => device.deviceType === 'NET_GUN' || device.deviceType === 'NET_LAUNCHER')
    },
    // 发射器
    launcherDevice() {
      return this.controlDevices().find(device => device.deviceType === 'LAUNCHER')
    },
    // 语音对讲
    audioDevice() {
      return this.controlDevices().find(device => ['SPEAKER', 'CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
    },
    // 警示灯
    warningLightDevices() {
      return this.controlDevices().filter(device => device.deviceType === 'WARNING_LIGHT')
    },
    // 车灯
    vehicleLightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'VEHICLE_LIGHT')
    },
    searchlightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'SEARCHLIGHT')
    },
  },
  data() {
    return {
      controlTimers: {},
      controlSeq: 1,
      controlSessions: {},
      ptzAutoRotateState: Object.assign({}, this.deviceStateCache?.ptzAutoRotateState || {}),
      // audioState: Object.assign({}, this.deviceStateCache?.audioState || {}),
      launcherSafety: Object.assign({}, this.deviceStateCache?.launcherSafety || {}),
      netGunSafety: Object.assign({}, this.deviceStateCache?.netGunSafety || {}),
      warningLightState: Object.assign({}, this.deviceStateCache?.warningLightState || {}),
      vehicleLightState: Object.assign(defaultVehicleLightState(), this.deviceStateCache?.vehicleLightState || {}),
      confirmedVehicleLightState: Object.assign(defaultVehicleLightState(), this.deviceStateCache?.vehicleLightState || {}),
      vehicleLightStateReady: !!this.deviceStateCache?.vehicleLightState,
      vehicleLightParts: [
        { key: 'front', label: '前灯' },
        { key: 'rear', label: '后灯' }
      ],
      vehicleLightModeOptions: [
        { value: 'OFF', label: '常关', code: 0 },
        { value: 'ON', label: '常开', code: 1 },
        { value: 'BREATH', label: '呼吸', code: 2 },
        { value: 'CUSTOM', label: '自定义', code: 3 }
      ]
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['persistDeviceStateCache']),
    controlDevices() {
      return this.selectedControlProfile.devices || []
    },
    controlDevice(deviceId) {
      return this.controlDevices().find(device => device.deviceId === deviceId)
    },
    isPtzAutoRotateOn(device) {
      if (!device) return false
      const key = this.ptzAutoRotateKey(device)
      if (this.ptzAutoRotateState[key] !== undefined) return !!this.ptzAutoRotateState[key]
      const status = device.status || device.runtimeStatus || {}
      if (status.autoRotateEnabled !== undefined) return !!status.autoRotateEnabled
      if (this.ptzAutoRotateState[key] !== undefined) return !!this.ptzAutoRotateState[key]
      return !!status.autoRotateEnabled
    },
    hasPtzAutoRotateStatus(device) {
      if (!device) return false
      const key = this.ptzAutoRotateKey(device)
      if (this.ptzAutoRotateState[key] !== undefined) return true
      const status = device.status || device.runtimeStatus || {}
      return status.autoRotateEnabled !== undefined
    },
    ptzAutoRotateKey(device) {
      return device ? `${this.selectedRobotId}:${device.deviceId}` : ''
    },
    async togglePtzAutoRotate() {
      const device = this.ptzDevice
      if (!device) return
      const key = this.ptzAutoRotateKey(device)
      const enabled = !this.isPtzAutoRotateOn(device)
      const ok = await this.sendDeviceCommand(device, 'ptz.auto_rotate', {
        enabled,
        panSpeed: 0.3
      }, `ptz_auto_rotate_${enabled ? 'on' : 'off'}`)
      if (ok) {
        this.$set(this.ptzAutoRotateState, key, enabled)
        this.persistDeviceStateCache({ ...this.deviceStateCache, ptzAutoRotateState: this.ptzAutoRotateState })
      }
    },
    async sendDeviceCommand(device, action, params, source) {
      try {
        const session = await this.ensureControlSession(device, action)
        const response = await sendEquipmentCommand(this.selectedRobotId,
            this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, action, params, source || action))
        console.log('API sendDeviceCommand', response)
        return true
      } catch (error) {
        this.$message.error(errorMessage(error))
        console.log('ERROR sendDeviceCommand', errorMessage(error))
        return false
      }
    },
    async firePayload(device, channel, source) {
      try {
        const session = await this.ensureControlSession(device, 'fire')
        const token = await createConfirmToken(this.selectedRobotId, {
          controlSessionId: session.controlSessionId,
          target: {
            scope: device.scope,
            deviceId: device.deviceId,
            deviceType: device.deviceType
          },
          action: 'fire',
          reason: 'manual_confirm'
        })
         const fireParams = device.deviceType === 'LAUNCHER'
            ? {
              tube: channel,
              waitStatusAfterFire: true,
              keepSafetyOn: false,
              confirmToken: token.confirmToken
            }
            : {
              confirmToken: token.confirmToken
            }
        const response = await sendEquipmentCommand(this.selectedRobotId,
          this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, 'fire', fireParams, source || `fire_${channel}`))
        console.log('API firePayload', response)
      } catch (error) {
        this.$message.error(errorMessage(error))
        console.log('ERROR firePayload', errorMessage(error))
      }
    },
    // 云台开始控制
    startFrameControl(kind) {
      // 本体需要判断是否是手动模式，否则提示切换到手动模式
      if (this.selectedRobot?.controlMode !== 'MANUAL' && kind.indexOf('base-') > -1) {
        // this.$message.warning('请先切换到手动模式')        
        if (this.$refs.controlModeWarningRef) {
          this.$refs.controlModeWarningRef.open({ robotId: this.selectedRobotId, controlMode: 'MANUAL' })
        } else {
          this.$emit('handleModeChange', 'MANUAL')
        }
        return
      }
      if (this.controlTimers[kind]) return
      this.sendFrameControl(kind)
      this.$set(this.controlTimers, kind, setInterval(() => this.sendFrameControl(kind), 100))
    },
    // 云台停止控制
    stopFrameControl(kind) {
      if (this.selectedRobot?.controlMode !== 'MANUAL') return
      if (!this.controlTimers[kind]) return
      clearInterval(this.controlTimers[kind])
      this.$delete(this.controlTimers, kind)      
    },
    async sendFrameControl(kind) {
      try {
        const frame = await this.controlFrame(kind)
        if (!frame || !this.wsConnected) return
        this.mediaSocket.send(JSON.stringify({
          type: 'control.command',
          requestId: `req_${Date.now()}_${this.controlSeq}`,
          payload: frame
        }))
      } catch (error) {
        console.log('ERROR control frame', errorMessage(error))
        this.$message.error(errorMessage(error))
        this.stopFrameControl(kind)
      }
    },
    async controlFrame(kind) {
      const robotId = this.selectedRobotId
      if (kind.indexOf('base-') === 0) {
        const device = this.baseDevice
        const session = await this.ensureControlSession(device, 'drive.velocity')
        const params = {
          'base-forward': { linearX: 0.3, linearY: 0, angularZ: 0 },
          'base-backward': { linearX: -0.3, linearY: 0, angularZ: 0 },
          'base-left': { linearX: 0, linearY: 0, angularZ: 0.3 },
          'base-right': { linearX: 0, linearY: 0, angularZ: -0.3 },
          'base-strafe-left': { linearX: 0, linearY: 0.2, angularZ: 0 },
          'base-strafe-right': { linearX: 0, linearY: -0.2, angularZ: 0 }
        }[kind]
        return this.commandPayload(robotId, session.controlSessionId, 'MANUAL', device, 'drive.velocity', params, kind)
      }
      if (kind.indexOf('ptz-') === 0) {
        const device = this.ptzDevice
        const directionAction = {
          'ptz-up': 'up',
          'ptz-down': 'down',
          'ptz-left': 'left',
          'ptz-right': 'right',
          'ptz-up-left': 'left_up',
          'ptz-up-right': 'right_up',
          'ptz-down-left': 'left_down',
          'ptz-down-right': 'right_down'
        }[kind]
        const session = await this.ensureControlSession(device, directionAction)
        const params = { speed: 20, duration: 0.3 }
        return this.commandPayload(robotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, directionAction, params, kind)
      }
      if (kind.indexOf('zoom-') === 0) {
        const device = this.ptzDevice
        const session = await this.ensureControlSession(device, 'camera.zoom')
        const params = { zoomSpeed: kind === 'zoom-in' ? 0.5 : -0.5 }
        return this.commandPayload(robotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, 'camera.zoom', params, kind)
      }
      return null
    },
    async ensureControlSession(device, action) {
      if (!device) throw new Error('未找到控制设备')
      const key = `${this.selectedRobotId}:${device.deviceId}:${action}`
      if (this.controlSessions[key] && this.controlSessions[key].status === 'ACTIVE') {
        return this.controlSessions[key]
      }
      let session
      if (device.deviceId === 'base' && ['NAVIGATION', 'ASSISTED'].includes(this.selectedRobot.controlMode)) {
        session = await takeoverControl(this.selectedRobotId, {
          fromMode: this.selectedRobot.controlMode,
          toMode: 'MANUAL',
          scope: 'ROBOT',
          deviceIds: ['base'],
          actions: ['drive.velocity'],
          observedStateSeq: this.selectedRobot.stateSeq || 0,
          reason: 'manual_takeover'
        })
      } else {
        session = await acquireControl(this.selectedRobotId, {
          scope: device.deviceId === 'base' ? 'ROBOT' : 'DEVICE',
          deviceIds: [device.deviceId],
          actions: [action],
          mode: 'EXCLUSIVE',
          reason: 'manual_teleop',
          ttlSeconds: 30
        })
      }
      if (session.code) {
        const error = new Error(session.message || session.code)
        error.code = session.code
        throw error
      }
      this.$set(this.controlSessions, key, session)
      return session
    },
    commandPayload(robotId, controlSessionId, controlMode, device, action, params, source) {
      return {
        robotId,
        controlSessionId,
        controlMode,
        target: {
          scope: device.scope,
          deviceId: device.deviceId,
          deviceType: device.deviceType
        },
        action,
        params,
        client: {
          terminalId: mediaClientId,
          source,
          seq: this.controlSeq++,
          timestamp: new Date().toISOString()
        }
      }
    },

    // ====================================================
    async sendDiscreteCommand(action) {
      const device = action === 'light.set'
          ? this.searchlightDevice
          : this.launcherDevice
      const session = await this.ensureControlSession(device, action)
      const params = {
        set_safety: { safetyOn: true, waitStatus: true },
        'light.set': { enabled: true, brightness: 80, mode: 'STEADY' }
      }[action]
      const response = await sendEquipmentCommand(this.selectedRobotId,
          this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, action, params, action))
      console.log('API sendEquipmentCommand', response)
    },
    isNetGunSafetyOn(device) {
      return !!(device && this.netGunSafety[device.deviceId])
    },
    isNetGunConnected(device) {
      if (!device) return true
      const status = this.deviceStatus(device)
      return status.connected !== false && status.online !== false
    },
    // 捕网器
    setFakeNetGunSafety(device, enabled) {
      this.$set(this.netGunSafety, device.deviceId, enabled)
    },
    isLauncherSafetyOn(device) {
      if (!device) return false
      const status = device.status || device.runtimeStatus || {}
      if (status.safetySwitchEnabled !== undefined) return !!status.safetySwitchEnabled
      if (this.launcherSafety[device.deviceId] !== undefined) return !!this.launcherSafety[device.deviceId]
      return !!status.safetySwitchEnabled
    },
    hasLauncherSafetyStatus(device) {
      if (!device) return false
      if (this.launcherSafety[device.deviceId] !== undefined) return true
      const status = device.status || device.runtimeStatus || {}
      return status.safetySwitchEnabled !== undefined
    },
    isLauncherConnected(device) {
      if (!device) return false
      const status = device.status || device.runtimeStatus || {}
      return status.connected !== false
    },
    launcherStatus() {
      const device = this.launcherDevice || {}
      return device.status || device.runtimeStatus || {}
    },
    launcherConnected() {
      return this.launcherStatus.connected !== false
    },
    launcherTubes() {
      const status = this.launcherStatus
      if (Array.isArray(status.tubes) && status.tubes.length) {
        return status.tubes.map(item => this.normalizeLauncherTube(item))
      }
      const profile = (this.launcherDevice && this.launcherDevice.controlProfile) || {}
      const tubes = Array.isArray(profile.tubes) && profile.tubes.length ? profile.tubes : [1, 2, 3, 4, 5, 6]
      return tubes.map(tube => this.normalizeLauncherTube({ tube }))
    },
    launcherTubes(device) {
      if (!device) return []
      const status = device.status || device.runtimeStatus || {}
      if (Array.isArray(status.tubes) && status.tubes.length) {
        return status.tubes.map(item => this.normalizeLauncherTube(item))
      }
      const profile = device.controlProfile || {}
      const tubes = Array.isArray(profile.tubes) && profile.tubes.length ? profile.tubes : [1, 2, 3, 4, 5, 6]
      return tubes.map(tube => this.normalizeLauncherTube({ tube }))
    },
    normalizeLauncherTube(tube) {
      const number = Number(tube.tube) || 0
      const state = tube.state === undefined ? 255 : Number(tube.state)
      return {
        tube: number,
        state,
        stateName: tube.stateName || this.launcherTubeStateName(state)
      }
    },
    launcherTubeStateName(state) {
      return {
        0: 'EMPTY',
        1: 'LOADED',
        2: 'FIRING',
        3: 'BLOCKED',
        255: 'UNKNOWN'
      }[state] || 'UNKNOWN'
    },
    launcherTubeLabel(tube) {
      return {
        EMPTY: '空',
        LOADED: '已装填',
        FIRING: '发射中',
        BLOCKED: '堵塞',
        UNKNOWN: '未知'
      }[tube.stateName] || '未知'
    },
    canFireLauncherTube(device, tube) {
      return !!(this.isLauncherConnected(device) && this.isLauncherSafetyOn(device) && tube && tube.state === 1)
    },
    async setLauncherSafety(device, enabled) {
      this.$set(this.launcherSafety, device.deviceId, enabled)
      this.persistDeviceStateCache({ ...this.deviceStateCache, launcherSafety: this.launcherSafety })
      const ok = await this.sendDeviceCommand(device, 'set_safety', {
        safetyOn: enabled,
        waitStatus: true
      }, `launcher_safety_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.launcherSafety, device.deviceId, !enabled)
        this.persistDeviceStateCache({ ...this.deviceStateCache, launcherSafety: this.launcherSafety })
      }
    },
    isWarningLightOn(device) {
      if (!device) return false
      const status = device.status || device.runtimeStatus || {}
      if (status.powerOn !== undefined || status.enabled !== undefined) {
        return !!(status.powerOn === undefined ? status.enabled : status.powerOn)
      }
      if (this.warningLightState[device.deviceId] !== undefined) return !!this.warningLightState[device.deviceId]
      return !!(status.powerOn === undefined ? status.enabled : status.powerOn)
    },
    hasWarningLightStatus(device) {
      if (!device) return false
      if (this.warningLightState[device.deviceId] !== undefined) return true
      const status = device.status || device.runtimeStatus || {}
      return status.powerOn !== undefined || status.enabled !== undefined
    },
    async setWarningLight(device, enabled) {
      this.$set(this.warningLightState, device.deviceId, enabled)
      this.persistDeviceStateCache({ ...this.deviceStateCache, warningLightState: this.warningLightState })
      const profile = device.controlProfile || {}
      const ok = await this.sendDeviceCommand(device, 'set_state', {
        lightId: profile.lightId || device.lightId || device.deviceId,
        powerOn: enabled
      }, `${device.deviceId}_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.warningLightState, device.deviceId, !enabled)
        this.persistDeviceStateCache({ ...this.deviceStateCache, warningLightState: this.warningLightState })
      }
    },
    async setNetGunSafety(device, enabled) {
      this.$set(this.netGunSafety, device.deviceId, enabled)
    },
    deviceStatus(device) {
      if (!device) return {}
      return device.status || device.runtimeStatus || {}
    },
    vehicleLightStatusPart(part) {
      if (!part || typeof part !== 'object') return null
      const mode = part.mode || this.vehicleLightModeOptions.find(item => item.code === part.modeCode)?.value || 'OFF'
      const normalized = ['OFF', 'ON', 'BREATH', 'CUSTOM'].includes(mode) ? mode : 'OFF'
      const brightness = part.brightness === undefined ? part.customValue : part.brightness
      return {
        mode: normalized,
        brightness: normalized === 'CUSTOM' ? Math.max(0, Math.min(100, Number(brightness || 0))) : 0
      }
    },
    hasVehicleLightStatus(device) {
      if (!device) return false
      const status = this.deviceStatus(device)
      return !!status.front || !!status.rear
    },
    syncVehicleLightStateFromStatus(device) {
      if (!device) return
      const status = this.deviceStatus(device)
      const front = this.vehicleLightStatusPart(status.front)
      const rear = this.vehicleLightStatusPart(status.rear)
      if (!front && !rear) return
      const next = this.cloneVehicleLightState()
      if (front) next.front = front
      if (rear) next.rear = rear
      this.vehicleLightState = next
      this.confirmedVehicleLightState = this.cloneVehicleLightState(next)
    },  
    cloneVehicleLightState(state) {
      const source = state || this.vehicleLightState
      return {
        front: Object.assign({}, source.front),
        rear: Object.assign({}, source.rear)
      }
    },
    async setVehicleLightMode(part, mode) {
      const next = this.cloneVehicleLightState()
      next[part].mode = mode
      if (mode === 'CUSTOM' && next[part].brightness <= 0) {
        next[part].brightness = 50
      }
      this.vehicleLightState = next
      const ok = await this.sendVehicleLightCommand(`vehicle_light_${part}_${String(mode).toLowerCase()}`)
      if (ok) {
        this.confirmedVehicleLightState = this.cloneVehicleLightState(next)
        this.vehicleLightStateReady = true
        this.persistDeviceStateCache({ ...this.deviceStateCache, vehicleLightState: this.vehicleLightState })
      } else if (this.vehicleLightStateReady) {
        this.vehicleLightState = this.cloneVehicleLightState(this.confirmedVehicleLightState)
      }
    },
    updateVehicleLightBrightness(part, value) {
      this.vehicleLightState[part].brightness = value
    },
    async setVehicleLightBrightness(part, value) {
      this.vehicleLightState[part].mode = 'CUSTOM'
      this.vehicleLightState[part].brightness = value
      const ok = await this.sendVehicleLightCommand(`vehicle_light_${part}_custom`)
      if (ok) {
        this.confirmedVehicleLightState = this.cloneVehicleLightState(next)
        this.vehicleLightStateReady = true
        this.persistDeviceStateCache({ ...this.deviceStateCache, vehicleLightStateReady: this.vehicleLightStateReady })
      } else if (this.vehicleLightStateReady) {
        this.vehicleLightState = this.cloneVehicleLightState(this.confirmedVehicleLightState)
      }
    },
    sendVehicleLightCommand(source) {
      const device = this.vehicleLightDevice
      if (!device) return false
      const state = this.cloneVehicleLightState()
      return this.sendDeviceCommand(device, 'light.vehicle.set', {
        front: this.vehicleLightPayloadPart(state.front),
        rear: this.vehicleLightPayloadPart(state.rear)
      }, source)
    },
    vehicleLightPayloadPart(part) {
      const mode = part.mode || 'OFF'
      const option = this.vehicleLightModeOptions.find(item => item.value === mode)
      return {
        mode,
        modeCode: option ? option.code : 0,
        brightness: mode === 'CUSTOM' ? part.brightness : 0
      }
    },
    hasVehicleLightStatus(device) {
      if (!device) return false
      if (this.vehicleLightStateReady) return true
      const status = device.status || device.runtimeStatus || {}
      return !!status.front || !!status.rear
    },
    syncAudioStatesFromDevices(robotId, devices, options = {}) {
      if (!robotId || !Array.isArray(devices)) return
      devices
          .filter(device => ['SPEAKER', 'CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
          .forEach(device => {
            const status = device.status || device.runtimeStatus || {}
            if (status.volume === undefined && status.volumePercent === undefined && status.muted === undefined) return
            const key = `${robotId}:${device.deviceId}`
            const next = Object.assign({}, this.audioState[key] || {})
            const volume = status.volume === undefined ? status.volumePercent : status.volume
            if (volume !== undefined && !(options.preserveExisting && next.volume !== undefined)) {
              next.volume = volume
            }
            if (status.muted !== undefined && !(options.preserveExisting && next.muted !== undefined)) {
              next.muted = status.muted
            }
            this.$set(this.audioState, key, next)
            commit('SET_AUDIO_STATE', { key, next })
          })
    },
    syncDeviceStatesFromDevices(robotId, devices, options = {}) {
      if (!robotId || !Array.isArray(devices)) return
      this.syncAudioStatesFromDevices(robotId, devices, options)
      if (robotId !== this.selectedRobotId) return
      devices.forEach(device => {
        const status = device.status || device.runtimeStatus || {}
        if (device.deviceType === 'LAUNCHER' && status.safetySwitchEnabled !== undefined &&
            !(options.preserveExisting && this.launcherSafety[device.deviceId] !== undefined)) {
          this.$set(this.launcherSafety, device.deviceId, !!status.safetySwitchEnabled)
        }
        if (device.deviceType === 'WARNING_LIGHT' && (status.powerOn !== undefined || status.enabled !== undefined) &&
            !(options.preserveExisting && this.warningLightState[device.deviceId] !== undefined)) {
          this.$set(this.warningLightState, device.deviceId, !!(status.powerOn === undefined ? status.enabled : status.powerOn))
        }
        const ptzKey = `${robotId}:${device.deviceId}`
        if (device.deviceType === 'DUAL_LIGHT_PTZ' && status.autoRotateEnabled !== undefined &&
            !(options.preserveExisting && this.ptzAutoRotateState[ptzKey] !== undefined)) {
          this.$set(this.ptzAutoRotateState, ptzKey, !!status.autoRotateEnabled)
        }
        if (device.deviceType === 'VEHICLE_LIGHT' && !(options.preserveExisting && this.vehicleLightStateReady)) {
          const next = this.cloneVehicleLightState()
          const front = this.vehicleLightStatusPart(status.front)
          const rear = this.vehicleLightStatusPart(status.rear)
          if (front) next.front = front
          if (rear) next.rear = rear
          if (front || rear) {
            this.vehicleLightState = next
            this.confirmedVehicleLightState = this.cloneVehicleLightState(next)
            this.vehicleLightStateReady = true
          }
        }
      })
      this.persistDeviceStateCache({
        ...this.deviceStateCache,
        audioState: this.audioState,
        launcherSafety: this.launcherSafety,
        netGunSafety: this.netGunSafety,
        warningLightState: this.warningLightState,
        ptzAutoRotateState: this.ptzAutoRotateState,
        vehicleLightStateReady: this.vehicleLightStateReady
      })
    },
  }
}