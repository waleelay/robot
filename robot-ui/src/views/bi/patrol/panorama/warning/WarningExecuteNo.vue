<template>
  <el-dialog
    class="execute-no-dialog flx-align-center"
    v-dialogDrag
    width="514px"
    height="292px"
    :visible.sync="executeDialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    :show-close="false"
    title=""
  >
    <el-input v-model="reason" type="textarea" :rows="5" placeholder="请输入无需处置的原因" />
    <template slot="footer">
      <el-button tt="modal"  @click="executeDialogVisible = false">取消</el-button>
      <el-button tt="modal" class="ml10" @click="executeWarning(1)">确认</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { executeAlarm } from '../../../../../api/media';

export default {
  name: 'WarningExecuteNo',
  data() {
    return {
      executeDialogVisible: false,
      loading: false,
      alarmId: '',
      reason: ''
    }
  },
  methods: {
    open(alarmId) {
      if (this.executeDialogVisible) return
      this.loading = false
      this.executeDialogVisible = true
      this.alarmId = alarmId
    },
    async executeWarning(type) {
      if (this.loading === true) {
        return false
      }
      this.loading = true
      try {
        await executeAlarm({ alarmId: this.alarmId, disposalStatus: 'CONFIRMED' })
        this.$message.success('成功切换告警状态')
        this.executeDialogVisible = false
        this.loading = false
        this.$emit('close')
      } catch (error) {
        this.loading = false
      }
    },
  }
}
</script>

<style lang="scss" scoped>
.execute-no-dialog {
  ::v-deep .el-dialog {
    background: #021328;
    border: 1px solid #4395FF;
    .el-dialog__header {
      position: relative;
      padding: 0;
      height: 60px;
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 20px;
      font-style: normal;
      font-weight: 400;
      line-height: 60px;
      text-align: center;
      background: linear-gradient(180deg, rgba(0, 119, 255, 0.49) 0.11%, rgba(184, 154, 255, 0.00) 100.01%);
      &::before {
        position: absolute;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
        background: url(~@/assets/images/new-bi/header-center.png) center center no-repeat;
        content: '';
      }
    }
    .el-dialog__body {
      padding: 20px 10px 10px;
    }

    .el-textarea__inner {
      padding: 20px;
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