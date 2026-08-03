<template>
  <div style="display: none;"></div>
</template>

<script>
import { acquireControl, setControlMode, takeoverControl } from '../../../../../../api/media'

export default {
  name: 'ControlModeWarning',
  data() {
    return {
      loading: false,
      timer: null,
      robotId: '',
      controlMode: '',
      controlModeName: ''
    }
  },
  methods: {
    hasOtherDialogOpen() {
      const dialogs = document.querySelectorAll('.el-dialog__wrapper')
      for (let i = 0; i < dialogs.length; i++) {
        const el = dialogs[i]
        if (el.style.display === 'none') continue
        const style = window.getComputedStyle(el)
        if (style.display !== 'none' && style.visibility !== 'hidden') {
          return true
        }
      }
      // 地图装备控制等非 el-dialog 弹层
      const panels = document.querySelectorAll('.machine-container.visible, .robot-control.inner-video-control')
      for (let i = 0; i < panels.length; i++) {
        const el = panels[i]
        const style = window.getComputedStyle(el)
        if (style.display !== 'none' && style.visibility !== 'hidden') {
          return true
        }
      }
      return false
    },
    async open(data) {
      if (this.loading) return
      this.loading = false
      this.robotId = data.robotId
      this.controlMode = data?.controlMode || ''
      this.controlModeName = this.controlMode === 'MANUAL' ? '手动模式' : '导航模式'
      const confirmApi = this.hasOtherDialogOpen()
        ? this.$secondaryConfirm
        : this.$primaryConfirm
      try {
        await confirmApi({
          title: '切换模式',
          message: `确认切换为${this.controlModeName}？`,
          confirmText: '确认切换',
          cancelText: '取消',
          onConfirm: () => this.execute()
        })
      } catch (error) {
        // 用户取消
      }
    },
    async execute() {
      if (this.loading === true) {
        return false
      }
      this.loading = true
      try {
        const robot = this.$store.state.websocketRobot.robots.find(item => item.robotId === this.robotId)
        if (!robot || robot.status !== 'online') {
          throw new Error('机器人不在线，不能切换控制模式')
        }
        let response
        if (robot.controlMode === 'NAVIGATION' && this.controlMode === 'MANUAL') {
          response = await takeoverControl(this.robotId, {
            observedStateSeq: robot.stateSeq
          })
        } else {
          const session = await acquireControl(this.robotId, {
            scope: 'ROBOT',
            deviceIds: ['base'],
            actions: ['control.mode.set', 'drive.velocity']
          })
          if (session.code) {
            throw new Error(session.message || session.code)
          }
          response = await setControlMode({
            robotId: this.robotId,
            controlMode: this.controlMode,
            controlSessionId: session.controlSessionId,
            observedStateSeq: robot.stateSeq
          })
        }
        if (response.code) {
          throw new Error(response.message || response.code)
        }
        this.$message.success(
          response.status === 'CONFIRMED' || response.modeChangeStatus === 'CONFIRMED'
            ? `机器人当前已是${response.controlModeName}`
            : `切换指令已下发，等待机器人确认`
        )
      } catch (error) {
        this.$message.error(error.message || '切换模式失败')
        throw error
      } finally {
        this.loading = false
      }
    }
  },
  beforeDestroy() {
    if (this.timer) {
      clearTimeout(this.timer)
    }
  }
}
</script>
