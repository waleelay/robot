<template>
  <div class="page">
    <header class="topbar">
      <h1>实时视频调试台</h1>
      <el-tag :type="wsConnected ? 'success' : 'info'">
        WebSocket {{ wsConnected ? '已连接' : '未连接' }}
      </el-tag>
    </header>

    <main class="workspace">
      <aside class="left-panel">
        <section class="robot-list">
          <div class="list-title">机器人设备</div>
          <button
              v-for="robot in robots"
              :key="robot.robotId"
              class="robot-item"
              :class="{ active: selectedRobotId === robot.robotId }"
              @click="selectedRobotId = robot.robotId"
          >
            <span>{{ robot.name }}</span>
            <small>{{ robot.robotId }} · {{ robot.status || 'offline' }} · 电量 {{ batteryText(robot.battery) }}</small>
          </button>
        </section>
        <section class="left-log">
          <div class="event-head">
            <span>事件日志</span>
            <el-button size="mini" @click="events = []">清空</el-button>
          </div>
          <div class="event-log">
            <div v-for="(item, index) in events" :key="index" class="event-item">
              {{ item }}
            </div>
          </div>
        </section>
      </aside>

      <section class="video-area">
        <div class="area-header">
          <div>
            <h2>{{ selectedRobot.name }}</h2>
            <span>{{ selectedRobot.type }} · {{ selectedRobot.status || 'offline' }} · 电量 {{ batteryText(selectedRobot.battery) }} · {{ selectedRobot.cameras.length }} 路摄像头</span>
          </div>
          <div class="area-actions">
            <el-radio-group v-model="gridMode" size="small">
              <el-radio-button :label="1">1宫格</el-radio-button>
              <el-radio-button :label="4">4宫格</el-radio-button>
              <el-radio-button :label="9">9宫格</el-radio-button>
            </el-radio-group>
            <el-button size="small" :disabled="selectedRobot.status !== 'online'" @click="startRobot(selectedRobot)">全部观看</el-button>
            <el-button size="small" @click="stopRobot(selectedRobot)">全部停止</el-button>
            <el-button size="small" :type="recordingMode ? 'primary' : ''" @click="toggleRecordings">
              {{ recordingMode ? '返回实时' : '录像回放' }}
            </el-button>
          </div>
        </div>

        <div v-if="recordingMode" class="recording-workspace">
          <div class="recording-list">
            <div class="recording-head">
              <el-tabs v-model="recordingTab" class="recording-tabs" @tab-click="loadRecordings">
                <el-tab-pane label="手动录像" name="manual" />
                <el-tab-pane label="巡逻录像" name="patrol" />
              </el-tabs>
              <el-button size="mini" :loading="recordingsLoading" @click="loadRecordings">刷新</el-button>
            </div>
            <button
                v-for="recording in recordings"
                :key="recording.recordingId"
                class="recording-item"
                :class="{ active: selectedRecording && selectedRecording.recordingId === recording.recordingId }"
                @click="playRecording(recording)"
            >
              <span>{{ recording.fileName }}</span>
              <small>{{ recording.deviceId }} · {{ durationText(recording.durationSeconds) }} · {{ recording.status }} · {{ recording.sourceType }}</small>
            </button>
            <div v-if="!recordings.length && !recordingsLoading" class="recording-empty">暂无录像记录</div>
          </div>
          <div class="recording-player">
            <video ref="recordedPlayer" controls playsinline />
            <div v-if="!selectedRecording" class="empty-video">选择 READY 录像开始回放</div>
          </div>
        </div>

        <div v-else class="camera-grid" :class="'grid-' + gridMode">
          <div v-for="camera in displayedCameras" :key="camera.key" class="camera-card">
            <div class="video-stage">
              <video :ref="camera.key" autoplay playsinline muted />
              <audio :ref="camera.key + '-audio'" autoplay />
              <div class="video-topbar">
                <div>
                  <strong>{{ camera.name }}</strong>
                  <span>{{ camera.deviceId }} · {{ groupTypeText(camera.groupType) }} · {{ camera.channel }}</span>
                </div>
                <div class="video-topbar-actions">
                  <el-select
                      v-model="camera.quality"
                      class="quality-select"
                      size="mini"
                      :disabled="qualitySelectDisabled(camera)"
                      @change="changeCameraQuality(camera)"
                  >
                    <el-option
                        v-for="option in qualityOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                    />
                  </el-select>
                  <el-tag size="mini" :type="statusType(camera.status)">
                    {{ camera.status || '未观看' }}
                  </el-tag>
                  <span
                      v-if="camera.watching"
                      class="latency-pill"
                      :class="'latency-' + latencyLevel(camera)"
                  >{{ latencyText(camera) }}</span>
                </div>
              </div>
              <div v-if="!camera.hasVideo" class="empty-video">
                {{ camera.session ? '等待 LiveKit Track' : '点击播放开始观看' }}
              </div>
              <div class="video-tools">
                <el-button
                    v-if="!camera.session || camera.stopped"
                    size="mini"
                    icon="el-icon-video-play"
                    :loading="camera.loading"
                    :disabled="selectedRobot.status !== 'online'"
                    @click="startCamera(selectedRobot, camera)"
                >观看</el-button>
                <el-button
                    v-else
                    size="mini"
                    icon="el-icon-video-pause"
                    :loading="camera.loading"
                    @click="stopCamera(camera)"
                >停止</el-button>
                <el-button
                    size="mini"
                    icon="el-icon-camera"
                    :disabled="!canSnapshot(camera)"
                    @click="snapshotCamera(camera)"
                >抓拍</el-button>
                <el-button
                    size="mini"
                    :icon="camera.recordingActive ? 'el-icon-video-pause' : 'el-icon-video-camera'"
                    :loading="camera.recordingBusy"
                    :disabled="!canRecord(camera)"
                    @click="toggleLiveRecording(camera)"
                >{{ camera.recordingActive ? '停录' : '录像' }}</el-button>
                <el-button
                    size="mini"
                    :icon="camera.intercomActive ? 'el-icon-turn-off-microphone' : 'el-icon-microphone'"
                    :loading="camera.intercomBusy"
                    :disabled="selectedRobot.status !== 'online'"
                    @click="toggleIntercom(selectedRobot, camera)"
                >{{ camera.intercomActive ? '挂断' : '通话' }}</el-button>
              </div>
              <div class="video-bottombar">
                <span>{{ camera.session ? camera.session.sessionId : '无会话' }}</span>
                <span>{{ camera.intercomActive ? '通话中' : (camera.viewerCount || 0) + ' 人观看' }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <aside class="equipment-panel">
        <div class="equipment-control">
          <div class="event-head">
            <span>装备控制</span>
            <el-tag size="mini">{{ selectedRobot.controlMode || 'MANUAL' }}</el-tag>
          </div>
          <div class="control-block">
            <strong>本体</strong>
            <small>{{ baseDevice ? baseDevice.deviceType : '无本体能力' }}</small>
            <div class="control-grid">
              <el-button size="mini" @mousedown.native="startFrameControl('base-forward')" @mouseup.native="stopFrameControl('base-forward')" @mouseleave.native="stopFrameControl('base-forward')" @touchstart.native.prevent="startFrameControl('base-forward')" @touchend.native.prevent="stopFrameControl('base-forward')">前进</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('base-backward')" @mouseup.native="stopFrameControl('base-backward')" @mouseleave.native="stopFrameControl('base-backward')" @touchstart.native.prevent="startFrameControl('base-backward')" @touchend.native.prevent="stopFrameControl('base-backward')">后退</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('base-left')" @mouseup.native="stopFrameControl('base-left')" @mouseleave.native="stopFrameControl('base-left')" @touchstart.native.prevent="startFrameControl('base-left')" @touchend.native.prevent="stopFrameControl('base-left')">左转</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('base-right')" @mouseup.native="stopFrameControl('base-right')" @mouseleave.native="stopFrameControl('base-right')" @touchstart.native.prevent="startFrameControl('base-right')" @touchend.native.prevent="stopFrameControl('base-right')">右转</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('base-strafe-left')" @mouseup.native="stopFrameControl('base-strafe-left')" @mouseleave.native="stopFrameControl('base-strafe-left')" @touchstart.native.prevent="startFrameControl('base-strafe-left')" @touchend.native.prevent="stopFrameControl('base-strafe-left')">左移</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('base-strafe-right')" @mouseup.native="stopFrameControl('base-strafe-right')" @mouseleave.native="stopFrameControl('base-strafe-right')" @touchstart.native.prevent="startFrameControl('base-strafe-right')" @touchend.native.prevent="stopFrameControl('base-strafe-right')">右移</el-button>
            </div>
          </div>
          <div class="control-block">
            <strong>双光云台</strong>
            <small>{{ ptzDevice ? ptzDevice.deviceId : '未绑定' }}</small>
            <div class="control-grid control-grid-3">
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-up-left')" @mouseup.native="stopFrameControl('ptz-up-left')" @mouseleave.native="stopFrameControl('ptz-up-left')" @touchstart.native.prevent="startFrameControl('ptz-up-left')" @touchend.native.prevent="stopFrameControl('ptz-up-left')">左上</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-up')" @mouseup.native="stopFrameControl('ptz-up')" @mouseleave.native="stopFrameControl('ptz-up')" @touchstart.native.prevent="startFrameControl('ptz-up')" @touchend.native.prevent="stopFrameControl('ptz-up')">上</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-up-right')" @mouseup.native="stopFrameControl('ptz-up-right')" @mouseleave.native="stopFrameControl('ptz-up-right')" @touchstart.native.prevent="startFrameControl('ptz-up-right')" @touchend.native.prevent="stopFrameControl('ptz-up-right')">右上</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-left')" @mouseup.native="stopFrameControl('ptz-left')" @mouseleave.native="stopFrameControl('ptz-left')" @touchstart.native.prevent="startFrameControl('ptz-left')" @touchend.native.prevent="stopFrameControl('ptz-left')">左</el-button>
              <el-button size="mini" :type="isPtzAutoRotateOn(ptzDevice) ? 'primary' : ''" @click="togglePtzAutoRotate">{{ isPtzAutoRotateOn(ptzDevice) ? '停止旋转' : '自动旋转' }}</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-right')" @mouseup.native="stopFrameControl('ptz-right')" @mouseleave.native="stopFrameControl('ptz-right')" @touchstart.native.prevent="startFrameControl('ptz-right')" @touchend.native.prevent="stopFrameControl('ptz-right')">右</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-down-left')" @mouseup.native="stopFrameControl('ptz-down-left')" @mouseleave.native="stopFrameControl('ptz-down-left')" @touchstart.native.prevent="startFrameControl('ptz-down-left')" @touchend.native.prevent="stopFrameControl('ptz-down-left')">左下</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-down')" @mouseup.native="stopFrameControl('ptz-down')" @mouseleave.native="stopFrameControl('ptz-down')" @touchstart.native.prevent="startFrameControl('ptz-down')" @touchend.native.prevent="stopFrameControl('ptz-down')">下</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('ptz-down-right')" @mouseup.native="stopFrameControl('ptz-down-right')" @mouseleave.native="stopFrameControl('ptz-down-right')" @touchstart.native.prevent="startFrameControl('ptz-down-right')" @touchend.native.prevent="stopFrameControl('ptz-down-right')">右下</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('zoom-in')" @mouseup.native="stopFrameControl('zoom-in')" @mouseleave.native="stopFrameControl('zoom-in')" @touchstart.native.prevent="startFrameControl('zoom-in')" @touchend.native.prevent="stopFrameControl('zoom-in')">变焦+</el-button>
              <el-button size="mini" @mousedown.native="startFrameControl('zoom-out')" @mouseup.native="stopFrameControl('zoom-out')" @mouseleave.native="stopFrameControl('zoom-out')" @touchstart.native.prevent="startFrameControl('zoom-out')" @touchend.native.prevent="stopFrameControl('zoom-out')">变焦-</el-button>
            </div>
          </div>
          <div class="control-block" v-if="audioDevice">
            <strong>客户端音量</strong>
            <small>{{ audioDevice.deviceId }} · {{ audioMuted(audioDevice) ? '已静音' : `音量 ${audioVolume(audioDevice)}` }}</small>
            <div class="audio-control">
              <el-slider
                  :value="audioVolume(audioDevice)"
                  :min="0"
                  :max="100"
                  :disabled="audioMuted(audioDevice)"
                  @input="value => updateAudioVolume(audioDevice, value)"
                  @change="value => setAudioVolume(audioDevice, value)"
              />
              <div class="audio-actions">
                <el-button size="mini" :type="audioMuted(audioDevice) ? 'primary' : ''" @click="toggleAudioMute(audioDevice)">
                  {{ audioMuted(audioDevice) ? '取消静音' : '静音' }}
                </el-button>
                <div class="audio-stepper">
                  <el-button size="mini" @click="adjustAudioVolume(audioDevice, 5)">+</el-button>
                  <span>音量</span>
                  <el-button size="mini" @click="adjustAudioVolume(audioDevice, -5)">-</el-button>
                </div>
              </div>
            </div>
          </div>
          <div class="control-block" v-if="launcherDevice">
            <strong>发射器</strong>
            <small>{{ launcherDevice.deviceId }}</small>
            <div class="control-inline">
              <span>安全开关</span>
              <el-switch
                  :value="isLauncherSafetyOn(launcherDevice)"
                  active-text="开"
                  inactive-text="关"
                  @change="setLauncherSafety(launcherDevice, $event)"
              />
            </div>
            <div class="control-grid control-grid-3">
              <el-button
                  v-for="channel in 6"
                  :key="channel"
                  size="mini"
                  type="danger"
                  :disabled="!isLauncherSafetyOn(launcherDevice)"
                  @click="firePayload(launcherDevice, channel, `launcher_${channel}`)"
              >发射{{ channel }}</el-button>
            </div>
          </div>
          <div class="control-block" v-if="netGunDevice">
            <strong>捕网枪</strong>
            <small>{{ netGunDevice.deviceId }}</small>
            <div class="control-inline">
              <span>安全开关</span>
              <el-switch
                  :value="isNetGunSafetyOn(netGunDevice)"
                  active-text="开"
                  inactive-text="关"
                  @change="setFakeNetGunSafety(netGunDevice, $event)"
              />
            </div>
            <div class="control-grid">
              <el-popconfirm
                  title="确认发射捕网枪？"
                  confirm-button-text="确认发射"
                  cancel-button-text="取消"
                  @confirm="firePayload(netGunDevice, 1, 'net_gun_fire')"
              >
                <el-button slot="reference" size="mini" type="danger" :disabled="!isNetGunSafetyOn(netGunDevice)">发射</el-button>
              </el-popconfirm>
            </div>
          </div>
          <div class="control-block" v-if="warningLightDevices.length">
            <strong>警示灯</strong>
            <div v-for="device in warningLightDevices" :key="device.deviceId" class="device-row">
              <div class="control-inline warning-light-row">
                <small>{{ device.displayName || device.deviceId }}</small>
                <el-switch
                    class="warning-switch"
                    :value="isWarningLightOn(device)"
                    active-text="开启"
                    inactive-text="关闭"
                    active-color="#31c56f"
                    inactive-color="#59606b"
                    @change="setWarningLight(device, $event)"
                />
              </div>
            </div>
          </div>
          <div class="control-block" v-if="vehicleLightDevice">
            <strong>车灯光</strong>
            <small>{{ vehicleLightDevice.deviceId }}</small>
            <div v-for="part in vehicleLightParts" :key="part.key" class="vehicle-light-row">
              <div class="vehicle-light-title">
                <span>{{ part.label }}</span>
                <small>{{ vehicleLightModeLabel(vehicleLightState[part.key].mode) }}</small>
              </div>
              <el-radio-group
                  class="vehicle-light-mode-group"
                  v-model="vehicleLightState[part.key].mode"
                  size="mini"
                  @change="mode => setVehicleLightMode(part.key, mode)"
              >
                <el-radio-button
                    v-for="option in vehicleLightModeOptions"
                    :key="option.value"
                    :label="option.value"
                >{{ option.label }}</el-radio-button>
              </el-radio-group>
              <div v-if="vehicleLightState[part.key].mode === 'CUSTOM'" class="vehicle-light-brightness">
                <span>自定义亮度</span>
                <el-slider
                    :value="vehicleLightState[part.key].customValue"
                    :min="0"
                    :max="100"
                    @input="value => updateVehicleLightBrightness(part.key, value)"
                    @change="value => setVehicleLightBrightness(part.key, value)"
                />
                <em>{{ vehicleLightState[part.key].customValue }}</em>
              </div>
            </div>
          </div>
          <div class="control-block" v-if="searchlightDevice">
            <strong>探照灯</strong>
            <el-button size="mini" @click="sendDeviceCommand(searchlightDevice, 'light.set', { enabled: true, brightness: 80, mode: 'STEADY' }, 'searchlight_on')">开关</el-button>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script>
import { Room, RoomEvent, VideoQuality } from 'livekit-client'
import Hls from 'hls.js'
import {
  acquireControl,
  createConfirmToken,
  createSnapshotFile,
  createVideoSession,
  getControlProfile,
  getRobots,
  getRecordings,
  getRecordingPlayUrl,
  getActiveLiveRecording,
  getViewerToken,
  heartbeatVideoSession,
  heartbeatIntercom,
  mediaClientId,
  restartVideoSession,
  sendEquipmentCommand,
  snapshotImageUrl,
  startLiveRecording,
  startCameraIntercom,
  startSessionIntercom,
  stopIntercom,
  stopLiveRecording,
  takeoverControl,
  stopVideoSession
} from './api/media'

// 前端内部摄像头状态模型。
// 后端返回的是业务会话/设备字段，页面还需要额外保存 LiveKit Room、加载状态、
// 本地停止意图、对讲 token 等 UI/连接态，所以统一在这里初始化。
function cameraState(robotId, deviceId, name, channel, groupType) {
  return {
    key: `${robotId}-${deviceId}-${channel}`,
    robotId,
    deviceId,
    name,
    groupType: groupType || 'body',
    channel,
    quality: 'auto',
    loading: false,
    hasVideo: false,
    hasAudio: false,
    watching: false,
    intercomActive: false,
    intercomBusy: false,
    intercomStatus: 'IDLE',
    intercomToken: null,
    recordingActive: false,
    recordingBusy: false,
    activeRecording: null,
    latencyMs: null,
    latencyLevel: 'unknown',
    statsTimer: null,
    statsTrack: null,
    statsRoom: null,
    stopping: false,
    stopped: false,
    restarting: false,
    connecting: false,
    disconnecting: false,
    qualityChanging: false,
    room: null,
    session: null,
    status: 'offline',
    viewerCount: 0
  }
}

export default {
  name: 'App',
  data() {
    return {
      wsConnected: false,
      socket: null,
      gridMode: 4,
      heartbeatTimer: null,
      stoppedSessionIds: new Set(),
      selectedRobotId: 'robot-001',
      robots: [
        {
          robotId: 'robot-001',
          name: '松灵四轮机器人',
          type: '轮式机器人',
          battery: null,
          status: 'offline',
          cameras: [
            cameraState('robot-001', 'camera01', '云台-可见光', 'visible', 'dual_gimbal'),
            cameraState('robot-001', 'camera02', '云台-热成像', 'visible', 'dual_gimbal'),
            cameraState('robot-001', 'camera03', '本体相机', 'visible', 'body')
          ]
        },
        {
          robotId: 'robot-002',
          name: '云深处四足机器狗',
          type: '四足机器人',
          battery: null,
          status: 'offline',
          cameras: [
            cameraState('robot-002', 'gimbal-001', '头部双光云台', 'visible', 'dual_gimbal'),
            cameraState('robot-002', 'gimbal-002', '腹部导航相机', 'visible', 'body'),
            cameraState('robot-002', 'gimbal-003', '尾部避障相机', 'visible', 'body')
          ]
        }
      ],
      events: [],
      recordingMode: false,
      recordingTab: 'manual',
      recordingsLoading: false,
      recordings: [],
      selectedRecording: null,
      recordedHls: null,
      qualityOptions: [
        { value: 'auto', label: '自动' },
        { value: 'main', label: '高清' },
        { value: 'sub', label: '流畅' }
      ],
      controlProfiles: {},
      controlSessions: {},
      controlSeq: 1,
      controlTimers: {},
      ptzAutoRotateState: {},
      audioState: {},
      launcherSafety: {},
      netGunSafety: {},
      warningLightState: {},
      vehicleLightState: {
        front: { mode: 'OFF', customValue: 50 },
        rear: { mode: 'OFF', customValue: 50 }
      },
      confirmedVehicleLightState: {
        front: { mode: 'OFF', customValue: 50 },
        rear: { mode: 'OFF', customValue: 50 }
      },
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
  computed: {
    // 当前选中的机器人对象。设备列表从 MQTT 心跳刷新，找不到时兜底到第一台。
    selectedRobot() {
      return this.robots.find(item => item.robotId === this.selectedRobotId) || this.robots[0]
    },
    // 宫格数量只影响展示摄像头数量，不会改变机器人真实摄像头列表。
    displayedCameras() {
      return this.selectedRobot.cameras.slice(0, this.gridMode)
    },
    selectedControlProfile() {
      return this.controlProfiles[this.selectedRobotId] || { devices: [] }
    },
    baseDevice() {
      return this.controlDevice('base')
    },
    ptzDevice() {
      return this.controlDevices().find(device => device.deviceType === 'DUAL_LIGHT_PTZ')
    },
    netGunDevice() {
      return this.controlDevices().find(device => device.deviceType === 'NET_GUN' || device.deviceType === 'NET_LAUNCHER')
    },
    launcherDevice() {
      return this.controlDevices().find(device => device.deviceType === 'LAUNCHER')
    },
    audioDevice() {
      return this.controlDevices().find(device => ['CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
    },
    warningLightDevices() {
      return this.controlDevices().filter(device => device.deviceType === 'WARNING_LIGHT')
    },
    vehicleLightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'VEHICLE_LIGHT')
    },
    searchlightDevice() {
      return this.controlDevices().find(device => device.deviceType === 'SEARCHLIGHT')
    }
  },
  watch: {
    // 回放列表按机器人过滤；切换机器人时清掉旧播放器，避免旧 HLS 继续占用网络。
    selectedRobotId() {
      if (this.recordingMode) {
        this.selectedRecording = null
        this.destroyRecordedHls()
        this.loadRecordings()
      }
      this.loadControlProfile(this.selectedRobotId)
    }
  },
  mounted() {
    // 页面启动时同时拉一次静态列表、订阅事件流，并开启 viewer/intercom 心跳。
    this.loadRobots()
    this.connectWebSocket()
    this.heartbeatTimer = setInterval(this.heartbeatViewers, 5000)
  },
  beforeDestroy() {
    // Vue 组件销毁时主动关闭所有长连接，避免 LiveKit/MQTT 状态仍以为浏览器在线。
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    Object.values(this.controlTimers).forEach(timer => clearInterval(timer))
    if (this.socket) this.socket.close()
    this.destroyRecordedHls()
    this.allCameras().forEach(camera => {
      this.stopLatencyStats(camera)
      if (camera.room) camera.room.disconnect()
    })
  },
  methods: {
    // 在“实时观看”和“录像回放”两套工作区之间切换。
    // 回放使用独立 HLS 实例，离开回放页必须销毁，否则会继续拉取分片。
    async toggleRecordings() {
      this.recordingMode = !this.recordingMode
      if (this.recordingMode) {
        await this.loadRecordings()
      } else {
        this.destroyRecordedHls()
        this.selectedRecording = null
      }
    },
    // 录像列表只拉 READY 状态，确保用户点开后一定能拿到可播放的 HLS 地址。
    async loadRecordings() {
      this.recordingsLoading = true
      try {
        const params = {
          robotId: this.selectedRobotId,
          status: 'READY',
          page: 0,
          size: 20
        }
        if (this.recordingTab === 'manual') {
          params.sourceType = 'LIVEKIT_EGRESS'
        }
        const response = await getRecordings(params)
        const items = response.items || []
        this.recordings = this.recordingTab === 'patrol'
          ? items.filter(item => item.sourceType !== 'LIVEKIT_EGRESS')
          : items
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        this.recordingsLoading = false
      }
    },
    // 播放录像时先向后端换取签名 URL，再根据浏览器能力选择原生 HLS 或 hls.js。
    // Safari 可原生播放 m3u8；Chrome/Edge 走 hls.js。
    async playRecording(recording) {
      if (recording.status !== 'READY') return
      try {
        const playback = await getRecordingPlayUrl(recording.recordingId)
        const player = this.$refs.recordedPlayer
        this.destroyRecordedHls()
        this.selectedRecording = recording
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = playback.playUrl
        } else if (Hls.isSupported()) {
          this.recordedHls = new Hls()
          this.recordedHls.loadSource(playback.playUrl)
          this.recordedHls.attachMedia(player)
        } else {
          throw new Error('当前浏览器不支持 HLS 播放')
        }
        await player.play().catch(() => {})
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      }
    },
    // 销毁当前录像播放器，包括 hls.js 实例、video src 和缩略图 hover 状态。
    destroyRecordedHls() {
      if (this.recordedHls) {
        this.recordedHls.destroy()
        this.recordedHls = null
      }
      const player = this.$refs.recordedPlayer
      if (player) {
        player.pause()
        player.removeAttribute('src')
        player.load()
      }
    },
    durationText(seconds) {
      if (!seconds) return '--:--'
      const hours = Math.floor(seconds / 3600)
      const minutes = Math.floor((seconds % 3600) / 60)
      const remainder = seconds % 60
      const text = `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
      return hours > 0 ? `${hours}:${text}` : text
    },
    async startRobot(robot) {
      if (robot.status !== 'online') return
      for (const camera of robot.cameras) {
        await this.startCamera(robot, camera)
      }
    },
    async stopRobot(robot) {
      for (const camera of robot.cameras) {
        await this.stopCamera(camera)
      }
    },
    // 周期性心跳同时承担三个职责：
    // 1. 让后端知道当前浏览器仍在观看，从而维持 viewerCount；
    // 2. 让后端知道对讲操作员仍在线，避免对讲被超时释放。
    // 3. 同步 session 级录像状态，录制结束后复位本浏览器的停录按钮。
    heartbeatViewers() {
      this.allCameras().forEach(camera => {
        if (camera.session && !camera.stopped && !camera.stopping) {
          const heartbeat = camera.watching
              ? heartbeatVideoSession(camera.session.sessionId)
              : Promise.resolve(camera.session)
          heartbeat
              .then(session => {
                if (camera.session && camera.session.sessionId === session.sessionId) {
                  camera.viewerCount = session.viewerCount
                }
              })
              .catch(() => {})
          if (camera.watching && !camera.recordingBusy) {
            this.syncActiveRecording(camera)
          }
        }
        if (camera.session && camera.intercomActive) {
          heartbeatIntercom(camera.session.sessionId)
              .then(response => {
                camera.intercomStatus = response.intercomStatus
              })
              .catch(() => {})
        }
      })
    },
    // 开始观看单路摄像头：先通过控制 API 创建/复用会话，再用返回的 token 连 LiveKit。
    async startCamera(robot, camera) {
      if (robot.status !== 'online') return
      camera.loading = true
      camera.stopped = false
      camera.stopping = false
      camera.restarting = false
      camera.watching = true
      const requestQuality = this.effectiveCameraQuality(camera)
      this.log('CLICK startCamera', {
        robotId: robot.robotId,
        deviceId: camera.deviceId,
        channel: camera.channel,
        quality: requestQuality,
        qualityPreference: camera.quality
      })
      try {
        const session = await createVideoSession({
          robotId: robot.robotId,
          deviceId: camera.deviceId,
          channel: camera.channel,
          quality: requestQuality,
          reuse: true
        })
        camera.session = this.mergeSession(camera, session)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        this.stoppedSessionIds.delete(camera.session.sessionId)
        this.log('API createVideoSession', camera.session)
        await this.connectLiveKit(camera, false, null, true)
        await this.syncActiveRecording(camera)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR createVideoSession', this.errorMessage(error))
      } finally {
        camera.loading = false
      }
    },
    // 停止观看只表示当前浏览器离开。若还有其他 viewer 或对讲占用，后端会保留 Room。
    async stopCamera(camera) {
      if (!camera.session) return
      camera.loading = true
      camera.stopping = true
      camera.stopped = true
      camera.restarting = false
      camera.disconnecting = true
      try {
        const sessionId = camera.session.sessionId
        if (camera.recordingActive) {
          await this.stopCameraRecording(camera)
        }
        if (!camera.intercomActive) this.stoppedSessionIds.add(sessionId)
        const stopped = await stopVideoSession(sessionId)
        this.log('API stopVideoSession', stopped)
        camera.watching = false
        camera.hasVideo = false
        this.stopLatencyStats(camera)
        camera.recordingActive = false
        camera.activeRecording = null
        const video = this.videoElement(camera)
        if (video) video.srcObject = null
        if (camera.intercomActive) {
          camera.status = stopped.status
          camera.viewerCount = stopped.viewerCount || 0
          return
        }
        if (camera.room) {
          const oldRoom = camera.room
          camera.room = null
          this.stopLatencyStats(camera)
          await oldRoom.disconnect()
        }
        camera.hasVideo = false
        if (camera.session && camera.session.sessionId === sessionId) {
          camera.session = null
          camera.status = ''
          camera.viewerCount = stopped.viewerCount || 0
        }
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        camera.disconnecting = false
        camera.stopping = false
        camera.loading = false
      }
    },
    effectiveCameraQuality(camera, value) {
      const quality = value || camera.quality || 'auto'
      if (quality === 'main' || quality === 'sub') return quality
      return this.gridMode === 1 ? 'main' : 'sub'
    },
    activeRecordingInProgress(camera) {
      return camera.recordingActive || (camera.activeRecording && camera.activeRecording.status === 'RECORDING')
    },
    intercomInProgress(camera) {
      return camera.intercomActive || (camera.intercomStatus && !['IDLE', 'FAILED'].includes(camera.intercomStatus))
    },
    qualitySelectDisabled(camera) {
      return camera.qualityChanging
          || this.activeRecordingInProgress(camera)
          || this.intercomInProgress(camera)
    },
    currentCameraState(camera) {
      return this.allCameras().find(item => item.key === camera.key) || camera
    },
    resetQualityChanging(camera) {
      camera.disconnecting = false
      camera.qualityChanging = false
      const current = this.currentCameraState(camera)
      current.disconnecting = false
      current.qualityChanging = false
    },
    async changeCameraQuality(camera) {
      if (!camera.session || !camera.watching || camera.stopped) return
      if (this.activeRecordingInProgress(camera)) {
        this.$message.warning('请先停止录像后再切换清晰度')
        return
      }
      if (this.intercomInProgress(camera)) {
        this.$message.warning('请先关闭对讲后再切换清晰度')
        return
      }
      const nextQuality = this.effectiveCameraQuality(camera)
      const currentQuality = camera.session.quality || this.effectiveCameraQuality(camera)
      if (nextQuality === currentQuality) return
      await this.switchCameraQuality(camera, nextQuality)
    },
    async switchCameraQuality(camera, nextQuality) {
      const oldSession = camera.session
      const oldRoom = camera.room
      if (!oldSession || camera.qualityChanging) return
      camera.qualityChanging = true
      let nextSession = null
      let nextRoom = null
      try {
        nextSession = await createVideoSession({
          robotId: camera.robotId,
          deviceId: camera.deviceId,
          channel: camera.channel,
          quality: nextQuality,
          reuse: true
        })
        nextRoom = await this.connectReplacementLiveKit(camera, nextSession, oldRoom)
        camera.room = nextRoom
        camera.session = Object.assign({}, nextSession)
        camera.status = nextSession.status
        camera.viewerCount = nextSession.viewerCount
        camera.hasVideo = true
        const publication = this.firstVideoPublication(nextRoom)
        if (publication && publication.track) this.startLatencyStats(camera, publication.track, nextRoom)
        camera.stopped = false
        camera.stopping = false
        this.stoppedSessionIds.delete(nextSession.sessionId)
        if (oldRoom) {
          camera.disconnecting = true
          Promise.resolve(oldRoom.disconnect()).catch(error => {
            this.log('ERROR disconnect old quality room', this.errorMessage(error))
          })
          camera.disconnecting = false
        }
        this.stoppedSessionIds.add(oldSession.sessionId)
        stopVideoSession(oldSession.sessionId).catch(error => {
          this.log('ERROR stop old quality session', this.errorMessage(error))
        })
        this.log('API switchCameraQuality', {
          from: oldSession.quality,
          to: nextQuality,
          oldSessionId: oldSession.sessionId,
          newSessionId: nextSession.sessionId
        })
      } catch (error) {
        if (nextRoom) {
          await Promise.resolve(nextRoom.disconnect()).catch(() => {})
        }
        if (nextSession && nextSession.sessionId) {
          stopVideoSession(nextSession.sessionId).catch(() => {})
        }
        this.$message.error(`清晰度切换失败：${this.errorMessage(error)}`)
        this.log('ERROR switchCameraQuality', this.errorMessage(error))
      } finally {
        this.resetQualityChanging(camera)
      }
    },
    async toggleIntercom(robot, camera) {
      if (camera.intercomActive) {
        await this.hangupIntercom(camera)
      } else {
        await this.startIntercom(robot, camera)
      }
    },
    async startIntercom(robot, camera) {
      camera.intercomBusy = true
      try {
        const response = camera.session
            ? await startSessionIntercom(camera.session.sessionId)
            : await startCameraIntercom({
              robotId: robot.robotId,
              deviceId: camera.deviceId,
              channel: camera.channel,
              quality: this.effectiveCameraQuality(camera)
            })
        camera.session = this.mergeSession(camera, {
          sessionId: response.sessionId,
          robotId: response.robotId,
          deviceId: response.deviceId,
          roomName: response.roomName,
          status: response.videoStatus,
          intercomStatus: response.intercomStatus,
          intercomAudioOnly: response.intercomAudioOnly,
          livekitUrl: response.livekitUrl
        })
        camera.intercomToken = response.operatorToken
        camera.intercomActive = true
        camera.intercomStatus = response.intercomStatus
        camera.stopped = false
        if (!camera.room) {
          await this.connectLiveKit(camera, false, response.operatorToken)
        }
        if (camera.room) {
          await camera.room.localParticipant.setMicrophoneEnabled(true, {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }, {
            name: 'audio.operator.mic'
          })
        }
        this.log('API startIntercom', response)
      } catch (error) {
        camera.intercomActive = false
        this.$message.error(this.errorMessage(error))
        this.log('ERROR startIntercom', this.errorMessage(error))
      } finally {
        camera.intercomBusy = false
      }
    },
    // 结束对讲时要分两种场景：
    // - 仍在观看视频：保留 LiveKit Room 和视频 track；
    // - 只是对讲：断开 Room 并清掉会话状态。
    async hangupIntercom(camera) {
      if (!camera.session) return
      camera.intercomBusy = true
      try {
        if (camera.room) {
          await camera.room.localParticipant.setMicrophoneEnabled(false)
        }
        const response = await stopIntercom(camera.session.sessionId)
        camera.intercomActive = false
        camera.intercomStatus = 'IDLE'
        camera.intercomToken = null
        camera.hasAudio = false
        if (camera.watching) {
          camera.session = this.mergeSession(camera, response)
        } else {
          if (camera.room) {
            camera.disconnecting = true
            try {
              await camera.room.disconnect()
            } finally {
              camera.disconnecting = false
            }
          }
          camera.room = null
          camera.session = null
          camera.status = ''
        }
        this.log('API stopIntercom', response)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        camera.intercomBusy = false
      }
    },
    async snapshotCamera(camera) {
      if (!camera.session) return
      const capturedAt = new Date().toISOString()
      const blob = await this.captureFrameBlob(camera)
      if (!blob) {
        this.$message.warning('当前画面不可抓拍')
        return
      }
      const form = new FormData()
      form.append('trackSid', camera.session.trackSid || 'TR_pending')
      form.append('reason', 'manual_abnormal')
      form.append('remark', `${camera.name} 手动抓拍`)
      form.append('clientCapturedAt', capturedAt)
      form.append('previewImageHash', `${blob.size}`)
      form.append('file', blob, `${camera.robotId}-${camera.deviceId}-${Date.now()}.jpg`)
      const response = await createSnapshotFile(camera.session.sessionId, form)
      this.log('API snapshot', response)
      this.showSnapshotSuccess(camera, response)
    },
    // 连接 LiveKit 的核心逻辑。
    // 普通观看使用 viewerToken；对讲使用 operatorToken，并在同一个 Room 中发布浏览器麦克风。
    // 事件回调里会把远端 audio/video track 挂到对应 DOM 元素上。
    async connectLiveKit(camera, refreshToken, connectionToken, forceReconnect) {
      if (camera.connecting && !forceReconnect) return
      if (!camera.session) return
      if (forceReconnect) camera.connecting = false
      camera.connecting = true
      try {
        if (!camera.intercomActive && (refreshToken || !camera.session.viewerToken || !camera.session.livekitUrl)) {
          const token = await getViewerToken(camera.session.sessionId)
          camera.session = this.mergeSession(camera, {
            livekitUrl: token.livekitUrl,
            roomName: token.roomName,
            viewerToken: token.token
          })
        }
        const token = connectionToken || (camera.intercomActive ? camera.intercomToken : camera.session.viewerToken)
        const livekitUrl = this.liveKitConnectionUrl(camera.session.livekitUrl)
        if (!token || !livekitUrl) {
          this.log('LiveKit skipped', { camera: camera.deviceId, hasToken: !!token, livekitUrl })
          return
        }
        if (camera.room) {
          camera.disconnecting = true
          const oldRoom = camera.room
          camera.room = null
          this.stopLatencyStats(camera)
          await oldRoom.disconnect()
          camera.disconnecting = false
        }
        const room = new Room()
        const sessionId = camera.session.sessionId
        room.on(RoomEvent.TrackSubscribed, (track, publication) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          if (track.kind === 'audio') {
            track.attach(this.audioElement(camera))
            camera.hasAudio = true
          } else if (track.kind === 'video' && camera.watching) {
            this.attachVideoTrack(camera, track, publication, room)
            camera.hasVideo = true
          }
          this.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
        })
        room.on(RoomEvent.TrackUnsubscribed, (track) => {
          if (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId) return
          track.detach()
          if (track.kind === 'audio') camera.hasAudio = false
          if (track.kind === 'video') {
            camera.hasVideo = false
            this.stopLatencyStats(camera)
          }
          this.log('LiveKit TrackUnsubscribed', `${camera.name} ${track.sid || track.name}`)
          if (track.kind === 'video' && camera.watching && !this.isStoppedSession(camera, sessionId)) {
            this.restartCamera(camera)
          }
        })
        room.on(RoomEvent.Disconnected, () => {
          if (camera.room !== room || camera.disconnecting) return
          camera.hasVideo = false
          this.stopLatencyStats(camera)
          this.log('LiveKit Disconnected', camera.name)
          if (camera.watching && !this.isStoppedSession(camera, sessionId)) this.restartCamera(camera)
        })
        camera.room = room
        await room.connect(livekitUrl, token)
        this.attachExistingVideoTracks(camera, room, sessionId)
        this.log('LiveKit connected', `${camera.name} ${camera.session.roomName}`)
      } catch (error) {
        camera.room = null
        camera.hasVideo = false
        this.log('ERROR LiveKit connect', this.errorMessage(error))
      } finally {
        camera.disconnecting = false
        camera.connecting = false
      }
    },
    connectReplacementLiveKit(camera, session, oldRoom) {
      const livekitUrl = this.liveKitConnectionUrl(session.livekitUrl)
      const token = session.viewerToken
      if (!token || !livekitUrl) {
        return Promise.reject(new Error('缺少新清晰度 LiveKit 连接信息'))
      }
      const room = new Room()
      const sessionId = session.sessionId
      return new Promise((resolve, reject) => {
        let settled = false
        const timeout = setTimeout(() => fail(new Error('等待新清晰度视频流超时')), 15000)
        const done = () => {
          if (settled) return
          settled = true
          clearTimeout(timeout)
          resolve(room)
        }
        const fail = (error) => {
          if (settled) return
          settled = true
          clearTimeout(timeout)
          Promise.resolve(room.disconnect()).catch(() => {})
          reject(error)
        }
        room.on(RoomEvent.TrackSubscribed, (track, publication) => {
          if (track.kind !== 'video') return
          this.prepareReplacementVideo(camera, track, publication, oldRoom)
              .then(() => {
                camera.hasVideo = true
                this.log('LiveKit TrackSubscribed', `${camera.name} ${track.sid || track.name}`)
                done()
              })
              .catch(fail)
        })
        room.on(RoomEvent.Disconnected, () => {
          fail(new Error('新清晰度 LiveKit 连接已断开'))
        })
        room.connect(livekitUrl, token)
            .then(() => {
              const publication = this.firstVideoPublication(room)
              if (!publication || !publication.track) return
              this.prepareReplacementVideo(camera, publication.track, publication, oldRoom)
                  .then(() => {
                    camera.hasVideo = true
                    this.log('LiveKit TrackAttached', `${camera.name} ${publication.track.sid || publication.track.name}`)
                    done()
                  })
                  .catch(fail)
            })
            .catch(fail)
      })
    },
    // 控制 WebSocket 接收后端发布的机器人上下线、视频状态、对讲状态等事件。
    // 这些事件用于修正本地 UI 状态，而不是替代 REST API 的命令结果。
    connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = process.env.VUE_APP_WS_URL || `${protocol}//${window.location.host}/ws/control`
      const socket = new WebSocket(url)
      socket.onopen = () => {
        this.wsConnected = true
        this.log('WebSocket connected', url)
      }
      socket.onclose = () => {
        this.wsConnected = false
        this.log('WebSocket closed', url)
      }
      socket.onmessage = (message) => {
        const event = JSON.parse(message.data)
        this.syncControlEvent(event)
        this.syncRobotEvent(event)
        this.syncSessionEvent(event)
        this.log('WS event', event)
      }
      this.socket = socket
    },
    // 拉取大屏全景聚合中的机器人列表，并转换成页面内部 cameraState，保证后续 UI 字段完整。
    async loadRobots() {
      const robots = await getRobots()
      if (!robots.length) return
      this.robots = robots.map(this.toRobotState)
      if (!this.robots.find(robot => robot.robotId === this.selectedRobotId)) {
        this.selectedRobotId = this.robots[0].robotId
      }
      await this.loadControlProfile(this.selectedRobotId)
    },
    async loadControlProfile(robotId) {
      if (!robotId) return
      try {
        const profile = await getControlProfile(robotId)
        this.$set(this.controlProfiles, robotId, profile)
        const index = this.robots.findIndex(robot => robot.robotId === robotId)
        if (index >= 0) {
          this.$set(this.robots, index, Object.assign({}, this.robots[index], {
            controlMode: profile.controlMode,
            stateSeq: profile.stateSeq
          }))
          this.syncAudioStatesFromDevices(this.selectedRobotId, profile.devices)
        }
      } catch (error) {
        this.log('ERROR getControlProfile', this.errorMessage(error))
      }
    },
    controlDevices() {
      return this.selectedControlProfile.devices || []
    },
    controlDevice(deviceId) {
      return this.controlDevices().find(device => device.deviceId === deviceId)
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
    startFrameControl(kind) {
      if (this.controlTimers[kind]) return
      this.sendFrameControl(kind)
      this.$set(this.controlTimers, kind, setInterval(() => this.sendFrameControl(kind), 100))
    },
    stopFrameControl(kind) {
      if (!this.controlTimers[kind]) return
      clearInterval(this.controlTimers[kind])
      this.$delete(this.controlTimers, kind)
    },
    async sendFrameControl(kind) {
      try {
        const frame = await this.controlFrame(kind)
        if (!frame || !this.socket || this.socket.readyState !== WebSocket.OPEN) return
        this.socket.send(JSON.stringify({
          type: 'control.command',
          requestId: `req_${Date.now()}_${this.controlSeq}`,
          payload: frame
        }))
      } catch (error) {
        this.log('ERROR control frame', this.errorMessage(error))
        this.$message.error(this.errorMessage(error))
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
        const session = await this.ensureControlSession(device, 'ptz.move')
        const params = {
          'ptz-up': { panSpeed: 0, tiltSpeed: 0.4 },
          'ptz-down': { panSpeed: 0, tiltSpeed: -0.4 },
          'ptz-left': { panSpeed: -0.4, tiltSpeed: 0 },
          'ptz-right': { panSpeed: 0.4, tiltSpeed: 0 },
          'ptz-up-left': { panSpeed: -0.3, tiltSpeed: 0.3 },
          'ptz-up-right': { panSpeed: 0.3, tiltSpeed: 0.3 },
          'ptz-down-left': { panSpeed: -0.3, tiltSpeed: -0.3 },
          'ptz-down-right': { panSpeed: 0.3, tiltSpeed: -0.3 }
        }[kind]
        return this.commandPayload(robotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, 'ptz.move', params, kind)
      }
      if (kind.indexOf('zoom-') === 0) {
        const device = this.ptzDevice
        const session = await this.ensureControlSession(device, 'camera.zoom')
        const params = { zoomSpeed: kind === 'zoom-in' ? 0.5 : -0.5 }
        return this.commandPayload(robotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, 'camera.zoom', params, kind)
      }
      return null
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
    async sendDiscreteCommand(action) {
      const device = action === 'light.set'
          ? this.searchlightDevice
          : this.launcherDevice
      const session = await this.ensureControlSession(device, action)
      const params = {
        'payload.safety_switch': { enabled: true },
        'light.set': { enabled: true, brightness: 80, mode: 'STEADY' }
      }[action]
      const response = await sendEquipmentCommand(this.selectedRobotId,
          this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, action, params, action))
      this.log('API sendEquipmentCommand', response)
    },
    isNetGunSafetyOn(device) {
      return !!(device && this.netGunSafety[device.deviceId])
    },
    setFakeNetGunSafety(device, enabled) {
      this.$set(this.netGunSafety, device.deviceId, enabled)
    },
    isLauncherSafetyOn(device) {
      return !!(device && this.launcherSafety[device.deviceId])
    },
    async setLauncherSafety(device, enabled) {
      this.$set(this.launcherSafety, device.deviceId, enabled)
      const ok = await this.sendDeviceCommand(device, 'payload.safety_switch', { enabled }, `launcher_safety_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.launcherSafety, device.deviceId, !enabled)
      }
    },
    isWarningLightOn(device) {
      return !!(device && this.warningLightState[device.deviceId])
    },
    async setWarningLight(device, enabled) {
      this.$set(this.warningLightState, device.deviceId, enabled)
      const ok = await this.sendDeviceCommand(device, 'light.warning.set', { enabled }, `${device.deviceId}_${enabled ? 'on' : 'off'}`)
      if (!ok) {
        this.$set(this.warningLightState, device.deviceId, !enabled)
      }
    },
    ptzAutoRotateKey(device) {
      return device ? `${this.selectedRobotId}:${device.deviceId}` : ''
    },
    isPtzAutoRotateOn(device) {
      return !!(device && this.ptzAutoRotateState[this.ptzAutoRotateKey(device)])
    },
    async togglePtzAutoRotate() {
      const device = this.ptzDevice
      if (!device) return
      const key = this.ptzAutoRotateKey(device)
      const enabled = !this.ptzAutoRotateState[key]
      const ok = await this.sendDeviceCommand(device, 'ptz.auto_rotate', {
        enabled,
        panSpeed: 0.3
      }, `ptz_auto_rotate_${enabled ? 'on' : 'off'}`)
      if (ok) {
        this.$set(this.ptzAutoRotateState, key, enabled)
      }
    },
    audioKey(device) {
      return device ? `${this.selectedRobotId}:${device.deviceId}` : ''
    },
    audioStatus(device) {
      const key = this.audioKey(device)
      return this.audioState[key] || { volume: 50, muted: false }
    },
    audioVolume(device) {
      return this.audioStatus(device).volume
    },
    audioMuted(device) {
      return this.audioStatus(device).muted
    },
    setAudioState(device, patch) {
      if (!device) return
      const key = this.audioKey(device)
      this.$set(this.audioState, key, Object.assign({}, this.audioStatus(device), patch))
    },
    updateAudioVolume(device, volume) {
      this.setAudioState(device, { volume })
    },
    async setAudioVolume(device, volume) {
      const previous = this.audioStatus(device)
      const nextVolume = Math.max(0, Math.min(100, Number(volume) || 0))
      this.setAudioState(device, { volume: nextVolume, muted: false })
      const ok = await this.sendDeviceCommand(device, 'volume.set', {
        volume: nextVolume,
        muted: false
      }, 'volume_slider')
      if (!ok) {
        this.setAudioState(device, previous)
      }
    },
    async adjustAudioVolume(device, delta) {
      const previous = this.audioStatus(device)
      const nextVolume = Math.max(0, Math.min(100, previous.volume + delta))
      const action = delta > 0 ? 'volume.up' : 'volume.down'
      this.setAudioState(device, { volume: nextVolume, muted: false })
      const ok = await this.sendDeviceCommand(device, action, {
        step: Math.abs(delta),
        volume: nextVolume,
        muted: false
      }, delta > 0 ? 'volume_up' : 'volume_down')
      if (!ok) {
        this.setAudioState(device, previous)
      }
    },
    async toggleAudioMute(device) {
      const previous = this.audioStatus(device)
      const muted = !previous.muted
      this.setAudioState(device, { muted })
      const ok = await this.sendDeviceCommand(device, 'volume.mute', {
        muted,
        volume: previous.volume
      }, muted ? 'volume_mute' : 'volume_unmute')
      if (!ok) {
        this.setAudioState(device, previous)
      }
    },
    async sendDeviceCommand(device, action, params, source) {
      try {
        const session = await this.ensureControlSession(device, action)
        const response = await sendEquipmentCommand(this.selectedRobotId,
            this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, action, params, source || action))
        this.log('API sendDeviceCommand', response)
        return true
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR sendDeviceCommand', this.errorMessage(error))
        return false
      }
    },
    async firePayload(device, channel, source) {
      try {
        const session = await this.ensureControlSession(device, 'payload.fire')
        const token = await createConfirmToken(this.selectedRobotId, {
          controlSessionId: session.controlSessionId,
          target: {
            scope: device.scope,
            deviceId: device.deviceId,
            deviceType: device.deviceType
          },
          action: 'payload.fire',
          reason: 'manual_confirm'
        })
        const response = await sendEquipmentCommand(this.selectedRobotId,
            this.commandPayload(this.selectedRobotId, session.controlSessionId, this.selectedRobot.controlMode || 'MANUAL', device, 'payload.fire', {
              channel,
              confirmToken: token.confirmToken
            }, source || `payload_fire_${channel}`))
        this.log('API firePayload', response)
      } catch (error) {
        this.$message.error(this.errorMessage(error))
        this.log('ERROR firePayload', this.errorMessage(error))
      }
    },
    vehicleLightModeLabel(mode) {
      return {
        OFF: '常关',
        ON: '常开',
        BREATH: '呼吸灯',
        CUSTOM: '自定义亮度'
      }[mode] || '常关'
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
      if (mode === 'CUSTOM' && next[part].customValue <= 0) {
        next[part].customValue = 50
      }
      this.vehicleLightState = next
      const ok = await this.sendVehicleLightCommand(`vehicle_light_${part}_${String(mode).toLowerCase()}`)
      if (ok) {
        this.confirmedVehicleLightState = this.cloneVehicleLightState()
      } else {
        this.vehicleLightState = this.cloneVehicleLightState(this.confirmedVehicleLightState)
      }
    },
    updateVehicleLightBrightness(part, value) {
      this.vehicleLightState[part].customValue = value
    },
    async setVehicleLightBrightness(part, value) {
      this.vehicleLightState[part].mode = 'CUSTOM'
      this.vehicleLightState[part].customValue = value
      const ok = await this.sendVehicleLightCommand(`vehicle_light_${part}_custom`)
      if (ok) {
        this.confirmedVehicleLightState = this.cloneVehicleLightState()
      } else {
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
        customValue: mode === 'CUSTOM' ? part.customValue : 0
      }
    },
    syncControlEvent(event) {
      if (!event) return
      if (event.type === 'control.command.rejected') {
        this.$message.error((event.payload && event.payload.message) || '控制命令被拒绝')
      }
    },
    // 机器人状态事件会刷新设备基础信息，但保留已有会话和 LiveKit 连接态。
    // 如果机器人离线，则主动断开对应 Room，避免页面误显示还在播放。
    syncRobotEvent(event) {
      const data = event && (event.data || event.payload)
      if (!data || !data.robotId) return
      if (event.event !== 'robot.state' && event.type !== 'robot.state') return
      const incoming = this.toRobotState(data)
      const index = this.robots.findIndex(robot => robot.robotId === incoming.robotId)
      if (index >= 0) {
        const existing = this.robots[index]
        const previous = new Map(existing.cameras.map(camera => [camera.deviceId, camera]))
        incoming.cameras = incoming.cameras.map(camera => {
          const old = previous.get(camera.deviceId)
          if (!old) return camera
          if (incoming.status === 'offline' && old.room) {
            old.disconnecting = true
            this.stopLatencyStats(old)
            old.room.disconnect()
          }
          return Object.assign(old, camera, {
            session: old.session,
            room: incoming.status === 'online' ? old.room : null,
            hasVideo: incoming.status === 'online' ? old.hasVideo : false,
            latencyMs: incoming.status === 'online' ? old.latencyMs : null,
            latencyLevel: incoming.status === 'online' ? old.latencyLevel : 'unknown',
            statsTimer: incoming.status === 'online' ? old.statsTimer : null,
            statsTrack: incoming.status === 'online' ? old.statsTrack : null,
            statsRoom: incoming.status === 'online' ? old.statsRoom : null,
            status: incoming.status === 'online' ? old.status : 'offline',
            viewerCount: old.viewerCount,
            watching: old.watching,
            hasAudio: old.hasAudio,
            quality: old.quality,
            qualityChanging: old.qualityChanging,
            activeRecording: old.activeRecording,
            recordingActive: old.recordingActive,
            recordingBusy: old.recordingBusy,
            intercomActive: old.intercomActive,
            intercomBusy: old.intercomBusy,
            intercomStatus: old.intercomStatus,
            intercomToken: old.intercomToken,
            stopped: old.stopped,
            stopping: old.stopping,
            restarting: old.restarting,
            connecting: old.connecting,
            disconnecting: old.disconnecting
          })
        })
        this.$set(this.robots, index, Object.assign(existing, incoming))
      } else {
        this.robots.push(incoming)
      }
      this.syncAudioStatesFromDevices(incoming.robotId, data.devices)
    },
    // 会话事件来自后端状态机。当前页面只同步自己正在关注的 session，
    // 并忽略用户已经明确停止的 session，防止自动重连覆盖本地意图。
    syncSessionEvent(event) {
      if (!event || !event.data || !event.data.sessionId) return
      const camera = this.allCameras().find(item => item.session && item.session.sessionId === event.data.sessionId)
      if (!camera || camera.stopped) return
      if (this.stoppedSessionIds.has(event.data.sessionId)) return
      if (event.data.robotId && event.data.status) {
        camera.session = this.mergeSession(camera, event.data)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        if (this.shouldAttachFromEvent(event, camera)) {
          this.connectLiveKit(camera, true)
        }
      }
      if (event.event.indexOf('video.intercom.') === 0) {
        camera.intercomStatus = event.data.intercomStatus || camera.intercomStatus
        camera.intercomActive = !['IDLE', 'FAILED'].includes(camera.intercomStatus)
      }
    },
    // LiveKit 断开、track 取消订阅或后端标记 INTERRUPTED 后触发重启。
    // 用 restarting 做短时间节流，避免多个事件同时到达造成重复下发命令。
    async restartCamera(camera) {
      if (camera.stopping || camera.stopped || camera.restarting) return
      if (!camera.session || camera.session.status === 'CLOSED') return
      if (this.stoppedSessionIds.has(camera.session.sessionId)) return
      if (!['STREAMING', 'INTERRUPTED'].includes(camera.session.status)) return
      try {
        camera.restarting = true
        const updated = await restartVideoSession(camera.session.sessionId)
        camera.session = this.mergeSession(camera, updated)
        camera.status = camera.session.status
        camera.viewerCount = camera.session.viewerCount
        this.log('API restartVideoSession', updated)
      } catch (error) {
        this.log('ERROR restartVideoSession', this.errorMessage(error))
      } finally {
        setTimeout(() => {
          camera.restarting = false
        }, 5000)
      }
    },
    syncAudioStatesFromDevices(robotId, devices) {
      if (!robotId || !Array.isArray(devices)) return
      devices
          .filter(device => ['CLIENT_AUDIO', 'VOLUME_CONTROL', 'INTERCOM'].includes(device.deviceType))
          .forEach(device => {
            const status = device.status || device.runtimeStatus || {}
            if (status.volume === undefined && status.muted === undefined) return
            const key = `${robotId}:${device.deviceId}`
            this.$set(this.audioState, key, Object.assign({}, this.audioState[key] || { volume: 50, muted: false }, {
              volume: status.volume === undefined ? (this.audioState[key] && this.audioState[key].volume) || 50 : status.volume,
              muted: status.muted === undefined ? !!(this.audioState[key] && this.audioState[key].muted) : status.muted
            }))
          })
    },
    isStoppedSession(camera, sessionId) {
      return camera.stopping || camera.stopped || this.stoppedSessionIds.has(sessionId)
    },
    shouldAttachFromEvent(event, camera) {
      if (!['video.session.streaming', 'video.track.published'].includes(event.event)) return false
      if (!event.data || event.data.status !== 'STREAMING') return false
      if (camera.hasVideo) return false
      return !camera.room || camera.room.state === 'disconnected'
    },
    videoElement(camera) {
      const ref = this.$refs[camera.key]
      return Array.isArray(ref) ? ref[0] : ref
    },
    audioElement(camera) {
      const ref = this.$refs[camera.key + '-audio']
      return Array.isArray(ref) ? ref[0] : ref
    },
    firstVideoPublication(room) {
      for (const participant of room.remoteParticipants.values()) {
        for (const publication of participant.trackPublications.values()) {
          if (publication.track && publication.track.kind === 'video') return publication
        }
      }
      return null
    },
    attachVideoTrack(camera, track, publication, room = camera.room) {
      if (publication && typeof publication.setVideoQuality === 'function') {
        publication.setVideoQuality(VideoQuality.HIGH)
      }
      const video = this.videoElement(camera)
      if (video) track.attach(video)
      this.startLatencyStats(camera, track, room)
    },
    detachRoomFromVideo(room, video) {
      if (!room || !video) return
      room.remoteParticipants.forEach(participant => {
        participant.trackPublications.forEach(publication => {
          if (publication.track && typeof publication.track.detach === 'function') {
            publication.track.detach(video)
          }
        })
      })
    },
    prepareReplacementVideo(camera, track, publication, oldRoom) {
      if (publication && typeof publication.setVideoQuality === 'function') {
        publication.setVideoQuality(VideoQuality.HIGH)
      }
      const warmup = document.createElement('video')
      warmup.autoplay = true
      warmup.muted = true
      warmup.playsInline = true
      Object.assign(warmup.style, {
        position: 'fixed',
        left: '-2px',
        top: '-2px',
        width: '1px',
        height: '1px',
        opacity: '0',
        pointerEvents: 'none'
      })
      document.body.appendChild(warmup)
      track.attach(warmup)
      return this.waitForVideoReady(warmup)
          .then(() => {
            const video = this.videoElement(camera)
            if (video) {
              this.detachRoomFromVideo(oldRoom, video)
              track.attach(video)
            }
          })
          .finally(() => {
            if (typeof track.detach === 'function') track.detach(warmup)
            warmup.remove()
          })
    },
    waitForVideoReady(video) {
      if (video.readyState >= 2 && video.videoWidth > 0) return Promise.resolve()
      video.play().catch(() => {})
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => cleanup(() => reject(new Error('等待新清晰度首帧超时'))), 5000)
        const onReady = () => {
          if (video.videoWidth > 0 || video.readyState >= 2) cleanup(resolve)
        }
        const cleanup = (done) => {
          clearTimeout(timeout)
          video.removeEventListener('loadeddata', onReady)
          video.removeEventListener('canplay', onReady)
          done()
        }
        video.addEventListener('loadeddata', onReady)
        video.addEventListener('canplay', onReady)
      })
    },
	    canSnapshot(camera) {
      return !!camera.session && camera.watching && !camera.stopping && !camera.stopped
    },
    canRecord(camera) {
      return !!camera.session && camera.watching && !camera.stopping && !camera.stopped
    },
    async toggleLiveRecording(camera) {
      if (camera.recordingActive) {
        await this.stopCameraRecording(camera)
      } else {
        await this.startCameraRecording(camera)
      }
    },
    async startCameraRecording(camera) {
      if (!camera.session || camera.recordingBusy) return
      camera.recordingBusy = true
      try {
        const active = await getActiveLiveRecording(camera.session.sessionId)
        if (active) {
          camera.activeRecording = active
          camera.recordingActive = false
          this.$message.info('当前视频正在录制中')
          return
        }
        const recording = await startLiveRecording(camera.session.sessionId)
        camera.activeRecording = recording
        camera.recordingActive = recording && recording.status === 'RECORDING'
        this.log('API startLiveRecording', recording)
        this.$message.success('已开始录像')
      } catch (error) {
        const data = error && error.response && error.response.data
        if (data && data.code === 'RECORDING_ALREADY_ACTIVE') {
          this.$message.info('当前视频正在录制中')
        } else {
          this.$message.error(this.errorMessage(error))
        }
      } finally {
        camera.recordingBusy = false
      }
    },
    async syncActiveRecording(camera) {
      if (!camera.session) return
      try {
        const recording = await getActiveLiveRecording(camera.session.sessionId)
        camera.activeRecording = recording
        if (!recording || recording.status !== 'RECORDING') {
          camera.recordingActive = false
        }
      } catch (_) {
        camera.activeRecording = null
        camera.recordingActive = false
      }
    },
    async stopCameraRecording(camera) {
      if (!camera.session || !camera.activeRecording || camera.recordingBusy) return
      camera.recordingBusy = true
      try {
        const recording = await stopLiveRecording(camera.session.sessionId, camera.activeRecording.recordingId)
        camera.activeRecording = recording
        camera.recordingActive = false
        this.log('API stopLiveRecording', recording)
        this.$message.success('录像已停止')
        if (this.recordingMode) await this.loadRecordings()
      } catch (error) {
        this.$message.error(this.errorMessage(error))
      } finally {
        camera.recordingBusy = false
      }
    },
    attachExistingVideoTracks(camera, room, sessionId, strict = true) {
      let attached = false
      room.remoteParticipants.forEach(participant => {
        participant.trackPublications.forEach(publication => {
          const track = publication.track
          if (!track || track.kind !== 'video') return
          if (strict && (camera.room !== room || !camera.session || camera.session.sessionId !== sessionId)) return
          this.attachVideoTrack(camera, track, publication, room)
          camera.hasVideo = true
          attached = true
          this.log('LiveKit TrackAttached', `${camera.name} ${track.sid || track.name}`)
        })
      })
      return attached
    },
    startLatencyStats(camera, track, room = camera.room) {
      this.stopLatencyStats(camera)
      camera.statsTrack = track
      camera.statsRoom = room
      camera.latencyMs = null
      camera.latencyLevel = 'unknown'
      const sample = async () => {
        if (camera.statsTrack !== track || camera.statsRoom !== room || (room && camera.room !== room)) return
        try {
          const stats = await this.videoStatsReport(track, room)
          const latencyMs = this.estimateLatencyMs(stats)
          if (camera.statsTrack !== track || camera.statsRoom !== room || (room && camera.room !== room)) return
          camera.latencyMs = latencyMs
          camera.latencyLevel = this.latencyLevel(camera)
        } catch (error) {
          if (camera.statsTrack === track && camera.statsRoom === room) {
            camera.latencyMs = null
            camera.latencyLevel = 'unknown'
          }
        }
      }
      sample()
      camera.statsTimer = setInterval(sample, 1000)
    },
    stopLatencyStats(camera) {
      if (camera.statsTimer) clearInterval(camera.statsTimer)
      camera.statsTimer = null
      camera.statsTrack = null
      camera.statsRoom = null
      camera.latencyMs = null
      camera.latencyLevel = 'unknown'
    },
    async videoStatsReport(track, room) {
      const peerStats = await this.peerConnectionStats(room)
      if (peerStats) return peerStats
      if (track && typeof track.getRTCStatsReport === 'function') {
        return track.getRTCStatsReport()
      }
      if (track && track.receiver && typeof track.receiver.getStats === 'function') {
        return track.receiver.getStats()
      }
      return null
    },
    async peerConnectionStats(room) {
      const manager = room && room.engine && room.engine.pcManager
      const transports = [
        manager && manager.subscriber,
        manager && manager.publisher
      ]
      for (const transport of transports) {
        if (transport && typeof transport.getStats === 'function') {
          const stats = await transport.getStats()
          if (stats) return stats
        }
      }
      return null
    },
    estimateLatencyMs(stats) {
      if (!stats || typeof stats.forEach !== 'function') return null
      const pairRtt = this.selectedCandidatePairRtt(stats)
      if (Number.isFinite(pairRtt)) return Math.round(pairRtt * 1000)
      let receiverRtt = null
      let jitterDelay = null
      stats.forEach(report => {
        if (receiverRtt === null
            && (report.type === 'remote-inbound-rtp' || report.type === 'remote-outbound-rtp')
            && Number.isFinite(report.roundTripTime)) {
          receiverRtt = report.roundTripTime
        }
        if (jitterDelay === null
            && report.type === 'inbound-rtp'
            && report.kind === 'video'
            && report.jitterBufferEmittedCount > 0
            && Number.isFinite(report.jitterBufferDelay)) {
          jitterDelay = report.jitterBufferDelay / report.jitterBufferEmittedCount
        }
      })
      const seconds = receiverRtt !== null ? receiverRtt : jitterDelay
      return Number.isFinite(seconds) ? Math.round(seconds * 1000) : null
    },
    selectedCandidatePairRtt(stats) {
      let selectedPairId = null
      stats.forEach(report => {
        if (report.type === 'transport' && report.selectedCandidatePairId) {
          selectedPairId = report.selectedCandidatePairId
        }
      })
      if (selectedPairId && stats.get) {
        const selected = stats.get(selectedPairId)
        if (selected && Number.isFinite(selected.currentRoundTripTime)) return selected.currentRoundTripTime
      }
      let fallback = null
      stats.forEach(report => {
        if (fallback !== null || report.type !== 'candidate-pair') return
        const selected = report.selected || report.nominated || report.state === 'succeeded'
        if (selected && Number.isFinite(report.currentRoundTripTime)) fallback = report.currentRoundTripTime
      })
      return fallback
    },
    latencyText(camera) {
      return Number.isFinite(camera.latencyMs) ? `${camera.latencyMs} ms` : '-- ms'
    },
    latencyLevel(camera) {
      if (!Number.isFinite(camera.latencyMs)) return 'unknown'
      if (camera.latencyMs < 80) return 'good'
      if (camera.latencyMs < 200) return 'warn'
      return 'bad'
    },
    liveKitConnectionUrl(serverUrl) {
      // HTTPS 页面不能直接连 ws:// LiveKit；生产环境通过同域 /livekit 反代升级到 wss。
      if (window.location.protocol === 'https:') {
        return `wss://${window.location.host}/livekit`
      }
      return serverUrl
    },
    captureFrameBlob(camera) {
      const video = this.videoElement(camera)
      if (!video || !video.videoWidth || !video.videoHeight) return Promise.resolve(null)
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      return new Promise(resolve => {
        canvas.toBlob(blob => resolve(blob), 'image/jpeg', 0.88)
      })
    },
    showSnapshotSuccess(camera, snapshot) {
      if (!snapshot || !snapshot.snapshotId) {
        this.$message.success('抓拍已保存')
        return
      }
      const url = snapshotImageUrl(snapshot.snapshotId)
      this.$message({
        type: 'success',
        customClass: 'snapshot-message',
        dangerouslyUseHTMLString: true,
        message: `抓拍已保存 <a class="message-link" href="${this.escapeHtml(url)}" target="_blank" rel="noopener noreferrer">查看</a>`
      })
    },
    escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[char]))
    },
    mergeSession(camera, update) {
      // 部分 API/事件只返回增量字段，保留已有 token/url/roomName，避免重连时丢上下文。
      const next = Object.assign({}, camera.session || {}, update || {})
      if (!update || !update.viewerToken) next.viewerToken = camera.session && camera.session.viewerToken
      if (!update || !update.livekitUrl) next.livekitUrl = camera.session && camera.session.livekitUrl
      if (!update || !update.roomName) next.roomName = camera.session && camera.session.roomName
      return next
    },
    allCameras() {
      return this.robots.reduce((items, robot) => items.concat(robot.cameras), [])
    },
    toRobotState(robot) {
      // 将后端机器人 DTO 转为页面状态，同时给缺失字段补默认值。
      const status = robot.status || robot.onlineStatus || 'offline'
      return {
        robotId: robot.robotId,
        clientId: robot.clientId,
        name: robot.name || robot.robotId,
        type: robot.type || '机器人',
        vendor: robot.vendor,
        model: robot.model,
        controlMode: robot.controlMode || 'MANUAL',
        stateSeq: robot.stateSeq || 0,
        battery: robot.battery,
        status,
        devices: robot.devices,
        cameras: (robot.cameras || []).map(camera => Object.assign(
            cameraState(robot.robotId, camera.deviceId || camera.cameraId, camera.name || camera.cameraId, camera.channel || 'visible', camera.groupType),
	            {
	              cameraId: camera.cameraId || camera.deviceId,
	              quality: camera.quality || 'auto',
	              status: status === 'online' ? camera.status || '' : 'offline'
	            }))
      }
    },
    batteryText(battery) {
      return battery === null || battery === undefined ? '--' : `${battery}%`
    },
    groupTypeText(groupType) {
      return {
        body: '本体',
        dual_gimbal: '双光云台',
        arm: '机械臂'
      }[groupType] || groupType || '未分组'
    },
    statusType(status) {
      if (status === 'STREAMING') return 'success'
      if (status === 'FAILED' || status === 'TIMEOUT' || status === 'offline') return 'danger'
      if (status === 'REQUESTING_CLIENT' || status === 'ROOM_READY') return 'warning'
      return 'info'
    },
    log(title, payload) {
      const line = `[${new Date().toLocaleTimeString()}] ${title}\n${typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)}`
      this.events.unshift(line)
    },
    errorMessage(error) {
      let data = error && error.response && error.response.data
      if (typeof data === 'string') {
        try {
          data = JSON.parse(data)
        } catch (ignored) {
          return data || '请求失败'
        }
      }
      if (data && data.code === 'INVALID_STATE' && data.message === 'Intercom is occupied by another operator') {
        return '该视频画面正在通话中，请结束当前通话后重试'
      }
      return data && (data.message || data.code || data.error)
          ? data.message || data.code || data.error
          : (error && error.message) || '请求失败'
    }
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #eef2f7;
  color: #1f2937;
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #dfe5ef;
}

.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 340px;
  gap: 12px;
  padding: 12px;
  height: calc(100vh - 56px);
  overflow: hidden;
}

.left-panel,
.robot-list,
.video-area,
.equipment-panel,
.left-log {
  background: #ffffff;
  border: 1px solid #dfe5ef;
  border-radius: 4px;
  min-height: 0;
}

.left-panel {
  display: grid;
  grid-template-rows: minmax(180px, 34%) minmax(0, 1fr);
  gap: 12px;
  background: transparent;
  border: 0;
}

.equipment-control {
  min-height: 0;
}

.control-block {
  padding: 8px 10px;
  border-bottom: 1px solid #eef2f7;
}

.control-block strong {
  display: block;
  font-size: 13px;
  margin-bottom: 3px;
}

.control-block small {
  display: block;
  color: #64748b;
  font-size: 11px;
  margin-bottom: 8px;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.control-grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.control-grid-4 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.audio-control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.audio-control .el-slider {
  padding: 0 4px;
}

.audio-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}

.audio-actions > .el-button {
  width: 100%;
}

.audio-stepper {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 30px;
  gap: 4px;
  align-items: center;
}

.audio-stepper span {
  text-align: center;
  font-size: 12px;
  color: #334155;
}

.audio-stepper .el-button {
  padding-left: 0;
  padding-right: 0;
}

.control-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 6px 0 8px;
  font-size: 12px;
  color: #334155;
}

.warning-light-row {
  margin-bottom: 6px;
}

.warning-light-row small {
  margin-bottom: 0;
}

.warning-switch .el-switch__label {
  color: rgba(255, 255, 255, 0.86);
}

.warning-switch .el-switch__label.is-active {
  color: #ffffff;
}

.vehicle-light-row {
  padding: 8px 0;
  border-top: 1px solid #e2e8f0;
}

.vehicle-light-row:first-of-type {
  border-top: 0;
}

.vehicle-light-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.vehicle-light-title span {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.vehicle-light-title small {
  margin-bottom: 0;
}

.vehicle-light-mode-group {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  width: 100%;
}

.vehicle-light-mode-group .el-radio-button {
  min-width: 0;
}

.vehicle-light-mode-group .el-radio-button__inner {
  width: 100%;
  border-left: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 7px 4px;
  font-size: 12px;
}

.vehicle-light-mode-group .el-radio-button__orig-radio:checked + .el-radio-button__inner {
  box-shadow: none;
}

.vehicle-light-brightness {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #334155;
}

.vehicle-light-brightness em {
  font-style: normal;
  text-align: right;
  color: #64748b;
}

.device-row {
  margin-top: 8px;
}

.robot-list {
  padding: 12px;
  overflow: auto;
}

.left-log {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
}

.robot-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  margin-bottom: 8px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
}

.robot-item.active {
  background: #eef6ff;
  border-color: #409eff;
}

.robot-item span {
  font-size: 14px;
  font-weight: 600;
}

.robot-item small {
  color: #64748b;
}

.video-area {
  padding: 12px;
  min-width: 0;
  overflow: hidden;
}

.area-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  min-height: 42px;
}

.area-header h2 {
  margin: 0 0 4px;
  font-size: 16px;
}

.area-header span {
  color: #64748b;
  font-size: 12px;
}

.area-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.camera-grid {
  display: grid;
  gap: 8px;
  padding: 6px;
  border: 2px solid #1e9bff;
  background: #f8fbff;
  height: calc(100vh - 132px);
  overflow: hidden;
}

.camera-grid.grid-1 {
  grid-template-columns: minmax(360px, 1fr);
  grid-auto-rows: minmax(0, 1fr);
}

.camera-grid.grid-4 {
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  grid-auto-rows: minmax(0, 1fr);
}

.camera-grid.grid-9 {
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  grid-auto-rows: minmax(0, 1fr);
}

.camera-card {
  border: 1px solid #1e9bff;
  border-radius: 2px;
  overflow: hidden;
  background: #0f172a;
  min-height: 0;
}

.recording-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 10px;
  height: calc(100vh - 132px);
  min-height: 0;
}

.recording-list {
  overflow: auto;
  padding: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.recording-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  gap: 8px;
}

.recording-tabs {
  flex: 1;
  min-width: 0;
}

.recording-tabs /deep/ .el-tabs__header {
  margin: 0;
}

.recording-tabs /deep/ .el-tabs__item {
  height: 28px;
  line-height: 28px;
  font-size: 13px;
}

.recording-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 8px;
  padding: 10px;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #ffffff;
  cursor: pointer;
}

.recording-item.active {
  border-color: #409eff;
  background: #eef6ff;
}

.recording-item span {
  font-size: 13px;
  font-weight: 600;
}

.recording-item small,
.recording-empty {
  color: #64748b;
  font-size: 12px;
}

.recording-player {
  position: relative;
  min-height: 0;
  background: #111827;
}

.recording-player video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #111827;
}

.video-stage video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.video-stage audio {
  display: none;
}

.video-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  min-height: 30px;
  padding: 6px 8px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  color: #ffffff;
  background: linear-gradient(180deg, rgba(2, 8, 23, 0.82), rgba(2, 8, 23, 0));
  z-index: 2;
}

.video-topbar strong,
.video-topbar span {
  display: block;
}

.video-topbar strong {
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
}

.video-topbar span {
  font-size: 11px;
  color: #cbd5e1;
}

.video-topbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.quality-select {
  width: 76px;
}

.quality-select /deep/ .el-input__inner {
  height: 24px;
  line-height: 24px;
  padding-left: 8px;
  padding-right: 24px;
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.28);
  background: rgba(15, 23, 42, 0.76);
}

.quality-select /deep/ .el-input__icon {
  line-height: 24px;
}

.latency-pill {
  min-width: 38px;
  display: inline-block;
  color: #e2e8f0;
  font-size: 11px;
  line-height: 16px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

.latency-pill.latency-good {
  color: #86efac;
}

.latency-pill.latency-warn {
  color: #fde68a;
}

.latency-pill.latency-bad {
  color: #fecaca;
}

.empty-video {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 13px;
  pointer-events: none;
}

.video-tools {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: flex;
  gap: 4px;
  z-index: 3;
}

.video-tools .el-button {
  margin-left: 0;
  padding: 5px 7px;
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.32);
  background: rgba(15, 23, 42, 0.78);
}

.video-tools .el-button:hover,
.video-tools .el-button:focus {
  color: #ffffff;
  border-color: #409eff;
  background: rgba(30, 64, 175, 0.9);
}

.video-bottombar {
  position: absolute;
  right: 8px;
  bottom: 8px;
  max-width: 58%;
  height: 24px;
  padding: 0 6px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: #e2e8f0;
  font-size: 11px;
  background: rgba(15, 23, 42, 0.72);
  border-radius: 2px;
  z-index: 2;
}

.video-bottombar span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.equipment-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.event-head {
  height: 42px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5eaf2;
}

.event-log {
  padding: 8px;
  overflow: auto;
  max-height: none;
  flex: 1;
}

.event-item {
  white-space: pre-wrap;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  padding: 8px;
  margin-bottom: 8px;
  border-radius: 4px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

/deep/ .message-link {
  color: #1677ff;
  font-weight: 600;
  margin-left: 8px;
  text-decoration: none;
}

/deep/ .snapshot-message {
  min-width: 0;
  width: auto;
  padding-right: 18px;
}

/deep/ .message-link:hover {
  text-decoration: underline;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 200px minmax(0, 1fr);
    height: auto;
    overflow: visible;
  }

  .video-area {
    overflow: visible;
  }

  .equipment-panel,
  .left-panel {
    grid-column: 1 / -1;
    min-height: 260px;
  }

  .left-panel {
    grid-template-rows: auto minmax(220px, auto);
  }

  .camera-grid,
  .camera-grid.grid-9 {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
    height: auto;
  }

  .camera-card {
    aspect-ratio: 16 / 9;
  }

  .recording-workspace {
    grid-column: 1 / -1;
    grid-template-columns: 260px minmax(0, 1fr);
    height: 420px;
  }
}

@media (max-width: 760px) {
  .workspace {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .camera-grid,
  .camera-grid.grid-4,
  .camera-grid.grid-9 {
    grid-template-columns: 1fr;
    height: auto;
  }

  .camera-card {
    aspect-ratio: 16 / 9;
  }

  .recording-workspace {
    display: block;
    height: auto;
  }

  .recording-list {
    max-height: 260px;
    margin-bottom: 8px;
  }

  .recording-player {
    height: 300px;
  }
}
</style>
