<template>
  <el-dialog
    class="error-dialog flx-align-center"
    v-dialogDrag
    width="514px"
    height="164px"
    :visible.sync="errorDialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <template slot="title">
      <div class="flx-align-center custom-header">
        <img src="@/assets/images/bi/new/warning-icon.png" alt="" width="24px" height="24px">
        <span class="ml10">误报</span>
      </div>
    </template>
    是否确认为误报
    <template slot="footer">
      <el-button tt="modal"  @click="errorDialogVisible = false">取消</el-button>
      <el-button tt="modal" class="ml10" @click="executeWarning(2)">确认</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { executeAlarm } from '../../../../../api/media';
export default {
  name: 'WarningExecuteError',
  data() {
    return {
      errorDialogVisible: false,
      loading: false,
      alarmId: ''
    }
  },
  methods: {
    open(alarmId) {
      if (this.errorDialogVisible) return
      this.loading = false
      this.errorDialogVisible = true
      this.alarmId = alarmId
    },
    async executeWarning(type) {
      if (this.loading === true) {
        return false
      }
      this.loading = true
      try {
        await executeAlarm({ alarmId: this.alarmId, disposalStatus: 'FALSE_ALARM' }).then(res => {
          this.$message.success('成功切换告警状态')
          this.errorDialogVisible = false
          this.loading = false
          this.$emit('close')
        })
      } catch (error) {
        this.loading = false
      }
    },
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

    .el-textarea__inner {
      color: rgba(255, 255, 255, 0.80);
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 18px;
      background: #0C2748;
      border: none;
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
        line-height: 20px; /* 142.857% */
        letter-spacing: 1.488px;
        border-radius: 3.473px;
        border: 1px solid #4395FF;
        background: rgba(9, 45, 72, 0.50);
      }
    }
  }
}
</style>