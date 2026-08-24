<template>
  <div class="control-mode-warning-body">
    <div class="message flx-align-center">
      <svg-icon icon-class="warning" class="warning-icon" />
      <span v-if="showTaskSelection" class="message-text ml10">
        {{ messagePrefix }}<span class="message-nowrap">{{ messageHighlight }}</span>{{ messageSuffix }}
      </span>
      <span v-else class="message-text ml10">{{ message }}</span>
    </div>

    <div v-if="showTaskSelection" class="task-section">
      <div class="task-section-title flx-align-center">
        <span class="task-section-bar" />
        <span>{{ taskSectionTitle }}</span>
      </div>
      <div class="task-options">
        <div
          class="task-option curp"
          :class="{ 'is-active': selectedTaskAction === 'pause' }"
          @click="$emit('update:selectedTaskAction', 'pause')"
        >
          <img src="@/assets/images/new-bi/task-pause-icon.svg" alt="" width="46" height="46">
          <div class="task-option-text">
            <div class="task-option-name">{{ pauseTaskName }}</div>
            <div class="task-option-desc">{{ pauseTaskDesc }}</div>
          </div>
        </div>
        <div
          class="task-option curp"
          :class="{ 'is-active': selectedTaskAction === 'terminate' }"
          @click="$emit('update:selectedTaskAction', 'terminate')"
        >
          <img src="@/assets/images/new-bi/task-terminate-icon.svg" alt="" width="46" height="46">
          <div class="task-option-text">
            <div class="task-option-name">{{ terminateTaskName }}</div>
            <div class="task-option-desc">{{ terminateTaskDesc }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="btns mt22">
      <el-button tt="modal" :disabled="confirming" @click="$emit('cancel')">{{ cancelText }}</el-button>
      <el-button
        tt="modal"
        class="ml10"
        :loading="confirming"
        :disabled="!canConfirm"
        @click="$emit('confirm')"
      >
        {{ confirmText }}
      </el-button>
    </div>
  </div>
</template>

<script>
const TEXT = {
  messagePrefix: '\u7acb\u5373\u63a5\u7ba1\u524d\uff0c\u8bf7',
  messageHighlight: '\u6682\u505c\u6216\u7ec8\u6b62',
  messageSuffix: '\u5f53\u524d\u4efb\u52a1',
  taskSectionTitle: '\u9009\u62e9\u8981\u5904\u7406\u7684\u4efb\u52a1',
  pauseTaskName: '\u6682\u505c\u4efb\u52a1',
  pauseTaskDesc: '\u6682\u505c\u5f53\u524d\u6267\u884c\u4efb\u52a1',
  terminateTaskName: '\u7ec8\u6b62\u4efb\u52a1',
  terminateTaskDesc: '\u7ec8\u6b62\u5f53\u524d\u6267\u884c\u4efb\u52a1',
  cancelText: '\u53d6\u6d88',
  confirmText: '\u786e\u5b9a'
}

export default {
  name: 'ControlModeWarningBody',
  props: {
    message: {
      type: String,
      default: ''
    },
    showTaskSelection: {
      type: Boolean,
      default: false
    },
    selectedTaskAction: {
      type: String,
      default: ''
    },
    confirming: {
      type: Boolean,
      default: false
    },
    canConfirm: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return TEXT
  }
}
</script>

<style lang="scss" scoped>
.control-mode-warning-body {
  .message {
    align-items: flex-start;
  }

  .warning-icon {
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    font-size: 20px;
    color: #FFDD00;
  }

  .message-text {
    color: #FFF;
    font-family: "Microsoft YaHei";
    font-size: 16px;
  }

  .message-nowrap {
    white-space: nowrap;
  }

  .task-section {
    margin-top: 17px;
  }

  .task-section-title {
    gap: 10px;
    color: #D0DEEE;
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: normal;
  }

  .task-section-bar {
    width: 2px;
    height: 12px;
    background: #2A86F3;
    flex-shrink: 0;
  }

  .task-options {
    display: flex;
    gap: 21px;
    margin-top: 20px;
    flex-wrap: nowrap;
  }

  .task-option {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1 1 0;
    min-width: 0;
    padding: 14px 20px;
    border-radius: 4px;
    background: #243348;
    box-sizing: border-box;
    border: 1px solid transparent;
    transition: background 0.2s ease, border-color 0.2s ease;

    &.is-active {
      background: #0A2243;
      border-color: #159AFF;
    }
  }

  .task-option-name {
    color: #FFF;
    font-family: "Microsoft YaHei";
    font-size: 16px;
    line-height: normal;
    white-space: nowrap;
  }

  .task-option-desc {
    margin-top: 11px;
    color: #92A0B6;
    font-family: "Microsoft YaHei";
    font-size: 12px;
    line-height: normal;
    white-space: nowrap;
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
  }
}
</style>
