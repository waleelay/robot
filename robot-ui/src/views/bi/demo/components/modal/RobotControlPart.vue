<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-02 14:10:40
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\modal\RobotControlPart.vue
 * @Version: 
-->
<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="异常报告"
  >
    <template slot="footer"></template>
    <div class="flex mb20 ml72">
      <div class="custom-modal-container robot-control-container">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">监区巡逻机器狗-01</div>
              <div class="status success ml10">空闲中</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt10 pr20 pb20 pl20 flex" style="align-items: flex-start">
            <div class="wp480 hp270 video" style="border: 1px solid #159AFF">
            </div>
            <div class="flex1 pl28 control flx-align-center">
              <div class="w50">
                <div class="custom-tab-button flex">
                  <div v-for="item in controlInfo.tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': controlInfo.tabIndex === item.value }" @click="controlInfo.tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
                </div>
                <div class="mt28 flex-column flx-center">
                  <img src="@/assets/images/new-bi/dog.png" alt="" srcset="" width="148" height="95">
                  <!-- <div class="mt26" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务名称：A区-夜间巡逻</div>
                  <div class="mt12" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务时间：20:00-22:00</div>
                  <div class="mt12" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务类型：status success</div> -->
                  <div class="mt38 robot-control" style="position: relative; top: 0; left: 0;justify-content: center">
                    <div style="color: #FFF; font-family: Inter; font-size: 12px;line-height: 15px; text-align: center;">当前装备无任务，可添加任务</div>
                    <div class="flx-center mt10 w100 control-btns">
                      <el-button type="primary" plain class="wp132" style="color: #17D1FF">
                        <!-- <svg-icon icon-class="plus" class="mr10" /> -->
                        添加任务
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
              <div class="w50 flex-column robot-control" style="position: relative; top: 0; left: 0;justify-content: center">
                <ControlPart />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import ControlPart from '../ControlPart.vue'
export default {
  name: 'RobotControlPart',
  components: {
    ControlPart,
  },
  data() {
    return {
      dialogVisible: false,
      controlInfo: {
        tabList: [
          {
            label: '基础控制',
            value: 0
          },
          {
            label: '高级控制',
            value: 1
          }
        ],
        tabIndex: 0,
      }
    }
  },
  methods: {
    showModal() {
      this.dialogVisible = true;
    }
  }
}
</script>