import Cookies from 'js-cookie';
import resampler from 'wave-resampler';

/**
 * 语音WebSocket客户端
 * 负责与服务器建立WebSocket连接，处理音频采集、编码、发送和接收播放
 */
export class VoiceWebSocket {
  /**
   * 构造函数
   * @param {string} clientId - 客户端ID
   * @param {Object} config - 配置对象
   */
  constructor(clientId = 'A1', config = {}) {
    const defaultConfig = {
      serverUrl: 'ws://192.168.1.133:9000/voice',
      targetId: 'C',
      reconnectInterval: 3000,
      maxReconnectAttempts: 5,
      audioGain: 1, // 录音增益10倍
      playbackGain: 1, // 播放增益10倍
      silenceThreshold: 100
    };
    this.config = { ...defaultConfig, ...config };
    this.clientId = clientId;
    this.websocket = null;
    this.reconnectAttempts = 0;
    this.isConnected = false;
    this.isRecording = false;
    this.audioContext = null;
    this.audioStream = null;
    this.sampleRate = 48000;
    this.channels = 1;
    this.audioProcessor = null;
    this.inputGainNode = null;
    this.retryCount = 0;
    this.maxRetries = 5;
    this.retryInterval = 2000;
    // 播放侧：参照本地 AudioPlayer 的“队列缓冲 + 丢旧包 + 循环消费”
    this.playQueue = [];
    this.isPlaying = false;
    this.playheadTime = 0; // WebAudio排程时间轴（用于连续排程避免断续）
    this.outputGainNode = null;
    this._playbackTimer = null;
    // 低延迟优先：限制排程前瞻，超限时主动追帧
    this.maxHeadroomSec = 0.35;
    this.handleOpen = this.handleOpen.bind(this);
    this.handleMessage = this.handleMessage.bind(this);
    this.handleClose = this.handleClose.bind(this);
    this.handleError = this.handleError.bind(this);
  }

  /**
   * 初始化WebSocket连接
   */
  init() {
    try {
      if (this.websocket) {
        this.close();
      }
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)({
        sampleRate: this.sampleRate
      });
      this.outputGainNode = this.audioContext.createGain();
      this.outputGainNode.gain.value = this.config.playbackGain || 1;
      this.outputGainNode.connect(this.audioContext.destination);
      this.playheadTime = 0;
      this.websocket = new WebSocket(this.config.serverUrl);
      this.websocket.onopen = this.handleOpen;
      this.websocket.onmessage = this.handleMessage;
      this.websocket.onclose = this.handleClose;
      this.websocket.onerror = this.handleError;
      this.websocket.binaryType = 'arraybuffer';
    } catch (error) {
      this.handleError(error);
    }
  }

  /**
   * WebSocket连接打开处理
   */
  handleOpen() {
    this.isConnected = true;
    this.reconnectAttempts = 0;
    this.retryCount = 0;
    this.sendMessage({
      type: 'register',
      clientId: this.clientId,
      targetId: 'none'
    });
    if (this.config.onOpen) this.config.onOpen();
  }

  /**
   * WebSocket消息处理
   * @param {MessageEvent} event - 消息事件
   */
  async handleMessage(event) {
    try {
      if (event.data instanceof ArrayBuffer) {
        await this.enqueuePcmForPlayback(event.data);
      } else if (typeof event.data === 'string') {
        let message = JSON.parse(event.data);
        if (message.sampleRate) this.sampleRate = message.sampleRate;
        if (message.channels) this.channels = message.channels;
        if (message.type === 'register' && message.status === 'success') {
          // 注册成功
        } else if (message.type === 'connect' && message.status === 'success') {
          this.retryCount = 0;
          if (this.config.onConnect) this.config.onConnect(message.targetId);
        } else if (message.type === 'connect' && message.status === 'error') {
          if (this.retryCount < this.maxRetries) {
            this.retryCount++;
            setTimeout(() => this.connect(), this.retryInterval);
          } else {
            if (this.config.onError) this.config.onError(new Error(message.message));
          }
        }
      }
    } catch (error) {
      if (this.config.onError) this.config.onError(error);
    }
  }

  /**
   * WebSocket连接关闭处理
   */
  handleClose() {
    this.isConnected = false;
    this.stopRecording();
    if (this.reconnectAttempts < this.config.maxReconnectAttempts) {
      this.reconnectAttempts++;
      setTimeout(() => this.init(), this.config.reconnectInterval);
    }
    if (this.config.onClose) this.config.onClose();
  }

  /**
   * WebSocket错误处理
   * @param {Error} error - 错误对象
   */
  handleError(error) {
    this.isConnected = false;
    if (this.config.onError) this.config.onError(error);
  }

  /**
   * 发送消息
   * @param {Object|ArrayBuffer} message - 消息内容（JSON对象或二进制数据）
   */
  sendMessage(message) {
    if (this.websocket && this.isConnected) {
      try {
        if (message instanceof ArrayBuffer) {
          this.websocket.send(message);
        } else {
          this.websocket.send(JSON.stringify(message));
        }
      } catch (error) {
        if (this.config.onError) this.config.onError(error);
      }
    }
  }

  /**
   * 连接到目标设备
   * @param {string} targetId - 目标设备ID
   */
  connect(targetId) {
    this.sendMessage({
      type: 'connect',
      clientId: this.clientId,
      targetId: targetId || Cookies.get('targetId')
    });
  }

  /**
   * 断开连接
   * @param {string} targetId - 目标设备ID
   */
  disconnect(targetId) {
    this.sendMessage({
      type: 'disconnect',
      clientId: this.clientId,
      targetId
    });
  }

  /**
   * 开始录音
   * @param {string} targetId - 目标设备ID
   */
  async startRecording(targetId) {
    if (this.isRecording) {
      return;
    }
    this.playQueue = [];
    try {
      this.connect(targetId);
      if (this.audioContext) {
        await this.audioContext.close();
      }
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)({
        sampleRate: this.sampleRate
      });
      this.outputGainNode = this.audioContext.createGain();
      this.outputGainNode.gain.value = this.config.playbackGain || 1;
      this.outputGainNode.connect(this.audioContext.destination);
      this.playheadTime = 0;
      this.audioStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: { ideal: this.sampleRate, exact: this.sampleRate },
          channelCount: { exact: this.channels },
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: true,
          latency: 0.02
        }
      });
      const source = this.audioContext.createMediaStreamSource(this.audioStream);
      this.inputGainNode = this.audioContext.createGain();
      this.inputGainNode.gain.value = this.config.audioGain || 1;
      this.audioProcessor = this.audioContext.createScriptProcessor(1024, this.channels, this.channels);
      this.audioProcessor.onaudioprocess = (event) => {
        // 获取麦克风采集的音频数据（Float32格式，范围-1.0到1.0）
        const inputData = event.inputBuffer.getChannelData(0);
        let processedData = inputData;

        // 重采样：如果浏览器实际采样率与目标不一致，强制转换为48000Hz
        if (this.audioContext.sampleRate !== this.sampleRate) {
          processedData = resampler.resample(inputData, this.audioContext.sampleRate, this.sampleRate, { channels: this.channels });
        }

        // 转换为16bit PCM：Float32转Int16（范围-32768到32767）
        const pcmData = new Int16Array(processedData.length);
        let isNonZero = false;
        for (let i = 0; i < processedData.length; i++) {
          // Float32转Int16：乘以32768并限制范围
          pcmData[i] = Math.max(-32768, Math.min(32767, processedData[i] * 32768));
          // 检测是否有有效音频（非静音）
          if (Math.abs(pcmData[i]) > this.config.silenceThreshold) {
            isNonZero = true;
          }
        }

        // 转换为小端序字节数组：每个Int16占2字节，低字节在前
        const byteArray = new Uint8Array(pcmData.length * 2);
        for (let i = 0; i < pcmData.length; i++) {
          byteArray[i * 2] = pcmData[i] & 0xff;        // 低字节
          byteArray[i * 2 + 1] = (pcmData[i] >> 8) & 0xff; // 高字节
        }

        // 发送有效音频数据（偶数长度且非静音）
        if (byteArray.buffer.byteLength % 2 === 0 && isNonZero) {
          this.sendMessage(byteArray.buffer);
        }
      };
      source.connect(this.inputGainNode);
      this.inputGainNode.connect(this.audioProcessor);
      this.audioProcessor.connect(this.audioContext.destination);
      this.isRecording = true;
    } catch (error) {
      this.isRecording = false;
      if (this.config.onError) this.config.onError(error);
      throw error;
    }
  }

  /**
   * 停止录音
   * @param {string} targetId - 目标设备ID
   */
  stopRecording(targetId) {
    if (this.isRecording) {
      if (this.audioStream) {
        this.audioStream.getTracks().forEach(track => track.stop());
        this.audioStream = null;
      }
      if (this.audioProcessor) {
        this.audioProcessor.disconnect();
        this.audioProcessor = null;
      }
      if (this.inputGainNode) {
        this.inputGainNode.disconnect();
        this.inputGainNode = null;
      }
      this.isRecording = false;
      this.playQueue = [];
      this.isPlaying = false;
      this.playheadTime = 0;
      if (this._playbackTimer) {
        clearTimeout(this._playbackTimer);
        this._playbackTimer = null;
      }
      this.disconnect(targetId);
    }
  }

  /**
   * 入队并触发播放（16bit PCM little-endian）
   */
  async enqueuePcmForPlayback(arrayBuffer) {
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)({
        sampleRate: this.sampleRate
      });
      this.outputGainNode = this.audioContext.createGain();
      this.outputGainNode.gain.value = this.config.playbackGain || 1;
      this.outputGainNode.connect(this.audioContext.destination);
    }
    try {
      if (arrayBuffer.byteLength % 2 !== 0) return;

      // 队列过大丢弃最旧的，避免延迟无限增长（参照本地 AudioPlayer）
      while (this.playQueue.length >= 60) this.playQueue.shift();
      this.playQueue.push(arrayBuffer);

      // 若当前排程已明显超前，说明出现累计延迟：主动丢弃旧包并重置时间轴追帧。
      const now = this.audioContext.currentTime;
      const headroom = this.playheadTime - now;
      if (headroom > this.maxHeadroomSec) {
        // 保留最近的小段数据，快速追到实时附近
        while (this.playQueue.length > 8) this.playQueue.shift();
        this.playheadTime = now + 0.06;
      }

      if (!this.isPlaying) {
        this.isPlaying = true;
        this._schedulePlayback();
      }
    } catch (error) {
      // 播放失败静默处理
    }
  }

  _pcm16leToFloat32(arrayBuffer) {
    const dv = new DataView(arrayBuffer);
    const len = dv.byteLength / 2;
    const out = new Float32Array(len);
    for (let i = 0; i < len; i++) {
      const s = dv.getInt16(i * 2, true); // little-endian
      out[i] = s / 32768;
    }
    return out;
  }

  _schedulePlayback() {
    if (!this.audioContext) {
      this.isPlaying = false;
      return;
    }
    // 尽量恢复音频上下文（部分浏览器需要用户手势；失败就下次再试）
    if (this.audioContext.state === 'suspended') {
      this.audioContext.resume().catch(() => {});
    }

    const channels = this.channels || 1;
    const sampleRate = this.sampleRate || 48000;
    const now = this.audioContext.currentTime;

    // 初始化播放时间轴：预留一点缓冲，抗网络抖动
    if (this.playheadTime < now) {
      this.playheadTime = now + 0.06; // 60ms 起播缓冲（可按需调）
    }
    // 若排程过于超前，直接回拉到低延迟窗口，避免“越播越慢”
    if (this.playheadTime - now > this.maxHeadroomSec) {
      this.playheadTime = now + 0.06;
    }

    // 一次性尽可能排程，避免“等上一段播放完才排下一段”造成空洞
    let scheduled = 0;
    while (this.playQueue.length > 0) {
      const arrayBuffer = this.playQueue.shift();
      const floats = this._pcm16leToFloat32(arrayBuffer);
      const frameLength = floats.length;
      if (frameLength <= 0) continue;

      const audioBuffer = this.audioContext.createBuffer(channels, frameLength, sampleRate);
      audioBuffer.getChannelData(0).set(floats);

      const source = this.audioContext.createBufferSource();
      source.buffer = audioBuffer;
      if (this.outputGainNode) {
        source.connect(this.outputGainNode);
      } else {
        source.connect(this.audioContext.destination);
      }

      const startAt = this.playheadTime;
      source.start(startAt);
      this.playheadTime = startAt + frameLength / sampleRate;
      scheduled++;

      // 低延迟模式：每轮适度排程，避免一次性排太多拉大前瞻
      if (scheduled >= 12) break;
    }

    if (this.playQueue.length === 0) {
      // 如果后续一段时间都没有新数据，就停止调度循环
      const headroom = this.playheadTime - this.audioContext.currentTime;
      if (headroom <= 0.08) {
        this.isPlaying = false;
        this._playbackTimer = null;
        return;
      }
    }

    // 继续调度：在“剩余缓冲”较低时更频繁触发
    const headroom = this.playheadTime - this.audioContext.currentTime;
    const nextInMs = headroom < 0.1 ? 8 : 18;
    this._playbackTimer = setTimeout(() => this._schedulePlayback(), nextInMs);
  }

  /**
   * 关闭连接和资源
   */
  close() {
    this.stopRecording();
    if (this.websocket) {
      this.websocket.onclose = null;
      this.websocket.close();
      this.websocket = null;
    }
    if (this.audioContext) {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }
    this.outputGainNode = null;
    this.playQueue = [];
    this.isPlaying = false;
    this.playheadTime = 0;
    if (this._playbackTimer) {
      clearTimeout(this._playbackTimer);
      this._playbackTimer = null;
    }
    this.isConnected = false;
  }
}
