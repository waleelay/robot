<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center primary-confirm-dialog"
    :visible.sync="dialogVisible"
    top="0"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <div class="custom-modal-container primary-confirm-container">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100" />
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">{{ currentTitle }}</div>
          <div class="close mr10" @click="handleCancel">
            <svg-icon icon-class="close" />
          </div>
        </div>
        <div class="info-content">
          <div class="message flx-align-center">
            <img
              v-if="currentShowIcon"
              src="@/assets/images/new-bi/warning-icon.png"
              alt=""
              width="20"
              height="20"
            >
            <span :class="{ ml10: currentShowIcon }">{{ currentMessage }}</span>
          </div>
          <div class="btns mt22">
            <el-button tt="modal" @click="handleCancel">{{ currentCancelText }}</el-button>
            <el-button tt="modal" class="ml10" :loading="loading" @click="handleConfirm">{{ currentConfirmText }}</el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import confirmDragMixin from './confirmDragMixin'

const DEFAULTS = {
  title: '提示',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  showIcon: true,
  draggable: true
}

export default {
  name: 'PrimaryConfirm',
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
        // keep dialog open for retry
        this.loading = false
      }
    }
  }
}
</script>