<template>
  <el-dialog
    class="bi-form-dialog-wrapper flx-align-center"
    v-dialogDrag
    width="420px"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    ref="speedDialogRef"
  >
    <template slot="title">
      <div class="bi-dialog-header">
        <div class="title">设定速度</div>
      </div>
    </template>
    <el-form ref="formRef" :model="formData" class="custom-form pt5">
      <el-form-item label="速度值（m/s）" prop="speed" :rules="{ required: true, message: '速度值（m/s）', trigger: 'blur' }">
        <el-input-number v-model="formData.speed" placeholder="速度值（m/s）" :precision='2'></el-input-number>
      </el-form-item>
    </el-form>
    <template slot="footer">
      <el-button tt="modal" @click="handleClose">取消</el-button>
      <el-button tt="modal" @click="handleConfirm">确定</el-button>
    </template>
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
      this.$refs['formRef'].resetFields()
      this.dialogVisible = false
    },
    handleConfirm() {
      this.$refs['formRef'].validate(valid => {
        if (!valid) return
        this.$emit('changeSpeed', this.formData.speed)
        this.handleClose()
      })
    },
  }
}
</script>

<style lang="scss" scoped>
@import "./speed.scss";
</style>
