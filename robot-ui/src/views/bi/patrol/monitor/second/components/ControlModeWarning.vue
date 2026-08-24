<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center primary-confirm-dialog control-mode-warning-dialog"
    :class="{ 'has-task-selection': showTaskSelection }"
    :visible.sync="visible"
    top="0"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
    @closed="resetState"
  >
    <div class="custom-modal-container primary-confirm-container control-mode-warning">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100" />
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">{{ dialogTitle }}</div>
          <div class="close mr10" @click="close">
            <svg-icon icon-class="close" />
          </div>
        </div>
        <div class="info-content">
          <ControlModeWarningBody
            :message="dialogMessage"
            :show-task-selection="showTaskSelection"
            :selected-task-action.sync="selectedTaskAction"
            :confirming="confirming"
            :can-confirm="canConfirm"
            @cancel="close"
            @confirm="handleConfirm"
          />
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { takeoverControl } from '../../../../../../api/media'
import { pauseTaskRecord, resumeTaskRecord, terminateTaskRecord } from '../../../../../../api/new-bi'
import { isActiveTaskStatus, isPausedTaskStatus, isRunningTaskStatus } from '../../../business/execution-status'
import ControlModeWarningBody from './ControlModeWarningBody.vue'

const ACTION_META = {
  takeover: {
    title: '\u7acb\u5373\u63a5\u7ba1',
    messageInTask: '\u7acb\u5373\u63a5\u7ba1\u524d\uff0c\u8bf7\u6682\u505c\u6216\u7ec8\u6b62\u5f53\u524d\u4efb\u52a1',
    messageDefault: '\u786e\u8ba4\u7acb\u5373\u63a5\u7ba1\uff1f'
  },
  resume: {
    title: '\u6062\u590d',
    messageDefault: '\u662f\u5426\u6062\u590d\u8be5\u4efb\u52a1\uff1f'
  },
  terminate: {
    title: '\u7ec8\u6b62',
    messageDefault: '\u662f\u5426\u7ec8\u6b62\u8be5\u4efb\u52a1\uff1f'
  }
}

export default {
  name: 'ControlModeWarning',
  components: { ControlModeWarningBody },
  data() {
    return {
      visible: false,
      confirming: false,
      robotId: '',
      action: 'takeover',
      selectedTaskAction: ''
    }
  },
  computed: {
    isInTask() {
      const base = this.getRobotBaseInfo()
      if (isActiveTaskStatus(base?.runningTask?.status)) return true
      if (base?.customStatusName === '\u4efb\u52a1\u4e2d') return true
      const taskData = this.$store.state.websocketExtraData?.taskData || {}
      const tasks = Array.isArray(base?.task) ? base.task : []
      return tasks.some(raw => {
        const id = raw?.taskId
        const info = (id != null && taskData[id]) ? taskData[id] : raw
        return info && isActiveTaskStatus(info.status)
      })
    },
    relatedTaskStatus() {
      return this.getRelatedTask()?.status
    },
    isRunningTask() {
      return isRunningTaskStatus(this.relatedTaskStatus)
    },
    showTaskSelection() {
      return this.action === 'takeover' && this.isInTask && this.isRunningTask
    },
    dialogTitle() {
      return ACTION_META[this.action]?.title || '\u63d0\u793a'
    },
    dialogMessage() {
      const meta = ACTION_META[this.action] || {}
      if (this.action === 'takeover') {
        return this.showTaskSelection ? meta.messageInTask : meta.messageDefault
      }
      return meta.messageDefault || ''
    },
    canConfirm() {
      if (this.confirming) return false
      if (this.showTaskSelection) return !!this.selectedTaskAction
      return true
    }
  },
  methods: {
    hasOtherDialogOpen() {
      const dialogs = document.querySelectorAll('.el-dialog__wrapper')
      for (let i = 0; i < dialogs.length; i++) {
        const el = dialogs[i]
        if (el.style.display === 'none') continue
        if (el.classList.contains('control-mode-warning-dialog')) continue
        const style = window.getComputedStyle(el)
        if (style.display !== 'none' && style.visibility !== 'hidden') {
          return true
        }
      }
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
    shouldUseSecondaryConfirm(data = {}) {
      return !!(data.useSecondaryConfirm || this.hasOtherDialogOpen())
    },
    getRobot() {
      const list = this.$store.state.websocketRobot?.robots || []
      return list.find(item => String(item.robotId) === String(this.robotId)) || null
    },
    getRobotBaseInfo() {
      return this.$store.state.websocketExtraData?.robotBaseInfo?.[this.robotId] || {}
    },
    getRelatedTask() {
      const base = this.getRobotBaseInfo()
      if (isActiveTaskStatus(base?.runningTask?.status)) return base.runningTask
      const taskData = this.$store.state.websocketExtraData?.taskData || {}
      const tasks = Array.isArray(base?.task) ? base.task : []
      let paused = null
      for (let i = 0; i < tasks.length; i++) {
        const raw = tasks[i]
        const id = raw?.taskId
        const info = (id != null && taskData[id]) ? taskData[id] : raw
        if (!info) continue
        if (isRunningTaskStatus(info.status)) return info
        if (!paused && isPausedTaskStatus(info.status)) paused = info
      }
      return paused
    },
    getTaskRecordId(task) {
      if (!task) return null
      return task.executionRecordId
        || task.activeWorkflowInstanceId
        || task.workflowInstanceId
        || task.recordId
        || task.taskInstanceId
        || null
    },
    unwrap(res) {
      if (res && res.code !== undefined) {
        if (res.code === '0' || res.code === 0 || res.code === 200) return res.data || {}
        throw new Error(res.message || '\u8bf7\u6c42\u5931\u8d25')
      }
      return res || {}
    },
    resetState() {
      this.confirming = false
      this.selectedTaskAction = ''
      this.action = 'takeover'
      this.robotId = ''
    },
    close() {
      if (this.confirming) return
      this.visible = false
    },
    async open(data = {}) {
      if (this.confirming || this.visible) return

      this.robotId = data.robotId
      this.action = data.action || 'takeover'
      this.selectedTaskAction = ''

      const useSecondary = this.shouldUseSecondaryConfirm(data)
      const isResumeOrTerminate = this.action === 'resume' || this.action === 'terminate'
      const needsTaskSelection = this.action === 'takeover' && this.isInTask && this.isRunningTask

      // 恢复/终止：基本信息走全局二级确认（默认图标，不用 warning.svg）
      // 地图远程控制：二级且无需任务选择时同样走 $secondaryConfirm
      if ((isResumeOrTerminate || useSecondary) && !needsTaskSelection) {
        const meta = ACTION_META[this.action] || {}
        try {
          await this.$secondaryConfirm({
            title: meta.title || '\u63d0\u793a',
            message: meta.messageDefault || '',
            confirmText: '\u786e\u5b9a',
            cancelText: '\u53d6\u6d88',
            onConfirm: () => this.executeAction()
          })
        } catch (error) {
          // 用户取消
        } finally {
          this.resetState()
        }
        return
      }

      // 一级确认，或二级但需任务选择（SecondaryConfirm 不支持自定义内容）
      this.visible = true
    },
    async runTaskAction(action) {
      const task = this.getRelatedTask()
      const recordId = this.getTaskRecordId(task)
      if (recordId == null || recordId === '') {
        throw new Error('\u7f3a\u5c11\u6267\u884c\u8bb0\u5f55\u6807\u8bc6\uff0c\u65e0\u6cd5\u64cd\u4f5c')
      }
      const apiMap = {
        pause: pauseTaskRecord,
        resume: resumeTaskRecord,
        terminate: terminateTaskRecord
      }
      const api = apiMap[action]
      if (!api) return
      const data = this.unwrap(await api(recordId, {}))
      if (data && data.accepted === false) {
        throw new Error((data && data.message) || '\u64cd\u4f5c\u672a\u63a5\u53d7')
      }
      const successMap = {
        pause: '\u5df2\u6682\u505c\u4efb\u52a1',
        resume: '\u5df2\u6062\u590d\u4efb\u52a1',
        terminate: '\u5df2\u7ec8\u6b62\u4efb\u52a1'
      }
      this.$message.success((data && data.message) || successMap[action])
    },
    async executeTakeover() {
      const robot = this.getRobot()
      if (!robot || robot.status !== 'online') {
        throw new Error('\u673a\u5668\u4eba\u4e0d\u5728\u7ebf\uff0c\u4e0d\u80fd\u63a5\u7ba1')
      }
      const response = await takeoverControl(this.robotId, {
        observedStateSeq: robot.stateSeq
      })
      if (response.code) {
        throw new Error(response.message || response.code)
      }
      this.$message.success(
        response.status === 'CONFIRMED' || response.modeChangeStatus === 'CONFIRMED'
          ? `\u673a\u5668\u4eba\u5f53\u524d\u5df2\u662f${response.controlModeName || '\u624b\u52a8\u6a21\u5f0f'}`
          : '\u63a5\u7ba1\u6307\u4ee4\u5df2\u4e0b\u53d1\uff0c\u7b49\u5f85\u673a\u5668\u4eba\u786e\u8ba4'
      )
    },
    async executeAction() {
      if (this.action === 'takeover') {
        if (this.showTaskSelection) {
          await this.runTaskAction(this.selectedTaskAction)
        }
        await this.executeTakeover()
        return
      }
      if (this.action === 'resume') {
        await this.runTaskAction('resume')
        return
      }
      if (this.action === 'terminate') {
        await this.runTaskAction('terminate')
      }
    },
    async handleConfirm() {
      if (!this.canConfirm) return
      this.confirming = true
      try {
        await this.executeAction()
        this.visible = false
      } catch (error) {
        this.$message.error(error?.message || '\u64cd\u4f5c\u5931\u8d25')
      } finally {
        this.confirming = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.control-mode-warning-dialog.has-task-selection {
  ::v-deep .primary-confirm-container .box {
    width: 650px;
  }

  ::v-deep .primary-confirm-container .top .title {
    font-size: 18px;
    font-family: "Alibaba PuHuiTi";
    font-weight: 500;
  }
}
</style>
