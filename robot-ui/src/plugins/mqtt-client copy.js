/*
 * @Author: dengxumei
 * @Date: 2026-05-14 10:18:04
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-05-15 09:23:08
 * @Description: MQTT全局客户端 - 单例模式，统一管理MQTT连接
 * @FilePath: \qihang-eiop-ui\src\plugins\mqtt-client.js
 * @Version: 
 */
import mqtt from "mqtt"

class MQTTGlobalClient {
  constructor() {
    this.client = null
    this.subscribers = new Map() // 存储订阅回调 { topic: [callbacks] }
    this.connectionListeners = new Set() // 连接状态监听器
    this.isConnected = false
    this.pendingSubscriptions = [] // 待订阅主题
    this.reconnectAttempts = 0 // 重连尝试次数
    this.maxReconnectAttempts = 10 // 最大重连次数
  }
  // ==================== 连接管理 ====================
  // 连接MQTT
  connect(options) {
    return new Promise((resolve, reject) => {
      if (this.client && this.isConnected) {
        resolve(this.client)
        return
      }
      const defaultOptions = {
        // host: `wss://${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_HOST.replaceAll("'", '') : location.host}/ws/mqtt`,
        host: 'ws://192.168.124.94:8083/mqtt',
        clientId: 'mqtt_client_' + +new Date(),
        // username: 'your-username',
        // password: 'your-password',
        keepalive: 60,
        // 5秒后重连
        reconnectPeriod: 5000, // 5秒自动重连
        connectTimeout: 10000
      }
      this.client = mqtt.connect(options?.host || defaultOptions.host, {
        ...defaultOptions,
        ...options
      })
      // 连接成功后，处理待订阅的主题
      this.client.on('connect', () => {
        console.log('MQTT connected')
        this.isConnected = true
        this.reconnectAttempts = 0
        // 通知所有监听器连接成功
        this.notifyConnectionChange(true)
        // 处理待订阅的主题
        this.pendingSubscriptions.forEach(({ topic, callback }) => {
          this.subscribe(topic, callback)
        })
        this.pendingSubscriptions = []
        resolve(this.client)
      })
      // 收到消息
      this.client.on('message', (topic, message) => {
        // 分发给订阅者
        const callbacks = this.subscribers.get(topic) || []
        callbacks.forEach(cb => {
          try {
            cb(topic, message.toString())
          } catch (err) {
            console.error(`Error in callback for topic ${topic}:`, err)
          }
        })
      })
      this.client.on('reconnect', () => {
        // 正在重新连接
        console.log('正在重新连接')
        this.reconnectAttempts++
        console.log(`MQTT reconnecting... (attempt ${this.reconnectAttempts})`)
        this.notifyReconnecting(this.reconnectAttempts)
      })
      this.client.on('error', (err) => {
        console.error('MQTT error:', err)
        this.isConnected = false
        this.notifyConnectionChange(false)
        reject(err)
      })
      this.client.on('close', () => {
        console.error('连接断开')
        // 监听断开，将自动重连
        this.isConnected = false
        this.notifyConnectionChange(false)
      })
      // 连接断开（手动或意外）
      this.client.on('disconnect', (packet) => {
        console.warn('MQTT disconnected:', packet)
        this.isConnected = false
        this.notifyConnectionChange(false)
      })
    })
  }
  // 断开连接
  disconnect() {
    if (this.client) {
      this.subscribers.clear()
      this.client.end()
      this.isConnected = false
      this.notifyConnectionChange(false)
    }
  }
  // ==================== 订阅管理 ====================
  // 订阅主题
  subscribe(topic, callback) {
    if (!this.isConnected) {
      this.pendingSubscriptions.push({ topic, callback })
      return
    }
    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, [])
    }
    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, [])
      // 首次订阅该主题
      this.client.subscribe(topic, { qos: 0 }, (err) => {
        if (err) {
          console.error(`Subscribe to ${topic} failed:`, err)
        } else {
          console.log(`Subscribed to topic: ${topic}`)
        }
      })
    }
    this.subscribers.get(topic).push(callback)
    this.client.subscribe(topic, (err) => {
      if (err) console.error(`Subscribe to ${topic} failed:`, err)
    })
  }
  // 取消订阅
  unsubscribe(topic, callback) {
    if (!this.subscribers.has(topic)) return
    const callbacks = this.subscribers.get(topic)
    if (callback) {
      // 移除特定回调
      const index = callbacks.indexOf(callback)
      if (index > -1) {
        callbacks.splice(index, 1)
      }
    } else {
      // 移除所有回调
      this.subscribers.delete(topic)
    }
    // 如果该主题没有回调了，取消MQTT订阅
    if (!callback && this.client) {
      this.client.unsubscribe(topic, (err) => {
        if (err) console.error(`Unsubscribe from ${topic} failed:`, err)
      })
    }
  }
  // 发布消息
  publish(topic, message, options = {}) {
    if (this.isConnected) {
      this.client.publish(topic, message, options)
    } else {
      console.warn('MQTT not connected, cannot publish')
    }
  }
  // 断开连接
  disconnect() {
    if (this.client) {
      this.subscribers.clear()
      this.client.end()
      this.isConnected = false
    }
  }
  // ==================== 连接状态监听 ====================
  // 添加连接状态监听器
  onConnectionChange(callback) {
    if (typeof callback === 'function') {
      this.connectionListeners.add(callback)
      // 立即调用一次，返回当前状态
      callback(this.isConnected)
    }
  }
  // 移除连接状态监听器
  offConnectionChange(callback) {
    this.connectionListeners.delete(callback)
  }
  // 通知连接状态变化
  notifyConnectionChange(isConnected) {
    this.connectionListeners.forEach(listener => {
      try {
        listener(isConnected)
      } catch (err) {
        console.error('Error in connection listener:', err)
      }
    })
  }
  // 通知正在重连
  notifyReconnecting(attempt) {
    this.connectionListeners.forEach(listener => {
      try {
        listener(false, true, attempt)
      } catch (err) {
        console.error('Error in connection listener:', err)
      }
    })
  }
  // ==================== 工具方法 ====================
  // 获取连接状态
  getConnectionStatus() {
    return {
      isConnected: this.isConnected,
      reconnectAttempts: this.reconnectAttempts,
      maxReconnectAttempts: this.maxReconnectAttempts
    }
  }
  // 检查是否已订阅某主题
  isSubscribed(topic) {
    return this.subscribers.has(topic)
  }
}

// 单例模式
export default new MQTTGlobalClient()