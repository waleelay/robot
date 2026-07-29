<template>
  <div class="box multi-in-one p20 mt10">
    <div class="tabs flx-align-center">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        class="tab-item flx-center"
        :class="{ active: activeTab === item.key }"
        @click="activeTab = item.key"
      >
        {{ item.label }}
      </button>
    </div>

    <div class="status-row flx-align-center">
      <div class="status-text">
        连接状态：<span :class="{ ok: connected, danger: !connected }">{{ connected ? '已连接' : '未连接' }}</span>
      </div>
      <div class="status-text">
        温度：<span>{{ temperatureText }}</span>
      </div>
    </div>

    <div v-if="activeTab === 'shout'" class="panel">
      <button
        type="button"
        class="action-btn flx-center"
        :class="{ active: broadcastActive, 'is-disabled': !canControl(broadcastAction) }"
        :disabled="!canControl(broadcastAction)"
        @click="toggleBroadcast"
      >
        <svg-icon icon-class="volume" class="btn-icon" />
        <span>{{ broadcastActive ? '停止喊话' : '开始喊话' }}</span>
      </button>
      <button
        type="button"
        class="action-btn flx-center mt12"
        :class="{ active: monitorActive, 'is-disabled': !canControl(monitorAction) }"
        :disabled="!canControl(monitorAction)"
        @click="toggleMonitor"
      >
        <svg-icon icon-class="volume" class="btn-icon" />
        <span>{{ monitorActive ? '停止收音' : '开始收音' }}</span>
      </button>
      <button
        type="button"
        class="action-btn alarm flx-center mt12"
        :class="{ active: alarmActive, 'is-disabled': !canControl(alarmAction) }"
        :disabled="!canControl(alarmAction)"
        @click="toggleAlarm"
      >
        <svg-icon icon-class="volume" class="btn-icon" />
        <span>{{ alarmActive ? '停止警报' : '播放警报' }}</span>
      </button>

      <div class="slider-block mt15">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': volumePercent + '%' }" />
            <input
              v-model.number="volume"
              type="range"
              :min="volumeMin"
              :max="volumeMax"
              class="custom-slider"
              :style="{ '--value-percent': volumePercent + '%' }"
              :disabled="!canControl('set_volume')"
              @change="setVolume"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>

      <div class="slider-block mt12">
        <div class="slider-label">喊话器俯仰</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': speakerTilt + '%' }" />
            <input
              v-model.number="speakerTilt"
              type="range"
              min="0"
              max="100"
              class="custom-slider"
              :style="{ '--value-percent': speakerTilt + '%' }"
              :disabled="!canControl('set_speaker_tilt')"
              @change="setSpeakerTilt"
            >
          </div>
          <span class="slider-value">{{ speakerTilt }}%</span>
        </div>
      </div>

      <div class="switch-row end mt12 flx-align-center">
        <span class="switch-label">喊话时禁用收音</span>
        <el-switch
          v-model="monitorSuppressed"
          active-text="开启"
          inactive-text="关闭"
          active-color="#3DB56A"
          inactive-color="#5E5E5E"
          :disabled="!monitorActive || !canControl('set_monitor_suppressed')"
          @change="setMonitorSuppressed"
        />
      </div>
    </div>

    <div v-else-if="activeTab === 'tts'" class="panel">
      <el-input
        v-model="ttsText"
        type="textarea"
        :rows="4"
        :maxlength="maxTextLength"
        show-word-limit
        placeholder="请输入播报文字"
        class="tts-input mt15"
        resize="none"
      />
      <div class="tts-options mt12 flx-align-center">
        <el-radio-group v-model="voiceType" class="voice-radios">
          <el-radio label="MALE">男声</el-radio>
          <el-radio label="FEMALE">女声</el-radio>
        </el-radio-group>
        <div class="switch-row flx-align-center ml-auto">
          <span class="switch-label">循环播放</span>
          <el-switch
            v-model="ttsLoop"
            active-text="开启"
            inactive-text="关闭"
            active-color="#3DB56A"
            inactive-color="#5E5E5E"
          />
        </div>
      </div>
      <div class="slider-block mt15">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': volumePercent + '%' }" />
            <input
              v-model.number="volume"
              type="range"
              :min="volumeMin"
              :max="volumeMax"
              class="custom-slider"
              :style="{ '--value-percent': volumePercent + '%' }"
              :disabled="!canControl('set_volume')"
              @change="setVolume"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>
      <div class="btns mt15 flx-center">
        <el-button
          type="primary"
          class="wp124 hp30 common-btn"
          :disabled="!canControl(ttsAction)"
          @click="toggleTts"
        >
          {{ ttsLoopActive ? '停止' : '播放' }}
        </el-button>
      </div>
    </div>

    <div v-else-if="activeTab === 'audio'" class="panel">
      <div class="audio-toolbar mt12 flx-align-center">
        <span>设备音频文件</span>
        <div class="audio-toolbar-actions flx-align-center">
          <el-tooltip content="刷新文件列表" placement="top">
            <el-button
              type="text"
              icon="el-icon-refresh"
              :disabled="!canControl('list_audio_files')"
              @click="refreshAudioFiles"
            />
          </el-tooltip>
        </div>
      </div>
      <div class="audio-list common-scroll">
        <div
          v-for="file in audioFiles"
          :key="file"
          class="audio-item flx-align-center"
          :class="{ active: selectedAudio === file }"
          role="button"
          tabindex="0"
          @click="selectedAudio = file"
          @keydown.enter="selectedAudio = file"
        >
          <svg-icon icon-class="music" class="music-icon" />
          <span class="audio-file-name">{{ file }}</span>
          <div class="audio-row-actions flx-align-center" @click.stop>
            <el-tooltip content="单次播放" placement="top">
              <el-button
                type="text"
                icon="el-icon-video-play"
                class="audio-icon-btn"
                :class="{ active: isAudioModeActive(file, false) }"
                :disabled="!canPlayAudioFile(file)"
                @click="playAudioFile(file, false)"
              />
            </el-tooltip>
            <el-tooltip content="循环播放" placement="top">
              <el-button
                type="text"
                icon="el-icon-refresh"
                class="audio-icon-btn"
                :class="{ active: isAudioModeActive(file, true) }"
                :disabled="!canPlayAudioFile(file)"
                @click="playAudioFile(file, true)"
              />
            </el-tooltip>
          </div>
        </div>
        <div v-if="!audioFiles.length" class="empty-text flx-center">文件列表未同步</div>
      </div>
      <div v-if="audioTransferText" class="local-state mt10">{{ audioTransferText }}</div>
      <div class="slider-block mt12">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': volumePercent + '%' }" />
            <input
              v-model.number="volume"
              type="range"
              :min="volumeMin"
              :max="volumeMax"
              class="custom-slider"
              :style="{ '--value-percent': volumePercent + '%' }"
              :disabled="!canControl('set_volume')"
              @change="setVolume"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>
      <input
        ref="audioFileInput"
        class="audio-file-input"
        type="file"
        accept=".mp3,.wav,audio/mpeg,audio/wav"
        @change="handleAudioFileSelected"
      >
      <div class="btns button-grid audio-command-grid mt12">
        <el-button
          :loading="audioUploading"
          :disabled="!canUploadAudio || audioUploading"
          type="primary"
          class="common-btn"
          @click="chooseAudioFile"
        >
          添加音频
        </el-button>
        <el-button :disabled="!canControl('stop_audio_file')" type="primary" class="common-btn" @click="stopAudioFile">停止</el-button>
        <el-button :disabled="!canDeleteFile" type="primary" class="common-btn danger-btn" @click="deleteAudioFile">删除</el-button>
      </div>
      <div class="local-state mt10">{{ audioPlaybackText }}</div>
    </div>

    <div v-else class="panel">
      <div class="light-switches-row mt15">
        <div class="light-row flx-align-center">
          <span class="switch-label">照明灯</span>
          <el-switch
            v-model="lightEnabled"
            active-text="开启"
            inactive-text="关闭"
            active-color="#3DB56A"
            inactive-color="#5E5E5E"
            :disabled="!canControl('light.set')"
            @change="setLightEnabled"
          />
        </div>
        <div class="light-row flx-align-center">
          <span class="switch-label">爆闪</span>
          <el-switch
            v-model="strobeEnabled"
            active-text="开启"
            inactive-text="关闭"
            active-color="#3DB56A"
            inactive-color="#5E5E5E"
            :disabled="!canControl('light.set')"
            @change="setStrobeEnabled"
          />
        </div>
      </div>
      <div class="slider-block mt12">
        <div class="slider-label">亮度调节</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': brightness + '%' }" />
            <input
              v-model.number="brightness"
              type="range"
              min="0"
              max="100"
              class="custom-slider"
              :style="{ '--value-percent': brightness + '%' }"
              :disabled="!canControl('light.set')"
              @change="setBrightness"
            >
          </div>
          <span class="slider-value">{{ brightness }}%</span>
        </div>
      </div>
      <div class="red-blue-row mt12 flx-align-center">
        <span class="switch-label">红蓝灯</span>
        <span class="mode-text">{{ redBlueMode ? `模式 ${redBlueMode}` : '关闭' }}</span>
        <el-button :disabled="!canControl('light.set')" type="primary" class="common-btn" @click="nextRedBlueMode">切换模式</el-button>
        <el-button :disabled="!canControl('light.set') || redBlueMode === 0" type="primary" class="common-btn" @click="closeRedBlue">关闭</el-button>
      </div>
      <div class="slider-block mt12">
        <div class="slider-label">照明灯俯仰</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg" />
            <div class="filled-glow" :style="{ '--value-percent': lightTilt + '%' }" />
            <input
              v-model.number="lightTilt"
              type="range"
              min="0"
              max="100"
              class="custom-slider"
              :style="{ '--value-percent': lightTilt + '%' }"
              :disabled="!canControl('set_light_tilt')"
              @change="setLightTilt"
            >
          </div>
          <span class="slider-value">{{ lightTilt }}%</span>
        </div>
      </div>
      <div class="local-state mt10">灯光状态：页面最近下发值</div>
    </div>
  </div>
</template>

<script>
import { Room, RoomEvent } from 'livekit-client';
import {
  heartbeatIntercom,
  startCameraIntercom,
  stopIntercom,
  transferMultiFunctionAudio,
  uploadFile
} from '@/api/media';
import { errorMessage } from '@/utils';
import yuntai from './yuntai';

export default {
  name: 'MultiInOne',
  mixins: [yuntai],
  data() {
    return {
      activeTab: 'shout',
      tabs: [
        { key: 'shout', label: '喊话模式' },
        { key: 'tts', label: '文字转语音' },
        { key: 'audio', label: '音频播放' },
        { key: 'light', label: '照明灯' }
      ],
      syncedDeviceId: '',
      mediaSessionId: '',
      mediaRoom: null,
      mediaHeartbeatTimer: null,
      remoteAudioElement: null,
      mediaBusy: false,
      volume: 50,
      pendingVolume: null,
      pendingVolumeUntil: 0,
      speakerTilt: 50,
      lightTilt: 50,
      brightness: 50,
      broadcastActive: false,
      monitorActive: false,
      monitorSuppressed: false,
      monitorSuppressedForBroadcast: false,
      alarmActive: false,
      ttsText: '',
      voiceType: 'MALE',
      ttsLoop: false,
      ttsLoopActive: false,
      selectedAudio: '',
      audioPlaying: false,
      audioLooping: false,
      audioPlayingFile: '',
      audioUploading: false,
      audioFilesRequestedFor: '',
      lightEnabled: false,
      strobeEnabled: false,
      redBlueMode: 0,
      busyActions: {}
    };
  },
  computed: {
    device() {
      return this.multiFunctionDevice;
    },
    status() {
      return (this.device && (this.device.status || this.device.runtimeStatus)) || {};
    },
    audioSession() {
      return this.status.audioSession || {};
    },
    connected() {
      if (!this.device) return false;
      const robotStatus = this.selectedControlProfile.onlineStatus || this.selectedRobot.status;
      if (robotStatus && robotStatus !== 'online') return false;
      return this.device.onlineStatus !== 'offline' &&
        this.status.connected !== false &&
        this.status.online !== false;
    },
    temperatureText() {
      const value = Number(this.status.temperatureC);
      return Number.isFinite(value) ? `${value.toFixed(1)}°C` : '--';
    },
    actions() {
      return Array.isArray(this.device && this.device.actions) ? this.device.actions : [];
    },
    profile() {
      return (this.device && this.device.controlProfile) || {};
    },
    volumeMin() {
      return this.clamp(this.profile.minVolumePercent, 0, 100, 0);
    },
    volumeMax() {
      const profileMax = this.clamp(this.profile.maxVolumePercent, this.volumeMin, 100, 100);
      return this.clamp(this.status.volumeLimitPercent, this.volumeMin, profileMax, profileMax);
    },
    volumePercent() {
      if (this.volumeMax <= this.volumeMin) return 0;
      return Math.round((this.volume - this.volumeMin) * 100 / (this.volumeMax - this.volumeMin));
    },
    maxTextLength() {
      return this.clamp(this.profile.maxTextLength, 1, 5000, 500);
    },
    audioFiles() {
      return Array.isArray(this.status.audioFiles) ? this.status.audioFiles.filter(Boolean) : [];
    },
    audioTransfer() {
      return this.status.audioTransfer && typeof this.status.audioTransfer === 'object'
        ? this.status.audioTransfer
        : {};
    },
    broadcastAction() {
      return this.broadcastActive ? 'stop_broadcast' : 'start_broadcast';
    },
    monitorAction() {
      return this.monitorActive ? 'stop_monitor' : 'start_monitor';
    },
    alarmAction() {
      return this.alarmActive ? 'stop_alarm' : 'play_alarm';
    },
    ttsAction() {
      return this.ttsLoopActive ? 'stop_tts' : 'play_tts';
    },
    canDeleteFile() {
      return !!this.selectedAudio && this.canControl('delete_audio_file');
    },
    canUploadAudio() {
      return this.connected &&
        this.actions.includes('upload_audio_file') &&
        !this.audioUploading &&
        !this.mediaBusy &&
        !['DOWNLOADING', 'UPLOADING'].includes(this.audioTransfer.status);
    },
    audioTransferText() {
      const status = this.audioTransfer.status;
      if (!status) return '';
      const names = {
        DOWNLOADING: '客户端下载中',
        UPLOADING: '正在写入设备',
        COMPLETED: '上传完成',
        FAILED: '上传失败'
      };
      const fileName = this.audioTransfer.fileName || '';
      const error = status === 'FAILED' && this.audioTransfer.error
        ? `：${this.audioTransfer.error}`
        : '';
      return `文件传输：${fileName} ${names[status] || status}${error}`;
    },
    audioPlaybackText() {
      if (!this.audioPlaying) return '播放状态：未播放';
      const fileName = this.audioPlayingFile || '未知文件';
      return `播放状态：${this.audioLooping ? '循环播放' : '单次播放'} · ${fileName}`;
    }
  },
  watch: {
    device: {
      immediate: true,
      deep: true,
      handler(device) {
        this.syncDeviceStatus(device);
      }
    }
  },
  beforeDestroy() {
    this.shutdownAudioSession();
  },
  methods: {
    clamp(value, min, max, fallback) {
      const number = Number(value);
      return Number.isFinite(number) ? Math.max(min, Math.min(max, Math.round(number))) : fallback;
    },
    canControl(action) {
      return this.connected &&
        this.actions.includes(action) &&
        !this.busyActions[action] &&
        !this.mediaBusy;
    },
    syncDeviceStatus(device) {
      if (!device) return;
      if (this.syncedDeviceId !== device.deviceId) {
        this.releaseMediaSession();
        this.syncedDeviceId = device.deviceId;
        this.mediaSessionId = '';
        this.alarmActive = false;
        this.ttsLoopActive = false;
        this.audioPlaying = false;
        this.audioLooping = false;
        this.audioPlayingFile = '';
        this.audioFilesRequestedFor = '';
        this.lightEnabled = false;
        this.strobeEnabled = false;
        this.redBlueMode = 0;
        this.pendingVolume = null;
        this.pendingVolumeUntil = 0;
        this.monitorSuppressedForBroadcast = false;
      }
      const status = device.status || device.runtimeStatus || {};
      const volume = Number(status.volumePercent);
      if (Number.isFinite(volume)) {
        const normalized = this.clamp(volume, this.volumeMin, this.volumeMax, 50);
        if (this.pendingVolume === normalized || Date.now() >= this.pendingVolumeUntil) {
          this.volume = normalized;
          this.pendingVolume = null;
          this.pendingVolumeUntil = 0;
        }
      }
      const session = status.audioSession || {};
      if (session.mediaSessionId) this.mediaSessionId = session.mediaSessionId;
      if (session.broadcastActive !== undefined) this.broadcastActive = !!session.broadcastActive;
      if (session.monitorActive !== undefined) this.monitorActive = !!session.monitorActive;
      if (session.monitorSuppressed !== undefined) this.monitorSuppressed = !!session.monitorSuppressed;
      const playback = status.audioPlayback || {};
      if (playback.playing !== undefined) this.audioPlaying = !!playback.playing;
      if (playback.loop !== undefined) this.audioLooping = !!playback.loop;
      if (playback.fileName !== undefined) this.audioPlayingFile = playback.fileName || '';
      if (Array.isArray(status.audioFiles)) {
        if (!status.audioFiles.includes(this.selectedAudio)) {
          this.selectedAudio = status.audioFiles[0] || '';
        }
      }
      if (this.connected &&
        this.actions.includes('list_audio_files') &&
        this.audioFilesRequestedFor !== device.deviceId) {
        this.audioFilesRequestedFor = device.deviceId;
        this.$nextTick(() => this.refreshAudioFiles(true));
      }
    },
    liveKitUrl(url) {
      if (window.location.protocol === 'https:') {
        return `wss://${window.location.host}/livekit`;
      }
      return url;
    },
    async ensureMediaSession(enableMicrophone) {
      if (this.mediaRoom && this.mediaSessionId) {
        if (enableMicrophone) await this.setLocalMicrophone(true);
        return this.mediaSessionId;
      }
      if (this.mediaBusy || !this.device) return '';
      this.mediaBusy = true;
      let response = null;
      let room = null;
      try {
        response = await startCameraIntercom({
          robotId: this.selectedRobotId,
          deviceId: this.device.deviceId,
          quality: 'sub'
        });
        const url = this.liveKitUrl(response.livekitUrl);
        if (!response.sessionId || !response.operatorToken || !url) {
          throw new Error('多合一音频会话参数不完整');
        }
        room = new Room({});
        room.on(RoomEvent.TrackSubscribed, (track) => {
          if (track.kind !== 'audio') return;
          this.attachRemoteAudio(track, response.sessionId);
        });
        room.on(RoomEvent.TrackUnsubscribed, (track) => {
          if (track.kind !== 'audio') return;
          track.detach();
          this.removeRemoteAudio();
        });
        room.on(RoomEvent.Disconnected, () => {
          this.handleMediaDisconnect(room, response.sessionId);
        });
        await room.connect(url, response.operatorToken);
        this.mediaRoom = room;
        this.mediaSessionId = response.sessionId;
        this.startMediaHeartbeat();
        if (enableMicrophone) await this.setLocalMicrophone(true);
        return response.sessionId;
      } catch (error) {
        if (room) await Promise.resolve(room.disconnect()).catch(() => {});
        if (response && response.sessionId) {
          await stopIntercom(response.sessionId).catch(() => {});
        }
        this.$message.error(errorMessage(error));
        return '';
      } finally {
        this.mediaBusy = false;
      }
    },
    attachRemoteAudio(track, sessionId) {
      this.removeRemoteAudio();
      const audio = track.attach();
      audio.dataset.multiFunctionSessionId = sessionId;
      audio.autoplay = true;
      audio.style.display = 'none';
      document.body.appendChild(audio);
      this.remoteAudioElement = audio;
      Promise.resolve(audio.play()).catch(() => {
        this.$message.warning('浏览器阻止了收音播放，请允许页面播放声音');
      });
    },
    removeRemoteAudio() {
      if (this.remoteAudioElement && typeof this.remoteAudioElement.remove === 'function') {
        this.remoteAudioElement.remove();
      }
      this.remoteAudioElement = null;
    },
    async setLocalMicrophone(enabled) {
      if (!this.mediaRoom) return;
      await this.mediaRoom.localParticipant.setMicrophoneEnabled(enabled, {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }, {
        name: 'audio.operator.mic'
      });
    },
    startMediaHeartbeat() {
      this.stopMediaHeartbeat();
      this.mediaHeartbeatTimer = window.setInterval(() => {
        if (this.mediaSessionId) heartbeatIntercom(this.mediaSessionId).catch(() => {});
      }, 5000);
    },
    stopMediaHeartbeat() {
      if (this.mediaHeartbeatTimer) {
        window.clearInterval(this.mediaHeartbeatTimer);
        this.mediaHeartbeatTimer = null;
      }
    },
    async handleMediaDisconnect(room, sessionId) {
      if (this.mediaRoom !== room) return;
      const device = this.device;
      const broadcastActive = this.broadcastActive;
      const monitorActive = this.monitorActive;
      this.stopMediaHeartbeat();
      this.mediaRoom = null;
      this.mediaSessionId = '';
      this.broadcastActive = false;
      this.monitorActive = false;
      this.monitorSuppressed = false;
      this.monitorSuppressedForBroadcast = false;
      this.removeRemoteAudio();
      if (device && broadcastActive) {
        await this.sendDeviceCommand(
          device,
          'stop_broadcast',
          { mediaSessionId: sessionId },
          'multi_broadcast_disconnected'
        ).catch(() => {});
      }
      if (device && monitorActive) {
        await this.sendDeviceCommand(
          device,
          'stop_monitor',
          { mediaSessionId: sessionId },
          'multi_monitor_disconnected'
        ).catch(() => {});
      }
      await stopIntercom(sessionId).catch(() => {});
    },
    async releaseMediaSession() {
      const sessionId = this.mediaSessionId;
      const room = this.mediaRoom;
      this.stopMediaHeartbeat();
      this.mediaSessionId = '';
      this.mediaRoom = null;
      this.removeRemoteAudio();
      if (room) {
        await Promise.resolve(room.localParticipant.setMicrophoneEnabled(false)).catch(() => {});
        await Promise.resolve(room.disconnect()).catch(() => {});
      }
      if (sessionId) await stopIntercom(sessionId).catch(() => {});
    },
    shutdownAudioSession() {
      const device = this.device;
      const sessionId = this.mediaSessionId;
      if (device && sessionId && this.broadcastActive) {
        this.sendDeviceCommand(
          device,
          'stop_broadcast',
          { mediaSessionId: sessionId },
          'multi_broadcast_page_close'
        );
      }
      if (device && sessionId && this.monitorActive) {
        this.sendDeviceCommand(
          device,
          'stop_monitor',
          { mediaSessionId: sessionId },
          'multi_monitor_page_close'
        );
      }
      this.releaseMediaSession();
    },
    async dispatchCommand(action, params, source) {
      if (!this.canControl(action)) return false;
      this.$set(this.busyActions, action, true);
      try {
        return await this.sendDeviceCommand(this.device, action, params || {}, source || action);
      } finally {
        this.$delete(this.busyActions, action);
      }
    },
    async toggleBroadcast() {
      const next = !this.broadcastActive;
      const action = next ? 'start_broadcast' : 'stop_broadcast';
      if (next && this.monitorActive && !this.monitorSuppressed) {
        const suppressed = await this.setMonitorSuppressed(true, true);
        if (!suppressed) return;
      }
      const mediaSessionId = next
        ? await this.ensureMediaSession(true)
        : this.mediaSessionId;
      if (!mediaSessionId) {
        if (next) await this.restoreMonitorAfterBroadcast();
        return;
      }
      const ok = await this.dispatchCommand(
        action,
        { mediaSessionId },
        next ? 'multi_broadcast_start' : 'multi_broadcast_stop'
      );
      if (ok) {
        this.broadcastActive = next;
        if (!next) {
          await this.setLocalMicrophone(false).catch(() => {});
          await this.restoreMonitorAfterBroadcast();
          if (!this.monitorActive) await this.releaseMediaSession();
        }
      } else if (next && !this.monitorActive) {
        await this.releaseMediaSession();
      } else if (next) {
        await this.restoreMonitorAfterBroadcast();
      }
    },
    async toggleMonitor() {
      const next = !this.monitorActive;
      const action = next ? 'start_monitor' : 'stop_monitor';
      const mediaSessionId = next
        ? await this.ensureMediaSession(false)
        : this.mediaSessionId;
      if (!mediaSessionId) return;
      const ok = await this.dispatchCommand(
        action,
        { mediaSessionId },
        next ? 'multi_monitor_start' : 'multi_monitor_stop'
      );
      if (ok) {
        this.monitorActive = next;
        if (next && this.broadcastActive && !this.monitorSuppressed) {
          const suppressed = await this.setMonitorSuppressed(true, true);
          if (!suppressed) {
            await this.dispatchCommand('stop_monitor', { mediaSessionId }, 'multi_monitor_rollback');
            this.monitorActive = false;
          }
        }
        if (!next) {
          this.monitorSuppressed = false;
          this.monitorSuppressedForBroadcast = false;
        }
        if (!next && !this.broadcastActive) await this.releaseMediaSession();
      } else if (next && !this.broadcastActive) {
        await this.releaseMediaSession();
      }
    },
    async setMonitorSuppressed(value, automatic = false) {
      const ok = await this.dispatchCommand(
        'set_monitor_suppressed',
        { suppressed: !!value },
        value ? 'multi_monitor_suppress' : 'multi_monitor_resume'
      );
      if (ok) {
        this.monitorSuppressed = !!value;
        this.monitorSuppressedForBroadcast = automatic && !!value;
      } else {
        this.monitorSuppressed = !value;
        if (automatic) this.monitorSuppressedForBroadcast = false;
      }
      return ok;
    },
    async restoreMonitorAfterBroadcast() {
      if (!this.monitorSuppressedForBroadcast || !this.monitorActive) return;
      await this.setMonitorSuppressed(false, true);
    },
    async toggleAlarm() {
      const next = !this.alarmActive;
      const ok = await this.dispatchCommand(
        next ? 'play_alarm' : 'stop_alarm',
        {},
        next ? 'multi_alarm_start' : 'multi_alarm_stop'
      );
      if (ok) this.alarmActive = next;
    },
    async setVolume() {
      const previous = Number(this.status.volumePercent);
      const next = this.clamp(this.volume, this.volumeMin, this.volumeMax, 50);
      this.volume = next;
      this.pendingVolume = next;
      this.pendingVolumeUntil = Date.now() + 4000;
      const ok = await this.dispatchCommand('set_volume', { volumePercent: next }, 'multi_volume');
      if (!ok) {
        this.pendingVolume = null;
        this.pendingVolumeUntil = 0;
        if (Number.isFinite(previous)) this.volume = previous;
      }
    },
    async setSpeakerTilt() {
      await this.dispatchCommand(
        'set_speaker_tilt',
        { positionPercent: this.clamp(this.speakerTilt, 0, 100, 50) },
        'multi_speaker_tilt'
      );
    },
    async toggleTts() {
      if (this.ttsLoopActive) {
        const ok = await this.dispatchCommand('stop_tts', {}, 'multi_tts_stop');
        if (ok) this.ttsLoopActive = false;
        return;
      }
      const text = this.ttsText.trim();
      if (!text) {
        this.$message.warning('请输入播报文字');
        return;
      }
      const ok = await this.dispatchCommand('play_tts', {
        text,
        voice: this.voiceType,
        loop: this.ttsLoop
      }, 'multi_tts_play');
      if (ok && this.ttsLoop) this.ttsLoopActive = true;
    },
    async refreshAudioFiles(silent = false) {
      const ok = await this.dispatchCommand('list_audio_files', {}, 'multi_audio_list');
      if (!silent && ok) this.$message.success('文件列表刷新指令已发送');
    },
    chooseAudioFile() {
      if (this.$refs.audioFileInput) this.$refs.audioFileInput.click();
    },
    async handleAudioFileSelected(event) {
      const input = event.target;
      const file = input && input.files && input.files[0];
      if (!file || !this.device || this.audioUploading) return;
      const extension = String(file.name || '').split('.').pop().toLowerCase();
      if (!['mp3', 'wav'].includes(extension)) {
        this.$message.warning('只支持 mp3、wav 音频文件');
        input.value = '';
        return;
      }
      if (file.size <= 0 || file.size > 20 * 1024 * 1024) {
        this.$message.warning('音频文件大小必须大于 0 且不超过 20MB');
        input.value = '';
        return;
      }
      this.audioUploading = true;
      try {
        const form = new FormData();
        form.append('fileType', 'AUDIO');
        form.append('robotId', this.selectedRobotId);
        form.append('deviceId', this.device.deviceId);
        form.append('sourceFileId', `multi-function/${this.selectedRobotId}/${this.device.deviceId}/${Date.now()}/${file.size}`);
        form.append('metadata', JSON.stringify({
          purpose: 'MULTI_FUNCTION_AUDIO',
          temporary: false
        }));
        form.append('file', file, file.name);
        const storedFile = await uploadFile(form, 120000);
        if (!storedFile || !storedFile.fileId) {
          throw new Error('媒体服务未返回 fileId');
        }
        const response = await transferMultiFunctionAudio(
          this.selectedRobotId,
          this.device.deviceId,
          storedFile.fileId
        );
        const transferId = response && (response.transferId || (response.data && response.data.transferId));
        this.$message.success(transferId
          ? `音频传输任务已下发：${transferId}`
          : '音频传输任务已下发');
      } catch (error) {
        this.$message.error(errorMessage(error));
      } finally {
        this.audioUploading = false;
        if (input) input.value = '';
      }
    },
    canPlayAudioFile(file) {
      return !!file && this.canControl('play_audio_file');
    },
    isAudioModeActive(file, loop) {
      return this.audioPlaying &&
        this.audioPlayingFile === file &&
        this.audioLooping === !!loop;
    },
    async playAudioFile(file, loop) {
      this.selectedAudio = file;
      if (this.audioPlaying) {
        if (this.isAudioModeActive(file, loop)) {
          await this.stopAudioFile();
          return;
        }
        const stopped = await this.stopAudioFile();
        if (!stopped) return;
      }
      const ok = await this.dispatchCommand('play_audio_file', {
        fileName: file,
        loop: !!loop
      }, loop ? 'multi_audio_loop' : 'multi_audio_once');
      if (ok) {
        this.audioPlaying = true;
        this.audioLooping = !!loop;
        this.audioPlayingFile = file;
      }
    },
    async stopAudioFile() {
      const ok = await this.dispatchCommand('stop_audio_file', {}, 'multi_audio_stop');
      if (ok) {
        this.audioPlaying = false;
        this.audioLooping = false;
        this.audioPlayingFile = '';
      }
      return ok;
    },
    async deleteAudioFile() {
      try {
        await this.$confirm(`确认删除音频文件“${this.selectedAudio}”？`, '删除确认', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        });
      } catch (_error) {
        return;
      }
      const ok = await this.dispatchCommand(
        'delete_audio_file',
        { fileName: this.selectedAudio },
        'multi_audio_delete'
      );
      if (ok) await this.refreshAudioFiles();
    },
    async setLightEnabled(value) {
      const ok = await this.dispatchCommand('light.set', { enabled: !!value }, 'multi_light_power');
      if (!ok) this.lightEnabled = !value;
    },
    async setStrobeEnabled(value) {
      const ok = await this.dispatchCommand('light.set', { strobeEnabled: !!value }, 'multi_light_strobe');
      if (!ok) this.strobeEnabled = !value;
      if (ok && !value) this.lightEnabled = false;
    },
    async setBrightness() {
      await this.dispatchCommand(
        'light.set',
        { brightness: this.clamp(this.brightness, 0, 100, 50) },
        'multi_light_brightness'
      );
    },
    async nextRedBlueMode() {
      const profile = this.profile.light || {};
      const max = this.clamp(profile.redBlueModeMax, 1, 255, 16);
      const next = this.redBlueMode >= max ? 1 : this.redBlueMode + 1;
      const ok = await this.dispatchCommand('light.set', { redBlueMode: next }, 'multi_red_blue_next');
      if (ok) this.redBlueMode = next;
    },
    async closeRedBlue() {
      const ok = await this.dispatchCommand('light.set', { redBlueMode: 0 }, 'multi_red_blue_off');
      if (ok) this.redBlueMode = 0;
    },
    async setLightTilt() {
      await this.dispatchCommand(
        'set_light_tilt',
        { positionPercent: this.clamp(this.lightTilt, 0, 100, 50) },
        'multi_light_tilt'
      );
    }
  }
};
</script>

<style scoped lang="scss">
.box {
  min-height: 440px;
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
}

.tabs {
  .tab-item {
    flex: 1;
    min-width: 0;
    height: 32px;
    margin-right: -1px;
    padding: 0 4px;
    color: #6AC5FF;
    font-family: "Alibaba PuHuiTi";
    font-size: 12px;
    letter-spacing: 0;
    border: 1px solid #4AB8FF;
    cursor: pointer;
    background: transparent;
    position: relative;
    &:last-child {
      margin-right: 0;
    }
    &.active {
      color: #FFF;
      background: #0A3560;
      box-shadow: inset 0 0 6px 0 #69C4FF;
      z-index: 1;
    }
  }
}

.status-row {
  margin-top: 14px;
  justify-content: space-between;
  .status-text {
    color: rgba(255, 255, 255, 0.8);
    font-family: "Microsoft YaHei";
    font-size: 13px;
    line-height: 18px;
    .ok {
      color: #00FF60;
    }
    .danger {
      color: #FF6B6B;
    }
  }
}

.action-btn {
  width: 100%;
  height: 34px;
  padding: 0;
  color: #FFF;
  font-family: "Alibaba PuHuiTi";
  font-size: 14px;
  letter-spacing: 0;
  background: #0F2B44;
  border: 0;
  border-radius: 2px;
  cursor: pointer;
  gap: 10px;
  &.active {
    color: #0BF9FE;
    box-shadow: 0 0 10px 3px #2f608d inset;
  }
  &.alarm {
    background: #7D1018;
  }
  &.is-disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .btn-icon {
    width: 16px;
    height: 16px;
    display: block;
  }
}

.slider-block {
  .slider-label {
    color: #FFF;
    font-family: "Alibaba PuHuiTi";
    font-size: 13px;
    letter-spacing: 0;
    line-height: 18px;
  }
  .slider-row {
    margin-top: 6px;
  }
  .slider-value {
    width: 42px;
    margin-left: 10px;
    color: #FFF;
    font-size: 13px;
    text-align: center;
    flex-shrink: 0;
  }
}

.switch-row,
.light-row,
.red-blue-row {
  gap: 10px;
  &.end {
    justify-content: flex-end;
  }
  .switch-label {
    color: rgba(255, 255, 255, 0.8);
    font-size: 13px;
    white-space: nowrap;
  }
}

.red-blue-row {
  .mode-text {
    flex: 1;
    color: #6AC5FF;
    font-size: 12px;
  }
  ::v-deep .el-button {
    width: 68px;
    height: 28px;
    padding: 0;
    margin: 0;
  }
}

.tts-input {
  ::v-deep .el-textarea__inner {
    min-height: 112px !important;
    padding: 10px;
    color: rgba(255, 255, 255, 0.8);
    font-size: 14px;
    background: #142941;
    border: none;
    border-radius: 4px;
    box-shadow: none;
  }
  ::v-deep .el-input__count {
    color: rgba(255, 255, 255, 0.5);
    background: transparent;
  }
}

.tts-options {
  .voice-radios {
    ::v-deep .el-radio {
      margin-right: 14px;
      color: #FFF;
      .el-radio__label {
        color: #FFF;
        font-size: 14px;
        padding-left: 6px;
      }
    }
  }
  .ml-auto {
    margin-left: auto;
  }
}

.audio-toolbar {
  justify-content: space-between;
  color: #FFF;
  font-size: 13px;
  ::v-deep .el-button {
    padding: 0;
    color: #6AC5FF;
  }
  .audio-toolbar-actions {
    gap: 12px;
  }
}

.audio-file-input {
  display: none;
}

.audio-list {
  height: 132px;
  overflow-y: auto;
  margin-top: 6px;
  background: rgba(2, 19, 40, 0.5);
  .audio-item {
    width: 100%;
    height: 33px;
    padding: 0 12px;
    color: #FFF;
    font-size: 13px;
    letter-spacing: 0;
    border: 0;
    cursor: pointer;
    gap: 8px;
    background: transparent;
    &.active {
      background: #0163C4;
    }
    .music-icon {
      width: 16px;
      height: 16px;
      flex-shrink: 0;
    }
    .audio-file-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .audio-row-actions {
      flex-shrink: 0;
      gap: 6px;
    }
    ::v-deep .audio-icon-btn {
      width: 24px;
      height: 24px;
      padding: 0;
      margin: 0;
      color: #6AC5FF;
      border: 0;
      background: transparent;
      &.active {
        color: #0BF9FE;
        text-shadow: 0 0 8px #09F;
      }
    }
  }
  .empty-text {
    height: 100%;
    color: rgba(255, 255, 255, 0.45);
    font-size: 12px;
  }
}

.btns {
  ::v-deep .el-button {
    padding: 0;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0;
    background: #021328;
    border-radius: 4px;
    border: none;
    box-shadow: 0 0 14px 2px #09F inset;
    text-align: center;
    &.danger-btn {
      box-shadow: 0 0 12px 1px #B52732 inset;
    }
  }
}

.button-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  ::v-deep .el-button {
    width: 100%;
    height: 30px;
    margin: 0;
  }
}

.audio-command-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.light-switches-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  .light-row {
    min-width: 0;
    justify-content: space-between;
  }
}

.local-state {
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
  text-align: right;
}

.progress {
  position: relative;
  height: 16px;
  .track-bg {
    position: absolute;
    top: 50%;
    left: 0;
    transform: translateY(-50%);
    width: 100%;
    height: 8px;
    background: #093974;
    border-radius: 2px;
    pointer-events: none;
    z-index: 1;
  }
  .filled-glow {
    position: absolute;
    top: 50%;
    left: 0;
    transform: translateY(-50%);
    width: var(--value-percent);
    height: 8px;
    background: #0C132A;
    border-radius: 2px;
    box-shadow: 0 0 6px 2px #09F inset;
    pointer-events: none;
    z-index: 2;
  }
  input[type=range].custom-slider {
    position: relative;
    z-index: 3;
    width: 100%;
    margin: 0;
    appearance: none;
    -webkit-appearance: none;
    background: transparent;
    outline: none;
    cursor: pointer;
    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
    &::-webkit-slider-runnable-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }
    &::-webkit-slider-thumb {
      width: 16px;
      height: 16px;
      margin-top: -4px;
      appearance: none;
      -webkit-appearance: none;
      background: #021328;
      border: none;
      border-radius: 50%;
      box-shadow: 0 0 10px 2px #09f inset;
    }
    &::-moz-range-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }
    &::-moz-range-thumb {
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      box-shadow: 0 0 10px 2px #09f inset;
    }
  }
}

.mt10 { margin-top: 10px; }
.mt12 { margin-top: 12px; }
.mt15 { margin-top: 15px; }
.wp124 { width: 124px; }

::v-deep {
  .el-switch {
    line-height: 18px !important;
    .el-switch__label.el-switch__label--right {
      margin-left: 3px;
    }
    .el-switch__core {
      width: 50px !important;
      &:after {
        top: 2px;
        left: 2px;
        width: 14px;
        height: 14px;
      }
    }
    .el-switch__label {
      position: absolute;
      display: none !important;
      font-weight: normal !important;
      z-index: 2000;
      * {
        font-size: 12px !important;
      }
      &.el-switch__label--left {
        margin-right: 0;
        margin-left: 19px;
      }
      &.el-switch__label--right {
        margin-left: 3px;
      }
      &.is-active {
        display: inline-block !important;
        color: #fff !important;
      }
    }
    &.is-checked .el-switch__core::after {
      left: unset;
      right: 3px;
    }
  }
}
</style>
