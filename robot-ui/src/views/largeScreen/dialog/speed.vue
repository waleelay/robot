<template>
  <el-dialog
    title="设定速度"
    ref="speedDialogRef"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :modal="false"
    :show-close="true"
    :before-close="handleClose"
    width="420px"
    customClass="speed-dialog"
  >
    <el-form ref="elForm" :model="formData" :rules="rules" label-width="120px">
      <el-form-item label="速度值（m/s）" prop="speed">
        <el-input-number v-model="formData.speed" placeholder="速度值（m/s）" :precision='2'></el-input-number>
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </div>
  </el-dialog>
</template>
<script>
export default {
  components: {},
  props: [],
  data() {
    return {
      formData: {},
      rules: {
        speed: [{
          required: true,
          message: '速度值（m/s）',
          trigger: 'blur'
        }],
      },
      dialogVisible: false
    }
  },
  methods: {
    handleClose() {
      this.$refs['elForm'].resetFields()
      this.dialogVisible = false
    },
    handleConfirm() {
      this.$refs['elForm'].validate(valid => {
        if (!valid) return
        this.$emit('changeSpeed', this.formData.speed)
        this.handleClose()
      })
    },
  }
}

</script>
<style lang="scss">
.speed-dialog {
  &:not(.is-fullscreen) {
    margin-top: 40vh !important;
  }
  .el-dialog__header {
    background: rgba(0,0,0,0.7);
    .el-dialog__title, .el-dialog__headerbtn i {
      color: #fff;
    }
  }
  .el-button {
    &--primary {
      background: rgba(0,0,0,0.7) !important;
      border-color: #000 !important;
    }
  }
}
</style>
