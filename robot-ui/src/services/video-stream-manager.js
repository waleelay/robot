import mqttClient from '@/plugins/mqtt-client'

class VideoStreamManager {
  constructor() {
      this.streams = new Map() // 存储所有视频流 { slotKey: streamInfo }
      this.subscribers = new Set() // 订阅视频状态变化的监听器
      this.isMqttConnected = false
      // 监听MQTT连接状态
      mqttClient.onConnectionChange((connected, reconnecting, attempt) => {
        this.isMqttConnected = connected
        this.handleMqttConnectionChange(connected, reconnecting, attempt)
      })
  }

  // ==================== 视频流管理 ====================
  // 注册视频流
  registerStream(slotKey, streamInfo) {
    this.streams.set(slotKey, {
      ...streamInfo,
      status: 'offline', // online, offline, loading, error
      reconnectTimer: null
    })
    this.notifyStreamChange()
  }
  // 更新视频流信息
  updateStream(slotKey, updates) {
    const stream = this.streams.get(slotKey)
    if (stream) {
      Object.assign(stream, updates)
      this.notifyStreamChange()
    }
  }
  // 移除视频流
  removeStream(slotKey) {
    const stream = this.streams.get(slotKey)
    if (stream && stream.reconnectTimer) {
      clearTimeout(stream.reconnectTimer)
    }
    this.streams.delete(slotKey)
    this.notifyStreamChange()
  }
  // 获取视频流信息
  getStream(slotKey) {
    return this.streams.get(slotKey)
  }
  // 获取所有视频流
  getAllStreams() {
    return Array.from(this.streams.values())
  }
  // 获取视频流数量
  getStreamCount() {
    return this.streams.size
  }

  // ==================== MQTT连接状态处理 ====================
  // 处理MQTT连接状态变化
  handleMqttConnectionChange(connected, reconnecting, attempt) {
    console.log(`MQTT connection changed: connected=${connected}, reconnecting=${reconnecting}`)

    if (connected) {
      // MQTT重连成功，重新连接所有视频流
      this.reconnectAllStreams()
    } else {
      // MQTT断开连接，标记所有视频为离线
      this.markAllOffline()
    }
  }
  // 标记所有视频为离线
  markAllOffline() {
    this.streams.forEach((stream, slotKey) => {
      stream.status = 'offline'
      if (stream.onStatusChange) {
        stream.onStatusChange('offline')
      }
    })
    this.notifyStreamChange()
  }

  // 重新连接所有视频流
  reconnectAllStreams() {
    this.streams.forEach((stream, slotKey) => {
      if (stream.deviceId) {
        this.scheduleReconnect(slotKey, stream.deviceId, 0)
      }
    })
  }

  // 调度重新连接
  scheduleReconnect(slotKey, deviceId, delay = 0) {
    const stream = this.streams.get(slotKey)
    if (!stream) return

    // 清除之前的重连定时器
    if (stream.reconnectTimer) {
      clearTimeout(stream.reconnectTimer)
    }

    stream.reconnectTimer = setTimeout(() => {
      if (this.isMqttConnected && stream.deviceId) {
        // 触发重新订阅
        if (stream.onReconnect) {
          stream.onReconnect(deviceId, slotKey)
        }
      }
    }, delay)
  }

  // ==================== 状态监听 ====================

  // 添加视频状态监听器
  onStreamChange(callback) {
    if (typeof callback === 'function') {
      this.subscribers.add(callback)
    }
  }

  // 移除视频状态监听器
  offStreamChange(callback) {
    this.subscribers.delete(callback)
  }
  // 通知所有监听器视频状态变化
  notifyStreamChange() {
    const streamList = Array.from(this.streams.entries()).map(([key, value]) => ({
      key,
      ...value
    }))

    this.subscribers.forEach(subscriber => {
      try {
        subscriber(streamList)
      } catch (err) {
        console.error('Error in stream change subscriber:', err)
      }
    })
  }

  // ==================== 辅助方法 ====================

  // 获取在线视频数量
  getOnlineCount() {
    return Array.from(this.streams.values()).filter(s => s.status === 'online').length
  }

  // 获取离线视频数量
  getOfflineCount() {
    return Array.from(this.streams.values()).filter(s => s.status === 'offline').length
  }

  // 清空所有视频流
  clearAllStreams() {
    this.streams.forEach((stream) => {
      if (stream.reconnectTimer) {
        clearTimeout(stream.reconnectTimer)
      }
    })
    this.streams.clear()
    this.notifyStreamChange()
  }
}

// 单例模式
export default new VideoStreamManager()