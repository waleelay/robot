<template>
  <div>
    <div class="wave-container flx-center hp22" ref="waveContainerRef">
      <div 
        v-for="(bar, index) in bars" 
        :key="index"
        class="wave-bar"
        :style="{
            height: bar.height + 'px',
            boxShadow: bar.boxShadow
        }"
      ></div>
    </div>
  </div>
</template>

<script>
import { createAudioAnalyser, Track } from 'livekit-client';

export default {
  name: 'VolumeWave',
  props: {
    selectCamera: {
      type: Object,
      default: () => ({})
    },
    muted: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    intercomActive() {
      return this.selectCamera.intercomActive
    },
    track() {
      const localParticipant = this.selectCamera?.room?.localParticipant
      if (!localParticipant) return null
      const publication = localParticipant.getTrackPublication
        ? localParticipant.getTrackPublication(Track.Source.Microphone)
        : localParticipant.getTrack && localParticipant.getTrack(Track.Source.Microphone)
      return publication?.track || publication || null
    }
  },
  data() {
    return {
      bars: [],
      // 音频相关
      analyser: null,
      analyserCleanup: null,
      calculateVolume: null,
      dataArray: null,
      animationFrame: null,
      smoothedVolume: 0,
      isIdleWave: true,
      BAR_COUNT: 15,
      MIN_HEIGHT: 8,
      MAX_HEIGHT: 22
    }
  },
  mounted() {
    this.initBars();
    this.startIdleWave();
    this.startMicrophone();
  },
  watch: {
    track() {
      this.startMicrophone();
    },
    intercomActive() {
      this.startMicrophone();
    },
    muted() {
      this.startMicrophone();
    }
  },
  methods: {
    initBars() {
      this.bars = [];
      for (let i = 0; i < this.BAR_COUNT; i++) {
        this.bars.push({
          height: this.MIN_HEIGHT,
          boxShadow: '0 0 12px rgba(35, 178, 255, 0.3), 0 0 6px rgba(35, 178, 255, 0.2)'
        });
      }
    }, 
    // 空闲波动
    startIdleWave() {
      this.isIdleWave = true;
      if (this.animationFrame) {
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = null;
      }
      this.updateIdleWave();
    },
    updateIdleWave() {
      if (!this.isIdleWave) return;
      const time = Date.now() / 1000;
      const bars = this.bars;
      for (let i = 0; i < bars.length; i++) {
        const phase = (i / bars.length) * Math.PI * 2;
        const wave = Math.sin(time * 1.5 + phase) * 0.5 + 0.5;
        const height = this.MIN_HEIGHT + (this.MAX_HEIGHT - this.MIN_HEIGHT) * (0.2 + 0.3 * wave);
        const bright = 0.5 + 0.5 * wave;
        bars[i].height = height;
        bars[i].boxShadow = `0 0 ${20 * bright}px rgba(35, 178, 255, ${0.4 * bright}), 0 0 ${10 * bright}px rgba(35, 178, 255, ${0.3 * bright})`;
      }
      this.animationFrame = requestAnimationFrame(this.updateIdleWave);
    },
    stopIdleWave() {
      this.isIdleWave = false;
      if (this.animationFrame) {
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = null;
      }
    },
    // 更新柱子（麦克风模式）
    updateBars(volume) {
      const range = this.MAX_HEIGHT - this.MIN_HEIGHT;
        const baseHeight = this.MIN_HEIGHT + range * Math.min(volume, 1);
        const bars = this.bars;
        const now = Date.now();
        
        for (let i = 0; i < bars.length; i++) {
          // 每个柱子有细微差异，模拟不同频段
          const offset = 0.6 + 0.4 * Math.sin(i * 0.7 + now * 0.002);
          let individualHeight = baseHeight * (0.7 + 0.3 * (i / bars.length));
          const jitter = 0.92 + 0.16 * Math.sin(i * 1.1 + performance.now() * 0.005);
          individualHeight = individualHeight * jitter;
          individualHeight = Math.min(this.MAX_HEIGHT, Math.max(this.MIN_HEIGHT, individualHeight));
          
          const bright = 0.4 + 0.6 * (individualHeight / this.MAX_HEIGHT);
          bars[i].height = individualHeight;
          bars[i].boxShadow = `0 0 ${10 * bright}px rgba(35, 178, 255, ${0.3 * bright}), 0 0 ${4 * bright}px rgba(35, 178, 255, ${0.2 * bright})`;
        }
    },
    // 处理音频
    processAudio() {
      if (!this.analyser) return;
      let rawVolume = this.calculateVolume ? this.calculateVolume() : 0
      if (!rawVolume) {
        this.analyser.getByteFrequencyData(this.dataArray);
        let sum = 0;
        for (let i = 0; i < this.dataArray.length; i++) {
          sum += this.dataArray[i] * this.dataArray[i];
        }
        rawVolume = Math.sqrt(sum / this.dataArray.length) / 255;
      }
      let volume = Math.pow(rawVolume, 0.65);
      volume = Math.min(volume, 1.0);
      
      this.smoothedVolume = this.smoothedVolume * 0.7 + volume * 0.3;
      const finalVolume = Math.max(this.smoothedVolume, 0.03);
      this.updateBars(finalVolume);
      
      this.animationFrame = requestAnimationFrame(this.processAudio);
    },
    // 启动麦克风
    startMicrophone() {
      if (!this.intercomActive || this.muted || !this.track) {
        this.stopMicrophone();
        return;
      }
      this.stopAudioAnalyser();
      let audioAnalyser;
      try {
        audioAnalyser = createAudioAnalyser(this.track);
      } catch (error) {
        this.startIdleWave();
        return;
      }
      this.analyser = audioAnalyser.analyser;
      this.calculateVolume = audioAnalyser.calculateVolume;
      this.analyserCleanup = audioAnalyser.cleanup;
      this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);

      this.stopIdleWave();
      this.smoothedVolume = 0.08;
      this.processAudio();
    },
    // 停止麦克风
    stopMicrophone() {
      this.stopAudioAnalyser();
      this.startIdleWave();
    },
    stopAudioAnalyser() {
      if (this.animationFrame) {
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = null;
      }
      if (this.analyserCleanup) {
        this.analyserCleanup();
      }
      this.analyser = null;
      this.analyserCleanup = null;
      this.calculateVolume = null;
      this.dataArray = null;
    },
    cleanup() {
      this.stopAudioAnalyser();
    }
  },
  beforeDestroy() {
    this.cleanup();
  }
}
</script>

<style lang="scss" scoped>
  .wave-container {
    gap: 3px;
    height: 22px;
    // width: 70px;
    // background: rgba(0, 0, 0, 0.3);
    border-radius: 80px;
    // box-shadow: inset 0 4px 12px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.03);
    .wave-bar {
      width: 2px;
      height: 22px;
      background: #23B2FF;
      border-radius: 20px;
      box-shadow: 0 0 10px rgba(35, 178, 255, 0.6), 0 0 8px rgba(35, 178, 255, 0.3);
      transform-origin: bottom;
      transition: height 0.08s ease-out;
      flex-shrink: 0;
    }
    .wave-bar:nth-child(odd) {
      background: #23B2FF;
      box-shadow: 0 0 12px rgba(35, 178, 255, 0.7), 0 0 10px rgba(35, 178, 255, 0.4);
    }
    .wave-bar:nth-child(3n) {
      background: #3abfff;
      box-shadow: 0 0 16px rgba(35, 178, 255, 0.8), 0 0 12px rgba(70, 190, 255, 0.4);
    }
    .wave-bar:nth-child(5n+2) {
      background: #1aa3e8;
      box-shadow: 0 0 10px rgba(30, 160, 230, 0.7), 0 0 8px rgba(35, 178, 255, 0.5);
    }
  }
</style>
