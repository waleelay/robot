<template>
  <el-dialog
    class="secondary-confirm-dialog flx-align-center"
    width="514px"
    :visible.sync="dialogVisible"
    top="0"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <template slot="title">
      <div class="flx-align-center custom-header">
        <img
          v-if="currentShowIcon"
          src="@/assets/images/new-bi/warning-icon.png"
          alt=""
          width="24"
          height="24"
        >
        <span :class="{ ml10: currentShowIcon }">{{ currentTitle }}</span>
      </div>
    </template>
    {{ currentMessage }}
    <template slot="footer">
      <el-button tt="modal" @click="handleCancel">{{ currentCancelText }}</el-button>
      <el-button tt="modal" class="ml10" :loading="loading" @click="handleConfirm">{{ currentConfirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script>
import confirmDragMixin from './confirmDragMixin'

const DEFAULTS = {
  title: '\u63d0\u793a',
  message: '',
  confirmText: '\u786e\u8ba4',
  cancelText: '\u53d6\u6d88',
  showIcon: true,
  draggable: true
}

export default {
  name: 'SecondaryConfirm',
  mixins: [confirmDragMixin],
  props: {
    title: { type: String, default: DEFAULTS.title },
    message: { type: String, default: DEFAULTS.message },
    confirmText: { type: String, default: DEFAULTS.confirmText },
    cancelText: { type: String, default: DEFAULTS.cancelText },
    showIcon: { type: Boolean, default: DEFAULTS.showIcon }
  },
  data() {
    return {
      dialogVisible: false,
      loading: false,
      options: {},
      resolver: null
    }
  },
  computed: {
    currentTitle() {
      return this.options.title != null ? this.options.title : this.title
    },
    currentMessage() {
      return this.options.message != null ? this.options.message : this.message
    },
    currentConfirmText() {
      return this.options.confirmText != null ? this.options.confirmText : this.confirmText
    },
    currentCancelText() {
      return this.options.cancelText != null ? this.options.cancelText : this.cancelText
    },
    currentShowIcon() {
      return this.options.showIcon != null ? this.options.showIcon : this.showIcon
    }
  },
  methods: {
    open(options = {}) {
      if (this.dialogVisible) return Promise.reject('busy')
      this.options = { ...options }
      this.loading = false
      this.dialogVisible = true
      return new Promise((resolve, reject) => {
        this.resolver = { resolve, reject }
      })
    },
    close() {
      this.teardownConfirmDrag()
      this.dialogVisible = false
      this.loading = false
      this.options = {}
      this.resolver = null
    },
    handleCancel() {
      const resolver = this.resolver
      this.close()
      this.$emit('cancel')
      if (resolver) resolver.reject('cancel')
    },
    async handleConfirm() {
      if (this.loading) return
      this.loading = true
      try {
        if (typeof this.options.onConfirm === 'function') {
          await this.options.onConfirm()
        }
        const resolver = this.resolver
        this.$emit('confirm')
        this.close()
        if (resolver) resolver.resolve(true)
      } catch (error) {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.secondary-confirm-dialog {
  ::v-deep .el-dialog {
    margin-top: 0 !important;
    background: #021328;
    border: 1px solid #4395FF;
    .el-dialog__header {
      padding: 0;
      padding-left: 20px;
      height: 60px;
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 20px;
      font-style: normal;
      font-weight: 400;
      line-height: 60px;
      background: linear-gradient(180deg, rgba(0, 119, 255, 0.49) 0.11%, rgba(184, 154, 255, 0.00) 100.01%);
    }
    .el-dialog__body {
      padding: 10px 30px 20px 27px;
      color: rgba(255, 255, 255, 0.80);
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 18px;
    }
    .el-dialog__footer {
      padding: 0 10px 18px;
      text-align: right;
      .el-button[tt="modal"] {
        height: auto;
        padding: 10px 20px;
        color: #FFF;
        text-align: center;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        font-style: normal;
        font-weight: 400;
        line-height: 20px;
        letter-spacing: 1.488px;
        border-radius: 3.473px;
        border: 1px solid #4395FF;
        background: rgba(9, 45, 72, 0.50);
      }
    }
  }
}
</style>
