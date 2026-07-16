<template>
  <div class="box multi-in-one p20 mt10">
    <div class="tabs flx-align-center">
      <div
        v-for="item in tabs"
        :key="item.key"
        class="tab-item flx-center"
        :class="{ active: activeTab === item.key }"
        @click="activeTab = item.key"
      >
        {{ item.label }}
      </div>
    </div>

    <!-- 喊话模式 -->
    <div v-if="activeTab === 'shout'" class="panel">
      <div class="status-row flx-align-center">
        <div class="status-text">
          连接状态：<span class="ok">已连接</span>
        </div>
        <div class="status-text">
          温度：0°C  <span class="ok">温度正常</span>
        </div>
      </div>
      <div class="action-btn flx-center" @click="noop">
        <svg-icon icon-class="volume" class="btn-icon" style="font-size: 16px;" />
        <span>开始喊话</span>
      </div>
      <div class="action-btn flx-center mt19" @click="noop">
        <svg-icon icon-class="volume" class="btn-icon" style="font-size: 16px;" />
        <span>开始收音</span>
      </div>
      <div class="action-btn alarm flx-center mt19" @click="noop">
        <svg-icon icon-class="volume" class="btn-icon" style="font-size: 16px;" />
        <span>播放警报</span>
      </div>
      <div class="slider-block mt18">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': volume + '%' }"></div>
            <input
              type="range"
              min="0"
              max="100"
              v-model.number="volume"
              class="custom-slider"
              :style="{ '--value-percent': volume + '%' }"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>
      <div class="slider-block mt15">
        <div class="slider-label">俯仰角度</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': pitchPercent + '%' }"></div>
            <input
              type="range"
              min="-90"
              max="90"
              v-model.number="pitch"
              class="custom-slider"
              :style="{ '--value-percent': pitchPercent + '%' }"
            >
          </div>
          <span class="slider-value">{{ pitchDisplay }}</span>
        </div>
      </div>
      <div class="switch-row end mt16 flx-align-center">
        <span class="switch-label">喊话时禁用收音</span>
        <el-switch
          v-model="disableMicOnShout"
          active-text="开启"
          inactive-text="关闭"
          active-color="#3DB56A"
          inactive-color="#5E5E5E"
        />
      </div>
    </div>

    <!-- 文字转语音 -->
    <div v-else-if="activeTab === 'tts'" class="panel">
      <div class="status-row">
        <div class="status-text">
          连接状态：<span class="ok">已连接</span>
        </div>
      </div>
      <el-input
        type="textarea"
        v-model="ttsText"
        :rows="6"
        placeholder="请输入文字"
        class="tts-input mt15"
        resize="none"
      />
      <div class="tts-options mt15 flx-align-center">
        <el-radio-group v-model="voiceType" class="voice-radios">
          <el-radio label="male">男声</el-radio>
          <el-radio label="female">女声</el-radio>
        </el-radio-group>
        <div class="switch-row flx-align-center ml-auto">
          <span class="switch-label">循环播放</span>
          <el-switch
            v-model="loopPlay"
            active-text="开启"
            inactive-text="关闭"
            active-color="#159AFF"
            inactive-color="#5E5E5E"
          />
        </div>
      </div>
      <div class="slider-block mt20">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': volume + '%' }"></div>
            <input
              type="range"
              min="0"
              max="100"
              v-model.number="volume"
              class="custom-slider"
              :style="{ '--value-percent': volume + '%' }"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>
      <div class="btns mt20 flx-center">
        <el-button type="primary" class="wp124 hp30" @click="noop">播放</el-button>
      </div>
    </div>

    <!-- 音频播放 -->
    <div v-else-if="activeTab === 'audio'" class="panel">
      <div class="status-row flx-align-center">
        <div class="status-text">
          连接状态：<span class="ok">已连接</span>
        </div>
        <div class="status-text">
          温度：0°C  <span class="ok">温度正常</span>
        </div>
      </div>
      <div class="audio-list common-scroll mt15">
        <div
          v-for="(file, index) in audioList"
          :key="index"
          class="audio-item flx-align-center"
          :class="{ active: selectedAudio === index }"
          @click="selectedAudio = index"
        >
          <svg-icon icon-class="music" class="music-icon" style="font-size: 16px;" />
          <span>{{ file }}</span>
        </div>
      </div>
      <div class="slider-block mt18">
        <div class="slider-label">调整音量</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': volume + '%' }"></div>
            <input
              type="range"
              min="0"
              max="100"
              v-model.number="volume"
              class="custom-slider"
              :style="{ '--value-percent': volume + '%' }"
            >
          </div>
          <span class="slider-value">{{ volume }}%</span>
        </div>
      </div>
      <div class="btns mt20 flx-center">
        <el-button type="primary" class="wp96 hp30" @click="noop">添加</el-button>
        <el-button type="primary" class="wp96 hp30 ml10" @click="noop">删除</el-button>
        <el-button type="primary" class="wp96 hp30 ml10" @click="noop">停止</el-button>
      </div>
    </div>

    <!-- 照明灯 -->
    <div v-else class="panel">
      <div class="status-row">
        <div class="status-text">
          连接状态：<span class="ok">已连接</span>
        </div>
      </div>
      <div class="light-row mt20 flx-align-center">
        <span class="switch-label">探照灯</span>
        <el-switch
          v-model="searchLight"
          active-text="开启"
          inactive-text="关闭"
          active-color="#159AFF"
          inactive-color="#5E5E5E"
        />
      </div>
      <div class="btns mt12">
        <el-button type="primary" class="wp96 hp30" @click="noop">开爆闪</el-button>
      </div>
      <div class="slider-block mt18">
        <div class="slider-label">亮度调节</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': brightness + '%' }"></div>
            <input
              type="range"
              min="0"
              max="100"
              v-model.number="brightness"
              class="custom-slider"
              :style="{ '--value-percent': brightness + '%' }"
            >
          </div>
          <span class="slider-value">{{ brightness }}%</span>
        </div>
      </div>
      <div class="light-row mt22 flx-align-center">
        <span class="switch-label">红蓝灯</span>
        <el-switch
          v-model="rbLight"
          active-text="开启"
          inactive-text="关闭"
          active-color="#159AFF"
          inactive-color="#5E5E5E"
        />
      </div>
      <div class="btns mt12">
        <el-button type="primary" class="wp96 hp30" @click="noop">切换模式</el-button>
      </div>
      <div class="slider-block mt18">
        <div class="slider-label">俯仰控制</div>
        <div class="slider-row flx-align-center">
          <div class="progress flex1">
            <div class="track-bg"></div>
            <div class="filled-glow" :style="{ '--value-percent': pitchPercent + '%' }"></div>
            <input
              type="range"
              min="-90"
              max="90"
              v-model.number="pitch"
              class="custom-slider"
              :style="{ '--value-percent': pitchPercent + '%' }"
            >
          </div>
          <span class="slider-value">{{ pitchDisplay }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MultiInOne',
  data() {
    return {
      activeTab: 'shout',
      tabs: [
        { key: 'shout', label: '喊话模式' },
        { key: 'tts', label: '文字转语音' },
        { key: 'audio', label: '音频播放' },
        { key: 'light', label: '照明灯' }
      ],
      volume: 70,
      pitch: 50,
      brightness: 70,
      disableMicOnShout: false,
      ttsText: '',
      voiceType: 'male',
      loopPlay: false,
      selectedAudio: 2,
      audioList: [
        '安全巡检提示音.mp3',
        '安全巡检提示音.mp3',
        '安全巡检提示音.mp3',
        '安全巡检提示音.mp3',
        '安全巡检提示音.mp3'
      ],
      searchLight: false,
      rbLight: false
    }
  },
  computed: {
    pitchPercent() {
      return Math.round(((this.pitch + 90) / 180) * 100)
    },
    pitchDisplay() {
      const val = Number(this.pitch)
      return (val >= 0 ? `+${val}` : `${val}`) + '°'
    }
  },
  methods: {
    noop() {}
  }
}
</script>

<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
}

.tabs {
  .tab-item {
    flex: 1;
    height: 32px;
    margin-right: -1px;
    color: #6AC5FF;
    font-family: "Alibaba PuHuiTi";
    font-size: 12px;
    letter-spacing: 0.86px;
    border: 1px solid #4AB8FF;
    cursor: pointer;
    background: transparent;
    position: relative;
    &:last-child {
      margin-right: 0;
    }
    &.active {
      color: #4AB8FF;
      background: #0A3560;
      box-shadow: inset 0 0 6px 0 #69C4FF;
      z-index: 1;
    }
  }
}

.status-row {
  margin-top: 19px;
  justify-content: space-between;
  .status-text {
    color: rgba(255, 255, 255, 0.8);
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: 18px;
    .ok {
      color: #00FF60;
    }
  }
}

.action-btn {
  margin-top: 20px;
  width: 100%;
  height: 36px;
  color: #FFF;
  font-family: "Alibaba PuHuiTi";
  font-size: 14px;
  letter-spacing: 0.86px;
  background: #0F2B44;
  border-radius: 2px;
  cursor: pointer;
  gap: 10px;
  &.alarm {
    background: #AE0000;
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
    font-size: 14px;
    letter-spacing: 0.86px;
    line-height: 20px;
  }
  .slider-row {
    margin-top: 8px;
  }
  .slider-value {
    width: 42px;
    margin-left: 10px;
    color: #FFF;
    font-family: "Alibaba PuHuiTi";
    font-size: 14px;
    letter-spacing: 0.86px;
    text-align: center;
    flex-shrink: 0;
  }
}

.switch-row,
.light-row {
  gap: 10px;
  &.end {
    justify-content: flex-end;
  }
  .switch-label {
    color: rgba(255, 255, 255, 0.8);
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: 18px;
    white-space: nowrap;
  }
}

.tts-input {
  ::v-deep .el-textarea__inner {
    height: 160px;
    padding: 10px;
    color: rgba(255, 255, 255, 0.8);
    font-family: "Microsoft YaHei";
    font-size: 14px;
    letter-spacing: 0.28px;
    background: #142941;
    border: none;
    border-radius: 4px;
    box-shadow: none;
    &::placeholder {
      color: rgba(255, 255, 255, 0.8);
    }
  }
}

.tts-options {
  .voice-radios {
    ::v-deep .el-radio {
      margin-right: 20px;
      color: #FFF;
      .el-radio__label {
        color: #FFF;
        font-family: "Microsoft YaHei";
        font-size: 16px;
        padding-left: 10px;
      }
      .el-radio__inner {
        width: 14px;
        height: 14px;
        background: transparent;
        border: 1px solid #4AB8FF;
        &::after {
          width: 6px;
          height: 6px;
          background: #4AB8FF;
        }
      }
      &.is-checked .el-radio__inner {
        border-color: #4AB8FF;
        background: transparent;
      }
    }
  }
  .ml-auto {
    margin-left: auto;
  }
}

.audio-list {
  max-height: 191px;
  overflow-y: auto;
  padding: 0 10px;
  .audio-item {
    height: 36px;
    padding: 0 10px;
    margin: 0 -10px;
    padding-left: 40px;
    color: #FFF;
    font-family: "Alibaba PuHuiTi";
    font-size: 14px;
    letter-spacing: 0.86px;
    border-radius: 2px;
    cursor: pointer;
    gap: 10px;
    &.active {
      // margin: 0;
      background: #0163C4;
    }
    .music-icon {
      width: 16px;
      height: 16px;
      display: block;
      flex-shrink: 0;
    }
  }
}

.btns {
  ::v-deep .el-button {
    padding: 0;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    background: #021328;
    border-radius: 4px;
    border: none;
    box-shadow: 0 0 14px 2px #09F inset;
    text-align: center;
  }
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
    -webkit-appearance: none;
    appearance: none;
    background: transparent;
    outline: none;
    cursor: pointer;
    margin: 0;
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
      margin-top: -4px;
    }
    &::-moz-range-thumb {
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      cursor: pointer;
      box-shadow: 0 0 10px 2px #09f inset;
    }
  }
}

.mt12 { margin-top: 12px; }
.mt15 { margin-top: 15px; }
.mt16 { margin-top: 16px; }
.mt18 { margin-top: 18px; }
.mt19 { margin-top: 19px; }
.mt20 { margin-top: 20px; }
.mt22 { margin-top: 22px; }
.wp96 { width: 96px; }

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
