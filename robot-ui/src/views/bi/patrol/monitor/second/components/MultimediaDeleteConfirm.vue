<template>
  <el-dialog
    class="error-dialog flx-align-center"
    v-dialogDrag
    width="514px"
    height="164px"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <template slot="title">
      <div class="flx-align-center custom-header">
        <img src="@/assets/images/new-bi/warning-icon.png" alt="" width="24px" height="24px">
        <span class="ml10">删除</span>
      </div>
    </template>
    记录删除后不可恢复，确认执行删除操作?
    <template slot="footer">
      <el-button tt="modal" @click="dialogVisible = false">取消</el-button>
      <el-button tt="modal" class="ml10" :loading="loading" @click="confirmDelete">确认</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { deleteFile } from '../../../../../../api/media.js'

export default {
  name: 'MultimediaDeleteConfirm',
  data() {
    return {
      dialogVisible: false,
      loading: false,
      item: null
    }
  },
  methods: {
    open(item) {
      if (this.dialogVisible) return
      this.loading = false
      this.item = item || null
      this.dialogVisible = true
    },
    async confirmDelete() {
      if (this.loading || !this.item?.fileId) return
      this.loading = true
      try {
        await deleteFile(this.item.fileId)
        // this.$message.success('删除成功')
        this.dialogVisible = false
        this.$emit('confirm', this.item)
      } catch (error) {
        // this.$message.error('删除失败')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.error-dialog {
  ::v-deep .el-dialog {
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
