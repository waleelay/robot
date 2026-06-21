<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-03-31 17:22:03
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\modal\warning\WarningS.vue
 * @Version: 
-->
<template>
  <!-- <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="模态框"
  >
    <template slot="footer"></template> -->
    <div class="flex popver-warning-s">
      <div class="warning-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <!-- <div class="custom-arrow"></div> -->
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">告警快速处理</div>
              <div class="status ml10 success">
                {{ details.type === 0 ? '业务告警' : details.type === 1 ? '任务告警' : '设备告警' }}
              </div>
            </div>
            <!-- <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div> -->
          </div>
          <div class="img pr10 pl10 mt5 w100 hp285">
            <div style="position: absolute; color: #fff; font-size: 12px; line-height: 16px; font-weight: 600;" class="mt10 ml10">
              {{ details.name }}
            </div>
            <img src="@/assets/images/new-bi/video-bg.png" alt="" class="w100 h100" height="270" style="padding-bottom: 0 !important;">
          </div>
          <div class="info-content pr10 pl10 flex">
            <div class="item mt10 w50">设备名称：机器狗001</div>
            <div class="item mt10 w50">告警信息：{{ details.info }}</div>
            <div class="item mt10 w50">告警时间：{{ details.time }}</div>
            <div class="item mt10 w50 flx-align-center">严重等级：
              <div class="status" :class="{'red': details.type === 0, 'orange': details.type === 1, 'green': details.type === 2}">
                {{ details.type === 0 ? '高风险' : details.type === 1 ? '中风险' : '低风险' }}
              </div>
            </div> 
            <div class="item mt10 w50">告警位置：B区-5号通道</div>
          </div>
          <div class="btns mt20 mr10 mb20 ml10 flx-align-center">
            <el-button type="primary" class="flex1">预案处置</el-button>
            <el-button type="primary" class="flex1 success" @click="$refs.warningExecuteNoRef.open(details.ErrorId)">一键处置</el-button>
            <el-button type="primary" class="flex1 error" @click="$refs.warningExecuteErrorRef.open(details.ErrorId)">误报屏蔽</el-button>
          </div>
        </div>
      </div>
      <WarningExecuteError ref="warningExecuteErrorRef" @getAllData="getAllData" />
      <WarningExecuteNo ref="warningExecuteNoRef" @getAllData="getAllData" />
    </div>
  <!-- </el-dialog> -->
</template>

<script>
import WarningExecuteNo from './WarningExecuteNo.vue';
import WarningExecuteError from './WarningExecuteError.vue';
export default {
  name: 'WarningS',
  components: { WarningExecuteNo, WarningExecuteError },
  props: {
    details: {
      type: Object,
      defauls: () => ({})
    }
  },
  data() {
    return {
      dialogVisible: true,
    }
  },
  methods: {
    showModal() {
      this.dialogVisible = true;
    },
    getAllData() {

    }
  }
}
</script>

<style lang="scss" scoped>
@import './scss/warning-s.scss';
</style>