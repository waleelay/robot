<template>
  <!-- 导航 → 手动：先暂停/终止，再确认切换 -->
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center primary-confirm-dialog control-mode-warning-dialog"
    :visible.sync="navToManualVisible"
    top="0"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
    @closed="resetNavToManualState"
  >
    <div class="custom-modal-container primary-confirm-container control-mode-warning">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100" />
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">切换为手动模式</div>
          <div class="close mr10" @click="closeNavToManual">
            <svg-icon icon-class="close" />
          </div>
        </div>
        <div class="info-content">
          <div class="message flx-align-center">
            <img
              src="@/assets/images/new-bi/warning-icon.png"
              alt=""
              width="20"
              height="20"
            >
            <span class="ml10">{{ dialogTipText }}</span>
          </div>
          <div v-if="taskActionDone" class="task-hint mt12">
            已{{ taskActionDoneLabel }}任务，可确认切换。
          </div>
          <div class="btns mt22">
            <el-button tt="modal" :disabled="!!taskActing || modeSwitching" @click="closeNavToManual">取消</el-button>
            <template v-if="showTaskActionButtons">
              <el-button
                v-if="showPauseButton"
                tt="modal"
                class="ml10"
                :class="{ 'is-done': taskActionDone === 'pause' }"
                :loading="taskActing === 'pause'"
                :disabled="!!taskActing || !!taskActionDone || modeSwitching"
                @click="handleTaskAction('pause')"
              >
                {{ taskActionDone === 'pause' ? '已暂停' : '暂停任务' }}
              </el-button>
              <el-button
                v-if="showTerminateButton"
                tt="modal"
                class="ml10"
                :class="{ 'is-done': taskActionDone === 'terminate' }"
                :loading="taskActing === 'terminate'"
                :disabled="!!taskActing || !!taskActionDone || modeSwitching"
                @click="handleTaskAction('terminate')"
              >
                {{ taskActionDone === 'terminate' ? '已终止' : '终止任务' }}
              </el-button>
            </template>
            <el-button
              tt="modal"
              class="ml10"
              :loading="modeSwitching"
              :disabled="!canConfirmSwitch || !!taskActing"
              :title="confirmSwitchTitle"
              @click="confirmNavToManual"
            >
              确认切换
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { acquireControl, setControlMode, takeoverControl } from '../../../../../../api/media'
import { pauseTaskRecord, terminateTaskRecord } from '../../../../../../api/new-bi'
import { isActiveTaskStatus, isPausedTaskStatus, isRunningTaskStatus } from '../../../business/execution-status'

export default {
  name: 'ControlModeWarning',
  data() {
    return {
      loading: false,
      timer: null,
      robotId: '',
      controlMode: '',
      controlModeName: '',
      navToManualVisible: false,
      taskActing: '',
      taskActionDone: '',
      modeSwitching: false
    }
  },
  computed: {
    // 是否处于任务中（running 或 paused）
    isInTask() {
      const base = this.getRobotBaseInfo()
      if (isActiveTaskStatus(base?.runningTask?.status)) return true
      if (base?.customStatusName === '任务中') return true
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
    isPausedTask() {
      return isPausedTaskStatus(this.relatedTaskStatus)
    },
    // 任务中才展示暂停/终止；已暂停不再展示暂停；成功处理后仍保留完成态直到关闭
    showTaskActionButtons() {
      return this.isInTask || !!this.taskActionDone
    },
    showPauseButton() {
      return this.taskActionDone === 'pause' || (this.showTaskActionButtons && this.isRunningTask && !this.taskActionDone)
    },
    showTerminateButton() {
      return this.taskActionDone === 'terminate' || (this.showTaskActionButtons && this.isInTask && !this.taskActionDone)
    },
    // 无任务或已暂停可直接确认；running 须先暂停/终止成功
    canConfirmSwitch() {
      if (this.modeSwitching) return false
      if (!this.isInTask || this.taskActionDone || this.isPausedTask) return true
      return false
    },
    dialogTipText() {
      if (this.isRunningTask && !this.taskActionDone) {
        return '当前装备处于任务中，切换为手动模式前请先暂停或终止任务。'
      }
      return '确认将控制模式切换为手动模式？'
    },
    confirmSwitchTitle() {
      if (this.canConfirmSwitch) return '确认切换为手动模式'
      return '请先暂停或终止任务'
    },
    taskActionDoneLabel() {
      if (this.taskActionDone === 'pause') return '暂停'
      if (this.taskActionDone === 'terminate') return '终止'
      return '处理'
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
    getRobot() {
      const list = this.$store.state.websocketRobot?.robots || []
      return list.find(item => String(item.robotId) === String(this.robotId)) || null
    },
    getRobotBaseInfo() {
      return this.$store.state.websocketExtraData?.robotBaseInfo?.[this.robotId] || {}
    },
    /** 当前执行中的任务（running 或 paused，优先 running） */
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
        throw new Error(res.message || '请求失败')
      }
      return res || {}
    },
    resetNavToManualState() {
      this.taskActing = ''
      this.taskActionDone = ''
      this.modeSwitching = false
    },
    closeNavToManual() {
      if (this.taskActing || this.modeSwitching) return
      this.navToManualVisible = false
    },
    async open(data) {
      if (this.loading || this.navToManualVisible || this.modeSwitching) return
      this.robotId = data.robotId
      this.controlMode = data?.controlMode || ''
      this.controlModeName = this.controlMode
      const useSecondaryConfirm = !!data.useSecondaryConfirm
      const robot = this.getRobot()
      const isNavToManual = robot?.controlMode === '导航模式' && this.controlMode === '手动模式'

      // 地图远程控制面板：一律走全局二级确认框
      if (isNavToManual && !useSecondaryConfirm) {
        this.resetNavToManualState()
        this.navToManualVisible = true
        return
      }

      const confirmApi = (useSecondaryConfirm || this.hasOtherDialogOpen())
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
    async handleTaskAction(action) {
      if (!this.isInTask) return
      if (this.taskActing || this.taskActionDone || this.modeSwitching) return
      const task = this.getRelatedTask()
      const recordId = this.getTaskRecordId(task)
      if (recordId == null || recordId === '') {
        this.$message.error('缺少执行记录标识，无法操作')
        return
      }
      const api = action === 'pause' ? pauseTaskRecord : terminateTaskRecord
      const successMessage = action === 'pause' ? '已暂停任务' : '已终止任务'
      this.taskActing = action
      try {
        const data = this.unwrap(await api(recordId, {}))
        if (data && data.accepted === false) {
          this.$message.warning((data && data.message) || '操作未接受')
          return
        }
        this.taskActionDone = action
        this.$message.success((data && data.message) || successMessage)
      } catch (error) {
        this.$message.error(error?.message || (action === 'pause' ? '暂停失败' : '终止失败'))
      } finally {
        this.taskActing = ''
      }
    },
    async confirmNavToManual() {
      if (!this.canConfirmSwitch) return
      this.modeSwitching = true
      try {
        await this.execute()
        this.navToManualVisible = false
      } catch (error) {
        // execute 内已提示
      } finally {
        this.modeSwitching = false
      }
    },
    async execute() {
      if (this.loading === true) {
        return false
      }
      this.loading = true
      try {
        const robot = this.getRobot()
        if (!robot || robot.status !== 'online') {
          throw new Error('机器人不在线，不能切换控制模式')
        }
        let response
        if (robot.controlMode === '导航模式' && this.controlMode === '手动模式') {
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

<style lang="scss" scoped>
.control-mode-warning {
  .message {
    align-items: flex-start;
    span {
      color: rgba(255, 255, 255, 0.88);
      font-family: "Microsoft YaHei";
      font-size: 14px;
      line-height: 22px;
    }
  }
  .task-hint {
    color: #0BF9FE;
    font-family: "Microsoft YaHei";
    font-size: 12px;
    line-height: 18px;
  }
  .btns {
    display: flex;
    justify-content: flex-end;
    flex-wrap: wrap;
    .el-button.is-disabled,
    .el-button.is-disabled:hover {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .el-button.is-done {
      border-color: #0BF9FE !important;
      color: #0BF9FE !important;
    }
  }
}
</style>
