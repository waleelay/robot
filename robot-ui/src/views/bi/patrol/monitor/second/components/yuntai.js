import { mapActions, mapState } from "vuex";
import { acquireControl, mediaClientId, sendEquipmentCommand, createConfirmToken } from "../../../../../../api/media";
import { errorMessage } from "../../../../../../utils";
import ControlModeWarning from "./ControlModeWarning.vue";

function cachedVehicleLightEnabled(cache) {
  if (cache?.vehicleLightEnabled !== undefined) return !!cache.vehicleLightEnabled
  return cache?.vehicleLightState?.front?.mode === 'ON' &&
    cache?.vehicleLightState?.rear?.mode === 'ON'
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
    warningLightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'WARNING_LIGHT')
    },
    warningLightQueryKey() {
      const device = this.warningLightDevice
      if (this.visible === false ||
          !this.selectedRobotId ||
          !device ||
          !this.hasDeviceAction(device, 'get_state')) return ''
      return `${this.selectedRobotId}:${device.deviceId}`
    },
    // 车灯
    vehicleLightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'VEHICLE_LIGHT')
    },
    searchlightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'SEARCHLIGHT')
    },
    multiFunctionDevice() {
      return this.controlDevices().find(device => device.deviceType === 'MULTI_FUNCTION_BROADCASTER')
    },
  },
  data() {
    return {
      controlTimers: {},
      controlSeq: 1,
      controlSessions: {},
      lastWarningLightQueryKey: '',
      ptzAutoRotateState: Object.assign({}, this.deviceStateCache?.ptzAutoRotateState || {}),
      // audioState: Object.assign({}, this.deviceStateCache?.audioState || {}),
      launcherSafety: Object.assign({}, this.deviceStateCache?.launcherSafety || {}),
      netGunSafety: Object.assign({}, this.deviceStateCache?.netGunSafety || {}),
      warningLightState: Object.assign({}, this.deviceStateCache?.warningLightState || {}),
      vehicleLightEnabled: cachedVehicleLightEnabled(this.deviceStateCache)
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
    async handleModeChange(controlMode) {
      if (this.selectedRobot.controlMode === controlMode) return
      this.$refs.controlModeWarningRef.open({ robotId: this.selectedRobotId, controlMode })
    },
    controlModeCommand(controlMode) {
      return controlMode === '手动模式' ? '手动模式' : '导航模式'
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
            this.commandPayload(this.selectedRobotId, session.controlSessionId, this.controlModeCommand(this.selectedRobot.controlMode), device, action, params, source || action))
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
          this.commandPayload(this.selectedRobotId, session.controlSessionId, this.controlModeCommand(this.selectedRobot.controlMode), device, 'fire', fireParams, source || `fire_${channel}`))
        console.log('API firePayload', response)
      } catch (error) {
        this.$message.error(errorMessage(error))
        console.log('ERROR firePayload', errorMessage(error))
      }
    },
    // 云台开始控制
    startFrameControl(kind) {
      // 本体需要判断是否是手动模式，否则提示切换到手动模式
      if (this.selectedRobot?.controlMode !== '手动模式' && kind.indexOf('base-') > -1) {
        // this.$message.warning('请先切换到手动模式')
        if (this.$refs.controlModeWarningRef) {
          this.$refs.controlModeWarningRef.open({ robotId: this.selectedRobotId, controlMode: '手动模式' })
        } else {
          this.$emit('handleModeChange', '手动模式')
        }
        return
      }
      if (this.controlTimers[kind]) return
      this.sendFrameControl(kind)
      this.$set(this.controlTimers, kind, setInterval(() => this.sendFrameControl(kind), 100))
    },
    // 云台停止控制
    stopFrameControl(kind) {
      if (this.selectedRobot?.controlMode !== '手动模式') return
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
        return this.commandPayload(robotId, session.controlSessionId, '手动模式', device, 'drive.velocity', params, kind)
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
        return this.commandPayload(robotId, session.controlSessionId, this.controlModeCommand(this.selectedRobot.controlMode), device, directionAction, params, kind)
      }
      if (kind.indexOf('zoom-') === 0) {
        const device = this.ptzDevice
        const session = await this.ensureControlSession(device, 'camera.zoom')
        const params = { zoomSpeed: kind === 'zoom-in' ? 0.5 : -0.5 }
        return this.commandPayload(robotId, session.controlSessionId, this.controlModeCommand(this.selectedRobot.controlMode), device, 'camera.zoom', params, kind)
      }
      return null
    },
    async ensureControlSession(device, action) {
      if (!device) throw new Error('未找到控制设备')
      const key = `${this.selectedRobotId}:${device.deviceId}:${action}`
      if (this.controlSessions[key] && this.controlSessions[key].status === 'ACTIVE') {
        return this.controlSessions[key]
      }
      if (device.deviceId === 'base' && this.selectedRobot.controlMode !== '手动模式') {
        throw new Error('请先将机器人切换到手动模式')
      }
      const session = await acquireControl(this.selectedRobotId, {
        scope: device.deviceId === 'base' ? 'ROBOT' : 'DEVICE',
        deviceIds: [device.deviceId],
        actions: [action],
        mode: 'EXCLUSIVE',
        reason: 'manual_teleop',
        ttlSeconds: 30
      })
      if (session.code) {
        const error = new Error(session.message || session.code)
        error.code = session.code
        throw error
      }
      this.$set(this.controlSessions, key, session)
      return session
    },
    commandPayload(robotId, controlSessionId, controlMode, device, action, params, source) {
      const payload = {
        robotId,
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
      if (controlSessionId) payload.controlSessionId = controlSessionId
      return payload
    },

    // ====================================================
    async sendDiscreteCommand(action) {
      const device = action === 'light.set'
          ? this.searchlightDevice
          : this.launcherDevice
      const session = await this.ensureControlSession(device, action)
      const params = {
        set_safety: { safety_on: true, wait_status: true },
        'light.set': { enabled: true, brightness: 80, mode: 'STEADY' }
      }[action]
      const response = await sendEquipmentCommand(this.selectedRobotId,
          this.commandPayload(this.selectedRobotId, session.controlSessionId, this.controlModeCommand(this.selectedRobot.controlMode), device, action, params, action))
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
      const state = tube.state === undefined
          ? (tube.loaded === true ? 1 : tube.loaded === false ? 0 : this.launcherTubeStateFromName(tube.stateName))
          : Number(tube.state)
      return {
        tube: number,
        state,
        loaded: tube.loaded === undefined ? state === 1 : !!tube.loaded,
        stateName: tube.stateName || this.launcherTubeStateName(state)
      }
    },
    launcherTubeStateFromName(stateName) {
      return {
        EMPTY: 0,
        LOADED: 1,
        FIRING: 2,
        BLOCKED: 3,
        UNKNOWN: 255,
        空仓: 0,
        在仓: 1,
        发射中: 2,
        堵塞: 3,
        未知: 255
      }[stateName] ?? 255
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
      const state = tube?.state === undefined ? this.launcherTubeStateFromName(tube?.stateName) : tube.state
      return {
        0: '空',
        1: '已装填',
        2: '发射中',
        3: '堵塞',
        255: '未知'
      }[state] || '未知'
    },
    launcherTubeLoaded(tube) {
      if (!tube) return false
      if (tube.loaded !== undefined) return !!tube.loaded
      if (tube.state !== undefined) return Number(tube.state) === 1
      return this.launcherTubeStateFromName(tube.stateName) === 1
    },
    canFireLauncherTube(device, tube) {
      return !!(this.isLauncherConnected(device) && this.isLauncherSafetyOn(device) && this.launcherTubeLoaded(tube))
    },
    async setLauncherSafety(device, enabled) {
      this.$set(this.launcherSafety, device.deviceId, enabled)
      this.persistDeviceStateCache({ ...this.deviceStateCache, launcherSafety: this.launcherSafety })
      const ok = await this.sendDeviceCommand(device, 'set_safety', {
        safety_on: enabled,
        wait_status: true
      }, `launcher_safety_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.launcherSafety, device.deviceId, !enabled)
        this.persistDeviceStateCache({ ...this.deviceStateCache, launcherSafety: this.launcherSafety })
      }
    },
    isWarningLightOn(device) {
      if (!device) return false
      const status = device.status || device.runtimeStatus || {}
      const powerOn = status.powerOn === undefined ? status.enabled : status.powerOn
      if (powerOn !== undefined) return this.warningLightPowerOn(powerOn)
      if (this.warningLightState[device.deviceId] !== undefined) return !!this.warningLightState[device.deviceId]
      return false
    },
    warningLightPowerOn(value) {
      return Array.isArray(value) ? value.length > 0 && value.every(Boolean) : !!value
    },
    hasDeviceAction(device, action) {
      return !!(device && Array.isArray(device.actions) && device.actions.includes(action))
    },
    async queryWarningLightState() {
      const device = this.warningLightDevice
      if (!device || !this.hasDeviceAction(device, 'get_state')) return
      const profile = device.controlProfile || {}
      try {
        await sendEquipmentCommand(this.selectedRobotId,
          this.commandPayload(
            this.selectedRobotId,
            null,
            this.controlModeCommand(this.selectedRobot?.controlMode),
            device,
            'get_state',
            { lightId: profile.lightId || 'all' },
            `${device.deviceId}_get_state`
          ))
      } catch (error) {
        console.warn('WARN query warning light state', errorMessage(error))
      }
    },
    async setWarningLight(device, enabled) {
      this.$set(this.warningLightState, device.deviceId, enabled)
      this.persistDeviceStateCache({ ...this.deviceStateCache, warningLightState: this.warningLightState })
      const profile = device.controlProfile || {}
      const ok = await this.sendDeviceCommand(device, 'set_state', {
        lightId: profile.lightId || 'all',
        powerOn: enabled
      }, `${device.deviceId}_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.warningLightState, device.deviceId, !enabled)
        this.persistDeviceStateCache({ ...this.deviceStateCache, warningLightState: this.warningLightState })
      }
    },
    async switchWarningLightMode(device) {
      if (!device) return
      const profile = device.controlProfile || {}
      await this.sendDeviceCommand(device, 'set_mode', {
        lightId: profile.lightId || 'all',
        mode: 2
      }, `${device.deviceId}_mode_pulse`)
    },
    async setNetGunSafety(device, enabled) {
      this.$set(this.netGunSafety, device.deviceId, enabled)
    },
    deviceStatus(device) {
      if (!device) return {}
      return device.status || device.runtimeStatus || {}
    },
    async setVehicleLights(enabled) {
      const device = this.vehicleLightDevice
      if (!device) return
      const previous = this.vehicleLightEnabled
      this.vehicleLightEnabled = !!enabled
      this.persistDeviceStateCache({
        ...this.deviceStateCache,
        vehicleLightEnabled: this.vehicleLightEnabled
      })
      const mode = this.vehicleLightEnabled ? 'ON' : 'OFF'
      const ok = await this.sendDeviceCommand(device, 'light.vehicle.set', {
        front: { mode, brightness: 0 },
        rear: { mode, brightness: 0 }
      }, `vehicle_light_${this.vehicleLightEnabled ? 'on' : 'off'}`)
      if (!ok) {
        this.vehicleLightEnabled = previous
        this.persistDeviceStateCache({
          ...this.deviceStateCache,
          vehicleLightEnabled: this.vehicleLightEnabled
        })
      }
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
          const powerOn = status.powerOn === undefined ? status.enabled : status.powerOn
          this.$set(
            this.warningLightState,
            device.deviceId,
            this.warningLightPowerOn(powerOn)
          )
        }
        const ptzKey = `${robotId}:${device.deviceId}`
        if (device.deviceType === 'DUAL_LIGHT_PTZ' && status.autoRotateEnabled !== undefined &&
            !(options.preserveExisting && this.ptzAutoRotateState[ptzKey] !== undefined)) {
          this.$set(this.ptzAutoRotateState, ptzKey, !!status.autoRotateEnabled)
        }
      })
      this.persistDeviceStateCache({
        ...this.deviceStateCache,
        audioState: this.audioState,
        launcherSafety: this.launcherSafety,
        netGunSafety: this.netGunSafety,
        warningLightState: this.warningLightState,
        ptzAutoRotateState: this.ptzAutoRotateState,
        vehicleLightEnabled: this.vehicleLightEnabled
      })
    },
  }
}
