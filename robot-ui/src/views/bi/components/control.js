export default {
  data() {
    return {
      // 设备状态：online/offline
      deviceStatus: 'offline',
      // 当前倍率（只读）
      currentMultiple: 1,
      // 当前速度系数（只读）
      currentSliderValue: 100,
      // WebSocket实例
      ws: null,
      // 心跳定时任务映射（deviceId -> timerId）
      heartbeatTimerMap: {}
    }
  },
  created() {
    // 初始化设备列表 + WebSocket
    this.initWebSocket()
  },
  mounted() {
    // 仅监听键盘事件（核心）
    window.addEventListener('keydown', this.handleKeyDown)
    window.addEventListener('keyup', this.handleKeyUp)
    // 页面关闭/刷新兜底清理
    window.addEventListener('beforeunload', this.handlePageUnload)
  },
  beforeDestroy() {
    // 清理所有资源
    window.removeEventListener('keydown', this.handleKeyDown)
    window.removeEventListener('keyup', this.handleKeyUp)
    window.removeEventListener('beforeunload', this.handlePageUnload)
    this.clearAllHeartbeatTimer()
    if (this.ws) this.ws.close(1000, '页面关闭')
  },
  methods: {
    /**
     * 初始化WebSocket（仅用于键盘指令传输）
     */
    initWebSocket() {
      if (this.ws) this.ws.close()
      const wsUrl = process.env.NODE_ENV === 'development' ? process.env.VUE_APP_WEBSOCKET_URL1 : `wss://${location.hostname}/dogControl`
      this.ws = new WebSocket(wsUrl)

      // 连接成功：自动启动当前设备心跳
      this.ws.onopen = () => {
        this.addLog('WebSocket连接成功')
        if (this.dogId) this.startDeviceHeartbeat(this.dogId)
      }

      // 接收消息：仅更新状态/日志
      this.ws.onmessage = (e) => {
        const data = JSON.parse(e.data)
        if (data.type === 'status' && data.deviceId === this.dogId) {
          // this.currentMultiple = data.multiple
          // this.currentSliderValue = data.sliderValue
          this.deviceStatus = data.status
        }
        if (data.type === 'log') this.addLog(data.message)
      }

      // 连接关闭：清理心跳
      this.ws.onclose = (e) => {
        this.addLog(`WebSocket关闭：${e.reason}`)
        this.clearAllHeartbeatTimer()
        // 自动重连（页面未关闭时）
        if (document.visibilityState === 'visible') setTimeout(() => this.initWebSocket(), 3000)
      }

      // 连接错误：仅日志
      this.ws.onerror = (err) => this.addLog(`WebSocket错误：${err.message}`)
    },

    // ======================== 2. 心跳自动管理（无手动操作） ========================
    /**
     * 启动设备心跳（仅自动触发）
     */
    startDeviceHeartbeat(deviceId) {
      this.stopDeviceHeartbeat(deviceId) // 防止重复
      if (this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'startHeartbeat', deviceId }))
        // 前端心跳检测（每2秒）
        this.heartbeatTimerMap[deviceId] = setInterval(() => {
          // TODO:
          if (this.deviceStatus === 'offline' && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: 'startHeartbeat', deviceId }))
            this.addLog(`【心跳检测】设备${deviceId}离线，重新启动`)
          }
        }, 2000)
        this.addLog(`设备${deviceId}心跳自动启动`)
      }
    },

    /**
     * 停止设备心跳（仅自动触发）
     */
    stopDeviceHeartbeat(deviceId) {
      if (this.heartbeatTimerMap[deviceId]) {
        clearInterval(this.heartbeatTimerMap[deviceId])
        delete this.heartbeatTimerMap[deviceId]
      }
      if (this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'stopHeartbeat', deviceId }))
      }
      this.addLog(`设备${deviceId}心跳自动停止`)
    },

    /**
     * 清理所有心跳任务
     */
    clearAllHeartbeatTimer() {
      Object.keys(this.heartbeatTimerMap).forEach(id => this.stopDeviceHeartbeat(id))
      this.heartbeatTimerMap = {}
    },

    // ======================== 3. 核心：键盘事件监听（仅这部分是主动操作） ========================
    /**
     * 键盘按下：仅发送指令，无其他操作
     */
    handleKeyDown(e) {
      if (!this.dogId) {
        this.addLog('请先选择设备再操作键盘')
        return
      }
      // 仅发送键盘按下指令
      if (this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({
          type: 'keyboard',
          deviceId: this.dogId,
          key: e.key,
          event: 'down'
        }))
        this.addLog(`键盘按下：${e.key}`)
      } else {
        this.addLog('WebSocket未连接，键盘指令发送失败')
      }
    },

    /**
     * 键盘释放：仅发送指令，无其他操作
     */
    handleKeyUp(e) {
      if (!this.dogId) return
      // 仅发送键盘释放指令
      if (this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({
          type: 'keyboard',
          deviceId: this.dogId,
          key: e.key,
          event: 'up'
        }))
        this.addLog(`键盘释放：${e.key}`)
      }
    },


    /**
     * 页面关闭兜底
     */
    handlePageUnload() {
      this.clearAllHeartbeatTimer()
      if (this.ws) this.ws.close()
    },

    /**
     * 添加日志（仅键盘/心跳相关）
     */
    addLog(message) {
      const time = new Date().toLocaleTimeString()
      console.log(`[${time}] ${message}`);
      
      // this.logList.unshift(`[${time}] ${message}`)
      // // 仅保留最新50条日志
      // if (this.logList.length > 50) this.logList.pop()
    }
  }
}