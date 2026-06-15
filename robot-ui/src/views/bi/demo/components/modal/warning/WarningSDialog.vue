<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="模态框"
  >
    <template slot="footer"></template>
    <div class="flex mb20 ml72">
      <div class="custom-modal-container robot-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">告警快速处理</div>
              <div class="status ml10">业务告警 </div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <img src="@/assets/images/bi/bg.png" alt="" class="p10 w100" height="190" style="padding-bottom: 0 !important;">
          <div class="info-content pr10 pl10 flex flex-column">
            <div class="item mt10">告警时间：2026.03.05</div>
            <div class="item mt10">告警位置：B区-5号通道</div>
            <div class="item mt10 flx-align-center">告警等级：
              <span class="status">低风险</span>
            </div> 
          </div>
          <div class="btns m10 mb20 flx-align-center">
            <el-button type="primary" class="flex1">预案处置</el-button>
            <el-button type="primary" class="flex1 success">一键处置</el-button>
            <el-button type="primary" class="flex1 error">误报屏蔽</el-button>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'WarningInfoDialog',
  data() {
    return {
      dialogVisible: true,
    }
  },
  methods: {
    showModal() {
      this.dialogVisible = true;
    }
  }
}
</script>