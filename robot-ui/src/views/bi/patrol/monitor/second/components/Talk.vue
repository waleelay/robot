<!--
 * @Author: dengxumei
 * @Date: 2026-04-08 09:36:42
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-09 14:45:52
 * @Description:
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\second\components\Talk.vue
 * @Version:
-->
<template>
  <div class="flx-justify-between flex-column" :class="{ 'is-inner': isMapInner }" :style="{ pointerEvents: talkAvailable ? 'auto' : 'none' }">
    <div class="circle flx-center flex-column" :class="{ 'talking': selectCamera.intercomActive }" @click="handleTalk">
      <span v-if="!selectCamera.intercomActive || !selectCamera?.room?.localParticipant">
        <svg-icon :icon-class="selectCamera.intercomActive ? 'mic-fill' : 'mic-off-fill'" />
      </span>
      <VolumeWave :selectCamera="selectCamera" :isMapInner="isMapInner" v-else />
      <span class="mt8">{{ selectCamera.intercomActive ? '正在通话' : '点击通话' }}</span>
      <span v-if="selectCamera.intercomActive && selectCamera?.room?.localParticipant" class="mt2" style="color: rgba(255, 255, 255, 0.80); font-family: 'Alibaba PuHuiTi'; font-size: 10px; font-style: normal; letter-spacing: 0.2px;">点击结束</span>
    </div>
    <div v-if="!isMapInner" class="wp269 hp16 mt22 progress flx-align-center">
      <span style="color: #FFF; font-size:16px;" @click="toggleAudioMute(audioDevice)">
        <svg-icon :icon-class="volumeIconClass" />
      </span>
      <div class="volume-range ml10">
        <div class="track-bg" />
        <div class="filled-glow" :style="{ '--value-percent': `${displayVolume}%` }" />
        <input
          :value="displayVolume"
          type="range"
          min="0"
          max="100"
          step="1"
          class="custom-slider"
          aria-label="扬声器音量"
          :aria-valuetext="displayMuted ? `已静音，音量 ${displayVolume}` : `音量 ${displayVolume}`"
          :disabled="displayMuted"
          @input="updateAudioVolume($event.target.value)"
          @change="setAudioVolume($event.target.value)"
        >
      </div>
      <span class="volume-value">{{ displayVolume }}</span>
    </div>
    <div class="btns" :class="{'mt20': !isMapInner, 'mt30': isMapInner}">
      <el-button v-if="!isMapInner" type="primary" class="wp124 hp30" @click="toggleAudioMute(audioDevice)">{{ displayMuted ? '取消静音' : '静音' }}</el-button>
      <div class="volume-stepper wp124 hp30" :class="{ 'ml20': !isMapInner, 'is-disabled': displayMuted }">
        <button type="button" class="btn-volume" aria-label="音量减" :disabled="displayMuted" @click="adjustAudioVolume(audioDevice, -5)">
          <svg-icon icon-class="minus" />
        </button>
        <span class="ml10 mr10">音量</span>
        <button type="button" class="btn-volume" aria-label="音量加" :disabled="displayMuted" @click="adjustAudioVolume(audioDevice, 5)">
          <svg-icon icon-class="plus" />
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { sendEquipmentCommand } from '../../../../../../api/media';
import { errorMessage } from '../../../../../../utils';
import yuntai from './ptz-control-mixin';
import { mapActions, mapState } from 'vuex';
import VolumeWave from './VolumeWave.vue';
import { audioStatusVersion } from '../../../../../../utils/audio-device-state';

let talkOwnerSequence = 0

export default {
  name: 'Talk',
  mixins: [yuntai],
  components: { VolumeWave },
  computed: {
    ...mapState('websocketRobot', ['cameras', 'robots']),
    selectedRobotId() {
      return this.targetRobotId || this.$store.getters['websocketRobot/getSelectedRobotId'] || ''
    },
    selectedRobot() {
      if (this.targetRobotId) {
        if (String(this.targetRobot?.robotId || '') === String(this.targetRobotId)) return this.targetRobot
        return (this.robots || []).find(item => String(item.robotId) === String(this.targetRobotId)) ||
          this.robotBaseInfo?.[this.targetRobotId] || {}
      }
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    selectCamera() {
      return this.cameras?.[this.selectedRobot?.cameras?.[0]?.key] || {}
    },
    talkAvailable() {
      return Boolean(this.selectedRobotId && this.selectCamera.key && this.selectedRobot.status === 'online')
    },
    displayVolume() {
      return this.localVolume === null ? this.normalizeAudioVolume(this.audioVolume(this.audioDevice)) : this.localVolume
    },
    displayMuted() {
      return this.requestedMuted === null ? this.audioMuted(this.audioDevice) : this.requestedMuted
    },
    audioStatusVersion() {
      const status = this.audioStatus(this.audioDevice)
      return audioStatusVersion(this.selectedRobotId, this.audioDevice?.deviceId, status)
    },
    // 音量图标类，根据音量值动态计算
    volumeIconClass() {
      const cur = Number(this.displayVolume)
      if (cur === 0 || this.displayMuted) {
        return 'volume-mute';
      } else if (cur < 50) {
        return 'volume-l';
      } else {
        return 'volume';
      }
    }
  },
  props: {
    isMapInner: {
      type: Boolean,
      default: false
    },
    targetRobotId: {
      type: [String, Number],
      default: ''
    },
    targetRobot: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      localVolume: null,
      volumeInteracting: false,
      lastUnmutedVolume: null,
      requestedMuted: null,
      intercomOwnerId: `talk:${++talkOwnerSequence}`,
      ownedIntercomCameraKey: '',
      talkDisposed: false
    }
  },
  watch: {
    audioStatusVersion: {
      immediate: true,
      handler() {
        this.reconcileAudioStatus()
      }
    },
    selectedRobotId() {
      this.releaseOwnedIntercom()
      this.localVolume = null
      this.volumeInteracting = false
      this.lastUnmutedVolume = null
      this.requestedMuted = null
    }
  },
  methods: {
    ...mapActions('websocketRobot', ['toggleIntercom', 'stopOwnedIntercom']),
    normalizeAudioVolume(volume) {
      const number = Number(volume)
      return Number.isFinite(number) ? Math.max(0, Math.min(100, Math.round(number))) : 50
    },
    reconcileAudioStatus() {
      const device = this.audioDevice
      if (!device) return
      const volume = this.normalizeAudioVolume(this.audioVolume(device))
      const muted = this.audioMuted(device)
      if (!muted) this.lastUnmutedVolume = volume
      if (!this.volumeInteracting) {
        this.localVolume = muted && volume === 0 && this.lastUnmutedVolume !== null
          ? this.lastUnmutedVolume
          : volume
      }
      this.requestedMuted = null
    },
    async toggleAudioMute(device) {
      const currentMuted = this.displayMuted
      const muted = !currentMuted
      if (muted) this.lastUnmutedVolume = this.displayVolume
      this.requestedMuted = muted
      const ok = await this.sendDeviceCommand(device, 'set_mute', {
        mute: muted
      }, muted ? 'volume_mute' : 'volume_unmute')
      if (!ok) this.requestedMuted = null
    },
    updateAudioVolume(volume) {
      this.volumeInteracting = true
      this.localVolume = this.normalizeAudioVolume(volume)
    },
    async setAudioVolume(volume) {
      this.volumeInteracting = false
      await this.requestAudioVolume(this.audioDevice, volume, 'volume_slider')
    },
    async adjustAudioVolume(device, delta) {
      const nextVolume = this.normalizeAudioVolume(this.displayVolume + delta)
      await this.requestAudioVolume(device, nextVolume, delta > 0 ? 'volume_up' : 'volume_down')
    },
    async requestAudioVolume(device, volume, source) {
      const nextVolume = this.normalizeAudioVolume(volume)
      this.localVolume = nextVolume
      const ok = await this.sendDeviceCommand(device, 'set_volume', {
        volumePercent: nextVolume
      }, source)
      if (!ok) {
        this.localVolume = this.normalizeAudioVolume(this.audioVolume(device))
      }
      return ok
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
    async handleTalk() {
      if (!this.talkAvailable) return
      if (!this.selectCamera.intercomActive && this.audioDevice && this.audioMuted(this.audioDevice)) {
        await this.toggleAudioMute(this.audioDevice)
      }
      if (this.talkDisposed) return
      const cameraKey = this.selectCamera.key
      const wasActive = this.selectCamera.intercomActive
      if (!wasActive) this.ownedIntercomCameraKey = cameraKey
      await this.toggleIntercom({
        robotId: this.selectedRobotId,
        camera: this.selectCamera,
        ownerId: this.intercomOwnerId
      })
      if (wasActive) this.ownedIntercomCameraKey = ''
    },
    async releaseOwnedIntercom() {
      const cameraKey = this.ownedIntercomCameraKey
      this.ownedIntercomCameraKey = ''
      if (!cameraKey) return false
      try {
        return await this.stopOwnedIntercom({ cameraKey, ownerId: this.intercomOwnerId })
      } catch (error) {
        console.warn('[Talk] release owned intercom failed', error)
        return false
      }
    },
    audioStatus(device) {
      const status = (device && (device.status || device.runtimeStatus)) || {}
      const key = device && this.selectedRobotId ? `${this.selectedRobotId}:${device.deviceId}` : ''
      const reported = key ? (this.audioState?.[key] || {}) : {}
      const reportedVolume = reported.volume === undefined ? reported.volumePercent : reported.volume
      return {
        ...status,
        ...reported,
        volume: reportedVolume === undefined
          ? (status.volumePercent === undefined ? (status.volume === undefined ? 50 : status.volume) : status.volumePercent)
          : reportedVolume,
        muted: reported.muted === undefined ? (status.muted === undefined ? false : status.muted) : reported.muted
      }
    },
    hasAudioStatus(device) {
      const status = (device && (device.status || device.runtimeStatus)) || {}
      return (status.volume !== undefined || status.volumePercent !== undefined) && status.muted !== undefined
    },
    audioVolume(device) {
      return this.audioStatus(device).volume
    },
    audioMuted(device) {
      return this.audioStatus(device).muted
    },

  },
  beforeDestroy() {
    this.talkDisposed = true
    this.releaseOwnedIntercom()
  }
}
</script>
<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
  .circle {
    width: 126px;
    height: 126px;
    color: #fff;
    background: #021328;
    box-shadow: 0 0 17px 0 #159Aff inset;
    border-radius: 50%;
    font-family: "Alibaba PuHuiTi";
    font-size: 16px;
    letter-spacing: 0.2px;
    .svg-icon {
      font-size: 59px;
      color: #159Aff;
    }
    &.talking {
      // box-shadow: 0 0 60px 0 #42B3FF inset;
      // &, .svg-icon {
      //   color: #0BF9FE
      // }
    }
  }
}

.btns {
  ::v-deep .el-button {
    padding: 0;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    background: #021328 !important;
    border-radius: 4px;
    border: none !important;
    box-shadow: 0 0 14px 2px #09F inset !important;
    text-align: center;
    .svg-icon {
      font-size: 16px;
      cursor: pointer;
    }
    &.is-disabled {
      background: #080808;
      box-shadow: 0 0 14px 2px #515151 inset;
      cursor: not-allowed;
    }
    .btn-volume {
      &:active {
        color: #0BF9FE;
        // box-shadow: 0 0 10px 3px #0BF9FE inset;
      }
    }
    // &:not(.is-disabled) {
    //   &:active {
    //     color: #0BF9FE;
    //     box-shadow: 0 0 10px 3px #0BF9FE inset;
    //   }
    // }
  }
  .volume-stepper {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    vertical-align: middle;
    background: #021328;
    border-radius: 4px;
    box-shadow: 0 0 14px 2px #09F inset;
    &.is-disabled {
      background: #080808;
      box-shadow: 0 0 14px 2px #515151 inset;
    }
    .btn-volume {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      color: inherit;
      font: inherit;
      background: transparent;
      border: 0;
      cursor: pointer;
      .svg-icon {
        font-size: 16px;
      }
      &:active {
        color: #0BF9FE;
      }
      &:disabled {
        cursor: not-allowed;
      }
    }
  }
}

// 音量
.progress {
  position: relative;
  .volume-range {
    position: relative;
    display: flex;
    align-items: center;
    flex: 1;
    min-width: 0;
    height: 16px;
  }
  .volume-value {
    width: 28px;
    margin-left: 8px;
    color: #FFF;
    font-size: 12px;
    line-height: 16px;
    text-align: right;
  }
  .track-bg {
    position: absolute;
    top: 50%;
    left: 0;
    transform: translateY(-50%);
    width: 100%;
    height: 8px;
    background: #093974;   /* 未划过背景色 */
    border-radius: 2px;
    box-shadow: inset 0 1px 3px rgba(0,0,0,0.4), 0 1px 0 rgba(255,255,255,0.05);
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
    background: #0C132A;   /* 划过区域背景色 */
    border-radius: 2px 0 0 2px;  /* 左侧圆角，右侧平直按宽度变化，当宽度100%时变为全圆角，但为了美观，动态处理 */
    box-shadow: 0 0 6px 2px #09F inset;   /* 内阴影效果，仅在划过区域呈现 */
    pointer-events: none;
    z-index: 2;
    // transition: width 0.05s linear; /* 顺滑跟随滑块，但滑块是实时，同步很快 */
  }
  input[type=range].custom-slider {
    position: relative;
    z-index: 3;
    width: 100%;
    -webkit-appearance: none;
    appearance: none;
    background: transparent;  /* 完全透明，让下面两层显示 */
    outline: none;
    cursor: pointer;
    margin: 0;
    padding: 0;
    /* 滑块轨道完全透明，因为我们使用下层自定义轨道 */
    &::-webkit-slider-runnable-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }
    &::-moz-range-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }

    /* 滑块手柄样式 - 科技感圆形 */
    &::-webkit-slider-thumb {
      -webkit-appearance: none;
      appearance: none;
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      box-shadow: 0 0 10px 2px #09f inset;
      cursor: pointer;
      margin-top: -4px;  /* 因为轨道高10px，thumb高22px，垂直居中偏移 */
      transition: 0.1s ease;
      z-index: 10;
    }

    &::-webkit-slider-thumb:hover {
      transform: scale(1.2);
      background: #021328;
      box-shadow: 0 0 10px 2px #09f inset;
    }

    /* Firefox */
    &::-moz-range-thumb {
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      cursor: pointer;
      box-shadow: 0 0 10px 2px #09f inset;
      margin-top: -4px;  /* 因为轨道高10px，thumb高22px，垂直居中偏移 */
    }

    &::-moz-range-thumb:hover {
      transform: scale(1.15);
      background: #021328;
    }

    /* 兼容 Edge 以及确保滑块高度正常 */
    &:focus {
      outline: none;
    }
    &:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }
  }
}

.is-inner {
  .circle {
    width: 90px;
    height: 90px;
    font-family: "Alibaba PuHuiTi";
    font-size: 10px;
    line-height: 10px;
    letter-spacing: 0.146px;
    .svg-icon {
      font-size: 32px;
    }
  }
}
</style>
