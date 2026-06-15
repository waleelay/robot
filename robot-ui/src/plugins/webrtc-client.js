import JsWebRTC from 'jswebrtc' // 假设使用的是jswebrtc库

class WebRTCGlobalClient {
  constructor() {
    this.webrtc = null
    this.streams = new Map() // 存储视频流 { streamId: MediaStream }
    this.connections = new Map() // 存储连接 { streamId: connection }
    this.streamCallbacks = new Map() // 存储流回调
  }

  // 初始化WebRTC
  init(config) {
    if (this.webrtc) return this.webrtc
    
    const defaultConfig = {
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' }
      ]
    }
    
    this.webrtc = new JsWebRTC({ ...defaultConfig, ...config })
    return this.webrtc
  }

  // 通过MQTT消息建立视频流
  async createVideoStream(streamId, mqttMessage) {
    if (this.streams.has(streamId)) {
      return this.streams.get(streamId)
    }

    // 解析MQTT消息中的SDP信息
    const sdpInfo = JSON.parse(mqttMessage)
    
    // 创建PeerConnection
    const pc = new RTCPeerConnection(this.webrtc.config)
    this.connections.set(streamId, pc)

    // 接收远程流
    pc.ontrack = (event) => {
      const stream = event.streams[0]
      this.streams.set(streamId, stream)
      
      // 触发回调
      if (this.streamCallbacks.has(streamId)) {
        this.streamCallbacks.get(streamId)(stream)
      }
    }

    // 设置远程SDP
    await pc.setRemoteDescription(new RTCSessionDescription(sdpInfo.sdp))
    
    // 创建Answer
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    
    // 发送Answer到MQTT
    return answer
  }

  // 注册流回调
  onStream(streamId, callback) {
    this.streamCallbacks.set(streamId, callback)
    
    // 如果流已经存在，立即回调
    if (this.streams.has(streamId)) {
      callback(this.streams.get(streamId))
    }
  }

  // 移除流
  removeStream(streamId) {
    const pc = this.connections.get(streamId)
    if (pc) {
      pc.close()
      this.connections.delete(streamId)
    }
    this.streams.delete(streamId)
    this.streamCallbacks.delete(streamId)
  }

  // 清理所有连接
  destroy() {
    this.connections.forEach(pc => pc.close())
    this.connections.clear()
    this.streams.clear()
    this.streamCallbacks.clear()
    if (this.webrtc) {
      this.webrtc.destroy()
      this.webrtc = null
    }
  }
}

export default new WebRTCGlobalClient()