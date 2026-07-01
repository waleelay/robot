<template>
  <el-dialog
    class="execute-dialog flx-align-center"
    v-dialogDrag
    width="912px"
    height="180px"
    :visible.sync="executeDialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <template slot="title"></template>
    <div class="flx-center w100 h100">
      <span class="text">指令已确认 派遣中</span>
    </div>
    <template slot="footer"></template>
  </el-dialog>
</template>

<script>
export default {
  name: 'WarningExecute',
  data() {
    return {
      executeDialogVisible: false,
      loading: false,
      timer: null
    }
  },
  methods: {
    open() {
      if (this.executeDialogVisible) return
      this.loading = false
      this.executeDialogVisible = true
      this.timer = setTimeout(() => {
        this.executeDialogVisible = false
        // this.$emit('close')
      }, 3000)
    },
  },
  beforeDestroy() {
    if (this.timer) {
      clearTimeout(this.timer)
    }
  }
}
</script>

<style lang="scss" scoped>
.execute-dialog {
  ::v-deep .el-dialog {
    margin-top: 0 !important;
    background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 6.03%, rgba(4, 23, 62, 0.32) 56.39%, rgba(7, 45, 94, 0.31) 101.39%, rgba(4, 62, 151, 0.40) 109.49%);
    backdrop-filter: blur(15px);
    .el-dialog__header {
      display: none;
    }
    .el-dialog__body {
      height: 180px;
      padding: 0 !important;
    }
  }
}
.text {
  padding: 10px 60px;
  color: #EFF9FF;
  font-family: "Alibaba PuHuiTi";
  font-size: 16px;
  line-height: 20px;
  letter-spacing: 0.857px;
  border-radius: 2px;
  border: 1px solid #2A86F3;
  background: rgba(9, 45, 72, 0.50);
  box-shadow: 0 0 20px 0 #2A86F3 inset;
}
</style>