
import { controlVoice, controlVoiceStatus } from "../../../../api/bi";
export default {
  data() {
    return {
      // 当前音量值 原声音*100，视频音量0-1
      currentVolume: 100,
      // 是否静音
      isMuted: false,
      // 之前的音量值（用于取消静音）
      previousVolume: 100,
      // 是否显示脉冲动画
      isPulsing: false,
      // 是否需要考虑上下键跟机器狗操作键的冲突
      visible: false,
      // 打开/关闭麦克风
      openMic: true,
      isTalk: false,
      isDisabled: false
    }
  },
  props: {
    endpoint: String,
    dogId: String
  },
  computed: {
    // 音量显示值
    volumeValue() {
      const cur  = Number(this.currentVolume)
      return (cur === 0 || this.isMuted) ? '扬声器：静音' : `扬声器：${cur}%`;
    },
    // 音量图标类，根据音量值动态计算
    volumeIconClass() {
      const cur  = Number(this.currentVolume)
      if (cur === 0 || this.isMuted) {
        return 'volume-mute';
      } else if (cur < 50) {
        return 'volume-l';
      } else {
        return 'volume';
      }
    },
    // 语音系统是否连接
    voiceConnect() {
      return this.$store.getters['voiceCall/getConnected'];
    },
    // 语音websocket
    voiceWebSocket() {
      return this.$store.getters['voiceCall/getVoiceWebSocket'];
    },
  },
  methods: {
    // 更新音量
    updateVolume() {
      const cur  = Number(this.currentVolume)
      // 触发脉冲动画
      // this.triggerPulse();
      // 如果音量为0，自动静音
      if (cur === 0) {
        this.isMuted = true;
      } else {
        this.isMuted = false;
      }
    },
    // 键盘事件处理
    handleKeydown(event) {
      event.preventDefault();
      switch (event.key) {
        case 'ArrowUp':
          this.handleControlVoice('VOLUME_UP')
          break;
        case 'ArrowDown':
          this.handleControlVoice('VOLUME_DOWN')
          break;
        case 'm':
        case 'M':
          this.handleControlVoice('MUTE')
          break;
        default:
          break;
      }
    },
    addEventListeners() {
      document.addEventListener('keydown', this.handleKeydown)
    },
    removeEventListeners() {
      document.removeEventListener('keydown', this.handleKeydown)
    },

    // 对讲处理
    // 语音、云台
    async handleTalk() {
      if (this.isDisabled) {
        this.$message.warning('请勿重复点击！间隔3s后可再次点击。');
        return;
      }
      this.isTalk = !this.isTalk;
      this.isDisabled = true;
      setTimeout(() => {
        this.isDisabled = false;
        if (!this.voiceConnect) {
          this.isTalk = false
          this.$message.error('语音设备连接失败')
        }
      }, 3000);

      if (this.voiceConnect) {
        if (this.isTalk) {
          const res = await controlVoiceStatus('on', 'endpoint')
           console.log('%c打开语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          const res = await controlVoiceStatus('off', 'endpoint')
          console.log('%c关闭语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.stopRecording();
          this.voiceWebSocket.sendMessage({ type: 'stopTalk', clientId: this.voiceWebSocket.clientId });
        }
      }
    },
    // 开启对讲
    async startRecording() {
      try {
        await this.voiceWebSocket.startRecording('dogId');
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    // 关闭对讲
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording('dogId');
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
    // 麦克风控制
    handleControlVoice(command) {
      if (!this.isTalk) return
      controlVoice(command, 'endpoint'.split(':')[0]).then(res => {
        if (res.code === 200) {
          this.currentVolume = res.data.volume
          this.isMuted = res.data.isMuted
        }
      })
    },
  },
  mounted() {
    // TODO: 建立对讲连接后才可监听
    // 添加事件监听
    this.addEventListeners()
    // 音量
    // 初始音量设置
    // TODO:是否静音，从父组件获取或者其他
    // this.isMuted = this.muted;
    this.currentVolume = 0.2 * 100;
    this.updateVolume();
    // TODO: 添加键盘事件监听 统一写在addEventListeners方法里
    window.addEventListener('resize', this.resizeChange);
  },
  beforeDestroy() {
    // 移除键盘事件监听
    this.removeEventListeners();
    window.removeEventListener('resize', this.resizeChange);
    // 清理资源
    if (this.voiceConnect) {
      this.voiceWebSocket.stopRecording('endpoint');
    }
    // 清理资源
    if (this.voiceWebSocket) {
      this.voiceWebSocket.close();
    }
  }
}